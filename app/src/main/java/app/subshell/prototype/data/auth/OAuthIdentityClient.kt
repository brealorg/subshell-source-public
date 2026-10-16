package app.subshell.prototype.data.auth

internal enum class AuthIdentityFailureKind {
    HTTP,
    TRANSPORT,
    PROTOCOL,
    INVALID_IDENTITY,
}

internal sealed interface AuthIdentityResult {
    data class Success(
        val identity: AuthAccountIdentity,
    ) : AuthIdentityResult

    data class Failure(
        val kind: AuthIdentityFailureKind,
        val retryable: Boolean,
    ) : AuthIdentityResult
}

internal fun interface OAuthAccountIdentityClient {
    suspend fun resolve(session: StoredOAuthSession): AuthIdentityResult
}
