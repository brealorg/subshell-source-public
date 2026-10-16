package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.ConnectAccountFailureKind
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface AuthBrowserLaunchResult {
    data class Ready(
        val authorizationUrl: String,
        val clientMode: OAuthClientMode,
        val freshAccountSession: Boolean = false,
    ) : AuthBrowserLaunchResult

    data object NotStarted : AuthBrowserLaunchResult
}

internal fun freshAccountAuthorizationLaunch(
    authorizationUrl: String,
    clientMode: OAuthClientMode,
): AuthBrowserLaunchResult.Ready =
    AuthBrowserLaunchResult.Ready(
        authorizationUrl = authorizationUrl,
        clientMode = clientMode,
        freshAccountSession = true,
    )

internal interface AuthConnectFlow {
    val uiState: StateFlow<ConnectAccountUiState>

    suspend fun beginAuthorization(): AuthBrowserLaunchResult

    suspend fun beginReauthentication(
        accountStableId: String,
    ): AuthBrowserLaunchResult

    suspend fun browserLaunchFailed()

    suspend fun cancel()

    // LOW4_CONNECT_PHASE_RESTORED_OFF_MAIN_V1 (OCB-474): restores the phase
    // from the stored records (Keystore reads) off the main thread; the
    // ViewModel calls it once at start and after a logout.
    suspend fun reset()

    fun hasStoredCallback(): Boolean

    suspend fun handleCallbackResultToken(resultToken: String)

    suspend fun resumeStoredCallbackIfPresent()
}

internal class UnavailableAuthConnectFlow(
    providerLabel: String,
) : AuthConnectFlow {
    private val unavailable = MutableStateFlow(
        ConnectAccountUiState.unavailable(providerLabel),
    )

    override val uiState: StateFlow<ConnectAccountUiState> = unavailable.asStateFlow()

    override suspend fun beginAuthorization(): AuthBrowserLaunchResult =
        AuthBrowserLaunchResult.NotStarted

    override suspend fun beginReauthentication(
        accountStableId: String,
    ): AuthBrowserLaunchResult =
        AuthBrowserLaunchResult.NotStarted

    override suspend fun browserLaunchFailed() = Unit

    override suspend fun cancel() = Unit

    override suspend fun reset() = Unit

    override fun hasStoredCallback(): Boolean = false

    override suspend fun handleCallbackResultToken(resultToken: String) = Unit

    override suspend fun resumeStoredCallbackIfPresent() = Unit
}

internal class ConfiguredAuthConnectFlowController(
    private val sessionManager: OAuthSessionManager,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val store: EncryptedAuthSessionStore,
    private val providerLabel: String,
    private val clientMode: OAuthClientMode,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthConnectFlow {
    private val completionMutex = Mutex()
    private var directoryRevision = 0L
    private val initialState = state(ConnectAccountPhase.IDLE)
    private val mutableUiState = MutableStateFlow(initialState)

    override val uiState: StateFlow<ConnectAccountUiState> =
        mutableUiState.asStateFlow()

    override suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        if (mutableUiState.value.inProgress) return AuthBrowserLaunchResult.NotStarted

        mutableUiState.value = state(ConnectAccountPhase.LAUNCHING)
        return try {
            val authorizationUrl = sessionManager.beginAuthorization()
            mutableUiState.value = state(ConnectAccountPhase.AWAITING_CALLBACK)
            freshAccountAuthorizationLaunch(
                authorizationUrl = authorizationUrl,
                clientMode = clientMode,
            )
        } catch (_: Exception) {
            withContext(storageDispatcher) { cleanupTransientAuthorization() }
            mutableUiState.value = failure(
                ConnectAccountFailureKind.STORAGE,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    override suspend fun beginReauthentication(
        accountStableId: String,
    ): AuthBrowserLaunchResult {
        if (mutableUiState.value.inProgress) {
            return AuthBrowserLaunchResult.NotStarted
        }
        val pinnedAccountId = accountStableId.trim()
        val account = accountCoordinator.snapshot().accounts
            .firstOrNull { candidate ->
                candidate.stableId == pinnedAccountId &&
                    candidate.source == AccountSessionSource.AUTHENTICATED &&
                    candidate.state ==
                    AccountSessionState.REAUTHENTICATION_REQUIRED
            } ?: return AuthBrowserLaunchResult.NotStarted

        // LAUNCHING first: a second tap while the store work runs finds it in progress.
        mutableUiState.value = state(ConnectAccountPhase.LAUNCHING)
        withContext(storageDispatcher) { cleanupTransientAuthorization() }
        return try {
            val authorizationManager =
                sessionManager.forAccountAuthorization(account.stableId)
            val authorizationUrl =
                authorizationManager.beginAuthorization()
            mutableUiState.value =
                state(ConnectAccountPhase.AWAITING_CALLBACK)
            freshAccountAuthorizationLaunch(
                authorizationUrl = authorizationUrl,
                clientMode = clientMode,
            )
        } catch (_: Exception) {
            withContext(storageDispatcher) { cleanupTransientAuthorization() }
            mutableUiState.value = failure(
                ConnectAccountFailureKind.STORAGE,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    override suspend fun browserLaunchFailed() {
        withContext(storageDispatcher) { cleanupTransientAuthorization() }
        mutableUiState.value = failure(
            ConnectAccountFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    override suspend fun cancel() {
        withContext(storageDispatcher) { cleanupTransientAuthorization() }
        mutableUiState.value = state(ConnectAccountPhase.CANCELLED)
    }

    override suspend fun reset() {
        val restored = withContext(storageDispatcher) { restoredInitialState() }
        // The first restore yields to anything the flow did meanwhile.
        if (!mutableUiState.compareAndSet(initialState, restored)) {
            if (restoredOnce) mutableUiState.value = restored
        }
        restoredOnce = true
    }

    @Volatile
    private var restoredOnce = false

    override fun hasStoredCallback(): Boolean =
        runCatching(store::callback).getOrNull() != null

    override suspend fun handleCallbackResultToken(resultToken: String) {
        when {
            resultToken == AUTH_CALLBACK_CANCELLED_TOKEN -> cancel()
            resultToken == CALLBACK_ACCEPTED_TOKEN ||
                resultToken == CALLBACK_DENIED_TOKEN -> completeStoredCallback()
            resultToken.startsWith(CALLBACK_REJECTED_PREFIX) -> {
                if (withContext(storageDispatcher) { hasStoredCallback() }) {
                    completeStoredCallback()
                } else {
                    withContext(storageDispatcher) { cleanupTransientAuthorization() }
                    mutableUiState.value = failure(
                        ConnectAccountFailureKind.CALLBACK_REJECTED,
                        retryable = true,
                    )
                }
            }
            else -> {
                if (withContext(storageDispatcher) { hasStoredCallback() }) {
                    completeStoredCallback()
                } else {
                    withContext(storageDispatcher) { cleanupTransientAuthorization() }
                    mutableUiState.value = failure(
                        ConnectAccountFailureKind.CALLBACK_MISSING,
                        retryable = true,
                    )
                }
            }
        }
    }

    override suspend fun resumeStoredCallbackIfPresent() {
        if (withContext(storageDispatcher) { hasStoredCallback() }) {
            completeStoredCallback()
        } else if (
            withContext(storageDispatcher) {
                runCatching(store::pendingAuthorization).getOrNull()
                    ?.let { it.authorizationOwnerKey == null } == true
            }
        ) {
            mutableUiState.value = state(ConnectAccountPhase.AWAITING_CALLBACK)
        }
    }

    private suspend fun completeStoredCallback() {
        completionMutex.withLock {
            val callback = withContext(storageDispatcher) { runCatching(store::callback).getOrNull() }
            if (callback == null) {
                if (mutableUiState.value.phase == ConnectAccountPhase.CONNECTED) return@withLock
                mutableUiState.value = failure(
                    ConnectAccountFailureKind.CALLBACK_MISSING,
                    retryable = true,
                )
                return@withLock
            }

            mutableUiState.value = state(ConnectAccountPhase.EXCHANGING)
            val expectedAccountStableId =
                callback.accountStableId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            val authorizationSessionManager =
                expectedAccountStableId
                    ?.let(sessionManager::forAccountAuthorization)
                    ?: sessionManager
            val result = accountCoordinator.completePendingAccount(
                authorizationSessionManager = authorizationSessionManager,
                expectedAccountStableId = expectedAccountStableId,
            ) { stage ->
                mutableUiState.value = when (stage) {
                    AuthAccountRegistrationStage.TOKEN_EXCHANGE ->
                        state(ConnectAccountPhase.EXCHANGING)
                    AuthAccountRegistrationStage.IDENTITY_RESOLUTION ->
                        state(ConnectAccountPhase.RESOLVING_IDENTITY)
                    AuthAccountRegistrationStage.REDDIT_FEED_SYNC ->
                        state(ConnectAccountPhase.SYNCING_REDDIT_FEED)
                }
            }
            mutableUiState.value = when (result) {
                is AuthAccountRegistrationResult.Registered -> {
                    directoryRevision += 1L
                    ConnectAccountUiState(
                        phase = ConnectAccountPhase.CONNECTED,
                        providerLabel = providerLabel,
                        username = result.account.username,
                        directoryRevision = directoryRevision,
                    )
                }
                is AuthAccountRegistrationResult.Failed -> {
                    android.util.Log.e(
                        "subshell-auth-connect",
                        "REGISTRATION_FAILED " +
                            "kind=${result.kind} " +
                            "retryable=${result.retryable} " +
                            "provider_error=${result.providerError ?: "none"}",
                    )
                    withContext(storageDispatcher) { cleanupTransientAuthorization() }
                    result.toUiState()
                }
            }
        }
    }

    private fun AuthAccountRegistrationResult.Failed.toUiState(): ConnectAccountUiState =
        when (kind) {
            AuthAccountRegistrationFailureKind.AUTHORIZATION_DENIED -> {
                if (providerError.equals(ACCESS_DENIED_ERROR, ignoreCase = true)) {
                    state(ConnectAccountPhase.CANCELLED)
                } else {
                    failure(
                        ConnectAccountFailureKind.PROVIDER,
                        retryable = retryable,
                    )
                }
            }
            AuthAccountRegistrationFailureKind.NO_PENDING_CALLBACK -> failure(
                ConnectAccountFailureKind.CALLBACK_MISSING,
                retryable = true,
            )
            AuthAccountRegistrationFailureKind.TOKEN_EXCHANGE -> failure(
                ConnectAccountFailureKind.TOKEN_EXCHANGE,
                retryable = retryable,
            )
            AuthAccountRegistrationFailureKind.CLIENT_CONFIGURATION -> failure(
                ConnectAccountFailureKind.CONFIGURATION_REQUIRED,
                retryable = false,
            )
            AuthAccountRegistrationFailureKind.IDENTITY -> failure(
                ConnectAccountFailureKind.IDENTITY,
                retryable = retryable,
            )
            AuthAccountRegistrationFailureKind.REDDIT_FEED -> failure(
                ConnectAccountFailureKind.REDDIT_FEED,
                retryable = retryable,
            )
            AuthAccountRegistrationFailureKind.LOCAL_FIXTURE_USERNAME_COLLISION,
            AuthAccountRegistrationFailureKind.AUTHENTICATED_USERNAME_COLLISION,
            -> failure(ConnectAccountFailureKind.ACCOUNT_CONFLICT)
            AuthAccountRegistrationFailureKind.REAUTHENTICATION_ACCOUNT_MISMATCH ->
                failure(
                    ConnectAccountFailureKind
                        .REAUTHENTICATION_ACCOUNT_MISMATCH,
                    retryable = true,
                )
            AuthAccountRegistrationFailureKind.STORAGE -> failure(
                ConnectAccountFailureKind.STORAGE,
                retryable = true,
            )
        }

    // LOW4_CONNECT_FLOW_ONLY_OWNS_OWNERLESS_RECORDS_V1 (OCB-475): a pending
    // authorization or callback with an owner key belongs to a capability
    // (moderation log, wiki, comment write …), not to sign-in. It neither
    // makes sign-in look in progress nor is deleted by the connect flow.
    private fun restoredInitialState(): ConnectAccountUiState = when {
        runCatching(store::callback).getOrNull()
            ?.let { it.authorizationOwnerKey == null } == true ->
            state(ConnectAccountPhase.AWAITING_CALLBACK)
        runCatching(store::pendingAuthorization).getOrNull()
            ?.let { it.authorizationOwnerKey == null } == true ->
            state(ConnectAccountPhase.AWAITING_CALLBACK)
        else -> state(ConnectAccountPhase.IDLE)
    }

    private fun cleanupTransientAuthorization() {
        // An unreadable record has no owner to protect and is removed as before.
        val pendingOwned = runCatching(store::pendingAuthorization).getOrNull()
            ?.authorizationOwnerKey != null
        val callbackOwned = runCatching(store::callback).getOrNull()
            ?.authorizationOwnerKey != null
        if (!pendingOwned) runCatching(store::deletePendingAuthorization)
        if (!callbackOwned) runCatching(store::deleteCallback)
        // The unbound session is the in-progress sign-in's, unless a
        // capability's authorization is still running on it.
        if (!pendingOwned && !callbackOwned) runCatching(store::deleteSession)
    }

    private fun failure(
        kind: ConnectAccountFailureKind,
        retryable: Boolean = false,
    ): ConnectAccountUiState = ConnectAccountUiState(
        phase = ConnectAccountPhase.ERROR,
        providerLabel = providerLabel,
        failureKind = kind,
        retryable = retryable,
        directoryRevision = directoryRevision,
    )

    private fun state(phase: ConnectAccountPhase): ConnectAccountUiState =
        ConnectAccountUiState(
            phase = phase,
            providerLabel = providerLabel,
            directoryRevision = directoryRevision,
        )

    companion object {
        private const val CALLBACK_ACCEPTED_TOKEN = "accepted"
        private const val CALLBACK_DENIED_TOKEN = "denied"
        private const val CALLBACK_REJECTED_PREFIX = "rejected:"
        private const val ACCESS_DENIED_ERROR = "access_denied"
    }
}
