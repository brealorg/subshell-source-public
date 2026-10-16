package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.chat.RedditChatWebTokenResult
import android.os.SystemClock
import android.util.Log

import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

// SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1
private const val MATRIX_DUAL_WRITE_LOG_TAG = "subshell-matrix-dual-write"

internal class RedditChatMatrixMediaWriter(
    private val tokenProvider: RedditChatWebTokenSource,
    private val mediaSource: RedditMediaUploadSource,
    private val client: OkHttpClient,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionCache: RedditChatMatrixSessionCache = RedditChatMatrixSessionCache(),
) {
    // SUBSHELL_MESSAGES02D5C_INITIAL_IMAGE_GIF_V1
    suspend fun prepareMedia(
        attachment: MessageReplyMediaAttachment,
    ): RedditMediaStageResult = withContext(ioDispatcher) {
        mediaSource.stageGallery(attachment.uri, attachment.mimeType)
    }

    suspend fun sendPrepared(
        request: MessageReplyRequest,
        expectedProviderAccountId: String,
        staged: RedditMediaStageResult.Staged,
    ): AuthenticatedRedditMessageReplyResult {
        val preparedSource = object : RedditMediaUploadSource {
            override suspend fun stage(
                uri: String,
                declaredMimeType: String,
            ): RedditMediaStageResult = staged
        }
        return RedditChatMatrixMediaWriter(
            tokenProvider = tokenProvider,
            mediaSource = preparedSource,
            client = client,
            json = json,
            ioDispatcher = ioDispatcher,
            sessionCache = sessionCache,
        ).send(
            request = request,
            expectedProviderAccountId = expectedProviderAccountId,
        )
    }

    suspend fun send(
        request: MessageReplyRequest,
        expectedProviderAccountId: String,
    ): AuthenticatedRedditMessageReplyResult = withContext(ioDispatcher) {
        val totalStartedAtMs = SystemClock.elapsedRealtime()
        val participant = request.directChatParticipantUsername
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return@withContext failed(MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE)
        val attachment = request.media
            ?: return@withContext failed(MessageReplyFailureKind.VALIDATION)

        val staged = when (val result = mediaSource.stageGallery(attachment.uri, attachment.mimeType)) {
            is RedditMediaStageResult.Staged -> result
            RedditMediaStageResult.UnsupportedType ->
                return@withContext failed(MessageReplyFailureKind.MEDIA_UNSUPPORTED)
            RedditMediaStageResult.TooLarge ->
                return@withContext failed(MessageReplyFailureKind.MEDIA_TOO_LARGE)
            RedditMediaStageResult.Empty,
            RedditMediaStageResult.Unavailable,
            -> return@withContext failed(MessageReplyFailureKind.MEDIA_UNAVAILABLE)
        }

        try {
            val webTokenResult = when (val result = tokenProvider.token()) {
                is RedditChatWebTokenResult.Success -> result
                // CHAT01_CHAT_SESSION_FAILURE_STEP_V1: each CHAT_SESSION_REQUIRED
                // names the step that failed (shown under the error), so a device
                // report tells which part of the Direct Chat media path stops.
                RedditChatWebTokenResult.NoRedditWebSession ->
                    return@withContext failed(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, step = CHAT_STEP_NO_WEB_SESSION)
                RedditChatWebTokenResult.Failed ->
                    return@withContext failed(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, step = CHAT_STEP_TOKEN_FAILED)
            }
            val webToken = webTokenResult.token

            val cachedIdentity = sessionCache.identityFor(
                token = webToken,
                providerAccountId = expectedProviderAccountId,
            )
            val identityCacheHit = cachedIdentity != null
            val identity = cachedIdentity ?: run {
                val whoami = matrixGet(webToken, listOf("_matrix", "client", "v3", "account", "whoami"))
                val resolved = when (whoami) {
                    is MatrixHttpResult.Success -> {
                        val root = parseObject(whoami.body)
                            ?: return@withContext failed(MessageReplyFailureKind.API)
                        root["user_id"]?.primitiveOrNull?.contentOrNull
                    }
                    is MatrixHttpResult.HttpFailure -> {
                        if (whoami.statusCode == 401 || whoami.statusCode == 403) {
                            invalidateAuthorization(webToken)
                        }
                        return@withContext mapHttp(whoami.statusCode, CHAT_STEP_WHOAMI)
                    }
                    is MatrixHttpResult.TransportFailure ->
                        return@withContext failed(MessageReplyFailureKind.TRANSPORT)
                } ?: return@withContext failed(MessageReplyFailureKind.API)

                if (!matrixUserMatchesRedditProviderId(resolved, expectedProviderAccountId)) {
                    invalidateAuthorization(webToken)
                    return@withContext failed(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, step = CHAT_STEP_IDENTITY_MISMATCH)
                }
                sessionCache.rememberIdentity(
                    token = webToken,
                    providerAccountId = expectedProviderAccountId,
                    matrixUserId = resolved,
                )
                resolved
            }

            val cachedRoomId = sessionCache.roomFor(
                token = webToken,
                providerAccountId = expectedProviderAccountId,
                participantUsername = participant,
            )
            val roomId = cachedRoomId ?: when (val room = resolveDirectRoom(webToken, identity, participant)) {
                is MatrixRoomResolution.Found -> room.roomId.also { resolvedRoom ->
                    sessionCache.rememberRoom(
                        token = webToken,
                        providerAccountId = expectedProviderAccountId,
                        participantUsername = participant,
                        roomId = resolvedRoom,
                        tokenExpiresAtEpochMs = webTokenResult.expiresAtEpochMs,
                    )
                }
                MatrixRoomResolution.NotFound ->
                    return@withContext failed(MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE)
                MatrixRoomResolution.AuthorizationRequired -> {
                    invalidateAuthorization(webToken)
                    return@withContext failed(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, step = CHAT_STEP_ROOM)
                }
                MatrixRoomResolution.TransportFailure ->
                    return@withContext failed(MessageReplyFailureKind.TRANSPORT)
                MatrixRoomResolution.ProtocolFailure ->
                    return@withContext failed(MessageReplyFailureKind.API)
                is MatrixRoomResolution.HttpFailure ->
                    return@withContext mapHttp(room.statusCode, CHAT_STEP_ROOM)
            }

            val uploadStartedAtMs = SystemClock.elapsedRealtime()
            val upload = uploadMedia(webToken, staged)
            val uploadDurationMs = SystemClock.elapsedRealtime() - uploadStartedAtMs
            val contentUri = when (upload) {
                is MatrixUploadResult.Uploaded -> upload.contentUri
                is MatrixUploadResult.HttpFailure -> {
                    if (upload.statusCode == 401 || upload.statusCode == 403) {
                        invalidateAuthorization(webToken)
                    }
                    return@withContext mapHttp(upload.statusCode, CHAT_STEP_UPLOAD)
                }
                MatrixUploadResult.TransportFailure ->
                    return@withContext failed(MessageReplyFailureKind.TRANSPORT)
                MatrixUploadResult.ProtocolFailure ->
                    return@withContext failed(MessageReplyFailureKind.API)
            }

            val sendStartedAtMs = SystemClock.elapsedRealtime()
            val send = readBackIfUnknown(
                token = webToken,
                roomId = roomId,
                result = sendImageEvent(
                    token = webToken,
                    roomId = roomId,
                    staged = staged,
                    contentUri = contentUri,
                ),
            )
            when (send) {
                is MatrixSendResult.Accepted -> {
                    val sendDurationMs = SystemClock.elapsedRealtime() - sendStartedAtMs
                    val sentMediaUri = redditMatrixDownloadUri(contentUri)
                    val sentMatrixMediaId = redditMatrixMediaId(contentUri)
                    // SUBSHELL_MESSAGES02C6R3_NONBLOCKING_MATRIX_ACCEPT_V1
                    // Matrix HTTP 200 + event_id is the server-confirmed media write boundary.
                    Log.i(
                        MATRIX_MEDIA_PERF_LOG_TAG,
                        "result=accepted total_ms=${SystemClock.elapsedRealtime() - totalStartedAtMs} " +
                            "upload_ms=$uploadDurationMs send_ms=$sendDurationMs " +
                            "identity_cache=${identityCacheHit} room_cache=${cachedRoomId != null}",
                    )
                    Log.i(
                        MATRIX_DUAL_WRITE_LOG_TAG,
                        "stage=image_accepted text_pending=${request.text.isNotBlank()}",
                    )

                    if (request.text.isBlank()) {
                        AuthenticatedRedditMessageReplyResult.Accepted(
                            request = request,
                            sentBody = request.text,
                            sentMediaUri = sentMediaUri,
                            sentMatrixMediaId = sentMatrixMediaId,
                            sentMatrixEventId = send.eventId,
                            metadata = NetworkResponseMetadata(
                                statusCode = 200,
                                receivedAtEpochMs = System.currentTimeMillis(),
                                rateLimit = NetworkRateLimit(
                                    remaining = null,
                                    used = null,
                                    resetAfterMs = null,
                                ),
                                retryAfterMs = null,
                            ),
                        )
                    } else {
                        when (
                            val textSend = readBackIfUnknown(
                                token = webToken,
                                roomId = roomId,
                                result = sendTextEvent(
                                    token = webToken,
                                    roomId = roomId,
                                    body = request.text,
                                ),
                            )
                        ) {
                            is MatrixSendResult.Accepted -> {
                                Log.i(MATRIX_DUAL_WRITE_LOG_TAG, "stage=text_accepted")
                                AuthenticatedRedditMessageReplyResult.Accepted(
                                    request = request,
                                    sentBody = request.text,
                                    sentMediaUri = sentMediaUri,
                                    sentMatrixMediaId = sentMatrixMediaId,
                                    sentMatrixEventId = send.eventId,
                                    metadata = NetworkResponseMetadata(
                                        statusCode = 200,
                                        receivedAtEpochMs = System.currentTimeMillis(),
                                        rateLimit = NetworkRateLimit(
                                            remaining = null,
                                            used = null,
                                            resetAfterMs = null,
                                        ),
                                        retryAfterMs = null,
                                    ),
                                )
                            }

                            is MatrixSendResult.HttpFailure -> {
                                // CHAT403_ROOM_REFUSAL_KEEPS_TOKEN_V1 (OCB-613)
                                val textRoomRefused =
                                    redditChatMatrixRoomRefusal(textSend.statusCode, textSend.errcode)
                                if (redditChatMatrixSessionRefusal(textSend.statusCode, textSend.errcode)) {
                                    invalidateAuthorization(webToken)
                                } else if (textSend.statusCode == 404 || textRoomRefused) {
                                    sessionCache.invalidateRoom(
                                        token = webToken,
                                        providerAccountId = expectedProviderAccountId,
                                        participantUsername = participant,
                                    )
                                }
                                Log.w(
                                    MATRIX_DUAL_WRITE_LOG_TAG,
                                    "stage=text_failed kind=http status=${textSend.statusCode} media_accepted=true",
                                )
                                mapHttp(textSend.statusCode, CHAT_STEP_TEXT_SEND, textRoomRefused).copy(
                                    sentMediaUri = sentMediaUri,
                                    sentMatrixMediaId = sentMatrixMediaId,
                                    sentMatrixEventId = send.eventId,
                                )
                            }

                            is MatrixSendResult.UnknownOutcome -> {
                                Log.w(
                                    MATRIX_DUAL_WRITE_LOG_TAG,
                                    "stage=text_outcome_unknown media_accepted=true",
                                )
                                failed(MessageReplyFailureKind.OUTCOME_UNKNOWN).copy(
                                    sentMediaUri = sentMediaUri,
                                    sentMatrixMediaId = sentMatrixMediaId,
                                    sentMatrixEventId = send.eventId,
                                )
                            }

                            MatrixSendResult.ProtocolFailure -> {
                                // A successful HTTP response without a verifiable text event_id is
                                // uncertain after the media has already committed. Never blind-retry.
                                Log.w(
                                    MATRIX_DUAL_WRITE_LOG_TAG,
                                    "stage=text_outcome_unknown kind=protocol media_accepted=true",
                                )
                                failed(MessageReplyFailureKind.OUTCOME_UNKNOWN).copy(
                                    sentMediaUri = sentMediaUri,
                                    sentMatrixMediaId = sentMatrixMediaId,
                                    sentMatrixEventId = send.eventId,
                                )
                            }
                        }
                    }
                }

                is MatrixSendResult.HttpFailure -> {
                    // CHAT403_ROOM_REFUSAL_KEEPS_TOKEN_V1 (OCB-613)
                    val roomRefused = redditChatMatrixRoomRefusal(send.statusCode, send.errcode)
                    if (redditChatMatrixSessionRefusal(send.statusCode, send.errcode)) {
                        invalidateAuthorization(webToken)
                    } else if (send.statusCode == 404 || roomRefused) {
                        sessionCache.invalidateRoom(
                            token = webToken,
                            providerAccountId = expectedProviderAccountId,
                            participantUsername = participant,
                        )
                    }
                    mapHttp(send.statusCode, CHAT_STEP_IMAGE_SEND, roomRefused)
                }
                is MatrixSendResult.UnknownOutcome ->
                    failed(MessageReplyFailureKind.OUTCOME_UNKNOWN)
                MatrixSendResult.ProtocolFailure ->
                    failed(MessageReplyFailureKind.API)
            }
        } finally {
            staged.file.delete()
        }
    }

    private fun resolveDirectRoom(
        token: String,
        ownUserId: String,
        participantUsername: String,
    ): MatrixRoomResolution {
        val filter = REDDIT_CHAT_MATRIX_ROOM_DISCOVERY_FILTER
        val result = matrixGet(
            token = token,
            pathSegments = listOf("_matrix", "client", "v3", "sync"),
            query = mapOf("timeout" to "0", "filter" to filter),
        )
        val root = when (result) {
            is MatrixHttpResult.Success -> parseObject(result.body)
                ?: return MatrixRoomResolution.ProtocolFailure
            is MatrixHttpResult.HttpFailure -> return if (result.statusCode == 401 || result.statusCode == 403) {
                MatrixRoomResolution.AuthorizationRequired
            } else {
                MatrixRoomResolution.HttpFailure(result.statusCode)
            }
            is MatrixHttpResult.TransportFailure -> return MatrixRoomResolution.TransportFailure
        }
        val joined = root["rooms"]
            ?.objectOrNull
            ?.get("join")
            ?.objectOrNull
            ?: return MatrixRoomResolution.NotFound

        val directMatches = joined.mapNotNull { (roomId, roomElement) ->
            if (
                roomContainsDirectParticipants(
                    room = roomElement.objectOrNull ?: return@mapNotNull null,
                    ownUserId = ownUserId,
                    participantUsername = participantUsername,
                )
            ) roomId else null
        }
        if (directMatches.size == 1) {
            return MatrixRoomResolution.Found(directMatches.single())
        }

        // Some initial-sync variants lazily omit member state. Resolve only a bounded set of joined
        // rooms and require one exact two-party username match; never guess between duplicates.
        val fallbackMatches = mutableListOf<String>()
        joined.keys.take(MAX_MEMBER_FALLBACK_ROOMS).forEach { roomId ->
            val members = matrixGet(
                token = token,
                pathSegments = listOf("_matrix", "client", "v3", "rooms", roomId, "members"),
                query = mapOf("not_membership" to "leave"),
            )
            val memberRoot = (members as? MatrixHttpResult.Success)
                ?.let { parseObject(it.body) }
                ?: return@forEach
            if (
                memberEventsMatchDirectParticipants(
                    events = memberRoot["chunk"] as? JsonArray ?: JsonArray(emptyList()),
                    ownUserId = ownUserId,
                    participantUsername = participantUsername,
                )
            ) {
                fallbackMatches += roomId
            }
        }
        return if (fallbackMatches.distinct().size == 1) {
            MatrixRoomResolution.Found(fallbackMatches.single())
        } else {
            MatrixRoomResolution.NotFound
        }
    }

    private fun roomContainsDirectParticipants(
        room: JsonObject,
        ownUserId: String,
        participantUsername: String,
    ): Boolean {
        val events = mutableListOf<JsonElement>()
        room["state"]?.objectOrNull?.get("events")?.arrayOrNull?.let(events::addAll)
        room["timeline"]?.objectOrNull?.get("events")?.arrayOrNull?.let(events::addAll)
        return memberEventsMatchDirectParticipants(
            events = JsonArray(events),
            ownUserId = ownUserId,
            participantUsername = participantUsername,
        )
    }

    private fun memberEventsMatchDirectParticipants(
        events: JsonArray,
        ownUserId: String,
        participantUsername: String,
    ): Boolean {
        val joinedMembers = linkedMapOf<String, String?>()
        events.forEach { element ->
            val event = element as? JsonObject ?: return@forEach
            if (event["type"]?.primitiveOrNull?.contentOrNull != "m.room.member") return@forEach
            val content = event["content"] as? JsonObject ?: return@forEach
            val stateKey = event["state_key"]?.primitiveOrNull?.contentOrNull ?: return@forEach
            val membership = content["membership"]?.primitiveOrNull?.contentOrNull
            if (membership == "join") {
                joinedMembers[stateKey] = content["displayname"]?.primitiveOrNull?.contentOrNull
            } else {
                joinedMembers.remove(stateKey)
            }
        }
        val ownJoined = ownUserId in joinedMembers
        val participantJoined = joinedMembers.any { (userId, displayName) ->
            userId != ownUserId &&
                displayName?.equals(participantUsername, ignoreCase = true) == true
        }
        return ownJoined && participantJoined && joinedMembers.size == 2
    }

    private fun uploadMedia(
        token: String,
        staged: RedditMediaStageResult.Staged,
    ): MatrixUploadResult {
        val url = MATRIX_BASE.newBuilder()
            .addPathSegments("_matrix/media/v3/upload")
            .addQueryParameter("filename", staged.fileName)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(staged.file.asRequestBody(staged.mimeType.toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    MatrixUploadResult.HttpFailure(response.code)
                } else {
                    val contentUri = parseObject(body)
                        ?.get("content_uri")
                        ?.primitiveOrNull
                        ?.contentOrNull
                        ?.takeIf { it.startsWith("mxc://reddit.com/") }
                    if (contentUri == null) {
                        MatrixUploadResult.ProtocolFailure
                    } else {
                        MatrixUploadResult.Uploaded(contentUri)
                    }
                }
            }
        } catch (_: IOException) {
            MatrixUploadResult.TransportFailure
        }
    }

    private fun sendImageEvent(
        token: String,
        roomId: String,
        staged: RedditMediaStageResult.Staged,
        contentUri: String,
    ): MatrixSendResult {
        val dimensions = imageDimensions(staged.file)
        val payload = buildJsonObject {
            put("msgtype", "m.image")
            put("body", "Image")
            put("url", contentUri)
            putJsonObject("info") {
                put("mimetype", staged.mimeType)
                put("size", staged.sizeBytes)
                dimensions?.first?.let { put("w", it) }
                dimensions?.second?.let { put("h", it) }
            }
        }.toString()
        val transactionId = "subshell_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val url = MATRIX_BASE.newBuilder()
            .addPathSegments("_matrix/client/v3/rooms")
            .addPathSegment(roomId)
            .addPathSegments("send/m.room.message")
            .addPathSegment(transactionId)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .put(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    MatrixSendResult.HttpFailure(response.code, redditChatMatrixErrcode(body))
                } else {
                    val eventId = parseObject(body)
                        ?.get("event_id")
                        ?.primitiveOrNull
                        ?.contentOrNull
                        ?.takeIf(String::isNotEmpty)
                    if (eventId == null) {
                        MatrixSendResult.ProtocolFailure
                    } else {
                        MatrixSendResult.Accepted(eventId)
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            MatrixSendResult.UnknownOutcome(transactionId)
        } catch (_: IOException) {
            MatrixSendResult.UnknownOutcome(transactionId)
        }
    }

    // SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1
    private fun sendTextEvent(
        token: String,
        roomId: String,
        body: String,
    ): MatrixSendResult {
        require(body.isNotBlank()) { "Matrix Direct Chat text cannot be blank" }
        val payload = buildJsonObject {
            put("body", body)
            put("msgtype", "m.text")
        }.toString()
        val transactionId = "subshell_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val url = MATRIX_BASE.newBuilder()
            .addPathSegments("_matrix/client/v3/rooms")
            .addPathSegment(roomId)
            .addPathSegments("send/m.room.message")
            .addPathSegment(transactionId)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .put(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    MatrixSendResult.HttpFailure(response.code, redditChatMatrixErrcode(responseBody))
                } else {
                    val eventId = parseObject(responseBody)
                        ?.get("event_id")
                        ?.primitiveOrNull
                        ?.contentOrNull
                        ?.takeIf(String::isNotEmpty)
                    if (eventId == null) {
                        MatrixSendResult.ProtocolFailure
                    } else {
                        MatrixSendResult.Accepted(eventId)
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            MatrixSendResult.UnknownOutcome(transactionId)
        } catch (_: IOException) {
            MatrixSendResult.UnknownOutcome(transactionId)
        }
    }

    // CHATREAD01_LOST_SEND_READ_BACK_BY_TRANSACTION_V1 (OCB-485): a send whose
    // answer was lost is read back once: the room's recent timeline, read with
    // the same token, carries unsigned.transaction_id on this sender's events.
    // Found = sent (its event id); anything else stays outcome unknown. Never
    // re-sent: a new PUT would use a new transaction id and post a copy.
    private fun readBackIfUnknown(
        token: String,
        roomId: String,
        result: MatrixSendResult,
    ): MatrixSendResult {
        if (result !is MatrixSendResult.UnknownOutcome) return result
        val filter = """{"room":{"rooms":[${JsonPrimitive(roomId)}],"timeline":{"limit":30}}}"""
        val read = matrixGet(
            token = token,
            pathSegments = listOf("_matrix", "client", "v3", "sync"),
            query = mapOf("timeout" to "0", "filter" to filter),
        ) as? MatrixHttpResult.Success ?: return result
        val eventId = redditChatMatrixEventIdForTransaction(read.body, result.transactionId)
            ?: return result
        Log.i(MATRIX_DUAL_WRITE_LOG_TAG, "stage=read_back_found")
        return MatrixSendResult.Accepted(eventId)
    }

    private fun invalidateAuthorization(token: String) {
        sessionCache.invalidateToken(token)
        tokenProvider.invalidate(token)
    }

    private fun matrixGet(
        token: String,
        pathSegments: List<String>,
        query: Map<String, String> = emptyMap(),
    ): MatrixHttpResult {
        val builder = MATRIX_BASE.newBuilder()
        pathSegments.forEach { segment -> builder.addPathSegment(segment) }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body.redditChatMatrixTextWithinCap()
                if (response.isSuccessful) {
                    MatrixHttpResult.Success(body)
                } else {
                    MatrixHttpResult.HttpFailure(response.code)
                }
            }
        } catch (_: IOException) {
            MatrixHttpResult.TransportFailure
        }
    }

    private fun parseObject(body: String): JsonObject? = runCatching {
        json.parseToJsonElement(body) as? JsonObject
    }.getOrNull()

    private fun imageDimensions(file: File): Pair<Int, Int>? {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private fun mapHttp(
        statusCode: Int,
        step: String,
        roomRefused: Boolean = false,
    ): AuthenticatedRedditMessageReplyResult.Failed = failed(
        kind = if (roomRefused) {
            MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE
        } else {
            redditChatMatrixHttpFailureKind(statusCode, step)
        },
        statusCode = statusCode,
        step = step,
    )

    private fun failed(
        kind: MessageReplyFailureKind,
        statusCode: Int? = null,
        step: String? = null,
    ): AuthenticatedRedditMessageReplyResult.Failed =
        AuthenticatedRedditMessageReplyResult.Failed(
            kind = kind,
            retryable = false,
            statusCode = statusCode,
            providerCode = step,
        )

    private sealed interface MatrixHttpResult {
        data class Success(val body: String) : MatrixHttpResult
        data class HttpFailure(val statusCode: Int) : MatrixHttpResult
        data object TransportFailure : MatrixHttpResult
    }

    private sealed interface MatrixRoomResolution {
        data class Found(val roomId: String) : MatrixRoomResolution
        data object NotFound : MatrixRoomResolution
        data object AuthorizationRequired : MatrixRoomResolution
        data object TransportFailure : MatrixRoomResolution
        data object ProtocolFailure : MatrixRoomResolution

        // CORRECT04_ROOM_LOOKUP_HTTP_IS_NOT_PROTOCOL_V1 (OCB-483): a 429/5xx while
        // looking up the room is an HTTP failure (retryable), not a protocol one
        // (which cleared the live resume cursor and named the wrong problem).
        data class HttpFailure(val statusCode: Int) : MatrixRoomResolution
    }

    private sealed interface MatrixUploadResult {
        data class Uploaded(val contentUri: String) : MatrixUploadResult
        data class HttpFailure(val statusCode: Int) : MatrixUploadResult
        data object TransportFailure : MatrixUploadResult
        data object ProtocolFailure : MatrixUploadResult
    }

    private sealed interface MatrixSendResult {
        data class Accepted(val eventId: String) : MatrixSendResult
        data class HttpFailure(val statusCode: Int, val errcode: String? = null) : MatrixSendResult
        // DEAD03_NO_MATRIX_SEND_TRANSPORT_FAILURE_V1 (OCB-484): every IOException
        // during a send is an unknown outcome, so there is no transport variant.
        data class UnknownOutcome(val transactionId: String) : MatrixSendResult
        data object ProtocolFailure : MatrixSendResult
    }

    companion object {
        private const val MATRIX_MEDIA_PERF_LOG_TAG = "subshell-matrix-media"
        private val MATRIX_BASE = "https://matrix.redditspace.com".toHttpUrl()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_MEMBER_FALLBACK_ROOMS = 30
    }
}

internal fun matrixUserMatchesRedditProviderId(
    matrixUserId: String,
    redditProviderAccountId: String,
): Boolean = matrixUserId.trim().equals(
    "@t2_${redditProviderAccountId.trim()}:reddit.com",
    ignoreCase = true,
)

internal fun redditMatrixMediaId(contentUri: String): String? {
    val prefix = "mxc://reddit.com/"
    return contentUri.trim()
        .takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.takeIf { it.isNotBlank() && '/' !in it }
}

internal fun redditMatrixDownloadUri(contentUri: String): String? {
    val mediaId = redditMatrixMediaId(contentUri) ?: return null
    return "https://matrix.redditspace.com".toHttpUrl()
        .newBuilder()
        .addPathSegments("_matrix/media/v3/download/reddit.com")
        .addPathSegment(mediaId)
        .build()
        .toString()
}

/** The event id of this sender's event with [transactionId] in a sync answer, or null. */
internal fun redditChatMatrixEventIdForTransaction(body: String, transactionId: String): String? {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return null
    fun find(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            val unsigned = element["unsigned"]?.objectOrNull
            val matches = unsigned?.get("transaction_id")?.primitiveOrNull?.contentOrNull == transactionId
            val eventId = element["event_id"]?.primitiveOrNull?.contentOrNull
            if (matches && !eventId.isNullOrEmpty()) eventId else element.values.firstNotNullOfOrNull(::find)
        }
        is JsonArray -> element.firstNotNullOfOrNull(::find)
        is JsonPrimitive -> null
    }
    return find(root)
}

// CHATSEND01_OUTCOME_UNKNOWN_V1: a send answered 5xx/408/421/425 may have been
// stored by the homeserver, and a resend uses a new transaction id, so it is
// outcome unknown. whoami and the upload deliver nothing to the chat.
internal fun redditChatMatrixHttpFailureKind(statusCode: Int, step: String): MessageReplyFailureKind =
    when {
        statusCode == 401 || statusCode == 403 -> MessageReplyFailureKind.CHAT_SESSION_REQUIRED
        statusCode == 429 -> MessageReplyFailureKind.RATE_LIMIT
        (step == CHAT_STEP_IMAGE_SEND || step == CHAT_STEP_TEXT_SEND) &&
            isRedditWriteOutcomeUnknownStatus(statusCode) ->
            MessageReplyFailureKind.OUTCOME_UNKNOWN
        else -> MessageReplyFailureKind.HTTP
    }

// CHAT01_CHAT_SESSION_FAILURE_STEP_V1: which Direct Chat media step failed.
internal const val CHAT_STEP_NO_WEB_SESSION = "CHAT_NO_WEB_SESSION"
internal const val CHAT_STEP_TOKEN_FAILED = "CHAT_WEB_TOKEN_FAILED"
internal const val CHAT_STEP_WHOAMI = "CHAT_MATRIX_WHOAMI"
internal const val CHAT_STEP_IDENTITY_MISMATCH = "CHAT_MATRIX_ACCOUNT_MISMATCH"
internal const val CHAT_STEP_ROOM = "CHAT_MATRIX_ROOM"
internal const val CHAT_STEP_UPLOAD = "CHAT_MATRIX_UPLOAD"
internal const val CHAT_STEP_IMAGE_SEND = "CHAT_MATRIX_IMAGE_SEND"
internal const val CHAT_STEP_TEXT_SEND = "CHAT_MATRIX_TEXT_SEND"
