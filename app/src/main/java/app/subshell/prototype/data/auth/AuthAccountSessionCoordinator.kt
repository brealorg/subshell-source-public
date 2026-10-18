package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.FailureDetails
import kotlin.coroutines.cancellation.CancellationException
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountId
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class AuthAccountRegistrationFailureKind {
    AUTHORIZATION_DENIED,
    NO_PENDING_CALLBACK,
    TOKEN_EXCHANGE,
    CLIENT_CONFIGURATION,
    IDENTITY,
    LOCAL_FIXTURE_USERNAME_COLLISION,
    AUTHENTICATED_USERNAME_COLLISION,
    REDDIT_FEED,
    REAUTHENTICATION_ACCOUNT_MISMATCH,
    STORAGE,
}

internal enum class AuthAccountRegistrationStage {
    TOKEN_EXCHANGE,
    IDENTITY_RESOLUTION,
    REDDIT_FEED_SYNC,
}

internal sealed interface AuthAccountRegistrationResult {
    data class Registered(
        val account: AccountSessionSummary,
    ) : AuthAccountRegistrationResult

    data class Failed(
        val kind: AuthAccountRegistrationFailureKind,
        val retryable: Boolean = false,
        val providerError: String? = null,
    ) : AuthAccountRegistrationResult
}

internal class AuthAccountSessionCoordinator(
    private val configuration: OAuthProviderConfiguration,
    private val store: EncryptedAuthSessionStore,
    private val tokenClient: OAuthTokenEndpointClient,
    private val identityClient: OAuthAccountIdentityClient,
    private val redditBootstrapper: AuthenticatedRedditBootstrapper? = null,
    localFixtureUsernames: List<String>,
    private val clock: AuthClock = SystemAuthClock,
    private val refreshSkewMs: Long = OAuthSessionManager.DEFAULT_REFRESH_SKEW_MS,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // DIAG05_SIGN_IN_FAILURES_ARE_RECORDED_V1: the sign-in's identity read and
    // first feed page fail without a screen of their own; they are recorded here.
    private val recordFailure: (scopeKey: String, details: FailureDetails?) -> Unit = { _, _ -> },
) : AccountSessionDirectory, NetworkBearerTokenProvider {
    /** DIAG05_SIGN_IN_FAILURES_ARE_RECORDED_V1: for the capability authorizers, which share this session. */
    internal fun recordUnshownFailure(scopeKey: String, details: FailureDetails?) = recordFailure(scopeKey, details)

    private val localAccounts = localFixtureAccountSummaries(localFixtureUsernames)
    private val localUsernames = localAccounts.map { it.username.lowercase() }.toSet()
    private val fallbackLocalAccount =
        requireNotNull(localAccounts.firstOrNull()) {
            "AUTH02 requires at least one local fixture account"
        }
    private val localAccountIds = localAccounts.map { it.stableId }.toSet()
    private val activeSessionMutex = Mutex()

    // LOW3_TOKEN_LOCK_PER_ACCOUNT_V1 (OCB-469): a token refresh (network,
    // retrying client) holds only its own account's lock, so loads for other
    // accounts do not wait behind it. Logout and capability replacement take
    // activeSessionMutex first and then the account's lock (one order).
    private val accountSessionLocks = KeyedMutex()
    private val directoryHydrationMutex = Mutex()
    private val directoryPersistenceMutex = Mutex()
    private val mutableDirectoryRevision = MutableStateFlow(0L)
    private val directoryStorageScope =
        CoroutineScope(SupervisorJob() + storageDispatcher)
    private val directoryCacheLock = Any()

    @Volatile
    private var directoryCache = CachedAccountSessionDirectory(
        registry = StoredAuthAccountRegistry(
            accounts = emptyList(),
            activeAccountId = fallbackLocalAccount.stableId,
        ),
        accountStates = emptyMap(),
        snapshot = AccountSessionDirectorySnapshot(
            accounts = localAccounts,
            activeAccountId = fallbackLocalAccount.stableId,
        ),
    )

    @Volatile
    private var directoryHydrated = false

    // AUTH09_REGISTRY_READ_FAILURE_NEVER_WRITES_V1 (#61): true while the
    // stored account registry exists but could not be read (Keystore/AES-GCM
    // failure, IO error, a newer build's format). The in-memory fallback must
    // then never be written over it.
    @Volatile
    private var registryReadFailed = false

    @Volatile
    private var directoryPersistenceJob: Job? = null

    override val revision: StateFlow<Long> =
        mutableDirectoryRevision.asStateFlow()

    override val isHydrated: Boolean
        get() = directoryHydrated

    internal fun startDirectoryHydration(
        onHydrated: (AccountSessionDirectorySnapshot) -> Unit = {},
    ) {
        directoryStorageScope.launch {
            onHydrated(hydrateDirectory())
        }
    }

    suspend fun completePendingAccount(
        onStage: (AuthAccountRegistrationStage) -> Unit = {},
    ): AuthAccountRegistrationResult =
        completePendingAccount(
            authorizationSessionManager = null,
            expectedAccountStableId = null,
            onStage = onStage,
        )

    suspend fun completePendingAccount(
        authorizationSessionManager: OAuthSessionManager?,
        expectedAccountStableId: String? = null,
        onStage: (AuthAccountRegistrationStage) -> Unit = {},
    ): AuthAccountRegistrationResult {
        onStage(AuthAccountRegistrationStage.TOKEN_EXCHANGE)
        val unboundManager =
            authorizationSessionManager
                ?: sessionManager(sessionKey = null)
        val exchanged = when (val result = unboundManager.completePendingCallback()) {
            is AuthSessionResult.Authenticated -> result.session
            is AuthSessionResult.EndpointFailure -> return AuthAccountRegistrationResult.Failed(
                AuthAccountRegistrationFailureKind.TOKEN_EXCHANGE,
                retryable = when (val endpoint = result.result) {
                    is OAuthEndpointResult.HttpFailure -> endpoint.retryable
                    is OAuthEndpointResult.TransportFailure -> endpoint.retryable
                    is OAuthEndpointResult.ProtocolFailure -> false
                    is OAuthEndpointResult.Success -> false
                },
            )
            AuthSessionResult.StorageFailure -> return AuthAccountRegistrationResult.Failed(
                AuthAccountRegistrationFailureKind.STORAGE,
            )
            AuthSessionResult.NoPendingCallback,
            AuthSessionResult.ReauthenticationRequired,
            -> return AuthAccountRegistrationResult.Failed(
                AuthAccountRegistrationFailureKind.NO_PENDING_CALLBACK,
            )
            AuthSessionResult.ClientConfigurationMismatch ->
                return AuthAccountRegistrationResult.Failed(
                    AuthAccountRegistrationFailureKind.CLIENT_CONFIGURATION,
                )
            is AuthSessionResult.AuthorizationDenied ->
                return AuthAccountRegistrationResult.Failed(
                    kind = AuthAccountRegistrationFailureKind.AUTHORIZATION_DENIED,
                    retryable = result.error.equals(
                        "temporarily_unavailable",
                        ignoreCase = true,
                    ),
                    providerError = result.error,
                )
        }

        onStage(AuthAccountRegistrationStage.IDENTITY_RESOLUTION)
        val identity = when (val resolved = identityClient.resolve(exchanged)) {
            is AuthIdentityResult.Success -> resolved.identity.normalized()
            is AuthIdentityResult.Failure -> {
                recordFailure("account_identity", resolved.details)
                runCatching(store::deleteSession)
                return AuthAccountRegistrationResult.Failed(
                    AuthAccountRegistrationFailureKind.IDENTITY,
                    retryable = resolved.retryable,
                )
            }
        }

        if (identity.username.lowercase() in localUsernames) {
            runCatching(store::deleteSession)
            return AuthAccountRegistrationResult.Failed(
                AuthAccountRegistrationFailureKind.LOCAL_FIXTURE_USERNAME_COLLISION,
            )
        }

        val accountId = authenticatedAccountId(
            configuration.provider,
            configuration.clientMode,
            identity.providerAccountId,
        )
        val expectedAccountId =
            expectedAccountStableId
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        if (
            expectedAccountId != null &&
            accountId != expectedAccountId
        ) {
            runCatching { store.deleteSession() }
            return AuthAccountRegistrationResult.Failed(
                AuthAccountRegistrationFailureKind
                    .REAUTHENTICATION_ACCOUNT_MISMATCH,
            )
        }
        hydrateDirectory()
        val registry = registry()
        val usernameOwner = registry.accounts.firstOrNull { existing ->
            existing.username.equals(identity.username, ignoreCase = true) &&
                existing.accountId != accountId
        }
        if (usernameOwner != null) {
            runCatching(store::deleteSession)
            return AuthAccountRegistrationResult.Failed(
                AuthAccountRegistrationFailureKind.AUTHENTICATED_USERNAME_COLLISION,
            )
        }

        val authenticatedSession = exchanged.copy(identity = identity)
        if (redditBootstrapper != null) {
            val bootstrapper = requireNotNull(redditBootstrapper)
            onStage(AuthAccountRegistrationStage.REDDIT_FEED_SYNC)
            // CONC01_BOOTSTRAP_RETHROWS_CANCELLATION_V1 (OCB-467): a failed first
            // sync does not stop the sign-in; a cancellation does.
            try {
                val bootstrap = bootstrapper.synchronize(authenticatedSession)
                if (bootstrap is AuthenticatedRedditBootstrapResult.Failed) {
                    recordFailure("account_first_feed", bootstrap.details)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // The account is registered without its first feed page.
                recordFailure(
                    "account_first_feed",
                    FailureDetails(localKind = LocalFailureKind.UNKNOWN).withExceptionClass(error),
                )
            }
        }

        val now = clock.epochMillis()
        return withContext(storageDispatcher) {
            try {
                store.saveSession(accountId, authenticatedSession)
                store.deleteSession()
                // AUTH19_REGISTRATION_SAVES_THE_CURRENT_REGISTRY_V1 (OCB-468): the
                // registry is read again here, after the feed sync, so a logout
                // or switch made meanwhile is kept, not undone by an old copy.
                val record = synchronized(directoryCacheLock) {
                    val current = registry()
                    val existing = current.accounts.firstOrNull { it.accountId == accountId }
                    StoredAuthAccountRecord(
                        accountId = accountId,
                        provider = configuration.provider,
                        providerAccountId = identity.providerAccountId,
                        username = identity.username,
                        createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                        lastUsedAtEpochMs = now,
                        clientMode = configuration.clientMode,
                    ).also { record ->
                        saveRegistry(
                            value = current.copy(
                                accounts = (current.accounts.filterNot { it.accountId == accountId } + record)
                                    .sortedBy { it.accountId },
                                activeAccountId = accountId,
                            ),
                            stateOverrides = mapOf(accountId to AccountSessionState.CONNECTED),
                        )
                    }
                }
                publishDirectoryChange()
                AuthAccountRegistrationResult.Registered(
                    record.toSummary(AccountSessionState.CONNECTED),
                )
            } catch (_: Exception) {
                runCatching { store.deleteSession(accountId) }
                AuthAccountRegistrationResult.Failed(
                    AuthAccountRegistrationFailureKind.STORAGE,
                )
            }
        }
    }

    fun activeAuthenticatedAccountId(): String? = snapshot().let { directory ->
        directory.accounts.firstOrNull { account ->
            account.stableId == directory.activeAccountId &&
                account.source == AccountSessionSource.AUTHENTICATED
        }?.stableId
    }

    suspend fun ensureActiveAccessToken(): AuthSessionResult {
        hydrateDirectory()
        val activeAccountId = activeAuthenticatedAccountId()
            ?: return AuthSessionResult.ReauthenticationRequired
        return ensureAccessTokenForAccount(
            accountStableId = activeAccountId,
            requiredScopes = configuration.scopes,
        )
    }

    /**
     * DATALAYER08: the token of the account a load was started for, with the
     * scopes ensureActiveAccessToken uses. A load that checked accountStableId
     * was active and then suspended must not pick up whichever account is
     * active afterwards: that stored account B's data under account A's key.
     */
    suspend fun ensureConfiguredAccessTokenForAccount(accountStableId: String): AuthSessionResult =
        ensureAccessTokenForAccount(
            accountStableId = accountStableId,
            requiredScopes = configuration.scopes,
        )

    suspend fun ensureAccessTokenForAccount(
        accountStableId: String,
        requiredScopes: Set<String>,
    ): AuthSessionResult {
        hydrateDirectory()
        val pinnedAccountId = accountStableId.trim()
        return withContext(storageDispatcher) {
            accountSessionLocks.withLock(pinnedAccountId) {
                val account = snapshot().accounts.firstOrNull { candidate ->
                    candidate.stableId == pinnedAccountId &&
                        candidate.source == AccountSessionSource.AUTHENTICATED
                } ?: return@withLock AuthSessionResult.ReauthenticationRequired
                val result =
                    sessionManager(account.stableId)
                        .ensureValidAccessToken(requiredScopes)
                if (result == AuthSessionResult.ReauthenticationRequired) {
                    updateCachedAccountState(
                        account.stableId,
                        AccountSessionState.REAUTHENTICATION_REQUIRED,
                    )
                    publishDirectoryChange()
                } else if (
                    result is AuthSessionResult.Authenticated &&
                    account.state == AccountSessionState.REAUTHENTICATION_REQUIRED
                ) {
                    // AUTH18_WORKING_TOKEN_RESTORES_CONNECTED_V1 (OCB-470): a store
                    // read that failed at start marks the account "Sign in again";
                    // once its token works again the account is connected again.
                    updateCachedAccountState(account.stableId, AccountSessionState.CONNECTED)
                    publishDirectoryChange()
                }
                result
            }
        }
    }

    internal suspend fun replaceAuthenticatedSessionForCapability(
        accountStableId: String,
        session: StoredOAuthSession,
        requiredScopes: Set<String>,
    ): Boolean {
        hydrateDirectory()
        return withContext(storageDispatcher) {
            val pinnedAccountId = accountStableId.trim()
            activeSessionMutex.withLock { accountSessionLocks.withLock(pinnedAccountId) {
                val identity = session.identity ?: return@withLock false
                val record = registry().accounts.firstOrNull { it.accountId == pinnedAccountId }
                    ?: return@withLock false
                val existingSession = runCatching {
                    store.session(pinnedAccountId)
                }.getOrNull() ?: return@withLock false
                if (
                    authenticatedAccountId(
                        session.provider,
                        session.clientMode,
                        identity.providerAccountId,
                    ) != pinnedAccountId ||
                    record.provider != session.provider ||
                    record.clientMode != session.clientMode ||
                    record.providerAccountId != identity.providerAccountId ||
                    !record.username.equals(identity.username, ignoreCase = true) ||
                    !constantTimeEquals(
                        existingSession.clientIdFingerprint,
                        session.clientIdFingerprint,
                    ) ||
                    !session.tokens.grants(requiredScopes)
                ) {
                    return@withLock false
                }
                try {
                    store.saveSession(pinnedAccountId, session)
                    store.deleteSession()
                    updateCachedAccountState(
                        pinnedAccountId,
                        AccountSessionState.CONNECTED,
                    )
                    publishDirectoryChange()
                    true
                } catch (_: Exception) {
                    runCatching {
                        store.saveSession(pinnedAccountId, existingSession)
                    }
                    false
                }
            } }
        }
    }

    suspend fun logoutActive(): AuthLogoutResult {
        hydrateDirectory()
        return withContext(storageDispatcher) {
            activeSessionMutex.withLock {
                val current = snapshot()
                val active = current.accounts.first {
                    it.stableId == current.activeAccountId
                }
                if (active.source == AccountSessionSource.LOCAL_FIXTURE) {
                    return@withLock AuthLogoutResult(
                        localSessionCleared = true,
                        remoteRevocationSucceeded = true,
                    )
                }

                val result = accountSessionLocks.withLock(active.stableId) {
                    sessionManager(active.stableId).logout()
                }
                synchronized(directoryCacheLock) {
                    val registry = registry()
                    val remaining = registry.accounts.filterNot {
                        it.accountId == active.stableId
                    }
                    saveRegistry(
                        registry.copy(
                            accounts = remaining,
                            activeAccountId = fallbackLocalAccount.stableId,
                        ),
                    )
                }
                publishDirectoryChange()
                result.copy(removedAccountStableId = active.stableId)
            }
        }
    }

    // AUTH16_MISSING_SCOPE_IS_NOT_REAUTHENTICATION_V1 (#139): a side-effect-free look
    // at the scopes of the account's stored session (no refresh, no state
    // change). Unknown (no session, unreadable) counts as granted, so the
    // normal path decides.
    internal fun storedSessionGrants(accountStableId: String, scopes: Set<String>): Boolean =
        runCatching { store.session(accountStableId.trim())?.tokens?.grants(scopes) }.getOrNull() ?: true

    internal fun sessionBoundBearerTokenProvider(
        accessToken: String,
    ): NetworkBearerTokenProvider =
        object : NetworkBearerTokenProvider {
            override fun bearerToken(): String = accessToken

            override fun onUnauthorized() {
                markReauthenticationRequiredForAccessToken(accessToken)
            }
        }

    internal fun markReauthenticationRequiredForAccessToken(
        rejectedAccessToken: String,
    ): Boolean {
        val token = rejectedAccessToken.trim()
        if (token.isEmpty()) return false
        val accountId = registry().accounts.firstNotNullOfOrNull { record ->
            val session =
                runCatching { store.session(record.accountId) }
                    .getOrNull()
                    ?: return@firstNotNullOfOrNull null
            if (
                constantTimeEquals(
                    session.tokens.accessToken,
                    token,
                )
            ) {
                record.accountId
            } else {
                null
            }
        } ?: return false
        val current =
            runCatching { store.session(accountId) }.getOrNull()
                ?: return false
        if (!constantTimeEquals(current.tokens.accessToken, token)) {
            return false
        }
        // AUTH11_401_KEEPS_REFRESH_TOKEN_V1 (#155): a 401 says this access token
        // is no longer accepted, not that the refresh grant is dead. With a
        // refresh token the session is kept and only the access token marked
        // expired, so the next ensureAccessTokenForAccount refreshes; the
        // account needs a new login only if that refresh is refused.
        if (!current.tokens.refreshToken.isNullOrBlank()) {
            return runCatching {
                store.saveSession(accountId, current.copy(tokens = current.tokens.copy(expiresAtEpochMs = 1L)))
                true
            }.getOrDefault(false)
        }
        val cleared = runCatching {
            store.deleteSession(accountId)
            store.session(accountId) == null
        }.getOrDefault(false)
        if (cleared) {
            updateCachedAccountState(
                accountId,
                AccountSessionState.REAUTHENTICATION_REQUIRED,
            )
            publishDirectoryChange()
        }
        return cleared
    }

    override fun onUnauthorized() {
        bearerToken()?.let(::markReauthenticationRequiredForAccessToken)
    }

    override fun snapshot(): AccountSessionDirectorySnapshot =
        directoryCache.snapshot

    override fun selectAccount(stableId: String): AccountSessionDirectorySnapshot {
        val current = snapshot()
        val selected = current.accounts.firstOrNull { it.stableId == stableId }
            ?: throw IllegalArgumentException("Unknown account session: $stableId")
        val now = clock.epochMillis()
        val updatedSnapshot = synchronized(directoryCacheLock) {
            val cache = directoryCache
            val registry = cache.registry
            val updatedRecords = registry.accounts.map { record ->
                if (record.accountId == selected.stableId) {
                    record.copy(lastUsedAtEpochMs = now)
                } else {
                    record
                }
            }
            val updatedRegistry = registry.copy(
                accounts = updatedRecords,
                activeAccountId = selected.stableId,
            ).validated(localAccountIds)
            updateDirectoryCache(
                registry = updatedRegistry,
                accountStates = cache.accountStates,
            )
        }
        publishDirectoryChange()

        directoryPersistenceJob = directoryStorageScope.launch {
            directoryPersistenceMutex.withLock {
                // AUTH09: never persist the fallback of an unreadable registry;
                // try to read the real one instead (the selection stays in memory).
                if (registryReadFailed) {
                    directoryHydrationMutex.withLock {
                        hydrateDirectoryFromStore()
                        directoryHydrated = !registryReadFailed
                    }
                    return@withLock
                }
                runCatching {
                    synchronized(directoryCacheLock) {
                        val authoritativeRegistry = directoryCache.registry
                        store.saveAccountRegistry(authoritativeRegistry)
                    }
                }.onFailure {
                    directoryHydrationMutex.withLock {
                        hydrateDirectoryFromStore()
                        directoryHydrated = !registryReadFailed
                    }
                }
            }
        }
        return updatedSnapshot
    }

    override fun bearerToken(): String? {
        val current = snapshot()
        val active = current.accounts.first { it.stableId == current.activeAccountId }
        if (active.source == AccountSessionSource.LOCAL_FIXTURE) return null
        return runCatching { store.session(active.stableId) }
            .getOrNull()
            ?.takeIf { session -> session.matches(configuration) }
            ?.tokens
            ?.takeIf { it.grants(configuration.scopes) }
            ?.takeIf { it.isUsableAt(clock.epochMillis(), refreshSkewMs) }
            ?.accessToken
    }

    private fun sessionManager(sessionKey: String?): OAuthSessionManager = OAuthSessionManager(
        configuration = configuration,
        store = store,
        tokenClient = tokenClient,
        clock = clock,
        refreshSkewMs = refreshSkewMs,
        sessionKey = sessionKey,
    )

    private fun registry(): StoredAuthAccountRegistry =
        directoryCache.registry

    private fun saveRegistry(
        value: StoredAuthAccountRegistry,
        stateOverrides: Map<String, AccountSessionState> = emptyMap(),
    ) {
        // AUTH09: `value` was built on the in-memory fallback of an unreadable
        // registry; writing it would erase every stored account. Re-read and
        // refuse this write (a retry then builds on the real registry).
        if (registryReadFailed) {
            hydrateDirectoryFromStore()
            directoryHydrated = !registryReadFailed
            throw IllegalStateException("Account registry was unreadable; not overwriting it")
        }
        val validated = value.validated(localAccountIds)
        synchronized(directoryCacheLock) {
            store.saveAccountRegistry(validated)
            val cache = directoryCache
            updateDirectoryCache(
                registry = validated,
                accountStates = cache.accountStates + stateOverrides,
            )
        }
    }

    private fun updateCachedAccountState(
        accountId: String,
        state: AccountSessionState,
    ) {
        // AUTH19_DIRECTORY_UPDATES_UNDER_ONE_LOCK_V1 (OCB-472): reading the cache
        // and writing it back happen under the lock, so concurrent updates are
        // not lost and a switch is not reverted by a stale copy.
        synchronized(directoryCacheLock) {
            val registry = registry()
            if (registry.accounts.none { it.accountId == accountId }) return
            val cache = directoryCache
            updateDirectoryCache(
                registry = registry,
                accountStates = cache.accountStates + (accountId to state),
            )
        }
    }

    private fun updateDirectoryCache(
        registry: StoredAuthAccountRegistry,
        accountStates: Map<String, AccountSessionState>,
    ): AccountSessionDirectorySnapshot = synchronized(directoryCacheLock) {
        val normalizedStates = registry.accounts.associate { record ->
            record.accountId to (
                accountStates[record.accountId]
                    ?: AccountSessionState.REAUTHENTICATION_REQUIRED
                )
        }
        val authenticated = registry.accounts.map { record ->
            record.toSummary(
                normalizedStates.getValue(record.accountId),
            )
        }
        val accounts = localAccounts + authenticated
        val active = registry.activeAccountId?.takeIf { candidate ->
            accounts.any { it.stableId == candidate }
        } ?: fallbackLocalAccount.stableId
        val snapshot = AccountSessionDirectorySnapshot(
            accounts = accounts,
            activeAccountId = active,
        )
        directoryCache = CachedAccountSessionDirectory(
            registry = registry.copy(activeAccountId = active),
            accountStates = normalizedStates,
            snapshot = snapshot,
        )
        snapshot
    }

    // INBOX06_WORKER_WAITS_FOR_ACCOUNT_DIRECTORY_V1
    override suspend fun hydratedSnapshot(): AccountSessionDirectorySnapshot = hydrateDirectory()

    internal suspend fun hydrateDirectory(): AccountSessionDirectorySnapshot =
        withContext(storageDispatcher) {
            directoryHydrationMutex.withLock {
                if (directoryHydrated) {
                    snapshot()
                } else {
                    hydrateDirectoryFromStore().also {
                        // AUTH09: an unreadable registry is retried next time.
                        directoryHydrated = !registryReadFailed
                    }
                }
            }
        }

    internal suspend fun awaitDirectoryPersistence() {
        directoryPersistenceJob?.join()
    }

    private fun hydrateDirectoryFromStore(): AccountSessionDirectorySnapshot {
        // AUTH09: "no file" (null) and "file unreadable" (exception) differ.
        val registryRead = try {
            Result.success(store.accountRegistry())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
        registryReadFailed = registryRead.isFailure
        val storedRegistry = registryRead.getOrNull()
        val fallbackRegistry = StoredAuthAccountRegistry(
            accounts = emptyList(),
            activeAccountId = fallbackLocalAccount.stableId,
        )
        val normalizedRegistry = runCatching {
            val candidate = storedRegistry ?: fallbackRegistry
            val knownAccountIds =
                localAccountIds + candidate.accounts.map { it.accountId }
            candidate
                .copy(
                    activeAccountId = candidate.activeAccountId
                        ?.takeIf { it in knownAccountIds }
                        ?: fallbackLocalAccount.stableId,
                )
                .validated(localAccountIds)
        }.getOrElse {
            fallbackRegistry
        }

        if (!registryReadFailed && (storedRegistry == null || normalizedRegistry != storedRegistry)) {
            runCatching {
                store.saveAccountRegistry(normalizedRegistry)
            }
        }

        val accountStates = normalizedRegistry.accounts.associate { record ->
            val session = runCatching {
                store.session(record.accountId)
            }.getOrNull()
            val state = if (
                session?.matches(configuration) == true &&
                (
                    session.tokens.isUsableAt(
                        clock.epochMillis(),
                        refreshSkewMs,
                    ) ||
                        session.tokens.refreshToken != null
                    )
            ) {
                AccountSessionState.CONNECTED
            } else {
                AccountSessionState.REAUTHENTICATION_REQUIRED
            }
            record.accountId to state
        }

        val snapshot = updateDirectoryCache(
            registry = normalizedRegistry,
            accountStates = accountStates,
        )
        publishDirectoryChange()
        return snapshot
    }

    private fun publishDirectoryChange() {
        mutableDirectoryRevision.update { revision -> revision + 1L }
    }
}

private data class CachedAccountSessionDirectory(
    val registry: StoredAuthAccountRegistry,
    val accountStates: Map<String, AccountSessionState>,
    val snapshot: AccountSessionDirectorySnapshot,
)

private fun AuthAccountIdentity.normalized(): AuthAccountIdentity = AuthAccountIdentity(
    providerAccountId = providerAccountId.trim().also {
        require(it.isNotEmpty()) { "Provider account ID cannot be blank" }
    },
    username = username.trim().also {
        require(it.isNotEmpty()) { "Authenticated username cannot be blank" }
    },
)

internal fun authenticatedAccountId(
    provider: OAuthProvider,
    clientMode: OAuthClientMode,
    providerAccountId: String,
): String = provider.name.lowercase() + ":" + clientMode.name.lowercase() + ":" +
    providerAccountId.trim()

private fun StoredAuthAccountRecord.toSummary(state: AccountSessionState): AccountSessionSummary =
    AccountSessionSummary(
        stableId = accountId,
        username = username,
        source = AccountSessionSource.AUTHENTICATED,
        state = state,
        providerLabel = when (clientMode) {
            OAuthClientMode.NATIVE_PRODUCTION -> "Reddit"
            OAuthClientMode.REDREADER_COMPATIBILITY -> "Reddit · RedReader compatibility"
        },
    )

private fun StoredAuthAccountRegistry.validated(localIds: Set<String>): StoredAuthAccountRegistry {
    require(accounts.map { it.accountId }.distinct().size == accounts.size) {
        "Authenticated account IDs must be unique"
    }
    require(accounts.map { it.username.lowercase() }.distinct().size == accounts.size) {
        "Authenticated usernames must be unique"
    }
    require(activeAccountId == null || activeAccountId in localIds || accounts.any { it.accountId == activeAccountId }) {
        "Active account ID is not registered"
    }
    return this
}
