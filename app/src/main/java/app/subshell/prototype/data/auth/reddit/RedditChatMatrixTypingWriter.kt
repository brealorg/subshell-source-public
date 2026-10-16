package app.subshell.prototype.data.auth.reddit

// SUBSHELL_MESSAGES02D2B_OUTBOUND_TYPING_RUNTIME_V1

import android.util.Log
import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.chat.RedditChatWebTokenResult
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal interface AuthenticatedRedditDirectChatTypingWriter {
    suspend fun setTyping(
        accountStableId: String,
        participantUsername: String,
        typing: Boolean,
    ): Boolean
}

/**
 * Best-effort Matrix typing transport for an already-open Direct Chat.
 *
 * D2B requires the C7/D1 foreground reader to have populated the shared in-memory Matrix identity
 * and direct-room cache. Typing performs no room discovery and never guesses a room.
 */
internal class RedditChatMatrixTypingWriter(
    private val capability: AuthInboxReadCapability,
    private val tokenProvider: RedditChatWebTokenSource,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionCache: RedditChatMatrixSessionCache = RedditChatMatrixSessionCache(),
) : AuthenticatedRedditDirectChatTypingWriter {
    private val writeMutex = Mutex()

    override suspend fun setTyping(
        accountStableId: String,
        participantUsername: String,
        typing: Boolean,
    ): Boolean {
        writeMutex.lock()
        try {
            return withContext(ioDispatcher) {
                setTypingLocked(
                    accountStableId = accountStableId,
                    participantUsername = participantUsername,
                    typing = typing,
                )
            }
        } finally {
            writeMutex.unlock()
        }
    }

    private suspend fun setTypingLocked(
        accountStableId: String,
        participantUsername: String,
        typing: Boolean,
    ): Boolean {
        val account = accountStableId.trim()
        val participant = participantUsername.trim()
        if (account.isEmpty() || participant.isEmpty()) {
            return logFailure(typing, "invalid_target")
        }

        val oauthSession = when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> authorization.session
            else -> return logFailure(typing, "oauth_session")
        }
        val redditIdentity = oauthSession.identity
            ?: return logFailure(typing, "oauth_identity")
        if (
            authenticatedAccountId(
                provider = oauthSession.provider,
                clientMode = oauthSession.clientMode,
                providerAccountId = redditIdentity.providerAccountId,
            ) != account ||
            !oauthSession.tokens.grants(RedditOAuth.liveInboxReadScopes)
        ) {
            return logFailure(typing, "oauth_scope")
        }

        val webToken = when (val result = tokenProvider.token()) {
            is RedditChatWebTokenResult.Success -> result.token
            RedditChatWebTokenResult.NoRedditWebSession,
            RedditChatWebTokenResult.Failed,
            -> return logFailure(typing, "web_session")
        }

        val matrixUserId = sessionCache.identityFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
        ) ?: return logFailure(typing, "warm_identity")
        if (!matrixUserMatchesRedditProviderId(matrixUserId, redditIdentity.providerAccountId)) {
            invalidateAuthorization(webToken)
            return logFailure(typing, "identity_mismatch")
        }

        val roomId = sessionCache.roomFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
            participantUsername = participant,
        ) ?: return logFailure(typing, "warm_room")

        val url = MATRIX_BASE.newBuilder()
            .addPathSegments("_matrix/client/v3/rooms")
            .addPathSegment(roomId)
            .addPathSegment("typing")
            .addPathSegment(matrixUserId)
            .build()
        val payload = redditChatMatrixTypingPayload(typing)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $webToken")
            .header("Accept", "application/json")
            .put(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TYPING_LOG_TAG, "desired=$typing result=accepted")
                    true
                } else {
                    when (response.code) {
                        401, 403 -> invalidateAuthorization(webToken)
                        404 -> sessionCache.invalidateRoom(
                            token = webToken,
                            providerAccountId = redditIdentity.providerAccountId,
                            participantUsername = participant,
                        )
                    }
                    logFailure(
                        typing = typing,
                        reason = when (response.code) {
                            401, 403 -> "authorization"
                            404 -> "room"
                            429 -> "rate_limit"
                            else -> "http"
                        },
                        statusCode = response.code,
                    )
                }
            }
        } catch (_: IOException) {
            logFailure(typing, "transport")
        }
    }

    private fun invalidateAuthorization(token: String) {
        sessionCache.invalidateToken(token)
        tokenProvider.invalidate(token)
    }

    private fun logFailure(
        typing: Boolean,
        reason: String,
        statusCode: Int? = null,
    ): Boolean {
        Log.d(
            TYPING_LOG_TAG,
            "desired=$typing result=failed reason=$reason status=${statusCode ?: -1}",
        )
        return false
    }

    companion object {
        private const val TYPING_LOG_TAG = "subshell-matrix-typing"
        private val MATRIX_BASE = "https://matrix.redditspace.com".toHttpUrl()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun redditChatMatrixTypingPayload(
    typing: Boolean,
    timeoutMs: Long = DIRECT_CHAT_MATRIX_TYPING_TIMEOUT_MS,
): String {
    require(timeoutMs in 1_000L..30_000L) {
        "Matrix typing timeout must be between 1 and 30 seconds"
    }
    return buildJsonObject {
        put("typing", typing)
        if (typing) put("timeout", timeoutMs)
    }.toString()
}

internal fun shouldSendDirectChatTypingUpdate(
    previousTyping: Boolean?,
    previousAttemptAtMs: Long,
    desiredTyping: Boolean,
    nowMs: Long,
    keepAliveMs: Long,
): Boolean {
    require(previousAttemptAtMs >= 0L) { "Previous typing attempt time cannot be negative" }
    require(nowMs >= 0L) { "Typing clock cannot be negative" }
    require(keepAliveMs > 0L) { "Typing keepalive must be positive" }
    if (previousTyping == null) return desiredTyping
    if (previousTyping != desiredTyping) return true
    return desiredTyping && nowMs - previousAttemptAtMs >= keepAliveMs
}

internal const val DIRECT_CHAT_MATRIX_TYPING_TIMEOUT_MS = 15_000L
