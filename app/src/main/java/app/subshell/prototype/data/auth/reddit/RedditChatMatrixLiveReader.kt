package app.subshell.prototype.data.auth.reddit

// SUBSHELL_MESSAGES02C7_FOREGROUND_MATRIX_DIRECT_CHAT_LIVE_READ_V1

import android.util.Log
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.chat.RedditChatWebTokenResult
import app.subshell.prototype.data.account.DirectChatLiveFailureKind
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.MessageBubble
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal sealed interface AuthenticatedRedditDirectChatLiveResult {
    data class Failed(
        val kind: DirectChatLiveFailureKind,
        val statusCode: Int? = null,
    ) : AuthenticatedRedditDirectChatLiveResult
}

internal interface AuthenticatedRedditDirectChatLiveReader {
    suspend fun stream(
        accountStableId: String,
        participantUsername: String,
        since: String? = null,
        onConnected: () -> Unit,
        onCursor: (String) -> Unit,
        onMessages: (List<MessageBubble>) -> Unit,
        onPeerTyping: (Boolean) -> Unit,
        onRedactions: (List<String>) -> Unit,
    ): AuthenticatedRedditDirectChatLiveResult
}

// SUBSHELL_MESSAGES02D1_MATRIX_EPHEMERAL_READ_PROBE_V1
internal data class RedditChatMatrixReadReceiptObservation(
    val eventId: String,
    val timestampMs: Long?,
    val threaded: Boolean,
)

internal data class RedditChatMatrixEphemeralObservation(
    val typingEventCount: Int = 0,
    val peerTypingUpdate: Boolean? = null,
    val receiptEventCount: Int = 0,
    val peerPublicReadReceipt: RedditChatMatrixReadReceiptObservation? = null,
    val peerPrivateReadReceiptObserved: Boolean = false,
)

// SUBSHELL_MESSAGES02D3A_REDACTION_READ_PROBE_V1
internal data class RedditChatMatrixRedactionObservation(
    val targetEventId: String,
    val senderIsSelf: Boolean,
)

// SUBSHELL_MESSAGES02E1_REPLY_REACTION_READ_PROBE_V1
internal data class RedditChatMatrixTimelineRelationObservation(
    val eventType: String,
    val relationType: String?,
    val senderIsSelf: Boolean,
    val relationTargetPresent: Boolean,
    val replyTargetPresent: Boolean,
    val messageType: String?,
    val reactionKeyPresent: Boolean,
)

// SUBSHELL_MESSAGES02E1R2_REACTION_SHAPE_READ_PROBE_V1
internal data class RedditChatMatrixTimelineShapeObservation(
    val eventType: String,
    val relationType: String?,
    val senderIsSelf: Boolean,
    val contentRelatesToPresent: Boolean,
    val relationTargetPresent: Boolean,
    val replyTargetPresent: Boolean,
    val messageType: String?,
    val unsignedPresent: Boolean,
    val unsignedRelationsPresent: Boolean,
    val unsignedAnnotationPresent: Boolean,
    val unsignedThreadPresent: Boolean,
    val unsignedReactionNamedStructurePresent: Boolean,
    val contentReactionNamedStructurePresent: Boolean,
)

internal data class RedditChatMatrixLiveSyncProjection(
    val nextBatch: String,
    val messages: List<MessageBubble>,
    val roomLeft: Boolean,
    val ephemeral: RedditChatMatrixEphemeralObservation =
        RedditChatMatrixEphemeralObservation(),
    val redactions: List<RedditChatMatrixRedactionObservation> = emptyList(),
    val timelineRelations: List<RedditChatMatrixTimelineRelationObservation> = emptyList(),
    val timelineShapes: List<RedditChatMatrixTimelineShapeObservation> = emptyList(),
    // LOW5_GAPPED_SYNC_IS_BACKFILLED_V1 (OCB-611): the server left events out
    // of this timeline (limited); prev_batch is where the gap ends.
    val limited: Boolean = false,
    val prevBatch: String? = null,
)

/**
 * Foreground-only read path for Reddit Direct Chat.
 *
 * The reader shares the C6 first-party web JWT and Matrix identity/room cache with the media
 * writer. It performs only GET /sync and GET room/member discovery calls. Cancellation cancels
 * the in-flight OkHttp call immediately, so leaving the screen or backgrounding the host cannot
 * leave a long poll running until its server timeout.
 */
internal class RedditChatMatrixLiveReader(
    private val capability: AuthInboxReadCapability,
    private val tokenProvider: RedditChatWebTokenSource,
    private val client: OkHttpClient,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionCache: RedditChatMatrixSessionCache = RedditChatMatrixSessionCache(),
) : AuthenticatedRedditDirectChatLiveReader {
    override suspend fun stream(
        accountStableId: String,
        participantUsername: String,
        since: String?,
        onConnected: () -> Unit,
        onCursor: (String) -> Unit,
        onMessages: (List<MessageBubble>) -> Unit,
        onPeerTyping: (Boolean) -> Unit,
        onRedactions: (List<String>) -> Unit,
    ): AuthenticatedRedditDirectChatLiveResult = withContext(ioDispatcher) {
        val account = accountStableId.trim()
        val participant = participantUsername.trim()
        if (account.isEmpty() || participant.isEmpty()) {
            return@withContext failed(DirectChatLiveFailureKind.PROTOCOL)
        }

        val oauthSession = when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> authorization.session
            else -> return@withContext failed(DirectChatLiveFailureKind.SESSION)
        }
        val redditIdentity = oauthSession.identity
            ?: return@withContext failed(DirectChatLiveFailureKind.SESSION)
        if (
            authenticatedAccountId(
                provider = oauthSession.provider,
                clientMode = oauthSession.clientMode,
                providerAccountId = redditIdentity.providerAccountId,
            ) != account ||
            !oauthSession.tokens.grants(RedditOAuth.liveInboxReadScopes)
        ) {
            return@withContext failed(DirectChatLiveFailureKind.SESSION)
        }

        val webTokenResult = when (val result = tokenProvider.token()) {
            is RedditChatWebTokenResult.Success -> result
            RedditChatWebTokenResult.NoRedditWebSession,
            RedditChatWebTokenResult.Failed,
            -> return@withContext failed(DirectChatLiveFailureKind.SESSION)
        }
        val token = webTokenResult.token

        val cachedIdentity = sessionCache.identityFor(
            token = token,
            providerAccountId = redditIdentity.providerAccountId,
        )
        val matrixUserId = cachedIdentity ?: when (
            val whoami = matrixGet(
                token = token,
                pathSegments = listOf("_matrix", "client", "v3", "account", "whoami"),
            )
        ) {
            is MatrixHttpResult.Success -> {
                val resolved = parseObject(whoami.body)
                    ?.get("user_id")
                    ?.primitiveOrNull
                    ?.contentOrNull
                    ?: return@withContext failed(DirectChatLiveFailureKind.PROTOCOL)
                if (!matrixUserMatchesRedditProviderId(resolved, redditIdentity.providerAccountId)) {
                    invalidateAuthorization(token)
                    return@withContext failed(DirectChatLiveFailureKind.SESSION)
                }
                sessionCache.rememberIdentity(
                    token = token,
                    providerAccountId = redditIdentity.providerAccountId,
                    matrixUserId = resolved,
                )
                resolved
            }
            is MatrixHttpResult.HttpFailure -> {
                if (whoami.statusCode == 401 || whoami.statusCode == 403) {
                    invalidateAuthorization(token)
                    return@withContext failed(DirectChatLiveFailureKind.SESSION, whoami.statusCode)
                }
                return@withContext failed(DirectChatLiveFailureKind.HTTP, whoami.statusCode)
            }
            MatrixHttpResult.TransportFailure ->
                return@withContext failed(DirectChatLiveFailureKind.TRANSPORT)
        }

        val cachedRoomId = sessionCache.roomFor(
            token = token,
            providerAccountId = redditIdentity.providerAccountId,
            participantUsername = participant,
        )
        val roomId = cachedRoomId ?: when (
            val resolution = resolveDirectRoom(
                token = token,
                ownUserId = matrixUserId,
                participantUsername = participant,
            )
        ) {
            is MatrixRoomResolution.Found -> resolution.roomId.also { resolvedRoom ->
                sessionCache.rememberRoom(
                    token = token,
                    providerAccountId = redditIdentity.providerAccountId,
                    participantUsername = participant,
                    roomId = resolvedRoom,
                    tokenExpiresAtEpochMs = webTokenResult.expiresAtEpochMs,
                )
            }
            MatrixRoomResolution.NotFound ->
                return@withContext failed(DirectChatLiveFailureKind.ROOM)
            MatrixRoomResolution.AuthorizationRequired -> {
                invalidateAuthorization(token)
                return@withContext failed(DirectChatLiveFailureKind.SESSION)
            }
            MatrixRoomResolution.TransportFailure ->
                return@withContext failed(DirectChatLiveFailureKind.TRANSPORT)
            MatrixRoomResolution.ProtocolFailure ->
                return@withContext failed(DirectChatLiveFailureKind.PROTOCOL)
            is MatrixRoomResolution.HttpFailure ->
                return@withContext failed(DirectChatLiveFailureKind.HTTP, resolution.statusCode)
        }

        var nextBatch = since?.trim()?.takeIf(String::isNotEmpty)
        if (nextBatch == null) {
            when (
                val baseline = matrixSync(
                    token = token,
                    roomId = roomId,
                    since = null,
                    timeoutMs = 0L,
                    timelineLimit = INITIAL_TIMELINE_LIMIT,
                    ownUserId = matrixUserId,
                    ownUsername = redditIdentity.username,
                    participantUsername = participant,
                )
            ) {
                is MatrixSyncResult.Projected -> {
                    if (baseline.projection.roomLeft) {
                        sessionCache.invalidateRoom(
                            token = token,
                            providerAccountId = redditIdentity.providerAccountId,
                            participantUsername = participant,
                        )
                        return@withContext failed(DirectChatLiveFailureKind.ROOM)
                    }
                    nextBatch = baseline.projection.nextBatch
                    onCursor(baseline.projection.nextBatch)
                    // SUBSHELL_MESSAGES02E2A_R1_BASELINE_MATRIX_HYDRATION_V1
                    // Initial /sync is a bounded room timeline, not merely a notification delta.
                    // Forward Matrix-backed messages regardless of age so existing displayed
                    // T4 bubbles can reconcile to exact live event IDs for React/Delete.
                    val baselineMessages =
                        redditChatMatrixBaselineHydrationMessages(baseline.projection.messages)
                    if (baselineMessages.isNotEmpty()) onMessages(baselineMessages)
                    baseline.projection.ephemeral.peerTypingUpdate?.let(onPeerTyping)
                    if (baseline.projection.redactions.isNotEmpty()) {
                        onRedactions(baseline.projection.redactions.map { it.targetEventId })
                    }
                }
                is MatrixSyncResult.HttpFailure -> {
                    if (baseline.statusCode == 401 || baseline.statusCode == 403) {
                        invalidateAuthorization(token)
                        return@withContext failed(
                            DirectChatLiveFailureKind.SESSION,
                            baseline.statusCode,
                        )
                    }
                    return@withContext failed(DirectChatLiveFailureKind.HTTP, baseline.statusCode)
                }
                MatrixSyncResult.TransportFailure ->
                    return@withContext failed(DirectChatLiveFailureKind.TRANSPORT)
                MatrixSyncResult.ProtocolFailure ->
                    return@withContext failed(DirectChatLiveFailureKind.PROTOCOL)
            }
        }

        Log.d(
            LIVE_LOG_TAG,
            "connected identity_cache=${cachedIdentity != null} " +
                "room_cache=${cachedRoomId != null} resumed=${since != null}",
        )
        onConnected()

        while (true) {
            val cursor = requireNotNull(nextBatch)
            when (
                val sync = matrixSync(
                    token = token,
                    roomId = roomId,
                    since = cursor,
                    timeoutMs = LONG_POLL_TIMEOUT_MS,
                    timelineLimit = LIVE_TIMELINE_LIMIT,
                    ownUserId = matrixUserId,
                    ownUsername = redditIdentity.username,
                    participantUsername = participant,
                )
            ) {
                is MatrixSyncResult.Projected -> {
                    if (sync.projection.roomLeft) {
                        sessionCache.invalidateRoom(
                            token = token,
                            providerAccountId = redditIdentity.providerAccountId,
                            participantUsername = participant,
                        )
                        return@withContext failed(DirectChatLiveFailureKind.ROOM)
                    }
                    if (sync.projection.limited) {
                        // LOW5_GAPPED_SYNC_IS_BACKFILLED_V1 (OCB-611): read what the
                        // server left out, back to the cursor this sync started at.
                        val gap = backfillGap(
                            token = token,
                            roomId = roomId,
                            from = sync.projection.prevBatch,
                            to = cursor,
                        )
                        Log.d(LIVE_LOG_TAG, "gap_backfill events=${gap.size}")
                        val gapMessages = redditChatMatrixTimelineMessages(
                            events = gap,
                            ownUserId = matrixUserId,
                            ownUsername = redditIdentity.username,
                            participantUsername = participant,
                        )
                        if (gapMessages.isNotEmpty()) onMessages(gapMessages)
                        val gapRedactions = redditChatMatrixTimelineRedactions(gap, matrixUserId)
                        if (gapRedactions.isNotEmpty()) onRedactions(gapRedactions.map { it.targetEventId })
                    }
                    nextBatch = sync.projection.nextBatch
                    onCursor(sync.projection.nextBatch)
                    if (sync.projection.messages.isNotEmpty()) {
                        Log.d(LIVE_LOG_TAG, "events=${sync.projection.messages.size}")
                        onMessages(sync.projection.messages)
                    }
                    sync.projection.ephemeral.peerTypingUpdate?.let(onPeerTyping)
                    if (sync.projection.redactions.isNotEmpty()) {
                        onRedactions(sync.projection.redactions.map { it.targetEventId })
                    }
                }
                is MatrixSyncResult.HttpFailure -> {
                    if (sync.statusCode == 401 || sync.statusCode == 403) {
                        invalidateAuthorization(token)
                        return@withContext failed(
                            DirectChatLiveFailureKind.SESSION,
                            sync.statusCode,
                        )
                    }
                    return@withContext failed(DirectChatLiveFailureKind.HTTP, sync.statusCode)
                }
                MatrixSyncResult.TransportFailure ->
                    return@withContext failed(DirectChatLiveFailureKind.TRANSPORT)
                MatrixSyncResult.ProtocolFailure ->
                    return@withContext failed(DirectChatLiveFailureKind.PROTOCOL)
            }
        }
        failed(DirectChatLiveFailureKind.PROTOCOL) // C7R1 unreachable stream fallback
    }

    private suspend fun matrixSync(
        token: String,
        roomId: String,
        since: String?,
        timeoutMs: Long,
        timelineLimit: Int,
        ownUserId: String,
        ownUsername: String,
        participantUsername: String,
    ): MatrixSyncResult {
        val query = linkedMapOf(
            "timeout" to timeoutMs.toString(),
            "filter" to redditChatMatrixLiveSyncFilter(
                roomId = roomId,
                timelineLimit = timelineLimit,
                ephemeralTypes = DIRECT_CHAT_EPHEMERAL_READ_TYPES,
            ),
        )
        since?.takeIf(String::isNotBlank)?.let { query["since"] = it }
        return when (
            val result = matrixGet(
                token = token,
                pathSegments = listOf("_matrix", "client", "v3", "sync"),
                query = query,
            )
        ) {
            is MatrixHttpResult.Success -> {
                val projection = redditChatMatrixLiveSyncProjection(
                    body = result.body,
                    roomId = roomId,
                    ownUserId = ownUserId,
                    ownUsername = ownUsername,
                    participantUsername = participantUsername,
                    json = json,
                ) ?: return MatrixSyncResult.ProtocolFailure
                val ephemeral = projection.ephemeral
                if (
                    ephemeral.typingEventCount > 0 ||
                    ephemeral.receiptEventCount > 0
                ) {
                    Log.d(
                        EPHEMERAL_LOG_TAG,
                        "typing_events=${ephemeral.typingEventCount} " +
                            "peer_typing=${ephemeral.peerTypingUpdate?.toString() ?: "unchanged"} " +
                            "receipt_events=${ephemeral.receiptEventCount} " +
                            "peer_read=${ephemeral.peerPublicReadReceipt != null} " +
                            "peer_read_threaded=${ephemeral.peerPublicReadReceipt?.threaded == true} " +
                            "peer_private=${ephemeral.peerPrivateReadReceiptObserved}",
                    )
                }
                if (projection.redactions.isNotEmpty()) {
                    val selfCount = projection.redactions.count { it.senderIsSelf }
                    Log.d(
                        REDACTION_LOG_TAG,
                        "redaction_events=${projection.redactions.size} " +
                            "sender_self=$selfCount " +
                            "sender_peer=${projection.redactions.size - selfCount}",
                    )
                }
                // DEAD05_TIMELINE_PROBE_LOGS_DEBUG_ONLY_V1 (OCB-487): the
                // timeline relation/shape probe lines are for debug builds;
                // release builds are not minified, so they would ship.
                if (BuildConfig.DEBUG) {
                    projection.timelineRelations.forEach { observation ->
                        Log.d(
                            TIMELINE_PROBE_LOG_TAG,
                            "event=${observation.eventType} " +
                                "rel=${observation.relationType ?: "none"} " +
                                "sender=${if (observation.senderIsSelf) "self" else "other"} " +
                                "relation_target=${observation.relationTargetPresent} " +
                                "reply_target=${observation.replyTargetPresent} " +
                                "msgtype=${observation.messageType ?: "none"} " +
                                "reaction_key=${observation.reactionKeyPresent}",
                        )
                    }
                    projection.timelineShapes.forEach { observation ->
                        Log.d(
                            TIMELINE_SHAPE_LOG_TAG,
                            "event=${observation.eventType} " +
                                "rel=${observation.relationType ?: "none"} " +
                                "sender=${if (observation.senderIsSelf) "self" else "other"} " +
                                "content_relates=${observation.contentRelatesToPresent} " +
                                "relation_target=${observation.relationTargetPresent} " +
                                "reply_target=${observation.replyTargetPresent} " +
                                "msgtype=${observation.messageType ?: "none"} " +
                                "unsigned=${observation.unsignedPresent} " +
                                "unsigned_relations=${observation.unsignedRelationsPresent} " +
                                "unsigned_annotation=${observation.unsignedAnnotationPresent} " +
                                "unsigned_thread=${observation.unsignedThreadPresent} " +
                                "unsigned_reaction_named=${observation.unsignedReactionNamedStructurePresent} " +
                                "content_reaction_named=${observation.contentReactionNamedStructurePresent}",
                        )
                    }
                }
                MatrixSyncResult.Projected(projection)
            }
            is MatrixHttpResult.HttpFailure -> MatrixSyncResult.HttpFailure(result.statusCode)
            MatrixHttpResult.TransportFailure -> MatrixSyncResult.TransportFailure
        }
    }

    private suspend fun backfillGap(
        token: String,
        roomId: String,
        from: String?,
        to: String,
    ): JsonArray =
        redditChatMatrixBackfillGap(from = from, pages = GAP_BACKFILL_PAGES, json = json) { pageFrom ->
            (
                matrixGet(
                    token = token,
                    pathSegments = listOf("_matrix", "client", "v3", "rooms", roomId, "messages"),
                    query = mapOf(
                        "from" to pageFrom,
                        "to" to to,
                        "dir" to "b",
                        "limit" to GAP_BACKFILL_PAGE_SIZE.toString(),
                    ),
                ) as? MatrixHttpResult.Success
            )?.body
        }

    private suspend fun resolveDirectRoom(
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
            is MatrixHttpResult.HttpFailure -> return if (
                result.statusCode == 401 || result.statusCode == 403
            ) {
                MatrixRoomResolution.AuthorizationRequired
            } else {
                MatrixRoomResolution.HttpFailure(result.statusCode)
            }
            MatrixHttpResult.TransportFailure -> return MatrixRoomResolution.TransportFailure
        }
        val joined = root["rooms"]
            ?.objectOrNull
            ?.get("join")
            ?.objectOrNull
            ?: return MatrixRoomResolution.NotFound

        val directMatches = joined.mapNotNull { (candidateRoomId, roomElement) ->
            if (
                roomContainsDirectParticipants(
                    room = roomElement.objectOrNull ?: return@mapNotNull null,
                    ownUserId = ownUserId,
                    participantUsername = participantUsername,
                )
            ) {
                candidateRoomId
            } else {
                null
            }
        }
        if (directMatches.size == 1) {
            return MatrixRoomResolution.Found(directMatches.single())
        }

        val fallbackMatches = mutableListOf<String>()
        joined.keys.take(MAX_MEMBER_FALLBACK_ROOMS).forEach { candidateRoomId ->
            when (
                val members = matrixGet(
                    token = token,
                    pathSegments = listOf(
                        "_matrix",
                        "client",
                        "v3",
                        "rooms",
                        candidateRoomId,
                        "members",
                    ),
                    query = mapOf("not_membership" to "leave"),
                )
            ) {
                is MatrixHttpResult.Success -> {
                    val memberRoot = parseObject(members.body) ?: return@forEach
                    if (
                        memberEventsMatchDirectParticipants(
                            events = memberRoot["chunk"] as? JsonArray
                                ?: JsonArray(emptyList()),
                            ownUserId = ownUserId,
                            participantUsername = participantUsername,
                        )
                    ) {
                        fallbackMatches += candidateRoomId
                    }
                }
                is MatrixHttpResult.HttpFailure,
                MatrixHttpResult.TransportFailure,
                -> Unit
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

    private suspend fun matrixGet(
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
        return executeCancellable(request)
    }

    private suspend fun executeCancellable(request: Request): MatrixHttpResult =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWith(
                            Result.success(MatrixHttpResult.TransportFailure),
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = try {
                            response.use { current ->
                                val body = current.body.redditChatMatrixTextWithinCap()
                                if (current.isSuccessful) {
                                    MatrixHttpResult.Success(body)
                                } else {
                                    MatrixHttpResult.HttpFailure(current.code)
                                }
                            }
                        } catch (_: IOException) {
                            MatrixHttpResult.TransportFailure
                        }
                        continuation.resumeWith(
                            Result.success(result),
                        )
                    }
                },
            )
        }

    private fun invalidateAuthorization(token: String) {
        sessionCache.invalidateToken(token)
        tokenProvider.invalidate(token)
    }

    private fun parseObject(body: String): JsonObject? = runCatching {
        json.parseToJsonElement(body) as? JsonObject
    }.getOrNull()

    private fun failed(
        kind: DirectChatLiveFailureKind,
        statusCode: Int? = null,
    ): AuthenticatedRedditDirectChatLiveResult.Failed =
        AuthenticatedRedditDirectChatLiveResult.Failed(
            kind = kind,
            statusCode = statusCode,
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

    private sealed interface MatrixSyncResult {
        data class Projected(
            val projection: RedditChatMatrixLiveSyncProjection,
        ) : MatrixSyncResult

        data class HttpFailure(val statusCode: Int) : MatrixSyncResult
        data object TransportFailure : MatrixSyncResult
        data object ProtocolFailure : MatrixSyncResult
    }

    companion object {
        private const val LIVE_LOG_TAG = "subshell-matrix-live"
        private const val EPHEMERAL_LOG_TAG = "subshell-matrix-ephemeral"
        private const val REDACTION_LOG_TAG = "subshell-matrix-redaction"
        private const val TIMELINE_PROBE_LOG_TAG = "subshell-matrix-relation"
        private const val TIMELINE_SHAPE_LOG_TAG = "subshell-matrix-shape"
        private val MATRIX_BASE = "https://matrix.redditspace.com".toHttpUrl()
        private val DIRECT_CHAT_EPHEMERAL_READ_TYPES = listOf("m.typing", "m.receipt")
        private const val LONG_POLL_TIMEOUT_MS = 20_000L
        private const val INITIAL_TIMELINE_LIMIT = 20
        private const val LIVE_TIMELINE_LIMIT = 50
        private const val MAX_MEMBER_FALLBACK_ROOMS = 30
        private const val GAP_BACKFILL_PAGES = 3
        private const val GAP_BACKFILL_PAGE_SIZE = 100
    }
}

internal fun redditChatMatrixLiveSyncFilter(
    roomId: String,
    timelineLimit: Int,
    ephemeralTypes: List<String> = emptyList(),
): String {
    require(roomId.isNotBlank()) { "Matrix live room ID cannot be blank" }
    require(timelineLimit in 0..100) { "Matrix live timeline limit must be between 0 and 100" }
    val normalizedEphemeralTypes = ephemeralTypes
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
    require(
        normalizedEphemeralTypes.all { type ->
            type == "m.typing" || type == "m.receipt"
        },
    ) {
        "Matrix Direct Chat ephemeral filter only allows m.typing and m.receipt"
    }
    return buildJsonObject {
        putJsonObject("room") {
            putJsonArray("rooms") { add(JsonPrimitive(roomId)) }
            putJsonObject("timeline") { put("limit", timelineLimit) }
            putJsonObject("state") { putJsonArray("types") { } }
            if (normalizedEphemeralTypes.isEmpty()) {
                // Preserve C7's explicit no-ephemeral default for callers that do not opt in.
                putJsonObject("ephemeral") { putJsonArray("types") { } }
            } else {
                putJsonObject("ephemeral") {
                    putJsonArray("types") {
                        normalizedEphemeralTypes.forEach { type ->
                            add(JsonPrimitive(type))
                        }
                    }
                }
            }
            putJsonObject("account_data") { putJsonArray("types") { } }
        }
        putJsonObject("presence") { putJsonArray("types") { } }
    }.toString()
}


// SUBSHELL_MESSAGES02E2A_R1_BASELINE_MATRIX_HYDRATION_V1
internal fun redditChatMatrixBaselineHydrationMessages(
    messages: List<MessageBubble>,
): List<MessageBubble> =
    messages
        .asSequence()
        .filter { message -> message.id.startsWith('$') }
        .distinctBy(MessageBubble::id)
        .toList()

internal fun redditChatMatrixLiveSyncProjection(
    body: String,
    roomId: String,
    ownUserId: String,
    ownUsername: String,
    participantUsername: String,
    json: Json = SubshellNetworkJson.providerJson,
): RedditChatMatrixLiveSyncProjection? {
    val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: return null
    val nextBatch = root["next_batch"]
        ?.primitiveOrNull
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: return null
    val rooms = root["rooms"] as? JsonObject
    val roomLeft = (rooms?.get("leave") as? JsonObject)?.containsKey(roomId) == true
    val joinedRoom = (rooms?.get("join") as? JsonObject)
        ?.get(roomId)
        ?.let { it as? JsonObject }
    val timeline = joinedRoom
        ?.get("timeline")
        ?.let { it as? JsonObject }
    val events = timeline
        ?.get("events")
        ?.let { it as? JsonArray }
        ?: JsonArray(emptyList())
    val limited = (timeline?.get("limited") as? JsonPrimitive)?.booleanOrNull == true
    val prevBatch = (timeline?.get("prev_batch") as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
    val ephemeral = redditChatMatrixEphemeralObservation(
        room = joinedRoom,
        ownUserId = ownUserId,
    )

    // CHATLIVE02_SAFE_MATRIX_JSON_V1: one event the projection cannot read is
    // skipped; it never fails the batch (the cursor would replay it forever).
    val messages = redditChatMatrixTimelineMessages(
        events = events,
        ownUserId = ownUserId,
        ownUsername = ownUsername,
        participantUsername = participantUsername,
    )
    val redactions = redditChatMatrixTimelineRedactions(events = events, ownUserId = ownUserId)
    val timelineRelations = redditChatMatrixTimelineRelationObservations(
        events = events,
        ownUserId = ownUserId,
    )
    val timelineShapes = redditChatMatrixTimelineShapeObservations(
        events = events,
        ownUserId = ownUserId,
    )

    return RedditChatMatrixLiveSyncProjection(
        nextBatch = nextBatch,
        messages = messages,
        roomLeft = roomLeft,
        ephemeral = ephemeral,
        redactions = redactions,
        timelineRelations = timelineRelations,
        timelineShapes = timelineShapes,
        limited = limited,
        prevBatch = prevBatch,
    )
}

internal fun redditChatMatrixTimelineMessages(
    events: JsonArray,
    ownUserId: String,
    ownUsername: String,
    participantUsername: String,
): List<MessageBubble> =
    events
        .mapNotNull { element ->
            runCatching {
                redditChatMatrixLiveMessageBubble(
                    element = element,
                    ownUserId = ownUserId,
                    ownUsername = ownUsername,
                    participantUsername = participantUsername,
                )
            }.getOrNull()
        }
        .distinctBy(MessageBubble::id)
        .sortedWith(
            compareBy<MessageBubble> { message -> message.createdEpochSeconds ?: Long.MAX_VALUE }
                .thenBy(MessageBubble::id),
        )

internal fun redditChatMatrixTimelineRedactions(
    events: JsonArray,
    ownUserId: String,
): List<RedditChatMatrixRedactionObservation> =
    events
        .mapNotNull { element ->
            runCatching {
                redditChatMatrixRedactionObservation(
                    element = element,
                    ownUserId = ownUserId,
                )
            }.getOrNull()
        }
        .distinctBy { observation ->
            observation.targetEventId to observation.senderIsSelf
        }

/** One page of GET rooms/{room}/messages: its events and the token to read on from. */
internal data class RedditChatMatrixMessagesPage(
    val events: JsonArray,
    val end: String?,
)

// LOW5_GAPPED_SYNC_IS_BACKFILLED_V1: reads back from [from] (a sync's
// prev_batch) at most [pages] pages; a page that fails or cannot be read keeps
// what was read before it.
internal suspend fun redditChatMatrixBackfillGap(
    from: String?,
    pages: Int,
    json: Json = SubshellNetworkJson.providerJson,
    fetchPage: suspend (from: String) -> String?,
): JsonArray {
    var pageFrom = from ?: return JsonArray(emptyList())
    val events = mutableListOf<JsonElement>()
    repeat(pages) {
        val body = fetchPage(pageFrom) ?: return JsonArray(events)
        val page = redditChatMatrixMessagesPage(body, json) ?: return JsonArray(events)
        events.addAll(page.events)
        pageFrom = page.end?.takeIf { page.events.isNotEmpty() } ?: return JsonArray(events)
    }
    return JsonArray(events)
}

internal fun redditChatMatrixMessagesPage(
    body: String,
    json: Json = SubshellNetworkJson.providerJson,
): RedditChatMatrixMessagesPage? {
    val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: return null
    val chunk = root["chunk"] as? JsonArray ?: return null
    val end = (root["end"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    return RedditChatMatrixMessagesPage(events = chunk, end = end)
}

internal fun redditChatMatrixTimelineRelationObservations(
    events: JsonArray,
    ownUserId: String,
): List<RedditChatMatrixTimelineRelationObservation> =
    events.mapNotNull { element ->
        val event = element as? JsonObject ?: return@mapNotNull null
        val eventTypeRaw = (event["type"] as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val content = event["content"] as? JsonObject ?: return@mapNotNull null
        val relation = content["m.relates_to"] as? JsonObject
        val relationTypeRaw = (relation?.get("rel_type") as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
        val inReplyTo = relation?.get("m.in_reply_to") as? JsonObject
        val replyTargetPresent =
            (inReplyTo?.get("event_id") as? JsonPrimitive)
                ?.contentOrNull
                ?.isNotBlank() == true
        val reactionLike =
            eventTypeRaw == "m.reaction" || relationTypeRaw == "m.annotation"

        if (!replyTargetPresent && !reactionLike) {
            return@mapNotNull null
        }

        RedditChatMatrixTimelineRelationObservation(
            eventType = sanitizedMatrixProbeToken(eventTypeRaw),
            relationType = relationTypeRaw?.let(::sanitizedMatrixProbeToken),
            senderIsSelf =
                (event["sender"] as? JsonPrimitive)?.contentOrNull == ownUserId,
            relationTargetPresent =
                (relation?.get("event_id") as? JsonPrimitive)
                    ?.contentOrNull
                    ?.isNotBlank() == true,
            replyTargetPresent = replyTargetPresent,
            messageType = (content["msgtype"] as? JsonPrimitive)
                ?.contentOrNull
                ?.let(::sanitizedMatrixProbeToken),
            reactionKeyPresent =
                (relation?.get("key") as? JsonPrimitive)?.contentOrNull != null,
        )
    }

internal fun redditChatMatrixTimelineShapeObservations(
    events: JsonArray,
    ownUserId: String,
): List<RedditChatMatrixTimelineShapeObservation> =
    events.mapNotNull { element ->
        val event = element as? JsonObject ?: return@mapNotNull null
        val eventTypeRaw = (event["type"] as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val content = event["content"] as? JsonObject
        val relation = content?.get("m.relates_to") as? JsonObject
        val relationTypeRaw = (relation?.get("rel_type") as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
        val inReplyTo = relation?.get("m.in_reply_to") as? JsonObject
        val unsigned = event["unsigned"] as? JsonObject
        val unsignedRelations =
            (unsigned?.get("m.relations") as? JsonObject)
                ?: (unsigned?.get("relations") as? JsonObject)

        RedditChatMatrixTimelineShapeObservation(
            eventType = sanitizedMatrixProbeToken(eventTypeRaw),
            relationType = relationTypeRaw?.let(::sanitizedMatrixProbeToken),
            senderIsSelf =
                (event["sender"] as? JsonPrimitive)?.contentOrNull == ownUserId,
            contentRelatesToPresent = relation != null,
            relationTargetPresent =
                (relation?.get("event_id") as? JsonPrimitive)
                    ?.contentOrNull
                    ?.isNotBlank() == true,
            replyTargetPresent =
                (inReplyTo?.get("event_id") as? JsonPrimitive)
                    ?.contentOrNull
                    ?.isNotBlank() == true,
            messageType = (content?.get("msgtype") as? JsonPrimitive)
                ?.contentOrNull
                ?.let(::sanitizedMatrixProbeToken),
            unsignedPresent = unsigned != null,
            unsignedRelationsPresent = unsignedRelations != null,
            unsignedAnnotationPresent =
                unsignedRelations?.containsKey("m.annotation") == true,
            unsignedThreadPresent =
                unsignedRelations?.containsKey("m.thread") == true,
            unsignedReactionNamedStructurePresent =
                matrixProbeHasReactionNamedStructure(
                    container = unsigned,
                    nested = unsignedRelations,
                ),
            contentReactionNamedStructurePresent =
                matrixProbeHasReactionNamedStructure(
                    container = content,
                    nested = relation,
                ),
        )
    }

private fun matrixProbeHasReactionNamedStructure(
    container: JsonObject?,
    nested: JsonObject?,
): Boolean =
    container?.keys?.any(::matrixProbeReactionNamedKey) == true ||
        nested?.keys?.any(::matrixProbeReactionNamedKey) == true

private fun matrixProbeReactionNamedKey(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.contains("reaction") || normalized.contains("annotation")
}

internal fun sanitizedMatrixProbeToken(value: String): String {
    val normalized = value.trim().take(64)
    if (normalized.isEmpty()) return "none"
    return if (
        normalized.all { char ->
            char.isLetterOrDigit() || char == '.' || char == '_' || char == '-'
        }
    ) {
        normalized
    } else {
        "other"
    }
}

private fun redditChatMatrixEphemeralObservation(
    room: JsonObject?,
    ownUserId: String,
): RedditChatMatrixEphemeralObservation {
    val ephemeralEvents = room
        ?.get("ephemeral")
        ?.let { it as? JsonObject }
        ?.get("events")
        ?.let { it as? JsonArray }
        ?: JsonArray(emptyList())

    var typingEventCount = 0
    var peerTypingUpdate: Boolean? = null
    var receiptEventCount = 0
    var peerPublicReadReceipt: RedditChatMatrixReadReceiptObservation? = null
    var peerPrivateReadReceiptObserved = false

    ephemeralEvents.forEach { element ->
        val event = element as? JsonObject ?: return@forEach
        when (event["type"]?.primitiveOrNull?.contentOrNull) {
            "m.typing" -> {
                typingEventCount += 1
                val content = event["content"] as? JsonObject ?: return@forEach
                val userIds = content["user_ids"] as? JsonArray ?: return@forEach
                peerTypingUpdate = userIds.any { userElement ->
                    userElement.primitiveOrNull?.contentOrNull
                        ?.takeIf(String::isNotBlank)
                        ?.let { userId -> userId != ownUserId }
                        ?: false
                }
            }

            "m.receipt" -> {
                receiptEventCount += 1
                val content = event["content"] as? JsonObject ?: return@forEach
                for ((eventId, receiptGroupsElement) in content) {
                    if (eventId.isBlank()) continue
                    val receiptGroups = receiptGroupsElement as? JsonObject ?: continue

                    val publicReaders = receiptGroups["m.read"] as? JsonObject
                    if (publicReaders != null) {
                        for ((userId, receiptElement) in publicReaders) {
                            if (userId == ownUserId || userId.isBlank()) continue
                            val receiptContent = receiptElement as? JsonObject
                            val timestampMs = receiptContent
                                ?.get("ts")
                                ?.primitiveOrNull
                                ?.longOrNull
                            val threaded = receiptContent
                                ?.get("thread_id")
                                ?.primitiveOrNull
                                ?.contentOrNull
                                ?.isNotBlank() == true
                            val candidate = RedditChatMatrixReadReceiptObservation(
                                eventId = eventId,
                                timestampMs = timestampMs,
                                threaded = threaded,
                            )
                            val currentTimestamp =
                                peerPublicReadReceipt?.timestampMs ?: Long.MIN_VALUE
                            val candidateTimestamp = timestampMs ?: Long.MIN_VALUE
                            if (
                                peerPublicReadReceipt == null ||
                                candidateTimestamp >= currentTimestamp
                            ) {
                                peerPublicReadReceipt = candidate
                            }
                        }
                    }

                    val privateReaders = receiptGroups["m.read.private"] as? JsonObject
                    if (
                        privateReaders?.keys?.any { userId ->
                            userId.isNotBlank() && userId != ownUserId
                        } == true
                    ) {
                        peerPrivateReadReceiptObserved = true
                    }
                }
            }
        }
    }

    return RedditChatMatrixEphemeralObservation(
        typingEventCount = typingEventCount,
        peerTypingUpdate = peerTypingUpdate,
        receiptEventCount = receiptEventCount,
        peerPublicReadReceipt = peerPublicReadReceipt,
        peerPrivateReadReceiptObserved = peerPrivateReadReceiptObserved,
    )
}

internal fun redditChatMatrixRedactionObservation(
    element: JsonElement,
    ownUserId: String,
): RedditChatMatrixRedactionObservation? {
    val event = element as? JsonObject ?: return null
    if (event["type"]?.primitiveOrNull?.contentOrNull != "m.room.redaction") return null

    val eventSender = event["sender"]
        ?.primitiveOrNull
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: return null
    val content = event["content"] as? JsonObject

    // Matrix room-version compatibility: older room versions expose `redacts` at top level,
    // while newer versions may carry the target in content. Observe either shape.
    val targetEventId = event["redacts"]
        ?.primitiveOrNull
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: content
            ?.get("redacts")
            ?.primitiveOrNull
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
        ?: return null

    return RedditChatMatrixRedactionObservation(
        targetEventId = targetEventId,
        senderIsSelf = eventSender == ownUserId,
    )
}

private fun redditChatMatrixLiveMessageBubble(
    element: JsonElement,
    ownUserId: String,
    ownUsername: String,
    participantUsername: String,
): MessageBubble? {
    val event = element as? JsonObject ?: return null
    if (event["type"]?.primitiveOrNull?.contentOrNull != "m.room.message") return null
    val eventId = event["event_id"]?.primitiveOrNull?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: return null
    val senderUserId = event["sender"]?.primitiveOrNull?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: return null
    val content = event["content"] as? JsonObject ?: return null
    val relation = content["m.relates_to"] as? JsonObject
    if (relation?.get("rel_type")?.primitiveOrNull?.contentOrNull == "m.replace") return null

    val body = when (content["msgtype"]?.primitiveOrNull?.contentOrNull) {
        "m.text" -> content["body"]?.primitiveOrNull?.contentOrNull
            ?.takeIf(String::isNotBlank)
        "m.image" -> content["url"]
            ?.primitiveOrNull
            ?.contentOrNull
            ?.let(::redditMatrixDownloadUri)
        else -> null
    } ?: return null

    val outgoing = senderUserId == ownUserId
    val createdEpochSeconds = event["origin_server_ts"]
        ?.primitiveOrNull
        ?.longOrNull
        ?.div(1_000L)
        ?.takeIf { it > 0L }
    return MessageBubble(
        id = eventId,
        sender = if (outgoing) ownUsername else participantUsername,
        age = "now",
        body = body,
        outgoing = outgoing,
        createdEpochSeconds = createdEpochSeconds,
    )
}
