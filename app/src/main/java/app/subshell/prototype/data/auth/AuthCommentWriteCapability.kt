package app.subshell.prototype.data.auth

import app.subshell.prototype.data.network.NetworkBearerTokenProvider

internal enum class AuthCommentWriteCapabilityFailureKind {
    ACCOUNT,
    CALLBACK,
    TOKEN_EXCHANGE,
    IDENTITY,
    SCOPE,
    STORAGE,
}

internal sealed interface AuthCommentWriteCapabilityResult {
    data class Upgraded(
        val accountStableId: String,
        val username: String,
    ) : AuthCommentWriteCapabilityResult

    data class Failed(
        val kind: AuthCommentWriteCapabilityFailureKind,
        val retryable: Boolean = false,
    ) : AuthCommentWriteCapabilityResult
}

internal interface AuthCommentWriteTokenSource {
    suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult

    // AUTH17_SESSION_BOUND_BEARER_V1: the provider for a client built with this
    // capability's token. The configured capabilities return the account
    // coordinator's session-bound one, so a 401 marks that token expired
    // (AUTH11: the refresh token is kept) and the next use refreshes it.
    fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        NetworkBearerTokenProvider { accessToken }
}

internal interface AuthCommentWriteCapability : AuthCommentWriteTokenSource {
    suspend fun beginAuthorization(accountStableId: String): String

    fun hasPendingAuthorization(): Boolean

    fun hasStoredCallback(): Boolean

    fun cancelPendingAuthorization()

    suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult
}

// DEAD06_CONFIGURED_CAPABILITIES_ARE_TOKEN_SOURCES_V1 (OCB-473): authorization
// runs through the owner-bound wrappers (ConfiguredAuthCapabilityAuthorizer);
// the Configured* classes only supply tokens/scope checks, so their former
// owner-less authorization halves (which matched any owner's pending record)
// are gone.
internal class ConfiguredAuthCommentWriteCapability(
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
) : AuthCommentWriteTokenSource {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        accountCoordinator.sessionBoundBearerTokenProvider(accessToken)

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        accountCoordinator.ensureAccessTokenForAccount(
            accountStableId = accountStableId,
            requiredScopes = configuration.scopes,
        )
}


internal class OwnerBoundAuthCommentWriteCapability(
    private val authorizer: AuthCapabilityAuthorizer,
    private val owner: AuthCapabilityAuthorizationOwner,
) : AuthCommentWriteCapability {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        authorizer.bearerTokenProvider(accessToken)

    override suspend fun beginAuthorization(accountStableId: String): String =
        authorizer.beginAuthorization(
            accountStableId = accountStableId,
            owner = owner,
        )

    override fun hasPendingAuthorization(): Boolean =
        authorizer.hasPendingAuthorization(owner)

    override fun hasStoredCallback(): Boolean =
        authorizer.hasStoredCallback(owner)

    override fun cancelPendingAuthorization() {
        authorizer.cancelPendingAuthorization(owner)
    }

    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        when (val result = authorizer.completePendingAuthorization(owner)) {
            is AuthCapabilityAuthorizationResult.Upgraded ->
                AuthCommentWriteCapabilityResult.Upgraded(
                    accountStableId = result.accountStableId,
                    username = result.username,
                )

            is AuthCapabilityAuthorizationResult.Failed ->
                AuthCommentWriteCapabilityResult.Failed(
                    kind = result.kind.toLegacyCommentWriteFailureKind(),
                    retryable = result.retryable,
                )
        }

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        authorizer.ensureAuthorized(accountStableId)
}

internal class OwnerContextBoundAuthCommentWriteCapability(
    private val authorizer: AuthContextualCapabilityAuthorizer,
    private val owner: AuthCapabilityAuthorizationOwner,
    contextKey: String,
) : AuthCommentWriteCapability {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        authorizer.bearerTokenProvider(accessToken)

    private val contextKey =
        requireNotNull(
            normalizeAuthCapabilityAuthorizationContextKey(contextKey),
        )

    override suspend fun beginAuthorization(accountStableId: String): String =
        authorizer.beginAuthorization(
            accountStableId = accountStableId,
            owner = owner,
            contextKey = contextKey,
        )

    override fun hasPendingAuthorization(): Boolean =
        authorizer.hasPendingAuthorization(
            owner = owner,
            contextKey = contextKey,
        )

    override fun hasStoredCallback(): Boolean =
        authorizer.hasStoredCallback(
            owner = owner,
            contextKey = contextKey,
        )

    override fun cancelPendingAuthorization() {
        authorizer.cancelPendingAuthorization(
            owner = owner,
            contextKey = contextKey,
        )
    }

    override suspend fun completePendingAuthorization():
        AuthCommentWriteCapabilityResult =
        when (
            val result = authorizer.completePendingAuthorization(
                owner = owner,
                contextKey = contextKey,
            )
        ) {
            is AuthCapabilityAuthorizationResult.Upgraded ->
                AuthCommentWriteCapabilityResult.Upgraded(
                    accountStableId = result.accountStableId,
                    username = result.username,
                )

            is AuthCapabilityAuthorizationResult.Failed ->
                AuthCommentWriteCapabilityResult.Failed(
                    kind = result.kind.toLegacyCommentWriteFailureKind(),
                    retryable = result.retryable,
                )
        }

    override suspend fun ensureAuthorized(
        accountStableId: String,
    ): AuthSessionResult =
        authorizer.ensureAuthorized(accountStableId)
}

private fun AuthCapabilityAuthorizationFailureKind.toLegacyCommentWriteFailureKind():
    AuthCommentWriteCapabilityFailureKind = when (this) {
        AuthCapabilityAuthorizationFailureKind.ACCOUNT ->
            AuthCommentWriteCapabilityFailureKind.ACCOUNT
        AuthCapabilityAuthorizationFailureKind.CALLBACK ->
            AuthCommentWriteCapabilityFailureKind.CALLBACK
        AuthCapabilityAuthorizationFailureKind.TOKEN_EXCHANGE ->
            AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE
        AuthCapabilityAuthorizationFailureKind.IDENTITY ->
            AuthCommentWriteCapabilityFailureKind.IDENTITY
        AuthCapabilityAuthorizationFailureKind.SCOPE ->
            AuthCommentWriteCapabilityFailureKind.SCOPE
        AuthCapabilityAuthorizationFailureKind.STORAGE ->
            AuthCommentWriteCapabilityFailureKind.STORAGE
    }
