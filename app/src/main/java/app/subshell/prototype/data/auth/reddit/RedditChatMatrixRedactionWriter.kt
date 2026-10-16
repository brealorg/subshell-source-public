package app.subshell.prototype.data.auth.reddit

// SUBSHELL_MESSAGES02D3B_DELETE_OWN_DIRECT_CHAT_MESSAGE_V1

import android.util.Log
import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.chat.RedditChatWebTokenResult
import app.subshell.prototype.data.account.DirectChatDeleteFailureKind
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal sealed interface AuthenticatedRedditDirectChatRedactionResult {
    data class Accepted(val redactionEventId: String) :
        AuthenticatedRedditDirectChatRedactionResult

    data class Failed(
        val kind: DirectChatDeleteFailureKind,
        val statusCode: Int? = null,
    ) : AuthenticatedRedditDirectChatRedactionResult
}

internal interface AuthenticatedRedditDirectChatRedactionWriter {
    suspend fun redact(
        accountStableId: String,
        participantUsername: String,
        targetEventId: String,
    ): AuthenticatedRedditDirectChatRedactionResult
}

/**
 * Deletes one already-resolved own Direct Chat Matrix event.
 *
 * D3B intentionally has no room discovery, no automatic retry and no generic message-id guessing.
 * The C7/D2B foreground session must already have a verified identity and two-party room cached.
 */
internal class RedditChatMatrixRedactionWriter(
    private val capability: AuthInboxReadCapability,
    private val tokenProvider: RedditChatWebTokenSource,
    private val client: OkHttpClient,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionCache: RedditChatMatrixSessionCache = RedditChatMatrixSessionCache(),
) : AuthenticatedRedditDirectChatRedactionWriter {
    private val writeMutex = Mutex()

    override suspend fun redact(
        accountStableId: String,
        participantUsername: String,
        targetEventId: String,
    ): AuthenticatedRedditDirectChatRedactionResult {
        writeMutex.lock()
        try {
            return withContext(ioDispatcher) {
                redactLocked(
                    accountStableId = accountStableId,
                    participantUsername = participantUsername,
                    targetEventId = targetEventId,
                )
            }
        } finally {
            writeMutex.unlock()
        }
    }

    private suspend fun redactLocked(
        accountStableId: String,
        participantUsername: String,
        targetEventId: String,
    ): AuthenticatedRedditDirectChatRedactionResult {
        val account = accountStableId.trim()
        val participant = participantUsername.trim()
        val target = redditChatMatrixRedactionEventIdOrNull(targetEventId)
            ?: return failed(DirectChatDeleteFailureKind.PROTOCOL)
        if (account.isEmpty() || participant.isEmpty()) {
            return failed(DirectChatDeleteFailureKind.PROTOCOL)
        }

        val oauthSession = when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> authorization.session
            else -> return failed(DirectChatDeleteFailureKind.SESSION)
        }
        val redditIdentity = oauthSession.identity
            ?: return failed(DirectChatDeleteFailureKind.SESSION)
        if (
            authenticatedAccountId(
                provider = oauthSession.provider,
                clientMode = oauthSession.clientMode,
                providerAccountId = redditIdentity.providerAccountId,
            ) != account ||
            !oauthSession.tokens.grants(RedditOAuth.liveInboxReadScopes)
        ) {
            return failed(DirectChatDeleteFailureKind.SESSION)
        }

        val webToken = when (val result = tokenProvider.token()) {
            is RedditChatWebTokenResult.Success -> result.token
            RedditChatWebTokenResult.NoRedditWebSession,
            RedditChatWebTokenResult.Failed,
            -> return failed(DirectChatDeleteFailureKind.SESSION)
        }

        val matrixUserId = sessionCache.identityFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
        ) ?: return failed(DirectChatDeleteFailureKind.SESSION)
        if (!matrixUserMatchesRedditProviderId(matrixUserId, redditIdentity.providerAccountId)) {
            invalidateAuthorization(webToken)
            return failed(DirectChatDeleteFailureKind.SESSION)
        }

        val roomId = sessionCache.roomFor(
            token = webToken,
            providerAccountId = redditIdentity.providerAccountId,
            participantUsername = participant,
        ) ?: return failed(DirectChatDeleteFailureKind.ROOM)

        val transactionId = "subshell_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val url = MATRIX_BASE.newBuilder().apply {
            redditChatMatrixRedactionPathSegments(
                roomId = roomId,
                targetEventId = target,
                transactionId = transactionId,
            ).forEach(::addPathSegment)
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $webToken")
            .header("Accept", "application/json")
            .put("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    // CHAT403_ROOM_REFUSAL_KEEPS_TOKEN_V1 (OCB-613): a room
                    // refusal keeps the token and reports the HTTP refusal.
                    val sessionRefused =
                        redditChatMatrixSessionRefusal(response.code, redditChatMatrixErrcode(body))
                    if (sessionRefused) {
                        invalidateAuthorization(webToken)
                    }
                    failed(
                        kind = if (sessionRefused) {
                            DirectChatDeleteFailureKind.SESSION
                        } else {
                            DirectChatDeleteFailureKind.HTTP
                        },
                        statusCode = response.code,
                    )
                } else {
                    val redactionEventId = parseObject(body)
                        ?.get("event_id")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let(::redditChatMatrixRedactionEventIdOrNull)
                        ?: return@use failed(DirectChatDeleteFailureKind.PROTOCOL)
                    Log.d(REDACTION_WRITE_LOG_TAG, "result=accepted")
                    AuthenticatedRedditDirectChatRedactionResult.Accepted(redactionEventId)
                }
            }
        } catch (_: IOException) {
            // The request may have reached Matrix. Never blind-retry a destructive mutation.
            failed(DirectChatDeleteFailureKind.OUTCOME_UNKNOWN)
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
        kind: DirectChatDeleteFailureKind,
        statusCode: Int? = null,
    ): AuthenticatedRedditDirectChatRedactionResult.Failed {
        Log.d(
            REDACTION_WRITE_LOG_TAG,
            "result=failed kind=${kind.name.lowercase()} status=${statusCode ?: -1}",
        )
        return AuthenticatedRedditDirectChatRedactionResult.Failed(
            kind = kind,
            statusCode = statusCode,
        )
    }

    companion object {
        private const val REDACTION_WRITE_LOG_TAG = "subshell-matrix-delete"
        private val MATRIX_BASE = "https://matrix.redditspace.com".toHttpUrl()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun redditChatMatrixRedactionEventIdOrNull(value: String?): String? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (normalized.length !in 2..512 || !normalized.startsWith('$')) return null
    if (normalized.any { ch -> ch.code < 32 || ch.code == 127 }) return null
    return normalized
}

internal fun redditChatMatrixRedactionPathSegments(
    roomId: String,
    targetEventId: String,
    transactionId: String,
): List<String> {
    require(roomId.isNotBlank()) { "Matrix room ID cannot be blank" }
    val target = requireNotNull(redditChatMatrixRedactionEventIdOrNull(targetEventId)) {
        "Matrix redaction target event ID is invalid"
    }
    require(transactionId.isNotBlank()) { "Matrix redaction transaction ID cannot be blank" }
    return listOf(
        "_matrix",
        "client",
        "v3",
        "rooms",
        roomId,
        "redact",
        target,
        transactionId,
    )
}
