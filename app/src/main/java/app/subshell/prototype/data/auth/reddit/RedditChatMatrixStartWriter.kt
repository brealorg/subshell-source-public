package app.subshell.prototype.data.auth.reddit

// SUBSHELL_MESSAGES02D5A_REAL_NEW_DIRECT_CHAT_V1

import android.util.Log
import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.chat.RedditChatWebTokenResult
import app.subshell.prototype.data.account.DirectChatStartFailureKind
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal sealed interface AuthenticatedRedditDirectChatStartResult {
    data class Accepted(
        val request: DirectChatStartRequest,
        val eventId: String,
        val roomCreated: Boolean,
    ) : AuthenticatedRedditDirectChatStartResult

    data class Failed(
        val kind: DirectChatStartFailureKind,
        val statusCode: Int? = null,
    ) : AuthenticatedRedditDirectChatStartResult
}

internal interface AuthenticatedRedditDirectChatStartWriter {
    suspend fun start(
        request: DirectChatStartRequest,
    ): AuthenticatedRedditDirectChatStartResult
}

/**
 * Starts or resumes one Reddit 1:1 Direct Chat using the protocol proven from Reddit Web:
 *
 * user_directory/search -> rooms?type=direct&with_user=... ->
 * createRoom { preset: reddit_dm, invite: [...] } only when lookup is definitively empty ->
 * send/m.room.message.
 *
 * createRoom and message send are non-retrying writes. An IOException at either write boundary is
 * deliberately reported as OUTCOME_UNKNOWN because Reddit may already have accepted the mutation.
 */
internal class RedditChatMatrixStartWriter(
    private val capability: AuthInboxReadCapability,
    private val tokenProvider: RedditChatWebTokenSource,
    private val client: OkHttpClient,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionCache: RedditChatMatrixSessionCache = RedditChatMatrixSessionCache(),
    private val mediaWriter: RedditChatMatrixMediaWriter? = null,
) : AuthenticatedRedditDirectChatStartWriter {
    private val startMutex = Mutex()

    override suspend fun start(
        request: DirectChatStartRequest,
    ): AuthenticatedRedditDirectChatStartResult {
        startMutex.lock()
        try {
            return withContext(ioDispatcher) {
                startLocked(request)
            }
        } finally {
            startMutex.unlock()
        }
    }

    private suspend fun startLocked(
        request: DirectChatStartRequest,
    ): AuthenticatedRedditDirectChatStartResult {
        val account = request.accountStableId.trim()
        val participant = request.participantUsername.trim()
        val body = request.text.trim()
        val media = request.media
        if (
            account.isEmpty() ||
            participant.isEmpty() ||
            participant.any { it.isWhitespace() || it == '/' } ||
            body.isNotEmpty() == (media != null) ||
            (
                media != null &&
                    (
                        media.uri.isBlank() ||
                            !media.mimeType.trim().startsWith("image/", ignoreCase = true)
                        )
                )
        ) {
            return failed("validation", DirectChatStartFailureKind.VALIDATION)
        }

        val preparedMedia = if (media != null) {
            val writer = mediaWriter
                ?: return failed("media_prepare", DirectChatStartFailureKind.UNAVAILABLE)
            when (val staged = writer.prepareMedia(media)) {
                is RedditMediaStageResult.Staged -> staged
                RedditMediaStageResult.UnsupportedType ->
                    return failed("media_prepare", DirectChatStartFailureKind.MEDIA_UNSUPPORTED)
                RedditMediaStageResult.TooLarge ->
                    return failed("media_prepare", DirectChatStartFailureKind.MEDIA_TOO_LARGE)
                RedditMediaStageResult.Empty,
                RedditMediaStageResult.Unavailable,
                -> return failed("media_prepare", DirectChatStartFailureKind.MEDIA_UNAVAILABLE)
            }
        } else {
            null
        }

        try {
        val oauthSession = when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> authorization.session
            else -> return failed("session", DirectChatStartFailureKind.SESSION)
        }
        val redditIdentity = oauthSession.identity
            ?: return failed("session", DirectChatStartFailureKind.SESSION)
        if (
            authenticatedAccountId(
                provider = oauthSession.provider,
                clientMode = oauthSession.clientMode,
                providerAccountId = redditIdentity.providerAccountId,
            ) != account ||
            !oauthSession.tokens.grants(RedditOAuth.liveInboxReadScopes)
        ) {
            return failed("session", DirectChatStartFailureKind.SESSION)
        }

        val webTokenResult = when (val result = tokenProvider.token()) {
            is RedditChatWebTokenResult.Success -> result
            RedditChatWebTokenResult.NoRedditWebSession,
            RedditChatWebTokenResult.Failed,
            -> return failed("web_session", DirectChatStartFailureKind.SESSION)
        }
        val webToken = webTokenResult.token

        val cachedIdentity = sessionCache.identityFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
        )
        val ownMatrixUserId = cachedIdentity ?: when (
            val whoami = execute(
                matrixGet(
                    token = webToken,
                    pathSegments = listOf("_matrix", "client", "v3", "account", "whoami"),
                ),
            )
        ) {
            is MatrixHttpResult.Success -> {
                val resolved = parseObject(whoami.body)
                    ?.get("user_id")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf(::redditChatMatrixUserIdShape)
                    ?: return failed("whoami", DirectChatStartFailureKind.PROTOCOL)
                if (!matrixUserMatchesRedditProviderId(resolved, redditIdentity.providerAccountId)) {
                    invalidateAuthorization(webToken)
                    return failed("whoami", DirectChatStartFailureKind.SESSION)
                }
                sessionCache.rememberIdentity(
                    token = webToken,
                    providerAccountId = redditIdentity.providerAccountId,
                    matrixUserId = resolved,
                )
                resolved
            }
            is MatrixHttpResult.HttpFailure -> {
                if (whoami.statusCode == 401 || whoami.statusCode == 403) {
                    invalidateAuthorization(webToken)
                    return failed(
                        "whoami",
                        DirectChatStartFailureKind.SESSION,
                        whoami.statusCode,
                    )
                }
                return failed("whoami", DirectChatStartFailureKind.HTTP, whoami.statusCode)
            }
            MatrixHttpResult.TransportFailure ->
                return failed("whoami", DirectChatStartFailureKind.TRANSPORT)
        }

        val cachedRoom = sessionCache.roomFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
            participantUsername = participant,
        )
        var roomCreated = false

        val roomId = if (cachedRoom != null && redditChatMatrixRoomIdShape(cachedRoom)) {
            Log.d(START_LOG_TAG, "stage=room result=cached")
            cachedRoom
        } else {
            val targetMatrixUserId = when (
                val directory = resolveParticipantMatrixUserId(
                    token = webToken,
                    participantUsername = participant,
                )
            ) {
                is MatrixDirectoryResolution.Found -> directory.userId
                MatrixDirectoryResolution.NotFound ->
                    return failed("directory", DirectChatStartFailureKind.USER_NOT_FOUND)
                MatrixDirectoryResolution.AuthorizationRequired -> {
                    invalidateAuthorization(webToken)
                    return failed("directory", DirectChatStartFailureKind.SESSION)
                }
                is MatrixDirectoryResolution.HttpFailure ->
                    return failed(
                        "directory",
                        DirectChatStartFailureKind.HTTP,
                        directory.statusCode,
                    )
                MatrixDirectoryResolution.TransportFailure ->
                    return failed("directory", DirectChatStartFailureKind.TRANSPORT)
                MatrixDirectoryResolution.ProtocolFailure ->
                    return failed("directory", DirectChatStartFailureKind.PROTOCOL)
            }
            if (targetMatrixUserId.equals(ownMatrixUserId, ignoreCase = true)) {
                return failed("directory", DirectChatStartFailureKind.USER_NOT_FOUND)
            }
            Log.d(START_LOG_TAG, "stage=directory result=accepted")

            when (
                val lookup = lookupDirectRoom(
                    token = webToken,
                    targetMatrixUserId = targetMatrixUserId,
                )
            ) {
                is MatrixDirectRoomLookup.Found -> {
                    Log.d(START_LOG_TAG, "stage=room result=existing")
                    lookup.roomId
                }
                MatrixDirectRoomLookup.Empty -> {
                    val create = execute(
                        matrixPost(
                            token = webToken,
                            pathSegments = listOf("_matrix", "client", "v3", "createRoom"),
                            payload = redditChatMatrixCreateRoomPayload(targetMatrixUserId),
                        ),
                    )
                    when (create) {
                        is MatrixHttpResult.Success -> {
                            val createdRoomId = parseObject(create.body)
                                ?.get("room_id")
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?.takeIf(::redditChatMatrixRoomIdShape)
                                ?: return failed("create", DirectChatStartFailureKind.PROTOCOL)
                            roomCreated = true
                            Log.d(START_LOG_TAG, "stage=room result=created")
                            createdRoomId
                        }
                        is MatrixHttpResult.HttpFailure -> {
                            if (create.statusCode == 401 || create.statusCode == 403) {
                                invalidateAuthorization(webToken)
                                return failed(
                                    "create",
                                    DirectChatStartFailureKind.SESSION,
                                    create.statusCode,
                                )
                            }
                            return failed(
                                "create",
                                DirectChatStartFailureKind.HTTP,
                                create.statusCode,
                            )
                        }
                        MatrixHttpResult.TransportFailure ->
                            return failed(
                                "create",
                                DirectChatStartFailureKind.OUTCOME_UNKNOWN,
                            )
                    }
                }
                MatrixDirectRoomLookup.AuthorizationRequired -> {
                    invalidateAuthorization(webToken)
                    return failed("room_lookup", DirectChatStartFailureKind.SESSION)
                }
                is MatrixDirectRoomLookup.HttpFailure ->
                    return failed(
                        "room_lookup",
                        DirectChatStartFailureKind.HTTP,
                        lookup.statusCode,
                    )
                MatrixDirectRoomLookup.TransportFailure ->
                    return failed("room_lookup", DirectChatStartFailureKind.TRANSPORT)
                MatrixDirectRoomLookup.ProtocolFailure ->
                    return failed("room_lookup", DirectChatStartFailureKind.PROTOCOL)
            }
        }

        sessionCache.rememberRoom(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
            participantUsername = participant,
            roomId = roomId,
            tokenExpiresAtEpochMs = webTokenResult.expiresAtEpochMs,
        )

        if (media != null) {
            val writer = mediaWriter
                ?: return failed("media", DirectChatStartFailureKind.UNAVAILABLE)
            val staged = preparedMedia
                ?: return failed("media", DirectChatStartFailureKind.MEDIA_UNAVAILABLE)
            val mediaRequest = MessageReplyRequest(
                accountStableId = account,
                inboxItemId = roomId,
                parentFullname = roomId,
                channel = MessageReplyChannel.DIRECT_CHAT,
                text = "",
                media = media,
                directChatParticipantUsername = participant,
            )
            return when (
                val result = writer.sendPrepared(
                    request = mediaRequest,
                    expectedProviderAccountId = redditIdentity.providerAccountId,
                    staged = staged,
                )
            ) {
                is AuthenticatedRedditMessageReplyResult.Accepted -> {
                    val eventId = result.sentMatrixEventId
                        ?.takeIf(::redditChatMatrixEventIdShape)
                        ?: return failed(
                            "media",
                            DirectChatStartFailureKind.OUTCOME_UNKNOWN,
                        )
                    Log.d(START_LOG_TAG, "stage=media result=accepted")
                    AuthenticatedRedditDirectChatStartResult.Accepted(
                        request = request.copy(text = "", media = media),
                        eventId = eventId,
                        roomCreated = roomCreated,
                    )
                }
                is AuthenticatedRedditMessageReplyResult.Failed ->
                    failed(
                        "media",
                        directChatStartFailureKindForMediaReply(result.kind),
                        result.statusCode,
                    )
            }
        }

        val transactionId = "subshell_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val send = execute(
            matrixPut(
                token = webToken,
                pathSegments = redditChatMatrixStartSendPathSegments(
                    roomId = roomId,
                    transactionId = transactionId,
                ),
                payload = redditChatMatrixStartTextPayload(body),
            ),
        )
        return when (send) {
            is MatrixHttpResult.Success -> {
                // WRITEOUT01_DIRECT_CHAT_START_ACCEPTED_UNREADABLE_IS_UNKNOWN_V1
                // (OCB-488): Reddit accepted the send (2xx); an answer without a
                // readable event id may still have delivered the message.
                val eventId = redditChatMatrixAcceptedEventId(parseObject(send.body))
                    ?: return failed("send", DirectChatStartFailureKind.OUTCOME_UNKNOWN)
                Log.d(START_LOG_TAG, "stage=send result=accepted")
                AuthenticatedRedditDirectChatStartResult.Accepted(
                    request = request.copy(text = body),
                    eventId = eventId,
                    roomCreated = roomCreated,
                )
            }
            is MatrixHttpResult.HttpFailure -> {
                if (send.statusCode == 401 || send.statusCode == 403) {
                    invalidateAuthorization(webToken)
                    return failed(
                        "send",
                        DirectChatStartFailureKind.SESSION,
                        send.statusCode,
                    )
                }
                if (send.statusCode == 404) {
                    sessionCache.invalidateRoom(
                        token = webToken,
                        providerAccountId = redditIdentity.providerAccountId,
                        participantUsername = participant,
                    )
                    return failed(
                        "send",
                        DirectChatStartFailureKind.ROOM,
                        send.statusCode,
                    )
                }
                failed("send", directChatStartSendHttpFailureKind(send.statusCode), send.statusCode)
            }
            MatrixHttpResult.TransportFailure ->
                failed("send", DirectChatStartFailureKind.OUTCOME_UNKNOWN)
        }
        } finally {
            preparedMedia?.file?.delete()
        }
    }

    private fun resolveParticipantMatrixUserId(
        token: String,
        participantUsername: String,
    ): MatrixDirectoryResolution {
        val result = execute(
            matrixPost(
                token = token,
                pathSegments = listOf("_matrix", "client", "v3", "user_directory", "search"),
                payload = redditChatMatrixDirectorySearchPayload(participantUsername),
            ),
        )
        return when (result) {
            is MatrixHttpResult.Success -> {
                val root = parseObject(result.body) ?: return MatrixDirectoryResolution.ProtocolFailure
                val results = root["results"] as? JsonArray
                    ?: return MatrixDirectoryResolution.ProtocolFailure
                val matches = results.mapNotNull { element ->
                    val candidate = element as? JsonObject ?: return@mapNotNull null
                    val displayName = (candidate["display_name"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?: return@mapNotNull null
                    if (
                        redditChatNormalizedDirectoryName(displayName)
                            .equals(participantUsername, ignoreCase = true)
                    ) {
                        (candidate["user_id"] as? JsonPrimitive)
                            ?.contentOrNull
                            ?.takeIf(::redditChatMatrixUserIdShape)
                    } else {
                        null
                    }
                }.distinct()
                when (matches.size) {
                    0 -> MatrixDirectoryResolution.NotFound
                    1 -> MatrixDirectoryResolution.Found(matches.single())
                    else -> MatrixDirectoryResolution.ProtocolFailure
                }
            }
            is MatrixHttpResult.HttpFailure -> when (result.statusCode) {
                401, 403 -> MatrixDirectoryResolution.AuthorizationRequired
                else -> MatrixDirectoryResolution.HttpFailure(result.statusCode)
            }
            MatrixHttpResult.TransportFailure ->
                MatrixDirectoryResolution.TransportFailure
        }
    }

    private fun lookupDirectRoom(
        token: String,
        targetMatrixUserId: String,
    ): MatrixDirectRoomLookup {
        val result = execute(
            matrixGet(
                token = token,
                pathSegments = listOf("_matrix", "client", "v3", "rooms"),
                query = linkedMapOf(
                    "include" to "state,timeline",
                    "seq" to "y",
                    "type" to "direct",
                    "with_user" to targetMatrixUserId,
                ),
            ),
        )
        return when (result) {
            is MatrixHttpResult.Success -> {
                val root = parseObject(result.body) ?: return MatrixDirectRoomLookup.ProtocolFailure
                val roomIds = redditChatMatrixDirectRoomIds(root)
                    ?: return MatrixDirectRoomLookup.ProtocolFailure
                when (roomIds.size) {
                    0 -> MatrixDirectRoomLookup.Empty
                    1 -> MatrixDirectRoomLookup.Found(roomIds.single())
                    else -> MatrixDirectRoomLookup.ProtocolFailure
                }
            }
            is MatrixHttpResult.HttpFailure -> when (result.statusCode) {
                401, 403 -> MatrixDirectRoomLookup.AuthorizationRequired
                else -> MatrixDirectRoomLookup.HttpFailure(result.statusCode)
            }
            MatrixHttpResult.TransportFailure ->
                MatrixDirectRoomLookup.TransportFailure
        }
    }

    private fun matrixGet(
        token: String,
        pathSegments: List<String>,
        query: Map<String, String> = emptyMap(),
    ): Request = Request.Builder()
        .url(matrixUrl(pathSegments, query))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .get()
        .build()

    private fun matrixPost(
        token: String,
        pathSegments: List<String>,
        payload: String,
    ): Request = Request.Builder()
        .url(matrixUrl(pathSegments))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun matrixPut(
        token: String,
        pathSegments: List<String>,
        payload: String,
    ): Request = Request.Builder()
        .url(matrixUrl(pathSegments))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .put(payload.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun matrixUrl(
        pathSegments: List<String>,
        query: Map<String, String> = emptyMap(),
    ): HttpUrl = MATRIX_BASE.newBuilder().apply {
        pathSegments.forEach(::addPathSegment)
        query.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build()

    private fun execute(request: Request): MatrixHttpResult = try {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (response.isSuccessful) {
                MatrixHttpResult.Success(responseBody)
            } else {
                MatrixHttpResult.HttpFailure(response.code)
            }
        }
    } catch (_: IOException) {
        MatrixHttpResult.TransportFailure
    }

    private fun invalidateAuthorization(token: String) {
        sessionCache.invalidateToken(token)
        tokenProvider.invalidate(token)
    }

    private fun parseObject(body: String): JsonObject? = runCatching {
        json.parseToJsonElement(body) as? JsonObject
    }.getOrNull()

    private fun failed(
        stage: String,
        kind: DirectChatStartFailureKind,
        statusCode: Int? = null,
    ): AuthenticatedRedditDirectChatStartResult.Failed {
        Log.d(
            START_LOG_TAG,
            "stage=$stage result=failed kind=${kind.name.lowercase()} " +
                "status=${statusCode ?: "none"}",
        )
        return AuthenticatedRedditDirectChatStartResult.Failed(
            kind = kind,
            statusCode = statusCode,
        )
    }

    private sealed interface MatrixHttpResult {
        data class Success(val body: String) : MatrixHttpResult
        data class HttpFailure(val statusCode: Int) : MatrixHttpResult
        data object TransportFailure : MatrixHttpResult
    }

    private sealed interface MatrixDirectoryResolution {
        data class Found(val userId: String) : MatrixDirectoryResolution
        data object NotFound : MatrixDirectoryResolution
        data object AuthorizationRequired : MatrixDirectoryResolution
        data class HttpFailure(val statusCode: Int) : MatrixDirectoryResolution
        data object TransportFailure : MatrixDirectoryResolution
        data object ProtocolFailure : MatrixDirectoryResolution
    }

    private sealed interface MatrixDirectRoomLookup {
        data class Found(val roomId: String) : MatrixDirectRoomLookup
        data object Empty : MatrixDirectRoomLookup
        data object AuthorizationRequired : MatrixDirectRoomLookup
        data class HttpFailure(val statusCode: Int) : MatrixDirectRoomLookup
        data object TransportFailure : MatrixDirectRoomLookup
        data object ProtocolFailure : MatrixDirectRoomLookup
    }

    companion object {
        private const val START_LOG_TAG = "subshell-matrix-chat-start"
        private val MATRIX_BASE = "https://matrix.redditspace.com".toHttpUrl()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun redditChatMatrixDirectorySearchPayload(participantUsername: String): String =
    JsonObject(
        mapOf(
            "search_term" to JsonPrimitive(participantUsername.trim()),
        ),
    ).toString()

internal fun redditChatMatrixCreateRoomPayload(targetMatrixUserId: String): String =
    JsonObject(
        linkedMapOf(
            "invite" to JsonArray(listOf(JsonPrimitive(targetMatrixUserId.trim()))),
            "preset" to JsonPrimitive("reddit_dm"),
        ),
    ).toString()

internal fun redditChatMatrixStartTextPayload(text: String): String =
    JsonObject(
        linkedMapOf(
            "body" to JsonPrimitive(text),
            "msgtype" to JsonPrimitive("m.text"),
        ),
    ).toString()

internal fun redditChatMatrixStartSendPathSegments(
    roomId: String,
    transactionId: String,
): List<String> = listOf(
    "_matrix",
    "client",
    "v3",
    "rooms",
    roomId,
    "send",
    "m.room.message",
    transactionId,
)

internal fun redditChatMatrixDirectRoomIds(root: JsonObject): List<String>? {
    val container = root["rooms"] ?: return null
    // Reddit Web's proven no-room response is {"rooms":null}. Treat only the
    // explicit JsonNull sentinel as definitively empty; all other unknown
    // container shapes continue to fail closed below.
    if (container === JsonNull) return emptyList()
    val candidates: List<Pair<String?, JsonElement?>> = when (container) {
        is JsonArray -> container.map { element -> null to element }
        is JsonObject -> container.map { (key, value) -> key to value }
        else -> return null
    }
    if (candidates.isEmpty()) return emptyList()

    val resolved = mutableListOf<String>()
    for ((mapKey, element) in candidates) {
        val roomId = mapKey
            ?.takeIf(::redditChatMatrixRoomIdShape)
            ?: when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> sequenceOf("room_id", "roomId", "id")
                    .mapNotNull { key ->
                        (element[key] as? JsonPrimitive)?.contentOrNull
                    }
                    .firstOrNull()
                else -> null
            }
            ?: return null
        if (!redditChatMatrixRoomIdShape(roomId)) return null
        resolved += roomId
    }
    return resolved.distinct()
}

internal fun redditChatMatrixUserIdShape(value: String): Boolean =
    value.startsWith("@") &&
        value.contains(':') &&
        value.none(Char::isWhitespace)

internal fun redditChatMatrixRoomIdShape(value: String): Boolean =
    value.startsWith("!") &&
        value.contains(':') &&
        value.none(Char::isWhitespace)


internal fun directChatStartFailureKindForMediaReply(
    kind: MessageReplyFailureKind,
): DirectChatStartFailureKind = when (kind) {
    MessageReplyFailureKind.MEDIA_UNSUPPORTED ->
        DirectChatStartFailureKind.MEDIA_UNSUPPORTED
    MessageReplyFailureKind.MEDIA_TOO_LARGE ->
        DirectChatStartFailureKind.MEDIA_TOO_LARGE
    MessageReplyFailureKind.MEDIA_UNAVAILABLE ->
        DirectChatStartFailureKind.MEDIA_UNAVAILABLE
    MessageReplyFailureKind.CHAT_SESSION_REQUIRED,
    MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED,
    MessageReplyFailureKind.AUTHORIZATION_REQUIRED,
    MessageReplyFailureKind.SESSION,
    -> DirectChatStartFailureKind.SESSION
    MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE ->
        DirectChatStartFailureKind.ROOM
    MessageReplyFailureKind.RATE_LIMIT,
    MessageReplyFailureKind.HTTP,
    -> DirectChatStartFailureKind.HTTP
    MessageReplyFailureKind.TRANSPORT ->
        DirectChatStartFailureKind.TRANSPORT
    MessageReplyFailureKind.API ->
        DirectChatStartFailureKind.PROTOCOL
    MessageReplyFailureKind.OUTCOME_UNKNOWN ->
        DirectChatStartFailureKind.OUTCOME_UNKNOWN
    MessageReplyFailureKind.UNAVAILABLE ->
        DirectChatStartFailureKind.UNAVAILABLE
    MessageReplyFailureKind.VALIDATION,
    MessageReplyFailureKind.MEDIA_CAPTION_UNSUPPORTED,
    -> DirectChatStartFailureKind.VALIDATION
}

internal fun redditChatMatrixEventIdShape(value: String): Boolean =
    value.startsWith("$") &&
        value.none(Char::isWhitespace)

internal fun redditChatNormalizedDirectoryName(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.startsWith("u/", ignoreCase = true)) {
        trimmed.drop(2).trim()
    } else {
        trimmed
    }
}

// CHATSEND01_OUTCOME_UNKNOWN_V1: the first message of a chat answered
// 5xx/408/421/425 may have been stored, and a resend uses a new transaction id.
internal fun directChatStartSendHttpFailureKind(statusCode: Int): DirectChatStartFailureKind =
    if (isRedditWriteOutcomeUnknownStatus(statusCode)) {
        DirectChatStartFailureKind.OUTCOME_UNKNOWN
    } else {
        DirectChatStartFailureKind.HTTP
    }

/** The event id of an accepted send, or null when the answer does not carry a readable one. */
internal fun redditChatMatrixAcceptedEventId(answer: JsonObject?): String? = answer
    ?.get("event_id")
    ?.primitiveOrNull
    ?.contentOrNull
    ?.takeIf(::redditChatMatrixEventIdShape)
