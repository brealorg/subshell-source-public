package app.subshell.prototype.data.auth

import android.net.Uri
import androidx.core.net.toUri
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class OAuthCallbackProcessor(
    private val store: EncryptedAuthSessionStore,
    private val clock: AuthClock = SystemAuthClock,
    private val pendingLifetimeMs: Long = DEFAULT_PENDING_AUTH_LIFETIME_MS,
) {
    fun process(uri: Uri): AuthCallbackResult {
        val pending = try {
            store.pendingAuthorization()
        } catch (_: Exception) {
            return AuthCallbackResult.Rejected(AuthCallbackFailureKind.STORAGE_FAILURE)
        } ?: return AuthCallbackResult.Rejected(
            AuthCallbackFailureKind.NO_PENDING_AUTHORIZATION,
        )

        if (clock.epochMillis() - pending.createdAtEpochMs > pendingLifetimeMs) {
            runCatching(store::deletePendingAuthorization)
            return AuthCallbackResult.Rejected(
                AuthCallbackFailureKind.PENDING_AUTHORIZATION_EXPIRED,
            )
        }

        val expected = pending.redirectUri.toUri()
        if (
            uri.scheme != expected.scheme ||
            uri.authority != expected.authority ||
            uri.path != expected.path ||
            (uri.fragment != null && uri.fragment != REDDIT_OAUTH_SENTINEL_FRAGMENT)
        ) {
            return AuthCallbackResult.Rejected(AuthCallbackFailureKind.REDIRECT_URI_MISMATCH)
        }

        val returnedState = uri.getQueryParameter("state").orEmpty()
        if (returnedState.isEmpty() || !constantTimeEquals(pending.state, returnedState)) {
            return AuthCallbackResult.Rejected(AuthCallbackFailureKind.STATE_MISMATCH)
        }

        val code = uri.getQueryParameter("code")?.trim()?.takeIf(String::isNotEmpty)
        val error = uri.getQueryParameter("error")?.trim()?.takeIf(String::isNotEmpty)
        if ((code == null) == (error == null)) {
            runCatching(store::deletePendingAuthorization)
            return AuthCallbackResult.Rejected(AuthCallbackFailureKind.MALFORMED_CALLBACK)
        }

        return try {
            store.deletePendingAuthorization()
            store.saveCallback(
                StoredOAuthCallback(
                    provider = pending.provider,
                    state = pending.state,
                    code = code,
                    error = error,
                    receivedAtEpochMs = clock.epochMillis(),
                    clientMode = pending.clientMode,
                    clientIdFingerprint = pending.clientIdFingerprint,
                    scopeFingerprint = pending.scopeFingerprint,
                    accountStableId = pending.accountStableId,
                    authorizationOwnerKey = pending.authorizationOwnerKey,
                    authorizationContextKey = pending.authorizationContextKey,
                ),
            )
            AuthCallbackResult.Accepted(
                provider = pending.provider,
                denied = error != null,
            )
        } catch (_: Exception) {
            AuthCallbackResult.Rejected(AuthCallbackFailureKind.STORAGE_FAILURE)
        }
    }

    companion object {
        const val DEFAULT_PENDING_AUTH_LIFETIME_MS = 10L * 60L * 1_000L
        private const val REDDIT_OAUTH_SENTINEL_FRAGMENT = "_"
    }
}

internal class OAuthSessionManager(
    private val configuration: OAuthProviderConfiguration,
    private val store: EncryptedAuthSessionStore,
    private val tokenClient: OAuthTokenEndpointClient,
    private val clock: AuthClock = SystemAuthClock,
    private val stateGenerator: OAuthStateGenerator = SecureOAuthStateGenerator(),
    private val refreshSkewMs: Long = DEFAULT_REFRESH_SKEW_MS,
    private val sessionKey: String? = null,
    private val authorizationAccountStableId: String? = null,
    private val authorizationOwnerKey: String? = null,
    private val authorizationContextKey: String? = null,
) : NetworkBearerTokenProvider {
    private val sessionMutex = Mutex()

    private val normalizedAuthorizationOwnerKey =
        normalizeAuthCapabilityAuthorizationOwnerKey(authorizationOwnerKey)

    private val normalizedAuthorizationContextKey =
        normalizeAuthCapabilityAuthorizationContextKey(authorizationContextKey)

    init {
        require(
            normalizedAuthorizationContextKey == null ||
                normalizedAuthorizationOwnerKey != null
        ) {
            "Authorization context requires an authorization owner"
        }
    }

    // DEAD01_SESSION_READ_ON_FIRST_USE_V1 (OCB-479): managers are built per call
    // (some on the main thread) and none is used as a bearer provider today, so
    // the stored session is read when bearerToken() first needs it, not in
    // every constructor.
    @Volatile
    private var cachedSessionValue: StoredOAuthSession? = null

    @Volatile
    private var cachedSessionLoaded = false

    private var cachedSession: StoredOAuthSession?
        get() {
            if (!cachedSessionLoaded) {
                cachedSessionValue = runCatching(::storedSession)
                    .getOrNull()
                    ?.takeIf { session -> session.matches(configuration) }
                cachedSessionLoaded = true
            }
            return cachedSessionValue
        }
        set(value) {
            cachedSessionValue = value
            cachedSessionLoaded = true
        }

    // LAUNCHASYNC01_STORE_WORK_OFF_MAIN_V1 (OCB-474): writing the pending
    // record (Keystore encrypt + file write) runs on IO; the browser is opened
    // by the caller on Main.
    suspend fun beginAuthorization(): String =
        withContext(Dispatchers.IO) { beginAuthorizationOnThisThread() }

    private fun beginAuthorizationOnThisThread(): String {
        val state = stateGenerator.generate()
        require(state.length >= MIN_STATE_LENGTH) { "OAuth state generator returned weak state" }
        store.savePendingAuthorization(
            PendingOAuthAuthorization(
                provider = configuration.provider,
                state = state,
                redirectUri = configuration.redirectUri,
                createdAtEpochMs = clock.epochMillis(),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
                scopeFingerprint = configuration.scopeFingerprint,
                accountStableId = authorizationAccountStableId,
                authorizationOwnerKey = normalizedAuthorizationOwnerKey,
                authorizationContextKey = normalizedAuthorizationContextKey,
            ),
        )
        store.deleteCallback()
        return configuration.authorizationUrl(state).toString()
    }

    suspend fun completePendingCallback(): AuthSessionResult = sessionMutex.withLock {
        val callback = try {
            store.callback()
        } catch (_: Exception) {
            return@withLock AuthSessionResult.StorageFailure
        } ?: return@withLock AuthSessionResult.NoPendingCallback

        store.deleteCallback()
        if (
            callback.provider != configuration.provider ||
            callback.clientMode != configuration.clientMode ||
            callback.clientIdFingerprint.isEmpty() ||
            callback.scopeFingerprint.isEmpty() ||
            !constantTimeEquals(
                callback.clientIdFingerprint,
                configuration.clientIdFingerprint,
            ) ||
            !constantTimeEquals(
                callback.scopeFingerprint,
                configuration.scopeFingerprint,
            ) ||
            callback.accountStableId != authorizationAccountStableId ||
            callback.authorizationOwnerKey != normalizedAuthorizationOwnerKey ||
            callback.authorizationContextKey != normalizedAuthorizationContextKey
        ) {
            return@withLock AuthSessionResult.ClientConfigurationMismatch
        }
        callback.error?.let { error ->
            return@withLock AuthSessionResult.AuthorizationDenied(error)
        }
        val code = callback.code ?: return@withLock AuthSessionResult.NoPendingCallback
        when (val exchange = tokenClient.exchangeAuthorizationCode(code)) {
            is OAuthEndpointResult.Success -> {
                if (!exchange.value.grants(configuration.scopes)) {
                    revokeQuietly(exchange.value)
                    return@withLock AuthSessionResult.ClientConfigurationMismatch
                }
                val session = StoredOAuthSession(
                    provider = configuration.provider,
                    tokens = exchange.value,
                    clientMode = configuration.clientMode,
                    clientIdFingerprint = configuration.clientIdFingerprint,
                )
                try {
                    saveStoredSession(session)
                    cachedSession = session
                    AuthSessionResult.Authenticated(session)
                } catch (_: Exception) {
                    revokeQuietly(exchange.value)
                    AuthSessionResult.StorageFailure
                }
            }
            is OAuthEndpointResult.HttpFailure -> AuthSessionResult.EndpointFailure(exchange)
            is OAuthEndpointResult.TransportFailure -> AuthSessionResult.EndpointFailure(exchange)
            is OAuthEndpointResult.ProtocolFailure -> AuthSessionResult.EndpointFailure(exchange)
        }
    }

    suspend fun ensureValidAccessToken(
        requiredScopes: Set<String> = configuration.scopes,
    ): AuthSessionResult = sessionMutex.withLock {
        val normalizedRequiredScopes = normalizeOAuthScopes(requiredScopes)
        if (normalizedRequiredScopes.isEmpty()) {
            return@withLock AuthSessionResult.ClientConfigurationMismatch
        }
        val session = try {
            storedSession()
        } catch (_: Exception) {
            return@withLock AuthSessionResult.StorageFailure
        } ?: return@withLock AuthSessionResult.ReauthenticationRequired

        if (!session.matches(configuration)) {
            cachedSession = null
            return@withLock AuthSessionResult.ClientConfigurationMismatch
        }

        if (!session.tokens.grants(normalizedRequiredScopes)) {
            cachedSession = null
            return@withLock AuthSessionResult.ReauthenticationRequired
        }

        if (session.tokens.isUsableAt(clock.epochMillis(), refreshSkewMs)) {
            cachedSession = session
            return@withLock AuthSessionResult.Authenticated(session)
        }

        val refreshToken = session.tokens.refreshToken
            ?: return@withLock AuthSessionResult.ReauthenticationRequired
        when (val refreshed = tokenClient.refresh(refreshToken)) {
            is OAuthEndpointResult.Success -> {
                val refreshedTokens = if (refreshed.value.scope.isBlank()) {
                    refreshed.value.copy(scope = session.tokens.scope)
                } else {
                    refreshed.value
                }
                if (!refreshedTokens.grants(normalizedRequiredScopes)) {
                    cachedSession = null
                    return@withLock AuthSessionResult.ReauthenticationRequired
                }
                val updated = session.copy(tokens = refreshedTokens)
                try {
                    saveStoredSession(updated)
                    cachedSession = updated
                    AuthSessionResult.Authenticated(updated)
                } catch (_: Exception) {
                    AuthSessionResult.StorageFailure
                }
            }
            is OAuthEndpointResult.HttpFailure -> {
                if (isTerminalRefreshRejection(refreshed)) {
                    runCatching {
                        if (sessionKey == null) {
                            store.deleteSession()
                        } else {
                            store.deleteSession(sessionKey)
                        }
                    }
                    cachedSession = null
                    AuthSessionResult.ReauthenticationRequired
                } else {
                    AuthSessionResult.EndpointFailure(refreshed)
                }
            }
            is OAuthEndpointResult.TransportFailure -> AuthSessionResult.EndpointFailure(refreshed)
            is OAuthEndpointResult.ProtocolFailure -> AuthSessionResult.EndpointFailure(refreshed)
        }
    }

    fun bindIdentity(identity: AuthAccountIdentity) {
        val current = storedSession() ?: error("Cannot bind identity without an OAuth session")
        check(current.matches(configuration)) {
            "Cannot bind identity to a session created by another OAuth client mode"
        }
        val updated = current.copy(identity = identity)
        saveStoredSession(updated)
        cachedSession = updated
    }

    suspend fun logout(): AuthLogoutResult = sessionMutex.withLock {
        val session = runCatching(::storedSession).getOrNull()
        val remoteResult = session
            ?.takeIf { stored -> stored.matches(configuration) }
            ?.tokens
            ?.let { tokens ->
                val refresh = tokens.refreshToken
                tokenClient.revoke(
                    token = refresh ?: tokens.accessToken,
                    tokenTypeHint = if (refresh != null) "refresh_token" else "access_token",
                )
            }
        runCatching {
            if (sessionKey == null) store.clearAll() else store.deleteSession(sessionKey)
        }
        cachedSession = null
        AuthLogoutResult(
            localSessionCleared = runCatching { storedSession() == null }.getOrDefault(false),
            remoteRevocationSucceeded = remoteResult == null || remoteResult is OAuthEndpointResult.Success,
        )
    }

    /**
     * SECAUDIT31_FAILED_REGISTRATION_REVOKES_ITS_GRANT_V1 (security audit
     * 2026-10-22, #31): a sign-in that exchanged its code but is not kept (its
     * identity cannot be read, its name is taken, it is another account than
     * the one being re-authorized) gives its grant back at Reddit, through the
     * client that received it. Deleting the local copy alone left a live
     * refresh token at Reddit. Best effort: a failed revoke changes nothing
     * else.
     */
    internal suspend fun revokeAbandonedGrant(tokens: OAuthTokenSet) {
        revokeQuietly(tokens)
    }

    private suspend fun revokeQuietly(tokens: OAuthTokenSet) {
        val refresh = tokens.refreshToken
        try {
            tokenClient.revoke(
                token = refresh ?: tokens.accessToken,
                tokenTypeHint = if (refresh != null) "refresh_token" else "access_token",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The grant stays at Reddit until the reader removes it there.
        }
    }

    internal fun forAccountAuthorization(
        accountStableId: String,
    ): OAuthSessionManager {
        val pinnedAccountId = accountStableId.trim()
        require(pinnedAccountId.isNotEmpty()) {
            "Account stable ID cannot be blank"
        }
        return OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            clock = clock,
            stateGenerator = stateGenerator,
            refreshSkewMs = refreshSkewMs,
            authorizationAccountStableId = pinnedAccountId,
        )
    }

    override fun bearerToken(): String? = cachedSession
        ?.takeIf { session -> session.matches(configuration) }
        ?.tokens
        ?.takeIf { tokens -> tokens.grants(configuration.scopes) }
        ?.takeIf { tokens -> tokens.isUsableAt(clock.epochMillis(), refreshSkewMs) }
        ?.accessToken

    // AUTH13_KEYED_SESSION_NEVER_FALLS_BACK_V1 (#134): a manager for one
    // account reads only that account's session. The unbound default file
    // holds an in-progress login or authorization, which may be another
    // account's; falling back to it handed that token to this account (and
    // logout revoked it).
    private fun storedSession(): StoredOAuthSession? =
        if (sessionKey != null) store.session(sessionKey) else store.session()

    private fun saveStoredSession(session: StoredOAuthSession) {
        sessionKey?.let { key -> store.saveSession(key, session) } ?: store.saveSession(session)
    }

    companion object {
        const val DEFAULT_REFRESH_SKEW_MS = 60L * 1_000L
        private const val MIN_STATE_LENGTH = 32
    }
}

internal fun StoredOAuthSession.matches(configuration: OAuthProviderConfiguration): Boolean =
    provider == configuration.provider &&
        clientMode == configuration.clientMode &&
        clientIdFingerprint.isNotEmpty() &&
        constantTimeEquals(clientIdFingerprint, configuration.clientIdFingerprint) &&
        tokens.grants(configuration.scopes)

internal fun String.oauthScopeSet(): Set<String> =
    split(' ').filter(String::isNotBlank).toSet()

// AUTH11_REFRESH_REJECTION_NEEDS_OAUTH_JSON_V1 (#155): the stored session (and
// its refresh token) is deleted only when the token endpoint rejects the grant
// in OAuth JSON: invalid_grant/unauthorized_client/invalid_client, or a
// 401/403 with a JSON body. A non-JSON 4xx keeps the session.
// AUTH12_OK_STATUS_OAUTH_ERROR_V1 (#133): the error codes count at any status,
// including Reddit's HTTP 200 {"error":"invalid_grant"}.
internal fun isTerminalRefreshRejection(failure: OAuthEndpointResult.HttpFailure): Boolean =
    failure.jsonBody && (
        failure.oauthError in TERMINAL_REFRESH_OAUTH_ERRORS ||
            failure.statusCode == 401 ||
            failure.statusCode == 403
        )

private val TERMINAL_REFRESH_OAUTH_ERRORS = setOf("invalid_grant", "unauthorized_client", "invalid_client")

internal const val SUBSHELL_AUTH08A2_SESSION_RECOVERY_V1 =
    "SUBSHELL_AUTH08A2_SESSION_RECOVERY_V1"
