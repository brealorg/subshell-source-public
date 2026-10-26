package app.subshell.prototype.data.auth

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AuthCustomFeedWriteCapabilityFailureKind {
    ACCOUNT,
    CALLBACK,
    TOKEN_EXCHANGE,
    IDENTITY,
    SCOPE,
    STORAGE,
}

internal sealed interface AuthCustomFeedWriteCapabilityResult {
    data class Upgraded(
        val accountStableId: String,
        val username: String,
        val session: StoredOAuthSession,
    ) : AuthCustomFeedWriteCapabilityResult

    data class Failed(
        val kind: AuthCustomFeedWriteCapabilityFailureKind,
        val retryable: Boolean = false,
    ) : AuthCustomFeedWriteCapabilityResult
}

internal interface AuthCustomFeedWriteTokenSource {
    suspend fun hasScopeGrant(accountStableId: String): Boolean

    suspend fun authorizedSession(accountStableId: String): StoredOAuthSession?

    suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult

    // AUTH17_SESSION_BOUND_BEARER_V1: the provider for a client built with this
    // capability's token. The configured capabilities return the account
    // coordinator's session-bound one, so a 401 marks that token expired
    // (AUTH11: the refresh token is kept) and the next use refreshes it.
    fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        NetworkBearerTokenProvider { accessToken }
}

internal interface AuthCustomFeedWriteCapability : AuthCustomFeedWriteTokenSource {
    suspend fun beginAuthorization(accountStableId: String): String

    fun hasPendingAuthorization(): Boolean

    fun hasStoredCallback(): Boolean

    fun cancelPendingAuthorization()

    suspend fun completePendingAuthorization(): AuthCustomFeedWriteCapabilityResult
}

internal class OwnerBoundAuthCustomFeedWriteCapability(
    private val delegate: AuthCustomFeedWriteTokenSource,
    private val authorizer: AuthCapabilityAuthorizer,
    private val owner: AuthCapabilityAuthorizationOwner =
        AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE,
) : AuthCustomFeedWriteCapability {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        authorizer.bearerTokenProvider(accessToken)

    override suspend fun beginAuthorization(accountStableId: String): String =
        authorizer.beginAuthorization(accountStableId, owner)

    override suspend fun hasScopeGrant(accountStableId: String): Boolean =
        delegate.hasScopeGrant(accountStableId)

    override suspend fun authorizedSession(accountStableId: String): StoredOAuthSession? =
        delegate.authorizedSession(accountStableId)

    override fun hasPendingAuthorization(): Boolean =
        authorizer.hasPendingAuthorization(owner)

    override fun hasStoredCallback(): Boolean =
        authorizer.hasStoredCallback(owner)

    override fun cancelPendingAuthorization() {
        authorizer.cancelPendingAuthorization(owner)
    }

    override suspend fun completePendingAuthorization():
        AuthCustomFeedWriteCapabilityResult =
        when (val result = authorizer.completePendingAuthorization(owner)) {
            is AuthCapabilityAuthorizationResult.Upgraded ->
                AuthCustomFeedWriteCapabilityResult.Upgraded(
                    accountStableId = result.accountStableId,
                    username = result.username,
                    session = result.session,
                )

            is AuthCapabilityAuthorizationResult.Failed ->
                AuthCustomFeedWriteCapabilityResult.Failed(
                    kind = when (result.kind) {
                        AuthCapabilityAuthorizationFailureKind.ACCOUNT ->
                            AuthCustomFeedWriteCapabilityFailureKind.ACCOUNT
                        AuthCapabilityAuthorizationFailureKind.CALLBACK ->
                            AuthCustomFeedWriteCapabilityFailureKind.CALLBACK
                        AuthCapabilityAuthorizationFailureKind.TOKEN_EXCHANGE ->
                            AuthCustomFeedWriteCapabilityFailureKind.TOKEN_EXCHANGE
                        AuthCapabilityAuthorizationFailureKind.IDENTITY ->
                            AuthCustomFeedWriteCapabilityFailureKind.IDENTITY
                        AuthCapabilityAuthorizationFailureKind.SCOPE ->
                            AuthCustomFeedWriteCapabilityFailureKind.SCOPE
                        AuthCapabilityAuthorizationFailureKind.STORAGE ->
                            AuthCustomFeedWriteCapabilityFailureKind.STORAGE
                    },
                    retryable = result.retryable,
                )
        }

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        delegate.ensureAuthorized(accountStableId)
}

// DEAD06_CONFIGURED_CAPABILITIES_ARE_TOKEN_SOURCES_V1 (OCB-473): authorization
// runs through the owner-bound wrappers (ConfiguredAuthCapabilityAuthorizer);
// the Configured* classes only supply tokens/scope checks, so their former
// owner-less authorization halves (which matched any owner's pending record)
// are gone.
internal class ConfiguredAuthCustomFeedWriteCapability(
    private val configuration: OAuthProviderConfiguration,
    private val store: EncryptedAuthSessionStore,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val clock: AuthClock = SystemAuthClock,
) : AuthCustomFeedWriteTokenSource {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        accountCoordinator.sessionBoundBearerTokenProvider(accessToken)

    override suspend fun hasScopeGrant(accountStableId: String): Boolean =
        authorizedSession(accountStableId) != null

    override suspend fun authorizedSession(accountStableId: String): StoredOAuthSession? {
        val pinnedAccountId = accountStableId.trim()
        if (
            pinnedAccountId.isEmpty() ||
            accountCoordinator.activeAuthenticatedAccountId() != pinnedAccountId
        ) {
            return null
        }
        // LAUNCHASYNC01_STORE_WORK_OFF_MAIN_V1: the session decrypt runs on IO.
        val session = withContext(Dispatchers.IO) {
            runCatching { store.session(pinnedAccountId) }.getOrNull()
        } ?: return null
        return session.takeIf { stored ->
            stored.identity != null &&
                stored.clientMode == configuration.clientMode &&
                constantTimeEquals(stored.clientIdFingerprint, configuration.clientIdFingerprint) &&
                stored.tokens.grants(configuration.scopes)
        }
    }

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
        val pinnedAccountId = accountStableId.trim()
        if (
            pinnedAccountId.isEmpty() ||
            accountCoordinator.activeAuthenticatedAccountId() != pinnedAccountId
        ) {
            return AuthSessionResult.ReauthenticationRequired
        }
        // STRICTMODE02_SESSION_DECRYPT_OFF_MAIN_V1 (#243): the custom feed
        // coordinator calls this on Main; the file read and the keystore
        // decrypt run on IO, as authorizedSession's already did.
        val stored = try {
            withContext(Dispatchers.IO) { store.session(pinnedAccountId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AuthSessionResult.StorageFailure
        } ?: return AuthSessionResult.ReauthenticationRequired
        if (
            stored.identity == null ||
            !stored.tokens.grants(configuration.scopes)
        ) {
            return AuthSessionResult.ReauthenticationRequired
        }
        if (
            stored.clientMode != configuration.clientMode ||
            !constantTimeEquals(stored.clientIdFingerprint, configuration.clientIdFingerprint)
        ) {
            return AuthSessionResult.ClientConfigurationMismatch
        }
        if (
            stored.tokens.isUsableAt(
                nowEpochMs = clock.epochMillis(),
                refreshSkewMs = OAuthSessionManager.DEFAULT_REFRESH_SKEW_MS,
            )
        ) {
            return AuthSessionResult.Authenticated(stored)
        }
        // AUTH20_REFRESH_THROUGH_THE_ACCOUNT_COORDINATOR_V1 (OCB-476/606): the
        // refresh runs under the coordinator's session mutex (no second refresh
        // of the same token racing it) and a refused refresh updates the
        // account's state, instead of a private OAuthSessionManager doing both
        // outside it.
        return accountCoordinator.ensureAccessTokenForAccount(
            accountStableId = pinnedAccountId,
            requiredScopes = configuration.scopes,
        )
    }
}
