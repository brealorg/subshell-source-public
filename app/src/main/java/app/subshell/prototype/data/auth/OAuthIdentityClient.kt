package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails

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
        // DIAG05_SIGN_IN_FAILURES_ARE_RECORDED_V1
        val details: FailureDetails? = null,
    ) : AuthIdentityResult
}

internal fun interface OAuthAccountIdentityClient {
    suspend fun resolve(session: StoredOAuthSession): AuthIdentityResult
}
