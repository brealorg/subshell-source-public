package app.subshell.prototype.data.auth.reddit

// SUBSHELL_MESSAGES02E2A_R3A_REACTION_RELATIONS_READ_PROBE_V1

import android.util.Log
import android.os.SystemClock
import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.chat.RedditChatWebTokenResult
import app.subshell.prototype.data.account.redditDirectChatReactionAsset
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.awaitResponse
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal interface AuthenticatedRedditDirectChatReactionRelationsProbeReader {
    suspend fun probe(
        accountStableId: String,
        participantUsername: String,
        targetEventIds: List<String>,
        onBatch: (RedditChatMatrixReactionRelationsReadResult) -> Unit = {},
    ): RedditChatMatrixReactionRelationsReadResult
}

internal data class RedditChatMatrixReactionRelationEvent(
    val reactionEventId: String,
    val targetEventId: String,
    val key: String,
    val senderIsSelf: Boolean,
)

internal data class RedditChatMatrixReactionRelationsReadResult(
    val authoritativeTargetEventIds: Set<String>,
    val reactions: List<RedditChatMatrixReactionRelationEvent>,
)

internal class RedditChatMatrixReactionRelationsProbeReader(
    private val capability: AuthInboxReadCapability,
    private val tokenProvider: RedditChatWebTokenSource,
    private val client: OkHttpClient,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionCache: RedditChatMatrixSessionCache = RedditChatMatrixSessionCache(),
) : AuthenticatedRedditDirectChatReactionRelationsProbeReader {
    override suspend fun probe(
        accountStableId: String,
        participantUsername: String,
        targetEventIds: List<String>,
        onBatch: (RedditChatMatrixReactionRelationsReadResult) -> Unit,
    ): RedditChatMatrixReactionRelationsReadResult = withContext(ioDispatcher) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val account = accountStableId.trim()
        val participant = participantUsername.trim()
        val targets = redditChatMatrixReactionRelationsProbeTargets(targetEventIds)

        fun emptyResult() = RedditChatMatrixReactionRelationsReadResult(
            authoritativeTargetEventIds = emptySet(),
            reactions = emptyList(),
        )

        if (account.isEmpty() || participant.isEmpty() || targets.isEmpty()) {
            Log.d(
                RELATIONS_PROBE_LOG_TAG,
                "result=skipped reason=invalid_input targets=${targets.size}",
            )
            return@withContext emptyResult()
        }

        val oauthSession = when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> authorization.session
            else -> {
                Log.d(
                    RELATIONS_PROBE_LOG_TAG,
                    "result=skipped reason=session targets=${targets.size}",
                )
                return@withContext emptyResult()
            }
        }
        val redditIdentity = oauthSession.identity
        if (
            redditIdentity == null ||
            authenticatedAccountId(
                provider = oauthSession.provider,
                clientMode = oauthSession.clientMode,
                providerAccountId = redditIdentity.providerAccountId,
            ) != account ||
            !oauthSession.tokens.grants(RedditOAuth.liveInboxReadScopes)
        ) {
            Log.d(
                RELATIONS_PROBE_LOG_TAG,
                "result=skipped reason=session_identity targets=${targets.size}",
            )
            return@withContext emptyResult()
        }

        val webToken = when (val result = tokenProvider.token()) {
            is RedditChatWebTokenResult.Success -> result.token
            RedditChatWebTokenResult.NoRedditWebSession,
            RedditChatWebTokenResult.Failed,
            -> {
                Log.d(
                    RELATIONS_PROBE_LOG_TAG,
                    "result=skipped reason=web_token targets=${targets.size}",
                )
                return@withContext emptyResult()
            }
        }

        val matrixUserId = sessionCache.identityFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
        )
        if (
            matrixUserId == null ||
            !matrixUserMatchesRedditProviderId(matrixUserId, redditIdentity.providerAccountId)
        ) {
            Log.d(
                RELATIONS_PROBE_LOG_TAG,
                "result=skipped reason=identity_cache targets=${targets.size}",
            )
            return@withContext emptyResult()
        }

        val roomId = sessionCache.roomFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
            participantUsername = participant,
        )
        if (roomId == null) {
            Log.d(
                RELATIONS_PROBE_LOG_TAG,
                "result=skipped reason=room_cache targets=${targets.size}",
            )
            return@withContext emptyResult()
        }

        val batches = redditChatMatrixReactionRelationsProbeBatches(targets)
        val statusCounts = linkedMapOf<Int, Int>()
        val authoritativeTargets = linkedSetOf<String>()
        val authoritativeReactions = mutableListOf<RedditChatMatrixReactionRelationEvent>()
        var reactions = 0
        var known = 0
        var self = 0
        var peer = 0
        var unknown = 0
        var malformed = 0
        var protocolFailures = 0
        var transportFailures = 0

        batches.forEachIndexed { batchIndex, batchTargets ->
            val batchReads = coroutineScope {
                batchTargets.map { targetEventId ->
                    async {
                        readReactionRelationsTarget(
                            token = webToken,
                            roomId = roomId,
                            targetEventId = targetEventId,
                            ownUserId = matrixUserId,
                        )
                    }
                }.awaitAll()
            }

            val progressiveTargets = linkedSetOf<String>()
            val progressiveReactions = mutableListOf<RedditChatMatrixReactionRelationEvent>()
            var progressiveReactionCount = 0
            var progressiveKnown = 0

            batchReads.forEach { read ->
                read.statusCode?.let { status ->
                    statusCounts[status] = (statusCounts[status] ?: 0) + 1
                }
                if (read.transportFailure) {
                    transportFailures += 1
                    return@forEach
                }
                val page = read.page
                if (read.statusCode == 200 && page == null) {
                    protocolFailures += 1
                    return@forEach
                }
                if (page == null) return@forEach

                if (read.complete) {
                    authoritativeTargets += read.targetEventId
                    progressiveTargets += read.targetEventId
                }
                authoritativeReactions += page.reactions
                progressiveReactions += page.reactions

                reactions += page.reactionCount
                known += page.knownCatalogCount
                self += page.selfCount
                peer += page.peerCount
                unknown += page.unknownKeyCount
                malformed += page.malformedCount
                progressiveReactionCount += page.reactionCount
                progressiveKnown += page.knownCatalogCount
            }

            if (progressiveTargets.isNotEmpty()) {
                onBatch(
                    RedditChatMatrixReactionRelationsReadResult(
                        authoritativeTargetEventIds = progressiveTargets,
                        reactions = progressiveReactions.distinctBy(
                            RedditChatMatrixReactionRelationEvent::reactionEventId,
                        ),
                    ),
                )
            }

            Log.d(
                RELATIONS_PROBE_LOG_TAG,
                "result=batch index=${batchIndex + 1}/${batches.size} " +
                    "targets=${batchTargets.size} authoritative=${progressiveTargets.size} " +
                    "reactions=$progressiveReactionCount known=$progressiveKnown " +
                    "elapsed_ms=${SystemClock.elapsedRealtime() - startedAtMs}",
            )
        }

        val statuses = statusCounts.entries
            .sortedBy { (status, _) -> status }
            .joinToString(separator = ",") { (status, count) -> "${status}x$count" }
            .ifEmpty { "none" }

        Log.d(
            RELATIONS_PROBE_LOG_TAG,
            "result=complete targets=${targets.size} statuses=$statuses " +
                "reactions=$reactions known=$known self=$self peer=$peer " +
                "unknown=$unknown malformed=$malformed " +
                "protocol=$protocolFailures transport=$transportFailures " +
                "batches=${batches.size} parallelism=$DIRECT_CHAT_REACTION_RELATIONS_PARALLELISM " +
                "elapsed_ms=${SystemClock.elapsedRealtime() - startedAtMs}",
        )

        RedditChatMatrixReactionRelationsReadResult(
            authoritativeTargetEventIds = authoritativeTargets,
            reactions = authoritativeReactions
                .distinctBy(RedditChatMatrixReactionRelationEvent::reactionEventId),
        )
    }

    private suspend fun readReactionRelationsTarget(
        token: String,
        roomId: String,
        targetEventId: String,
        ownUserId: String,
    ): RedditChatMatrixReactionRelationsTargetRead =
        redditChatMatrixReadReactionRelationPages(
            targetEventId = targetEventId,
            ownUserId = ownUserId,
            json = json,
        ) { from ->
            val url = MATRIX_BASE.newBuilder().apply {
                redditChatMatrixReactionRelationsPathSegments(
                    roomId = roomId,
                    targetEventId = targetEventId,
                ).forEach(::addPathSegment)
                addQueryParameter("limit", DIRECT_CHAT_REACTION_RELATIONS_PAGE_LIMIT.toString())
                from?.let { cursor -> addQueryParameter("from", cursor) }
            }.build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()
            try {
                // CONC03_REACTION_PROBE_CALLS_CANCELLABLE_V1 (OCB-486): a cancelled
                // probe (chat closed) cancels its call instead of waiting up to the
                // 90 s call timeout. Writers still finish a call they started.
                client.newCall(request).awaitResponse().use { response ->
                    RedditChatMatrixRelationsAnswer(response.code, response.body.string())
                }
            } catch (_: IOException) {
                null
            }
        }

    companion object {
        private const val RELATIONS_PROBE_LOG_TAG = "subshell-reaction-rel"
        private val MATRIX_BASE = "https://matrix.redditspace.com".toHttpUrl()
    }
}

// SUBSHELL_MESSAGES02E2A_R3C_FAST_REACTION_HYDRATION_V1
internal fun redditChatMatrixReactionRelationsProbeTargets(
    targetEventIds: List<String>,
    maxTargets: Int = DIRECT_CHAT_REACTION_RELATIONS_PROBE_MAX_TARGETS,
): List<String> {
    require(maxTargets > 0) { "Direct Chat reaction relations probe target bound must be positive" }
    return targetEventIds
        .asSequence()
        .map(String::trim)
        .filter { eventId -> eventId.startsWith('$') }
        .distinct()
        .toList()
        .takeLast(maxTargets)
        .asReversed()
}

internal fun redditChatMatrixReactionRelationsProbeBatches(
    newestFirstTargetEventIds: List<String>,
    parallelism: Int = DIRECT_CHAT_REACTION_RELATIONS_PARALLELISM,
): List<List<String>> {
    require(parallelism > 0) { "Direct Chat reaction relations parallelism must be positive" }
    return newestFirstTargetEventIds.chunked(parallelism)
}

internal fun redditChatMatrixReactionRelationsPathSegments(
    roomId: String,
    targetEventId: String,
): List<String> {
    require(roomId.isNotBlank()) { "Matrix room ID cannot be blank" }
    require(targetEventId.startsWith('$')) {
        "Matrix reaction relations target must be a Matrix event ID"
    }
    return listOf(
        "_matrix",
        "client",
        "v1",
        "rooms",
        roomId,
        "relations",
        targetEventId,
        "m.annotation",
        "m.reaction",
    )
}

internal data class RedditChatMatrixReactionRelationsTargetRead(
    val targetEventId: String,
    val statusCode: Int?,
    val page: RedditChatMatrixReactionRelationsPage?,
    val transportFailure: Boolean,
    // OCB-612: false when a later page could not be read or the page cap was
    // reached; the reactions read are kept, but none may be dropped.
    val complete: Boolean = true,
)

/** One /relations answer: the HTTP status and body, or null on a transport failure. */
internal data class RedditChatMatrixRelationsAnswer(val statusCode: Int, val body: String)

/**
 * OCB-612: reads a target's reactions across /relations pages (`from` =
 * next_batch) up to [maxPages]. Only a set read to its last page is complete.
 */
internal suspend fun redditChatMatrixReadReactionRelationPages(
    targetEventId: String,
    ownUserId: String,
    json: Json = SubshellNetworkJson.providerJson,
    maxPages: Int = DIRECT_CHAT_REACTION_RELATIONS_MAX_PAGES,
    fetch: suspend (from: String?) -> RedditChatMatrixRelationsAnswer?,
): RedditChatMatrixReactionRelationsTargetRead {
    val pages = mutableListOf<RedditChatMatrixReactionRelationsPage>()
    var from: String? = null
    var firstStatus: Int? = null
    fun incomplete() = RedditChatMatrixReactionRelationsTargetRead(
        targetEventId,
        firstStatus,
        pages.combined(),
        transportFailure = false,
        complete = false,
    )
    while (pages.size < maxPages) {
        val answer = fetch(from)
            ?: return if (pages.isEmpty()) {
                RedditChatMatrixReactionRelationsTargetRead(targetEventId, null, null, transportFailure = true)
            } else {
                incomplete()
            }
        if (firstStatus == null) firstStatus = answer.statusCode
        val page = if (answer.statusCode in 200..299) {
            redditChatMatrixReactionRelationsPage(answer.body, targetEventId, ownUserId, json)
        } else {
            null
        }
        if (page == null) {
            return if (pages.isEmpty()) {
                RedditChatMatrixReactionRelationsTargetRead(targetEventId, answer.statusCode, null, transportFailure = false)
            } else {
                incomplete()
            }
        }
        pages += page
        from = page.nextBatch ?: return RedditChatMatrixReactionRelationsTargetRead(
            targetEventId,
            firstStatus,
            pages.combined(),
            transportFailure = false,
        )
    }
    return incomplete()
}

private fun List<RedditChatMatrixReactionRelationsPage>.combined() = RedditChatMatrixReactionRelationsPage(
    reactions = flatMap { page -> page.reactions }.distinctBy(RedditChatMatrixReactionRelationEvent::reactionEventId),
    reactionCount = sumOf { page -> page.reactionCount },
    knownCatalogCount = sumOf { page -> page.knownCatalogCount },
    selfCount = sumOf { page -> page.selfCount },
    peerCount = sumOf { page -> page.peerCount },
    unknownKeyCount = sumOf { page -> page.unknownKeyCount },
    malformedCount = sumOf { page -> page.malformedCount },
    nextBatch = lastOrNull()?.nextBatch,
)

internal const val DIRECT_CHAT_REACTION_RELATIONS_MAX_PAGES = 5
internal const val DIRECT_CHAT_REACTION_RELATIONS_PAGE_LIMIT = 50

internal data class RedditChatMatrixReactionRelationsPage(
    val reactions: List<RedditChatMatrixReactionRelationEvent>,
    val reactionCount: Int,
    val knownCatalogCount: Int,
    val selfCount: Int,
    val peerCount: Int,
    val unknownKeyCount: Int,
    val malformedCount: Int,
    // CORRECT04_REACTION_RELATIONS_FOLLOW_NEXT_BATCH_V1 (OCB-612): Matrix pages
    // /relations; a page with next_batch is not the whole set.
    val nextBatch: String? = null,
)

internal fun redditChatMatrixReactionRelationsPage(
    body: String,
    targetEventId: String,
    ownUserId: String,
    json: Json = SubshellNetworkJson.providerJson,
): RedditChatMatrixReactionRelationsPage? {
    if (!targetEventId.startsWith('$') || ownUserId.isBlank()) return null
    val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: return null
    val chunk = root["chunk"] as? JsonArray ?: return null

    val projected = mutableListOf<RedditChatMatrixReactionRelationEvent>()
    var reactions = 0
    var known = 0
    var self = 0
    var peer = 0
    var unknown = 0
    var malformed = 0

    chunk.forEach { element ->
        val event = element as? JsonObject
        val eventType = (event?.get("type") as? JsonPrimitive)?.contentOrNull
        val reactionEventId = (event?.get("event_id") as? JsonPrimitive)?.contentOrNull
        val sender = (event?.get("sender") as? JsonPrimitive)?.contentOrNull
        val content = event?.get("content") as? JsonObject
        val relatesTo = content?.get("m.relates_to") as? JsonObject
        val relationType = (relatesTo?.get("rel_type") as? JsonPrimitive)?.contentOrNull
        val relationTarget = (relatesTo?.get("event_id") as? JsonPrimitive)?.contentOrNull
        val key = (relatesTo?.get("key") as? JsonPrimitive)?.contentOrNull

        if (
            eventType != "m.reaction" ||
            reactionEventId?.startsWith('$') != true ||
            sender.isNullOrBlank() ||
            relationType != "m.annotation" ||
            relationTarget != targetEventId ||
            key.isNullOrBlank()
        ) {
            malformed += 1
            return@forEach
        }

        reactions += 1
        val knownAsset = redditDirectChatReactionAsset(key)
        if (knownAsset != null) {
            known += 1
            projected += RedditChatMatrixReactionRelationEvent(
                reactionEventId = reactionEventId,
                targetEventId = targetEventId,
                key = knownAsset.key,
                senderIsSelf = sender == ownUserId,
            )
        } else {
            unknown += 1
        }
        if (sender == ownUserId) {
            self += 1
        } else {
            peer += 1
        }
    }

    return RedditChatMatrixReactionRelationsPage(
        nextBatch = (root["next_batch"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank),
        reactions = projected.distinctBy(
            RedditChatMatrixReactionRelationEvent::reactionEventId,
        ),
        reactionCount = reactions,
        knownCatalogCount = known,
        selfCount = self,
        peerCount = peer,
        unknownKeyCount = unknown,
        malformedCount = malformed,
    )
}

// DEAD05_NO_TEST_ONLY_RELATIONS_PROJECTION_V1 (OCB-487): the count-only
// projection had no production caller; tests read the page's counts directly.

private const val DIRECT_CHAT_REACTION_RELATIONS_PROBE_MAX_TARGETS = 12
private const val DIRECT_CHAT_REACTION_RELATIONS_PARALLELISM = 4
