package app.subshell.prototype.data.auth.reddit

// SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1

import android.util.Log
import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.chat.RedditChatWebTokenResult
import app.subshell.prototype.data.account.DirectChatReactionFailureKind
import app.subshell.prototype.data.account.redditDirectChatReactionAsset
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal sealed interface AuthenticatedRedditDirectChatReactionResult {
    data class Accepted(
        val reactionEventId: String,
        val targetEventId: String,
        val key: String,
    ) : AuthenticatedRedditDirectChatReactionResult

    data class Failed(
        val kind: DirectChatReactionFailureKind,
        val statusCode: Int? = null,
    ) : AuthenticatedRedditDirectChatReactionResult
}

internal interface AuthenticatedRedditDirectChatReactionWriter {
    suspend fun react(
        accountStableId: String,
        participantUsername: String,
        targetEventId: String,
        reactionKey: String,
    ): AuthenticatedRedditDirectChatReactionResult
}

/**
 * Sends one Matrix annotation reaction to an already-resolved Direct Chat event.
 *
 * E2A deliberately requires the foreground Direct Chat session to have a verified identity and
 * exact room cached. It performs no room discovery and never guesses a legacy inbox ID as a
 * Matrix target. Unknown post-dispatch transport outcomes are never automatically retried.
 */
internal class RedditChatMatrixReactionWriter(
    private val capability: AuthInboxReadCapability,
    private val tokenProvider: RedditChatWebTokenSource,
    private val client: OkHttpClient,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionCache: RedditChatMatrixSessionCache = RedditChatMatrixSessionCache(),
) : AuthenticatedRedditDirectChatReactionWriter {
    private val writeMutex = Mutex()

    override suspend fun react(
        accountStableId: String,
        participantUsername: String,
        targetEventId: String,
        reactionKey: String,
    ): AuthenticatedRedditDirectChatReactionResult {
        writeMutex.lock()
        try {
            return withContext(ioDispatcher) {
                reactLocked(
                    accountStableId = accountStableId,
                    participantUsername = participantUsername,
                    targetEventId = targetEventId,
                    reactionKey = reactionKey,
                )
            }
        } finally {
            writeMutex.unlock()
        }
    }

    private suspend fun reactLocked(
        accountStableId: String,
        participantUsername: String,
        targetEventId: String,
        reactionKey: String,
    ): AuthenticatedRedditDirectChatReactionResult {
        val account = accountStableId.trim()
        val participant = participantUsername.trim()
        val target = redditChatMatrixRedactionEventIdOrNull(targetEventId)
            ?: return failed(DirectChatReactionFailureKind.PROTOCOL)
        val key = redditChatMatrixReactionKeyOrNull(reactionKey)
            ?: return failed(DirectChatReactionFailureKind.PROTOCOL)
        if (account.isEmpty() || participant.isEmpty()) {
            return failed(DirectChatReactionFailureKind.PROTOCOL)
        }

        val oauthSession = when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> authorization.session
            else -> return failed(DirectChatReactionFailureKind.SESSION)
        }
        val redditIdentity = oauthSession.identity
            ?: return failed(DirectChatReactionFailureKind.SESSION)
        if (
            authenticatedAccountId(
                provider = oauthSession.provider,
                clientMode = oauthSession.clientMode,
                providerAccountId = redditIdentity.providerAccountId,
            ) != account ||
            !oauthSession.tokens.grants(RedditOAuth.liveInboxReadScopes)
        ) {
            return failed(DirectChatReactionFailureKind.SESSION)
        }

        val webToken = when (val result = tokenProvider.token()) {
            is RedditChatWebTokenResult.Success -> result.token
            RedditChatWebTokenResult.NoRedditWebSession,
            RedditChatWebTokenResult.Failed,
            -> return failed(DirectChatReactionFailureKind.SESSION)
        }

        val matrixUserId = sessionCache.identityFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
        ) ?: return failed(DirectChatReactionFailureKind.SESSION)
        if (!matrixUserMatchesRedditProviderId(matrixUserId, redditIdentity.providerAccountId)) {
            invalidateAuthorization(webToken)
            return failed(DirectChatReactionFailureKind.SESSION)
        }

        val roomId = sessionCache.roomFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
            participantUsername = participant,
        ) ?: return failed(DirectChatReactionFailureKind.ROOM)

        val transactionId = "subshell_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val url = MATRIX_BASE.newBuilder().apply {
            redditChatMatrixReactionPathSegments(
                roomId = roomId,
                transactionId = transactionId,
            ).forEach(::addPathSegment)
        }.build()
        val payload = redditChatMatrixReactionPayload(
            targetEventId = target,
            reactionKey = key,
        )
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $webToken")
            .header("Accept", "application/json")
            .put(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    // CHAT403_ROOM_REFUSAL_KEEPS_TOKEN_V1 (OCB-613)
                    val errcode = redditChatMatrixErrcode(body)
                    val sessionRefused = redditChatMatrixSessionRefusal(response.code, errcode)
                    val roomRefused = redditChatMatrixRoomRefusal(response.code, errcode)
                    when {
                        sessionRefused -> invalidateAuthorization(webToken)
                        roomRefused || response.code == 404 -> sessionCache.invalidateRoom(
                            token = webToken,
                            providerAccountId = redditIdentity.providerAccountId,
                            participantUsername = participant,
                        )
                    }
                    failed(
                        kind = when {
                            sessionRefused -> DirectChatReactionFailureKind.SESSION
                            roomRefused || response.code == 404 -> DirectChatReactionFailureKind.ROOM
                            else -> DirectChatReactionFailureKind.HTTP
                        },
                        statusCode = response.code,
                    )
                } else {
                    val reactionEventId = parseObject(body)
                        ?.get("event_id")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let(::redditChatMatrixRedactionEventIdOrNull)
                        ?: return@use failed(DirectChatReactionFailureKind.PROTOCOL)
                    Log.d(REACTION_WRITE_LOG_TAG, "result=accepted")
                    AuthenticatedRedditDirectChatReactionResult.Accepted(
                        reactionEventId = reactionEventId,
                        targetEventId = target,
                        key = key,
                    )
                }
            }
        } catch (_: IOException) {
            // The write may already have reached Matrix. Never blind-retry an unknown outcome.
            failed(DirectChatReactionFailureKind.OUTCOME_UNKNOWN)
        }
    }

    private fun parseObject(body: String): JsonObject? = runCatching {
        json.parseToJsonElement(body) as? JsonObject
    }.getOrNull()

    private fun invalidateAuthorization(token: String) {
        sessionCache.invalidateToken(token)
        tokenProvider.invalidate(token)
    }

    private fun failed(
        kind: DirectChatReactionFailureKind,
        statusCode: Int? = null,
    ): AuthenticatedRedditDirectChatReactionResult.Failed {
        Log.d(
            REACTION_WRITE_LOG_TAG,
            "result=failed kind=${kind.name.lowercase()} status=${statusCode ?: -1}",
        )
        return AuthenticatedRedditDirectChatReactionResult.Failed(
            kind = kind,
            statusCode = statusCode,
        )
    }

    companion object {
        private const val REACTION_WRITE_LOG_TAG = "subshell-matrix-reaction"
        private val MATRIX_BASE = "https://matrix.redditspace.com".toHttpUrl()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun redditChatMatrixReactionKeyOrNull(value: String?): String? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return redditDirectChatReactionAsset(normalized)?.key
}

internal fun redditChatMatrixReactionPathSegments(
    roomId: String,
    transactionId: String,
): List<String> {
    require(roomId.isNotBlank()) { "Matrix room ID cannot be blank" }
    require(transactionId.isNotBlank()) { "Matrix reaction transaction ID cannot be blank" }
    return listOf(
        "_matrix",
        "client",
        "v3",
        "rooms",
        roomId,
        "send",
        "m.reaction",
        transactionId,
    )
}

internal fun redditChatMatrixReactionPayload(
    targetEventId: String,
    reactionKey: String,
): String {
    val target = requireNotNull(redditChatMatrixRedactionEventIdOrNull(targetEventId)) {
        "Matrix reaction target event ID is invalid"
    }
    val key = requireNotNull(redditChatMatrixReactionKeyOrNull(reactionKey)) {
        "Matrix reaction key is invalid"
    }
    return buildJsonObject {
        putJsonObject("m.relates_to") {
            put("event_id", target)
            put("key", key)
            put("rel_type", "m.annotation")
        }
    }.toString()
}
