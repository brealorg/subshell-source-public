package app.subshell.prototype.auth

import app.subshell.prototype.data.diagnostics.recordFailureWithoutAScreen
import android.app.Application
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.RedditInboxUiFailureKind
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthInboxReadMutationResult
import app.subshell.prototype.data.auth.AuthInboxReadMutationWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxSnapshot
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.model.Post
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_INBOX01_COORDINATOR_V1
/**
 * The Reddit inbox: cache-first load, append, the optimistic read
 * mutations (mark one, mark all, mark seen), the exact-target resolution
 * and the inbox read authorization (begin, browser-launch failure, and the
 * callback completion the ViewModel's routing dispatches for
 * [AuthCapabilityAuthorizationOwner.INBOX_READ]). Moved verbatim out of
 * AuthConnectViewModel; the ViewModel keeps one-line forwarders. The
 * ViewModel never cancelled the inbox job in onCleared, so there is no
 * cancelPending() here either.
 * Characterised by AuthConnectViewModelInboxTest.
 */
internal class InboxCoordinator(
    private val application: Application,
    private val redditInboxLoader: AuthenticatedRedditInboxLoader?,
    private val inboxReadMutationWriter: AuthInboxReadMutationWriter?,
    private val inboxReadCapability: AuthInboxReadCapability?,
    private val inboxReadClientMode: OAuthClientMode?,
    private val accountSessionDirectory: AccountSessionDirectory?,
    private val scope: CoroutineScope,
    private val backgroundSyncKeeper: InboxBackgroundSyncKeeper = InboxBackgroundSyncKeeper(application),
) {
    private val _redditInboxUiState = MutableStateFlow(RedditInboxUiState())
    private var redditInboxJob: Job? = null
    private val deferredReadItemIds = linkedSetOf<Pair<String, String>>()

    // LOW7_FORCED_INBOX_RELOAD_WAITS_FOR_THE_RUNNING_WORK_V1 (OCB-421): a forced
    // reload (after a reply is accepted, after authorization) asked for while a
    // read or an append runs is kept and runs when that work ends.
    private val pendingForcedReloadAccounts = linkedSetOf<String>()

    val redditInboxUiState: StateFlow<RedditInboxUiState> =
        _redditInboxUiState.asStateFlow()

    // INBOXSSOT01_THE_INBOX_FOLLOWS_ITS_CACHE_V1: the stored inbox is the
    // source of truth (Google's offline-first pattern). The screen's state
    // follows it for the account last loaded, so whatever the 15-minute
    // background sync stored is shown at once: a notification's item is
    // already in the inbox when the notification is tapped.
    private var followedInboxAccount: String? = null
    private var followStoredInboxJob: Job? = null

    private fun followStoredInbox(account: String, loader: AuthenticatedRedditInboxLoader) {
        if (followedInboxAccount == account && followStoredInboxJob?.isActive == true) return
        followStoredInboxJob?.cancel()
        followedInboxAccount = account
        followStoredInboxJob = scope.launch {
            try {
                loader.observe(account).collect { snapshot -> applyStoredInbox(account, snapshot) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed read leaves the inbox as shown; the next load
                // follows the store again.
                followedInboxAccount = null
            }
        }
    }

    private fun applyStoredInbox(account: String, snapshot: AuthenticatedRedditInboxSnapshot) {
        val current = _redditInboxUiState.value
        if (!current.matches(account)) return
        // Marks not stored yet (sent after a load, or under way) stay shown.
        val pendingSeen = current.readMutationItemIds + deferredReadItemIds
            .filter { (deferredAccount, _) -> deferredAccount == account }
            .map { (_, itemId) -> itemId }
        val locallySeenIds = snapshot.locallySeenIds + pendingSeen
        if (
            current.loaded &&
            current.items == snapshot.items &&
            current.locallySeenIds == locallySeenIds &&
            current.nextCursor == snapshot.nextCursor
        ) {
            return
        }
        _redditInboxUiState.value = current.copy(
            items = snapshot.items,
            locallySeenIds = locallySeenIds,
            nextCursor = snapshot.nextCursor,
            // Before the first load this is the stored list, which a load
            // still refreshes.
            fromCache = current.fromCache || !current.loaded,
            loaded = true,
            contentRevision = current.contentRevision + 1L,
        )
    }

    /**
     * The ViewModel's callback routing: an INBOX_READ authorization that was
     * cancelled or never completed leaves the inbox asking for permission.
     */
    fun failPendingInboxAuthorization() {
        _redditInboxUiState.value =
            _redditInboxUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = RedditInboxUiFailureKind.SESSION,
                failureRetryable = true,
            )
    }

    /**
     * The user closed the browser during an INBOX_READ authorization: the
     * inbox asks for permission again, with no failure to report.
     */
    fun cancelPendingInboxAuthorization() {
        _redditInboxUiState.value =
            _redditInboxUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = null,
                failureRetryable = false,
            )
    }

    fun loadRedditInbox(
        accountStableId: String,
        forceRefresh: Boolean,
    ) {
        val account = accountStableId.trim()
        keepBackgroundSyncScheduled(account)
        if (account.isEmpty()) return
        redditInboxLoader?.let { loader -> followStoredInbox(account, loader) }
        // INBOX01_READ_DURING_LOAD_IS_DEFERRED_V1: reads deferred for another
        // account are dropped when this account's inbox loads.
        deferredReadItemIds.retainAll { (deferredAccount, _) -> deferredAccount == account }
        pendingForcedReloadAccounts.retainAll { it == account }
        val current = _redditInboxUiState.value
        if (
            current.matches(account) &&
            (current.loading || current.appending || current.authorizing || current.readMutationInProgress)
        ) {
            if (forceRefresh && (current.appending || current.readMutationInProgress)) {
                pendingForcedReloadAccounts += account
            }
            return
        }
        if (current.matches(account) && current.loaded && !current.fromCache && !forceRefresh) {
            return
        }
        val loader = redditInboxLoader
        if (loader == null) {
            _redditInboxUiState.value = RedditInboxUiState(
                accountStableId = account,
                failureKind = RedditInboxUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return
        }

        redditInboxJob?.cancel()
        val previousForAccount = current.takeIf { state -> state.matches(account) }
        _redditInboxUiState.value = RedditInboxUiState(
            accountStableId = account,
            items = previousForAccount?.items.orEmpty(),
            locallySeenIds = previousForAccount?.locallySeenIds.orEmpty(),
            nextCursor = previousForAccount?.nextCursor,
            loading = true,
            fromCache = previousForAccount?.fromCache == true,
            loaded = previousForAccount?.loaded == true,
            authorizationRequired = false,
            contentRevision = previousForAccount?.contentRevision ?: 0L,
        )
        authDebugLog(INBOX_LOAD_LOG_TAG, "start account=$account force=$forceRefresh")
        redditInboxJob = scope.launch {
            if (!forceRefresh) {
                val cached = try {
                    loader.cached(account)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AuthenticatedRedditInboxResult.Failed(
                        kind = AuthenticatedRedditInboxFailureKind.STORAGE,
                        retryable = true,
                        details = storageFailureDetails(),
                    )
                }
                if (!_redditInboxUiState.value.matches(account)) return@launch
                when (cached) {
                    is AuthenticatedRedditInboxResult.Loaded -> {
                        _redditInboxUiState.value = _redditInboxUiState.value.copy(
                            items = cached.snapshot.items,
                            locallySeenIds = cached.snapshot.locallySeenIds,
                            nextCursor = cached.snapshot.nextCursor,
                            fromCache = true,
                            loaded = true,
                            contentRevision = _redditInboxUiState.value.contentRevision + 1L,
                        )
                        authDebugLog(
                            INBOX_LOAD_LOG_TAG,
                            "cache account=$account count=${cached.snapshot.items.size}",
                        )
                    }
                    AuthenticatedRedditInboxResult.AuthorizationRequired,
                    AuthenticatedRedditInboxResult.NotCached,
                    is AuthenticatedRedditInboxResult.Failed,
                    -> Unit
                }
            }

            val refreshed = try {
                loader.synchronize(account)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            if (!_redditInboxUiState.value.matches(account)) return@launch
            _redditInboxUiState.value = when (refreshed) {
                is AuthenticatedRedditInboxResult.Loaded -> {
                    authDebugLog(
                        INBOX_LOAD_LOG_TAG,
                        "applied account=$account count=${refreshed.snapshot.items.size} " +
                            "next=${refreshed.snapshot.nextCursor != null}",
                    )
                    _redditInboxUiState.value.copy(
                        items = refreshed.snapshot.items,
                        locallySeenIds = refreshed.snapshot.locallySeenIds,
                        nextCursor = refreshed.snapshot.nextCursor,
                        loading = false,
                        fromCache = false,
                        loaded = true,
                        authorizationRequired = false,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        contentRevision = _redditInboxUiState.value.contentRevision + 1L,
                    )
                }
                AuthenticatedRedditInboxResult.AuthorizationRequired ->
                    _redditInboxUiState.value.copy(
                        loading = false,
                        authorizationRequired = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                    )
                AuthenticatedRedditInboxResult.NotCached ->
                    _redditInboxUiState.value.copy(
                        loading = false,
                        failureKind = RedditInboxUiFailureKind.STORAGE,
                        failureRetryable = true,
                    )
                is AuthenticatedRedditInboxResult.Failed -> {
                    authDebugLog(
                        INBOX_LOAD_LOG_TAG,
                        "failed account=$account kind=${refreshed.kind.name.lowercase()} " +
                            "retryable=${refreshed.retryable}",
                    )
                    _redditInboxUiState.value.copy(
                        loading = false,
                        failureKind = refreshed.kind.toUiFailureKind(),
                        failureRetryable = refreshed.retryable,
                        failureDetails = refreshed.details,
                    )
                }
            }
            sendDeferredReads(account)
            runPendingForcedReload(account)
        }
    }

    fun appendRedditInbox(accountStableId: String) {
        val account = accountStableId.trim()
        val current = _redditInboxUiState.value
        val cursor = current.nextCursor?.trim()?.takeIf(String::isNotEmpty) ?: return
        if (
            account.isEmpty() ||
            !current.matches(account) ||
            current.loading ||
            current.appending ||
            current.authorizing ||
            current.readMutationInProgress
        ) {
            return
        }
        val loader = redditInboxLoader ?: return
        redditInboxJob?.cancel()
        _redditInboxUiState.value = current.copy(
            appending = true,
            failureKind = null,
            failureRetryable = false,
            failureDetails = null,
        )
        redditInboxJob = scope.launch {
            val result = try {
                loader.append(account, cursor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            if (!_redditInboxUiState.value.matches(account)) return@launch
            _redditInboxUiState.value = when (result) {
                is AuthenticatedRedditInboxResult.Loaded -> _redditInboxUiState.value.copy(
                    items = result.snapshot.items,
                    locallySeenIds = result.snapshot.locallySeenIds,
                    nextCursor = result.snapshot.nextCursor,
                    appending = false,
                    fromCache = false,
                    loaded = true,
                    authorizationRequired = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureDetails = null,
                    contentRevision = _redditInboxUiState.value.contentRevision + 1L,
                )
                AuthenticatedRedditInboxResult.AuthorizationRequired ->
                    _redditInboxUiState.value.copy(
                        appending = false,
                        authorizationRequired = true,
                    )
                AuthenticatedRedditInboxResult.NotCached ->
                    _redditInboxUiState.value.copy(
                        appending = false,
                        failureKind = RedditInboxUiFailureKind.STORAGE,
                        failureRetryable = true,
                    )
                is AuthenticatedRedditInboxResult.Failed ->
                    _redditInboxUiState.value.copy(
                        appending = false,
                        failureKind = result.kind.toUiFailureKind(),
                        failureRetryable = result.retryable,
                        failureDetails = result.details,
                    )
            }
            sendDeferredReads(account)
            runPendingForcedReload(account)
        }
    }

    // LOW11_BACKGROUND_SYNC_SCHEDULED_ON_CHANGE_V1: the schedule is kept by
    // the one InboxBackgroundSyncKeeper the inbox and modmail share.
    fun keepBackgroundSyncScheduled(account: String) = backgroundSyncKeeper.keep(account)

    private fun runPendingForcedReload(account: String) {
        if (pendingForcedReloadAccounts.remove(account)) {
            loadRedditInbox(account, forceRefresh = true)
        }
    }

    // INBOX01_READ_DURING_LOAD_IS_DEFERRED_V1: an item opened while the inbox
    // loads or appends is sent once that finishes, if it is still unread.
    private fun sendDeferredReads(account: String) {
        val itemIds = deferredReadItemIds
            .filter { (deferredAccount, _) -> deferredAccount == account }
            .map { (_, itemId) -> itemId }
        deferredReadItemIds.removeAll { (deferredAccount, _) -> deferredAccount == account }
        itemIds.forEach { itemId -> markRedditInboxRead(account, itemId) }
    }

    // SUBSHELL_INBOXM3A2R2C1_LOCAL_SEEN_SERVER_FAILURE_STABILITY_V1
    // SUBSHELL_INBOXM3A2R2C2_AUTOMATIC_READ_FAILURE_ISOLATION_V1
    fun markRedditInboxRead(
        accountStableId: String,
        itemId: String,
    ) {
        val account = accountStableId.trim()
        val normalizedItemId = itemId.trim()
        val current = _redditInboxUiState.value
        val item = current.items.firstOrNull { candidate -> candidate.id == normalizedItemId }
            ?: return
        if (
            account.isEmpty() ||
            normalizedItemId.isEmpty() ||
            !current.matches(account) ||
            current.authorizing ||
            !item.unread ||
            normalizedItemId in current.readMutationItemIds
        ) {
            return
        }
        val writer = inboxReadMutationWriter ?: return
        // INBOX01_READ_DURING_LOAD_IS_DEFERRED_V1: a load or append replaces the
        // items; the read waits for it instead of being dropped.
        if (current.loading || current.appending) {
            deferredReadItemIds += account to normalizedItemId
            _redditInboxUiState.value = current.copy(
                locallySeenIds = current.locallySeenIds + normalizedItemId,
                contentRevision = current.contentRevision + 1L,
            )
            return
        }
        val previousSeen = current.locallySeenIds
        _redditInboxUiState.value = current.copy(
            locallySeenIds = previousSeen + normalizedItemId,
            readMutationItemIds = current.readMutationItemIds + normalizedItemId,
            contentRevision = current.contentRevision + 1L,
        )
        authDebugLog(INBOX_LOAD_LOG_TAG, "read-start account=$account item=$normalizedItemId")
        scope.launch {
            val result = try {
                writer.markRead(account, item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthInboxReadMutationResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                )
            }
            val latest = _redditInboxUiState.value
            if (!latest.matches(account)) return@launch
            _redditInboxUiState.value = when (result) {
                is AuthInboxReadMutationResult.Applied -> {
                    authDebugLog(INBOX_LOAD_LOG_TAG, "read-applied account=$account item=$normalizedItemId")
                    val remainingPending = latest.readMutationItemIds - normalizedItemId
                    latest.copy(
                        items = result.snapshot.items,
                        locallySeenIds = result.snapshot.locallySeenIds + remainingPending,
                        nextCursor = result.snapshot.nextCursor,
                        readMutationItemIds = remainingPending,
                        authorizationRequired = false,
                        contentRevision = latest.contentRevision + 1L,
                    )
                }
                AuthInboxReadMutationResult.AuthorizationRequired -> {
                    authDebugLog(INBOX_LOAD_LOG_TAG, "read-auth-required account=$account item=$normalizedItemId ui=isolated")
                    latest.copy(
                    // Opening the conversation is authoritative for local seen state.
                    // A failed server mark-read must not re-arm the automatic open effect.
                    locallySeenIds = latest.locallySeenIds,
                    readMutationItemIds = latest.readMutationItemIds - normalizedItemId,
                    authorizationRequired = true,
                        contentRevision = latest.contentRevision + 1L,
                    )
                }
                is AuthInboxReadMutationResult.Failed -> {
                    authDebugLog(
                        INBOX_LOAD_LOG_TAG,
                        "read-failed account=$account item=$normalizedItemId " +
                            "kind=${result.kind.name.lowercase()} retryable=${result.retryable} " +
                            "status=${result.details?.httpStatusCode ?: "none"} ui=isolated",
                    )
                    latest.copy(
                    // Opening the conversation is authoritative for local seen state.
                    // A failed server mark-read must not re-arm the automatic open effect.
                    locallySeenIds = latest.locallySeenIds,
                    readMutationItemIds = latest.readMutationItemIds - normalizedItemId,
                        contentRevision = latest.contentRevision + 1L,
                    )
                }
            }
            runPendingForcedReload(account)
        }
    }

    fun resolveRedditInboxTarget(
        accountStableId: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) {
        val account = accountStableId.trim()
        val targetPostId = postId.trim().removePrefix("t3_")
        if (account.isEmpty() || targetPostId.isEmpty()) {
            onResolved(null)
            return
        }
        val loader = redditInboxLoader
        if (loader == null) {
            onResolved(null)
            return
        }
        scope.launch {
            val result = try {
                loader.resolveTargetPost(account, targetPostId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditInboxTargetResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                )
            }
            // ISSUE124_INBOX_TARGET_FAILURES_ARE_SHOWN_V1: the caller is always
            // answered (it keeps a per-item "opening" state); it drops the
            // answer itself when the account changed.
            if (!_redditInboxUiState.value.matches(account)) {
                onResolved(null)
                return@launch
            }
            when (result) {
                is AuthenticatedRedditInboxTargetResult.Loaded -> {
                    authDebugLog(
                        INBOX_LOAD_LOG_TAG,
                        "target account=$account post=$targetPostId " +
                            "cache=${result.fromCache}",
                    )
                    onResolved(result.post)
                }
                AuthenticatedRedditInboxTargetResult.AuthorizationRequired -> {
                    authDebugLog(
                        INBOX_LOAD_LOG_TAG,
                        "target authorization-required account=$account post=$targetPostId",
                    )
                    _redditInboxUiState.value = _redditInboxUiState.value.copy(
                        authorizationRequired = true,
                    )
                    onResolved(null)
                }
                AuthenticatedRedditInboxTargetResult.NotFound -> {
                    authDebugLog(
                        INBOX_LOAD_LOG_TAG,
                        "target not-found account=$account post=$targetPostId",
                    )
                    onResolved(null)
                }
                is AuthenticatedRedditInboxTargetResult.Failed -> {
                    authDebugLog(
                        INBOX_LOAD_LOG_TAG,
                        "target failed account=$account post=$targetPostId " +
                            "kind=${result.kind.name.lowercase()} retryable=${result.retryable}",
                    )
                    recordFailureWithoutAScreen(application, "inbox_target_post", result.details)
                    onResolved(null)
                }
            }
        }
    }

    suspend fun beginInboxAuthorization(accountStableId: String): AuthBrowserLaunchResult {
        val account = accountStableId.trim()
        val capability = inboxReadCapability
        val clientMode = inboxReadClientMode
        if (account.isEmpty() || capability == null || clientMode == null) {
            _redditInboxUiState.value = RedditInboxUiState(
                accountStableId = account.takeIf(String::isNotEmpty),
                failureKind = RedditInboxUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (capability.hasScopeGrant(account)) {
            loadRedditInbox(account, forceRefresh = true)
            return AuthBrowserLaunchResult.NotStarted
        }
        val current = _redditInboxUiState.value.takeIf { state -> state.matches(account) }
        _redditInboxUiState.value = RedditInboxUiState(
            accountStableId = account,
            items = current?.items.orEmpty(),
            locallySeenIds = current?.locallySeenIds.orEmpty(),
            nextCursor = current?.nextCursor,
            fromCache = current?.fromCache == true,
            loaded = current?.loaded == true,
            authorizing = true,
            authorizationRequired = true,
            contentRevision = current?.contentRevision ?: 0L,
        )
        return try {
            // MOD01A221_ACCOUNT_PINNED_INBOX_AUTH_LAUNCH_V1
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = capability.beginAuthorization(account),
                clientMode = clientMode,
                freshAccountSession = true,
            )
        } catch (_: Exception) {
            capability.cancelPendingAuthorization()
            _redditInboxUiState.value = _redditInboxUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = RedditInboxUiFailureKind.SESSION,
                failureRetryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun inboxAuthorizationBrowserLaunchFailed() {
        inboxReadCapability?.cancelPendingAuthorization()
        _redditInboxUiState.value = _redditInboxUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
            failureKind = RedditInboxUiFailureKind.SESSION,
            failureRetryable = true,
        )
    }

    suspend fun completeInboxAuthorization(
        capability: AuthInboxReadCapability,
    ) {
        val expectedAccountId = _redditInboxUiState.value.accountStableId
            ?: accountSessionDirectory?.snapshot()?.activeAccountId
        _redditInboxUiState.value = _redditInboxUiState.value.copy(
            authorizing = true,
            failureKind = null,
            failureRetryable = false,
        )
        when (val result = capability.completePendingAuthorization()) {
            is AuthInboxReadCapabilityResult.Upgraded -> {
                if (
                    expectedAccountId != null &&
                    expectedAccountId != result.accountStableId
                ) {
                    _redditInboxUiState.value = _redditInboxUiState.value.copy(
                        authorizing = false,
                        authorizationRequired = true,
                        failureKind = RedditInboxUiFailureKind.SESSION,
                    )
                    return
                }
                _redditInboxUiState.value = _redditInboxUiState.value.copy(
                    accountStableId = result.accountStableId,
                    authorizing = false,
                    authorizationRequired = false,
                    failureKind = null,
                    failureRetryable = false,
                )
                loadRedditInbox(result.accountStableId, forceRefresh = true)
            }
            is AuthInboxReadCapabilityResult.Failed -> {
                _redditInboxUiState.value = _redditInboxUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = true,
                    failureKind = when (result.kind) {
                        AuthInboxReadCapabilityFailureKind.STORAGE ->
                            RedditInboxUiFailureKind.STORAGE
                        AuthInboxReadCapabilityFailureKind.ACCOUNT,
                        AuthInboxReadCapabilityFailureKind.CALLBACK,
                        AuthInboxReadCapabilityFailureKind.TOKEN_EXCHANGE,
                        AuthInboxReadCapabilityFailureKind.IDENTITY,
                        AuthInboxReadCapabilityFailureKind.SCOPE,
                        -> RedditInboxUiFailureKind.SESSION
                    },
                    failureRetryable = result.retryable,
                )
            }
        }
    }
}

private const val INBOX_LOAD_LOG_TAG = "subshell-inbox"

private fun AuthenticatedRedditInboxFailureKind.toUiFailureKind():
    RedditInboxUiFailureKind = when (this) {
    AuthenticatedRedditInboxFailureKind.CLIENT_CONFIGURATION ->
        RedditInboxUiFailureKind.CLIENT_CONFIGURATION
    AuthenticatedRedditInboxFailureKind.SESSION ->
        RedditInboxUiFailureKind.SESSION
    AuthenticatedRedditInboxFailureKind.HTTP ->
        RedditInboxUiFailureKind.HTTP
    AuthenticatedRedditInboxFailureKind.TRANSPORT ->
        RedditInboxUiFailureKind.TRANSPORT
    AuthenticatedRedditInboxFailureKind.PROTOCOL ->
        RedditInboxUiFailureKind.PROTOCOL
    AuthenticatedRedditInboxFailureKind.STORAGE ->
        RedditInboxUiFailureKind.STORAGE
}
