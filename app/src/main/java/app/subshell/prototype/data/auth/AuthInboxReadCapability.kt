package app.subshell.prototype.data.auth

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AuthInboxReadCapabilityFailureKind {
    ACCOUNT,
    CALLBACK,
    TOKEN_EXCHANGE,
    IDENTITY,
    SCOPE,
    STORAGE,
}

internal sealed interface AuthInboxReadCapabilityResult {
    data class Upgraded(
        val accountStableId: String,
        val username: String,
        val session: StoredOAuthSession,
    ) : AuthInboxReadCapabilityResult

    data class Failed(
        val kind: AuthInboxReadCapabilityFailureKind,
        val retryable: Boolean = false,
    ) : AuthInboxReadCapabilityResult
}

internal interface AuthInboxReadScopeSource {
    suspend fun hasScopeGrant(accountStableId: String): Boolean
}

internal interface AuthInboxReadCapability : AuthInboxReadScopeSource {
    suspend fun beginAuthorization(accountStableId: String): String

    fun hasPendingAuthorization(): Boolean

    fun hasStoredCallback(): Boolean

    fun cancelPendingAuthorization()

    suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult

    suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult

    // AUTH17_SESSION_BOUND_BEARER_V1: the provider for a client built with this
    // capability's token. The configured capabilities return the account
    // coordinator's session-bound one, so a 401 marks that token expired
    // (AUTH11: the refresh token is kept) and the next use refreshes it.
    fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        NetworkBearerTokenProvider { accessToken }
}

internal class OwnerBoundAuthInboxReadCapability(
    private val delegate: AuthInboxReadScopeSource,
    private val authorizer: AuthCapabilityAuthorizer,
    private val owner: AuthCapabilityAuthorizationOwner =
        AuthCapabilityAuthorizationOwner.INBOX_READ,
) : AuthInboxReadCapability {
    override fun bearerTokenProvider(accessToken: String): NetworkBearerTokenProvider =
        authorizer.bearerTokenProvider(accessToken)

    override suspend fun beginAuthorization(accountStableId: String): String =
        authorizer.beginAuthorization(accountStableId, owner)

    override suspend fun hasScopeGrant(accountStableId: String): Boolean =
        delegate.hasScopeGrant(accountStableId)

    override fun hasPendingAuthorization(): Boolean =
        authorizer.hasPendingAuthorization(owner)

    override fun hasStoredCallback(): Boolean =
        authorizer.hasStoredCallback(owner)

    override fun cancelPendingAuthorization() {
        authorizer.cancelPendingAuthorization(owner)
    }

    override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult =
        when (val result = authorizer.completePendingAuthorization(owner)) {
            is AuthCapabilityAuthorizationResult.Upgraded ->
                AuthInboxReadCapabilityResult.Upgraded(
                    accountStableId = result.accountStableId,
                    username = result.username,
                    session = result.session,
                )

            is AuthCapabilityAuthorizationResult.Failed ->
                AuthInboxReadCapabilityResult.Failed(
                    kind = when (result.kind) {
                        AuthCapabilityAuthorizationFailureKind.ACCOUNT ->
                            AuthInboxReadCapabilityFailureKind.ACCOUNT
                        AuthCapabilityAuthorizationFailureKind.CALLBACK ->
                            AuthInboxReadCapabilityFailureKind.CALLBACK
                        AuthCapabilityAuthorizationFailureKind.TOKEN_EXCHANGE ->
                            AuthInboxReadCapabilityFailureKind.TOKEN_EXCHANGE
                        AuthCapabilityAuthorizationFailureKind.IDENTITY ->
                            AuthInboxReadCapabilityFailureKind.IDENTITY
                        AuthCapabilityAuthorizationFailureKind.SCOPE ->
                            AuthInboxReadCapabilityFailureKind.SCOPE
                        AuthCapabilityAuthorizationFailureKind.STORAGE ->
                            AuthInboxReadCapabilityFailureKind.STORAGE
                    },
                    retryable = result.retryable,
                )
        }

    // MOD01A221_OWNER_BOUND_INBOX_SCOPE_CONTINUITY_V1
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        authorizer.ensureAuthorized(accountStableId)
}

internal fun AuthSessionResult.AuthorizationDenied.isMissingIncrementalScopeAuthorization(): Boolean =
    error == AUTH_MISSING_INCREMENTAL_SCOPE_ERROR

private const val AUTH_MISSING_INCREMENTAL_SCOPE_ERROR = "missing_incremental_scope"

// DEAD06_CONFIGURED_CAPABILITIES_ARE_TOKEN_SOURCES_V1 (OCB-473): authorization
// runs through the owner-bound wrappers (ConfiguredAuthCapabilityAuthorizer);
// the Configured* classes only supply tokens/scope checks, so their former
// owner-less authorization halves (which matched any owner's pending record)
// are gone.
internal class ConfiguredAuthInboxReadCapability(
    private val configuration: OAuthProviderConfiguration,
    private val store: EncryptedAuthSessionStore,
    private val accountCoordinator: AuthAccountSessionCoordinator,
) : AuthInboxReadScopeSource {
    override suspend fun hasScopeGrant(accountStableId: String): Boolean {
        val pinnedAccountId = accountStableId.trim()
        if (
            pinnedAccountId.isEmpty() ||
            accountCoordinator.activeAuthenticatedAccountId() != pinnedAccountId
        ) {
            return false
        }
        // LAUNCHASYNC01_STORE_WORK_OFF_MAIN_V1: the session decrypt runs on IO.
        val session = withContext(Dispatchers.IO) {
            runCatching { store.session(pinnedAccountId) }.getOrNull()
        } ?: return false
        return session.identity != null &&
            session.clientMode == configuration.clientMode &&
            constantTimeEquals(session.clientIdFingerprint, configuration.clientIdFingerprint) &&
            session.tokens.grants(configuration.scopes)
    }
}
