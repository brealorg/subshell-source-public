package app.subshell.prototype.data.auth

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.model.Post

internal data class AuthenticatedRedditProfileCommentsSnapshot(
    val comments: List<ProfileCommentFixture>,
    val nextCursor: String?,
)

internal sealed interface AuthenticatedRedditProfileCommentTargetResult {
    data class Loaded(
        val post: Post,
        val fromCache: Boolean,
    ) : AuthenticatedRedditProfileCommentTargetResult

    data object AuthorizationRequired : AuthenticatedRedditProfileCommentTargetResult

    data object NotFound : AuthenticatedRedditProfileCommentTargetResult

    data class Failed(
        val kind: AuthenticatedRedditProfileCommentsFailureKind,
        val retryable: Boolean = false,
    ) : AuthenticatedRedditProfileCommentTargetResult
}

internal enum class AuthenticatedRedditProfileCommentsFailureKind {
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

internal sealed interface AuthenticatedRedditProfileCommentsResult {
    data class Loaded(
        val snapshot: AuthenticatedRedditProfileCommentsSnapshot,
        val fromCache: Boolean,
    ) : AuthenticatedRedditProfileCommentsResult

    data object NotCached : AuthenticatedRedditProfileCommentsResult

    data object AuthorizationRequired : AuthenticatedRedditProfileCommentsResult

    data class Failed(
        val kind: AuthenticatedRedditProfileCommentsFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditProfileCommentsResult
}

// DEAD02_PROFILE_COMMENTS_FLOW_REMOVED_V1 (OCB-437/520): the Comments tab reads
// account history (RedditAccountHistoryKind.COMMENTS); the separate profile
// comments load/append path had no caller and is gone. This loader now only
// resolves a comment's target post.
internal interface AuthenticatedRedditProfileCommentsLoader {
    suspend fun resolveTargetPost(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditProfileCommentTargetResult
}

internal fun profileHistoryAuthorizationContextKey(username: String): String {
    val normalized = normalizeRedditUsername(username)
    val bytes = normalized.toByteArray(Charsets.UTF_8)
    val digits = "0123456789abcdef"
    return buildString(5 + bytes.size * 2) {
        append("user_")
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(digits[value ushr 4])
            append(digits[value and 0x0f])
        }
    }
}

internal fun profileHistoryUsernameFromAuthorizationContextKey(
    contextKey: String?,
): String? {
    val encoded = contextKey
        ?.trim()
        ?.lowercase()
        ?.takeIf { value -> value.startsWith("user_") }
        ?.removePrefix("user_")
        ?: return null

    if (encoded.isEmpty() || encoded.length % 2 != 0) {
        return null
    }

    val bytes = ByteArray(encoded.length / 2)
    for (index in bytes.indices) {
        val high = encoded[index * 2].digitToIntOrNull(16) ?: return null
        val low = encoded[index * 2 + 1].digitToIntOrNull(16) ?: return null
        bytes[index] = ((high shl 4) or low).toByte()
    }

    return runCatching {
        normalizeRedditUsername(String(bytes, Charsets.UTF_8))
    }.getOrNull()
}

internal interface AuthProfileHistoryCapability {
    suspend fun ensureAuthorized(
        accountStableId: String,
    ): AuthSessionResult

    // AUTH17_SESSION_BOUND_BEARER_V1: the provider for a client built with this
    // capability's token. The configured capabilities return the account
    // coordinator's session-bound one, so a 401 marks that token expired
    // (AUTH11: the refresh token is kept) and the next use refreshes it.
    fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        NetworkBearerTokenProvider { accessToken }
}

internal class ConfiguredAuthProfileHistoryCapability(
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
) : AuthProfileHistoryCapability {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        accountCoordinator.sessionBoundBearerTokenProvider(accessToken)

    override suspend fun ensureAuthorized(
        accountStableId: String,
    ): AuthSessionResult =
        accountCoordinator.ensureAccessTokenForAccount(
            accountStableId = accountStableId,
            requiredScopes = configuration.scopes,
        )
}
