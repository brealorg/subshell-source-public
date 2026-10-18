package app.subshell.prototype.auth

import app.subshell.prototype.data.account.CustomFeedCreateFailureKind
import app.subshell.prototype.data.account.CustomFeedCreatePhase
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedFailureKind
import app.subshell.prototype.data.account.CustomFeedManageFailureKind
import app.subshell.prototype.data.account.CustomFeedManagePhase
import app.subshell.prototype.data.account.CustomFeedPostsFailureKind
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.customFeedApiPathSegments
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.CustomFeedCreateResult
import app.subshell.prototype.data.auth.CustomFeedCreator
import app.subshell.prototype.data.auth.CustomFeedManageResult
import app.subshell.prototype.data.auth.CustomFeedPostReadResult
import app.subshell.prototype.data.auth.CustomFeedReadResult
import app.subshell.prototype.data.auth.CustomFeedReader
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.error.FailureDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_CUSTOMFEED01_COORDINATOR_V1
/**
 * Custom feeds (multireddits): the list and its posts, create / update /
 * delete with the custom-feed write capability (granted session or browser
 * authorization, completed by the ViewModel's callback routing through
 * [completeCustomFeedAuthorization]), and the outcome-unknown
 * reconciliation that re-reads the list instead of re-dispatching a write.
 * Moved verbatim out of AuthConnectViewModel; the ViewModel keeps one-line
 * forwarders. Characterised by AuthConnectViewModelCustomFeedsTest.
 */
internal class CustomFeedCoordinator(
    private val feedReader: CustomFeedReader?,
    private val feedCreator: CustomFeedCreator?,
    private val writeCapability: AuthCustomFeedWriteCapability?,
    private val writeClientMode: OAuthClientMode?,
    private val scope: CoroutineScope,
) {
    private val _customFeedsUiState = MutableStateFlow(CustomFeedsUiState())
    private var customFeedJob: Job? = null

    // REDDITFAV04: a favorite read back waits for the list load it starts.
    val customFeedLoadJob: Job?
        get() = customFeedJob
    private var customFeedOutcomeReconciliationJob: Job? = null
    private var customFeedCreateJob: Job? = null
    private var pendingCustomFeedCreateRequest: CustomFeedCreateRequest? = null

    // LOW9_CUSTOM_FEED_AUTHORIZATION_KNOWS_ITS_OPERATION_V1 (OCB-416): which
    // operation began the authorization; a create request left over (an
    // unknown outcome still being reconciled) is never sent for an update or
    // delete's callback.
    private var authorizationForManagement: Boolean? = null
    private var pendingCustomFeedUpdateRequest: CustomFeedUpdateRequest? = null
    private var pendingCustomFeedDeleteRequest: CustomFeedDeleteRequest? = null
    private var customFeedManageJob: Job? = null
    private var customFeedPostsJob: Job? = null

    // OCB189_CUSTOM_FEED_POSTS_KEPT_PER_FEED_V1: Home and the Feeds tab each
    // browse a custom feed through the one visible posts slot; opening one
    // replaced the other's posts, so returning reloaded it from the top under
    // its restored scroll position. The last few feeds' loaded posts are kept
    // per account and feed, and an open of a kept feed puts them back.
    private val customFeedPostsByFeed =
        object : LinkedHashMap<Pair<String, String>, CustomFeedPostsUiState>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Pair<String, String>, CustomFeedPostsUiState>?,
            ): Boolean = size > CUSTOM_FEED_POSTS_KEPT_FEEDS
        }

    private fun publishCustomFeedPosts(state: CustomFeedPostsUiState) {
        _customFeedPostsUiState.value = state
        val account = state.accountStableId.trim()
        val path = state.feedPath.trim()
        if (account.isNotEmpty() && path.isNotEmpty()) customFeedPostsByFeed[account to path] = state
    }
    private val _customFeedPostsUiState = MutableStateFlow(CustomFeedPostsUiState())

    val customFeedsUiState: StateFlow<CustomFeedsUiState> =
        _customFeedsUiState.asStateFlow()

    val customFeedPostsUiState: StateFlow<CustomFeedPostsUiState> =
        _customFeedPostsUiState.asStateFlow()

    /** ViewModel.onCleared: stop the outcome reconciliation loop. */
    fun cancelPending() {
        customFeedOutcomeReconciliationJob?.cancel()
    }

    /**
     * The ViewModel's callback routing: a CUSTOM_FEED_WRITE authorization
     * that was cancelled or never completed fails whichever operation is
     * pending (manage if an update/delete is pending, otherwise create).
     */
    fun failPendingCustomFeedAuthorization(message: String) {
        if (
            pendingCustomFeedUpdateRequest != null ||
            pendingCustomFeedDeleteRequest != null
        ) {
            failCustomFeedManage(
                CustomFeedManageFailureKind.CALLBACK,
                message,
            )
        } else {
            failCustomFeedCreate(
                CustomFeedCreateFailureKind.CALLBACK,
                message,
            )
        }
    }

    fun loadCustomFeeds(accountStableId: String, forceRefresh: Boolean) {
        val account = accountStableId.trim()
        if (account.isEmpty()) return
        val reader = feedReader
        if (reader == null) {
            _customFeedsUiState.value = CustomFeedsUiState(
                accountStableId = account,
                failureKind = CustomFeedFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }
        val current = _customFeedsUiState.value
        if (current.loading && current.accountStableId == account) return
        if (
            !forceRefresh &&
            current.accountStableId == account &&
            current.contentRevision > 0L &&
            current.failureKind == null
        ) return
        customFeedJob?.cancel()
        _customFeedsUiState.value = if (current.accountStableId == account) {
            current.copy(
                loading = true,
                failureKind = null,
                failureRetryable = false,
                failureStatusCode = null,
            )
        } else {
            CustomFeedsUiState(
                accountStableId = account,
                loading = true,
            )
        }
        customFeedJob = scope.launch {
            val preferredSession = when (
                val writeSession = writeCapability?.ensureAuthorized(account)
            ) {
                is AuthSessionResult.Authenticated -> writeSession.session
                else -> null
            }
            val result = reader.load(
                accountStableId = account,
                preferredSession = preferredSession,
            )
            if (_customFeedsUiState.value.accountStableId != account) return@launch
            _customFeedsUiState.value = when (result) {
                is CustomFeedReadResult.Loaded -> reconcileCustomFeedOutcomeAfterRead(
                    accountStableId = account,
                    feeds = result.feeds,
                    state = _customFeedsUiState.value.copy(
                        accountStableId = account,
                        feeds = result.feeds,
                        loading = false,
                        failureKind = null,
                        failureRetryable = false,
                        failureStatusCode = null,
                        contentRevision = _customFeedsUiState.value.contentRevision + 1L,
                    ),
                )
                is CustomFeedReadResult.Failed -> _customFeedsUiState.value.copy(
                    accountStableId = account,
                    loading = false,
                    failureKind = result.kind,
                    failureRetryable = result.retryable,
                    failureStatusCode = result.statusCode,
                )
            }
        }
    }

    suspend fun beginCustomFeedCreation(request: CustomFeedCreateRequest): AuthBrowserLaunchResult {
        val normalized = request.copy(
            accountStableId = request.accountStableId.trim(),
            displayName = request.displayName.trim(),
            subredditNames = request.subredditNames
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(String::lowercase),
        )
        if (
            normalized.accountStableId.isEmpty() ||
            normalized.displayName.isEmpty() ||
            normalized.displayName.length > 50 ||
            normalized.subredditNames.isEmpty()
        ) {
            failCustomFeedCreate(
                CustomFeedCreateFailureKind.VALIDATION,
                "Choose a name and at least one community",
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        val capability = writeCapability
        val creator = feedCreator
        val clientMode = writeClientMode
        if (capability == null || creator == null || clientMode == null) {
            failCustomFeedCreate(
                CustomFeedCreateFailureKind.CONFIGURATION,
                "Custom feed creation is unavailable for this account",
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        pendingCustomFeedCreateRequest = normalized
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            accountStableId = normalized.accountStableId,
            createFailureKind = null,
            createMessage = null,
            createFailureDetails = null,
        )
        val grantedSession = capability.authorizedSession(normalized.accountStableId)
        if (grantedSession != null) {
            createCustomFeed(
                creator = creator,
                request = normalized,
                authorizedSession = grantedSession,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            createPhase = CustomFeedCreatePhase.AUTHORIZING,
        )
        return try {
            authorizationForManagement = false
            val authorizationUrl = capability.beginAuthorization(normalized.accountStableId)
            _customFeedsUiState.value = _customFeedsUiState.value.copy(
                createPhase = CustomFeedCreatePhase.AWAITING_CALLBACK,
            )
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = clientMode,
            )
        } catch (_: Exception) {
            capability.cancelPendingAuthorization()
            failCustomFeedCreate(
                CustomFeedCreateFailureKind.AUTHORIZATION,
                "Couldn’t start Reddit authorization",
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun customFeedAuthorizationBrowserLaunchFailed() {
        writeCapability?.cancelPendingAuthorization()
        if (pendingCustomFeedUpdateRequest != null || pendingCustomFeedDeleteRequest != null) {
            failCustomFeedManage(
                CustomFeedManageFailureKind.AUTHORIZATION,
                "Couldn’t open Reddit authorization",
            )
        } else {
            failCustomFeedCreate(
                CustomFeedCreateFailureKind.AUTHORIZATION,
                "Couldn’t open Reddit authorization",
            )
        }
    }

    fun resetCustomFeedCreateState() {
        when (_customFeedsUiState.value.createPhase) {
            CustomFeedCreatePhase.CREATING,
            CustomFeedCreatePhase.AUTHORIZING,
            -> return
            CustomFeedCreatePhase.AWAITING_CALLBACK ->
                writeCapability?.cancelPendingAuthorization()
            else -> Unit
        }
        pendingCustomFeedCreateRequest = null
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            createPhase = CustomFeedCreatePhase.IDLE,
            createFailureKind = null,
            createMessage = null,
            createFailureDetails = null,
        )
    }

    suspend fun beginCustomFeedUpdate(request: CustomFeedUpdateRequest): AuthBrowserLaunchResult {
        val normalized = request.copy(
            accountStableId = request.accountStableId.trim(),
            path = request.path.trim(),
            displayName = request.displayName.trim(),
            subredditNames = request.subredditNames
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(String::lowercase),
        )
        if (
            normalized.accountStableId.isEmpty() ||
            normalized.displayName.isEmpty() ||
            normalized.displayName.length > 50 ||
            normalized.subredditNames.isEmpty() ||
            customFeedApiPathSegments(normalized.path) == null
        ) {
            failCustomFeedManage(CustomFeedManageFailureKind.VALIDATION, "Choose a valid name and at least one community")
            return AuthBrowserLaunchResult.NotStarted
        }
        pendingCustomFeedUpdateRequest = normalized
        pendingCustomFeedDeleteRequest = null
        return beginCustomFeedManagementAuthorization(normalized.accountStableId)
    }

    suspend fun beginCustomFeedDeletion(request: CustomFeedDeleteRequest): AuthBrowserLaunchResult {
        val normalized = request.copy(
            accountStableId = request.accountStableId.trim(),
            path = request.path.trim(),
            displayName = request.displayName.trim(),
        )
        if (normalized.accountStableId.isEmpty() || customFeedApiPathSegments(normalized.path) == null) {
            failCustomFeedManage(CustomFeedManageFailureKind.VALIDATION, "This custom feed has an invalid Reddit path")
            return AuthBrowserLaunchResult.NotStarted
        }
        pendingCustomFeedDeleteRequest = normalized
        pendingCustomFeedUpdateRequest = null
        return beginCustomFeedManagementAuthorization(normalized.accountStableId)
    }

    fun resetCustomFeedManageState() {
        when (_customFeedsUiState.value.managePhase) {
            CustomFeedManagePhase.SAVING,
            CustomFeedManagePhase.DELETING,
            CustomFeedManagePhase.AUTHORIZING,
            -> return
            CustomFeedManagePhase.AWAITING_CALLBACK ->
                writeCapability?.cancelPendingAuthorization()
            else -> Unit
        }
        pendingCustomFeedUpdateRequest = null
        pendingCustomFeedDeleteRequest = null
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            managePhase = CustomFeedManagePhase.IDLE,
            manageFailureKind = null,
            manageMessage = null,
            manageFailureDetails = null,
        )
    }

    fun loadCustomFeedPosts(
        accountStableId: String,
        feedPath: String,
        forceRefresh: Boolean,
    ) {
        updateCustomFeedPosts(
            accountStableId = accountStableId,
            feedPath = feedPath,
            append = false,
            forceRefresh = forceRefresh,
        )
    }

    // REDDITURL04_MULTIREDDIT_LINK_V1: the metadata of a feed opened from a
    // link, which may belong to another user. A read; null when unavailable.
    fun resolveCustomFeed(
        accountStableId: String,
        feedPath: String,
        onResolved: (CustomFeedSummary?) -> Unit,
    ) {
        val reader = feedReader
        if (reader == null) {
            onResolved(null)
            return
        }
        scope.launch {
            val feed = try {
                reader.loadFeed(accountStableId, feedPath)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            onResolved(feed)
        }
    }

    fun appendCustomFeedPosts(accountStableId: String, feedPath: String) {
        updateCustomFeedPosts(
            accountStableId = accountStableId,
            feedPath = feedPath,
            append = true,
            forceRefresh = false,
        )
    }

    private fun updateCustomFeedPosts(
        accountStableId: String,
        feedPath: String,
        append: Boolean,
        forceRefresh: Boolean,
    ) {
        val account = accountStableId.trim()
        val normalizedPath = feedPath.trim()
        val reader = feedReader
        if (account.isEmpty() || customFeedApiPathSegments(normalizedPath) == null || reader == null) {
            _customFeedPostsUiState.value = CustomFeedPostsUiState(
                accountStableId = account,
                feedPath = normalizedPath,
                failureKind = CustomFeedPostsFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }
        var current = _customFeedPostsUiState.value
        if (!append && !current.matches(account, normalizedPath)) {
            val kept = customFeedPostsByFeed[account to normalizedPath]
            if (kept != null && kept.contentRevision > 0L && !kept.loading && !kept.appending) {
                customFeedPostsJob?.cancel()
                publishCustomFeedPosts(kept)
                if (!forceRefresh) return
                current = kept
            }
        }
        if (append) {
            if (
                !current.matches(account, normalizedPath) ||
                current.appending ||
                current.loading ||
                current.nextCursor == null
            ) return
        } else {
            if (current.loading && current.matches(account, normalizedPath)) return
            if (!forceRefresh && current.matches(account, normalizedPath) && current.contentRevision > 0L) return
        }
        val after = if (append) current.nextCursor else null
        customFeedPostsJob?.cancel()
        publishCustomFeedPosts(if (append) {
            current.copy(
                appending = true,
                failureKind = null,
                failureRetryable = false,
                failureStatusCode = null,
                failedAppend = false,
            )
        } else if (current.matches(account, normalizedPath)) {
            current.copy(
                loading = true,
                appending = false,
                failureKind = null,
                failureRetryable = false,
                failureStatusCode = null,
                failedAppend = false,
            )
        } else {
            CustomFeedPostsUiState(
                accountStableId = account,
                feedPath = normalizedPath,
                loading = true,
            )
        })
        customFeedPostsJob = scope.launch {
            val preferredSession = when (
                val writeSession = writeCapability?.ensureAuthorized(account)
            ) {
                is AuthSessionResult.Authenticated -> writeSession.session
                else -> null
            }
            val result = reader.loadPosts(
                accountStableId = account,
                feedPath = normalizedPath,
                after = after,
                preferredSession = preferredSession,
            )
            val latest = _customFeedPostsUiState.value
            if (!latest.matches(account, normalizedPath)) return@launch
            publishCustomFeedPosts(when (result) {
                is CustomFeedPostReadResult.Loaded -> latest.copy(
                    posts = if (append) {
                        (latest.posts + result.posts).distinctBy { post -> post.id }
                    } else {
                        result.posts
                    },
                    nextCursor = result.nextCursor,
                    loading = false,
                    appending = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureStatusCode = null,
                    failureDetails = null,
                    failedAppend = false,
                    contentRevision = latest.contentRevision + 1L,
                )
                is CustomFeedPostReadResult.Failed -> latest.copy(
                    loading = false,
                    appending = false,
                    failureKind = result.kind,
                    failureRetryable = result.retryable,
                    failureStatusCode = result.statusCode,
                    failureDetails = result.details,
                    failedAppend = append,
                )
            })
        }
    }

    private suspend fun beginCustomFeedManagementAuthorization(accountStableId: String): AuthBrowserLaunchResult {
        val capability = writeCapability
        val creator = feedCreator
        val clientMode = writeClientMode
        if (capability == null || creator == null || clientMode == null) {
            failCustomFeedManage(CustomFeedManageFailureKind.CONFIGURATION, "Custom feed management is unavailable")
            return AuthBrowserLaunchResult.NotStarted
        }
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            accountStableId = accountStableId,
            manageFailureKind = null,
            manageMessage = null,
            manageFailureDetails = null,
        )
        val grantedSession = capability.authorizedSession(accountStableId)
        if (grantedSession != null) {
            runPendingCustomFeedManagement(creator, grantedSession)
            return AuthBrowserLaunchResult.NotStarted
        }
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            managePhase = CustomFeedManagePhase.AUTHORIZING,
        )
        return try {
            authorizationForManagement = true
            val authorizationUrl = capability.beginAuthorization(accountStableId)
            _customFeedsUiState.value = _customFeedsUiState.value.copy(
                managePhase = CustomFeedManagePhase.AWAITING_CALLBACK,
            )
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = clientMode,
            )
        } catch (_: Exception) {
            capability.cancelPendingAuthorization()
            failCustomFeedManage(CustomFeedManageFailureKind.AUTHORIZATION, "Couldn’t start Reddit authorization")
            AuthBrowserLaunchResult.NotStarted
        }
    }

    private fun runPendingCustomFeedManagement(
        creator: app.subshell.prototype.data.auth.CustomFeedCreator,
        authorizedSession: StoredOAuthSession,
    ) {
        val updateRequest = pendingCustomFeedUpdateRequest
        val deleteRequest = pendingCustomFeedDeleteRequest
        when {
            updateRequest != null -> updateCustomFeed(creator, updateRequest, authorizedSession)
            deleteRequest != null -> deleteCustomFeed(creator, deleteRequest, authorizedSession)
            else -> failCustomFeedManage(CustomFeedManageFailureKind.PROTOCOL, "No custom feed operation is pending")
        }
    }

    private fun updateCustomFeed(
        creator: app.subshell.prototype.data.auth.CustomFeedCreator,
        request: CustomFeedUpdateRequest,
        authorizedSession: StoredOAuthSession,
    ) {
        customFeedManageJob?.cancel()
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            managePhase = CustomFeedManagePhase.SAVING,
            manageFailureKind = null,
            manageMessage = null,
            manageFailureDetails = null,
        )
        customFeedManageJob = scope.launch {
            val result = creator.update(request, authorizedSession)
            // LOW8_CUSTOM_FEED_WRITE_RESULT_STAYS_WITH_ITS_ACCOUNT_V1 (OCB-417): after
            // an account switch the lists belong to another account; this
            // result is not written into them (nor reloaded for the old one).
            if (_customFeedsUiState.value.accountStableId != request.accountStableId) return@launch
            when (result) {
                CustomFeedManageResult.Updated -> {
                    clearPendingCustomFeedManagement()
                    _customFeedsUiState.value = _customFeedsUiState.value.copy(
                        managePhase = CustomFeedManagePhase.SUCCEEDED,
                        manageFailureKind = null,
                        manageMessage = null,
                        manageCompletionRevision = _customFeedsUiState.value.manageCompletionRevision + 1L,
                    )
                    loadCustomFeeds(request.accountStableId, true)
                }
                CustomFeedManageResult.AuthorizationRequired ->
                    failCustomFeedManage(CustomFeedManageFailureKind.AUTHORIZATION, "Reddit permission is required to edit this feed")
                is CustomFeedManageResult.Failed -> {
                    failCustomFeedManage(
                        result.kind,
                        customFeedManageMessage(result, "save"),
                        failureDetails = result.failureDetails,
                    )
                    if (result.kind == CustomFeedManageFailureKind.OUTCOME_UNKNOWN) {
                        scheduleCustomFeedOutcomeReconciliation(request.accountStableId)
                    }
                }
                CustomFeedManageResult.Deleted ->
                    failCustomFeedManage(CustomFeedManageFailureKind.PROTOCOL, "Unexpected Reddit response")
            }
        }
    }

    private fun deleteCustomFeed(
        creator: app.subshell.prototype.data.auth.CustomFeedCreator,
        request: CustomFeedDeleteRequest,
        authorizedSession: StoredOAuthSession,
    ) {
        customFeedManageJob?.cancel()
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            managePhase = CustomFeedManagePhase.DELETING,
            manageFailureKind = null,
            manageMessage = null,
            manageFailureDetails = null,
        )
        customFeedManageJob = scope.launch {
            val result = creator.delete(request, authorizedSession)
            // LOW8_CUSTOM_FEED_WRITE_RESULT_STAYS_WITH_ITS_ACCOUNT_V1 (OCB-417): after
            // an account switch the lists belong to another account; this
            // result is not written into them (nor reloaded for the old one).
            if (_customFeedsUiState.value.accountStableId != request.accountStableId) return@launch
            when (result) {
                CustomFeedManageResult.Deleted -> {
                    clearPendingCustomFeedManagement()
                    _customFeedsUiState.value = _customFeedsUiState.value.copy(
                        managePhase = CustomFeedManagePhase.SUCCEEDED,
                        manageFailureKind = null,
                        manageMessage = null,
                        manageCompletionRevision = _customFeedsUiState.value.manageCompletionRevision + 1L,
                    )
                    loadCustomFeeds(request.accountStableId, true)
                }
                CustomFeedManageResult.AuthorizationRequired ->
                    failCustomFeedManage(CustomFeedManageFailureKind.AUTHORIZATION, "Reddit permission is required to delete this feed")
                is CustomFeedManageResult.Failed -> {
                    failCustomFeedManage(
                        result.kind,
                        customFeedManageMessage(result, "delete"),
                        failureDetails = result.failureDetails,
                    )
                    if (result.kind == CustomFeedManageFailureKind.OUTCOME_UNKNOWN) {
                        scheduleCustomFeedOutcomeReconciliation(request.accountStableId)
                    }
                }
                CustomFeedManageResult.Updated ->
                    failCustomFeedManage(CustomFeedManageFailureKind.PROTOCOL, "Unexpected Reddit response")
            }
        }
    }

    private fun customFeedManageMessage(
        result: CustomFeedManageResult.Failed,
        verb: String,
    ): String = when (result.kind) {
        CustomFeedManageFailureKind.HTTP -> result.statusCode?.let { status ->
            "Reddit rejected the $verb request (HTTP $status)"
        } ?: "Reddit rejected the $verb request"
        CustomFeedManageFailureKind.OUTCOME_UNKNOWN ->
            "Reddit may have received the request. Refresh before trying again."
        CustomFeedManageFailureKind.VALIDATION -> "The custom feed details are invalid"
        CustomFeedManageFailureKind.ACCOUNT_CHANGED -> "The active Reddit account changed"
        CustomFeedManageFailureKind.CONFIGURATION -> "Custom feed management is unavailable"
        CustomFeedManageFailureKind.AUTHORIZATION,
        CustomFeedManageFailureKind.CALLBACK,
        -> "Reddit authorization did not complete"
        CustomFeedManageFailureKind.SESSION -> "Your Reddit session needs attention"
        CustomFeedManageFailureKind.PROTOCOL -> "Reddit returned an unexpected response"
    }

    private fun clearPendingCustomFeedManagement() {
        pendingCustomFeedUpdateRequest = null
        pendingCustomFeedDeleteRequest = null
    }

    private fun failCustomFeedManage(
        kind: CustomFeedManageFailureKind,
        message: String,
        failureDetails: FailureDetails? = null,
    ) {
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            managePhase = CustomFeedManagePhase.FAILED,
            manageFailureKind = kind,
            manageMessage = message,
            manageFailureDetails = failureDetails,
        )
    }

    suspend fun completeCustomFeedAuthorization(
        capability: app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability,
    ) {
        val createRequest = pendingCustomFeedCreateRequest
        val updateRequest = pendingCustomFeedUpdateRequest
        val deleteRequest = pendingCustomFeedDeleteRequest
        val expectedAccountId = createRequest?.accountStableId
            ?: updateRequest?.accountStableId
            ?: deleteRequest?.accountStableId
        val managing = authorizationForManagement ?: (updateRequest != null || deleteRequest != null)
        authorizationForManagement = null
        if (managing) {
            _customFeedsUiState.value = _customFeedsUiState.value.copy(
                managePhase = CustomFeedManagePhase.AUTHORIZING,
                manageFailureKind = null,
                manageMessage = null,
            )
        } else {
            _customFeedsUiState.value = _customFeedsUiState.value.copy(
                createPhase = CustomFeedCreatePhase.AUTHORIZING,
                createFailureKind = null,
                createMessage = null,
            )
        }
        when (val result = capability.completePendingAuthorization()) {
            is AuthCustomFeedWriteCapabilityResult.Upgraded -> {
                if (expectedAccountId == null || result.accountStableId != expectedAccountId) {
                    pendingCustomFeedCreateRequest = null
                    clearPendingCustomFeedManagement()
                    if (managing) {
                        failCustomFeedManage(
                            CustomFeedManageFailureKind.ACCOUNT_CHANGED,
                            "The active Reddit account changed during authorization",
                        )
                    } else {
                        failCustomFeedCreate(
                            CustomFeedCreateFailureKind.ACCOUNT_CHANGED,
                            "The active Reddit account changed during authorization",
                        )
                    }
                    return
                }
                val creator = feedCreator
                if (creator == null) {
                    if (managing) {
                        failCustomFeedManage(CustomFeedManageFailureKind.CONFIGURATION, "Custom feed management is unavailable")
                    } else {
                        failCustomFeedCreate(CustomFeedCreateFailureKind.CONFIGURATION, "Custom feed creation is unavailable")
                    }
                    return
                }
                when {
                    !managing && createRequest != null -> createCustomFeed(
                        creator = creator,
                        request = createRequest,
                        authorizedSession = result.session,
                    )
                    managing -> runPendingCustomFeedManagement(creator, result.session)
                    else -> failCustomFeedManage(CustomFeedManageFailureKind.PROTOCOL, "No custom feed operation is pending")
                }
            }
            is AuthCustomFeedWriteCapabilityResult.Failed -> {
                if (managing) clearPendingCustomFeedManagement() else pendingCustomFeedCreateRequest = null
                if (managing) {
                    val mappedKind = when (result.kind) {
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.ACCOUNT ->
                            CustomFeedManageFailureKind.ACCOUNT_CHANGED
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.CALLBACK ->
                            CustomFeedManageFailureKind.CALLBACK
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.TOKEN_EXCHANGE,
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.SCOPE,
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.STORAGE,
                        -> CustomFeedManageFailureKind.AUTHORIZATION
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.IDENTITY ->
                            CustomFeedManageFailureKind.SESSION
                    }
                    failCustomFeedManage(
                        mappedKind,
                        if (mappedKind == CustomFeedManageFailureKind.ACCOUNT_CHANGED) {
                            "The active Reddit account changed during authorization"
                        } else {
                            "Reddit authorization did not complete"
                        },
                    )
                } else {
                    val mappedKind = when (result.kind) {
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.ACCOUNT ->
                            CustomFeedCreateFailureKind.ACCOUNT_CHANGED
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.CALLBACK ->
                            CustomFeedCreateFailureKind.CALLBACK
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.TOKEN_EXCHANGE,
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.SCOPE,
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.STORAGE,
                        -> CustomFeedCreateFailureKind.AUTHORIZATION
                        app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind.IDENTITY ->
                            CustomFeedCreateFailureKind.SESSION
                    }
                    failCustomFeedCreate(
                        mappedKind,
                        if (mappedKind == CustomFeedCreateFailureKind.ACCOUNT_CHANGED) {
                            "The active Reddit account changed during authorization"
                        } else {
                            "Reddit authorization did not complete"
                        },
                    )
                }
            }
        }
    }

    private fun createCustomFeed(
        creator: app.subshell.prototype.data.auth.CustomFeedCreator,
        request: CustomFeedCreateRequest,
        authorizedSession: StoredOAuthSession? = null,
    ) {
        customFeedCreateJob?.cancel()
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            accountStableId = request.accountStableId,
            createPhase = CustomFeedCreatePhase.CREATING,
            createFailureKind = null,
            createMessage = null,
            createFailureDetails = null,
        )
        customFeedCreateJob = scope.launch {
            val result = creator.create(
                request = request,
                authorizedSession = authorizedSession,
            )
            // LOW8_CUSTOM_FEED_WRITE_RESULT_STAYS_WITH_ITS_ACCOUNT_V1 (OCB-417): after
            // an account switch the lists belong to another account; this
            // result is not written into them (nor reloaded for the old one).
            if (_customFeedsUiState.value.accountStableId != request.accountStableId) return@launch
            when (result) {
                CustomFeedCreateResult.Created -> {
                    pendingCustomFeedCreateRequest = null
                    _customFeedsUiState.value = _customFeedsUiState.value.copy(
                        createPhase = CustomFeedCreatePhase.SUCCEEDED,
                        createFailureKind = null,
                        createMessage = null,
                        createCompletionRevision =
                            _customFeedsUiState.value.createCompletionRevision + 1L,
                    )
                    loadCustomFeeds(request.accountStableId, true)
                }
                CustomFeedCreateResult.AuthorizationRequired -> {
                    failCustomFeedCreate(
                        CustomFeedCreateFailureKind.AUTHORIZATION,
                        "Reddit did not return the required permission. Reopen Create feed to authorize again.",
                    )
                }
                is CustomFeedCreateResult.Failed -> {
                    failCustomFeedCreate(
                        result.kind,
                        when (result.kind) {
                            CustomFeedCreateFailureKind.NAME_CONFLICT ->
                                "A custom feed with that name already exists"
                            CustomFeedCreateFailureKind.OUTCOME_UNKNOWN ->
                                "Reddit may have received the request. Refresh before trying again."
                            CustomFeedCreateFailureKind.VALIDATION ->
                                "Choose a valid name and at least one community"
                            CustomFeedCreateFailureKind.ACCOUNT_CHANGED ->
                                "The active Reddit account changed"
                            CustomFeedCreateFailureKind.CONFIGURATION ->
                                "Custom feed creation is unavailable"
                            CustomFeedCreateFailureKind.AUTHORIZATION,
                            CustomFeedCreateFailureKind.CALLBACK,
                            -> "Reddit authorization did not complete"
                            CustomFeedCreateFailureKind.SESSION ->
                                "Your Reddit session needs attention"
                            CustomFeedCreateFailureKind.HTTP ->
                                result.statusCode?.let { status ->
                                    "Reddit rejected the create request (HTTP $status)"
                                } ?: "Reddit rejected the create request"
                            CustomFeedCreateFailureKind.PROTOCOL ->
                                "Reddit returned an unexpected response"
                        },
                        failureDetails = result.failureDetails,
                    )
                    if (result.kind == CustomFeedCreateFailureKind.OUTCOME_UNKNOWN) {
                        scheduleCustomFeedOutcomeReconciliation(request.accountStableId)
                    }
                }
            }
        }
    }

    private fun scheduleCustomFeedOutcomeReconciliation(accountStableId: String) {
        val account = accountStableId.trim()
        if (account.isEmpty()) return
        customFeedOutcomeReconciliationJob?.cancel()
        customFeedOutcomeReconciliationJob = scope.launch {
            CUSTOM_FEED_RECONCILIATION_DELAYS_MS.forEachIndexed { index, delayMs ->
                if (delayMs > 0L) delay(delayMs)
                if (!hasUnconfirmedCustomFeedOutcome(account)) return@launch
                customFeedReconciliationFinalRead =
                    index == CUSTOM_FEED_RECONCILIATION_DELAYS_MS.lastIndex
                try {
                    loadCustomFeeds(account, true)
                    customFeedJob?.join()
                } finally {
                    customFeedReconciliationFinalRead = false
                }
                if (!hasUnconfirmedCustomFeedOutcome(account)) return@launch
            }
        }
    }

    private fun hasUnconfirmedCustomFeedOutcome(accountStableId: String): Boolean {
        val state = _customFeedsUiState.value
        if (state.accountStableId != accountStableId) return false
        return (
            state.createPhase == CustomFeedCreatePhase.FAILED &&
                state.createFailureKind == CustomFeedCreateFailureKind.OUTCOME_UNKNOWN
            ) || (
            state.managePhase == CustomFeedManagePhase.FAILED &&
                state.manageFailureKind == CustomFeedManageFailureKind.OUTCOME_UNKNOWN
            )
    }

    // MED3_CUSTOM_FEED_OUTCOME_WAITS_FOR_THE_LAST_READ_V1 (OCB-585): a read
    // that agrees with the change settles it at once; one that does not keeps
    // the unknown outcome (and its pending request) until the last read of the
    // backoff, then becomes a non-blocking "not confirmed" notice. The first
    // read used to settle it, so Reddit still processing a create looked like
    // "not created" and invited a duplicate.
    private var customFeedReconciliationFinalRead = false

    private fun reconcileCustomFeedOutcomeAfterRead(
        accountStableId: String,
        feeds: List<CustomFeedSummary>,
        state: CustomFeedsUiState,
    ): CustomFeedsUiState {
        var next = state
        val finalRead = customFeedReconciliationFinalRead

        val createRequest = pendingCustomFeedCreateRequest
            ?.takeIf { request -> request.accountStableId == accountStableId }
        if (
            next.createPhase == CustomFeedCreatePhase.FAILED &&
            next.createFailureKind == CustomFeedCreateFailureKind.OUTCOME_UNKNOWN &&
            createRequest != null
        ) {
            val created = feeds.any { feed ->
                feed.displayName.equals(createRequest.displayName, ignoreCase = true)
            }
            if (created || finalRead) pendingCustomFeedCreateRequest = null
            next = if (!created && !finalRead) {
                next
            } else if (created) {
                next.copy(
                    createPhase = CustomFeedCreatePhase.SUCCEEDED,
                    createFailureKind = null,
                    createMessage = null,
                    createFailureDetails = null,
                    createCompletionRevision = next.createCompletionRevision + 1L,
                )
            } else {
                next.copy(
                    createPhase = CustomFeedCreatePhase.IDLE,
                    createFailureKind = null,
                    createMessage = CUSTOM_FEED_CREATE_NOT_CONFIRMED_MESSAGE,
                    createFailureDetails = null,
                )
            }
        }

        val updateRequest = pendingCustomFeedUpdateRequest
            ?.takeIf { request -> request.accountStableId == accountStableId }
        val deleteRequest = pendingCustomFeedDeleteRequest
            ?.takeIf { request -> request.accountStableId == accountStableId }
        if (
            next.managePhase == CustomFeedManagePhase.FAILED &&
            next.manageFailureKind == CustomFeedManageFailureKind.OUTCOME_UNKNOWN
        ) {
            when {
                updateRequest != null -> {
                    val feed = feeds.firstOrNull { candidate ->
                        candidate.path.equals(updateRequest.path, ignoreCase = true)
                    }
                    val applied = feed != null &&
                        feed.displayName == updateRequest.displayName &&
                        feed.visibility.equals(updateRequest.visibility.apiValue, ignoreCase = true) &&
                        feed.subredditNames.map(String::lowercase).toSet() ==
                        updateRequest.subredditNames.map(String::lowercase).toSet()
                    if (applied || finalRead) clearPendingCustomFeedManagement()
                    next = if (!applied && !finalRead) {
                        next
                    } else if (applied) {
                        next.copy(
                            managePhase = CustomFeedManagePhase.SUCCEEDED,
                            manageFailureKind = null,
                            manageMessage = null,
                            manageFailureDetails = null,
                            manageCompletionRevision = next.manageCompletionRevision + 1L,
                        )
                    } else {
                        next.copy(
                            managePhase = CustomFeedManagePhase.IDLE,
                            manageFailureKind = null,
                            manageMessage = CUSTOM_FEED_SAVE_NOT_CONFIRMED_MESSAGE,
                            manageFailureDetails = null,
                        )
                    }
                }

                deleteRequest != null -> {
                    val deleted = feeds.none { candidate ->
                        candidate.path.equals(deleteRequest.path, ignoreCase = true)
                    }
                    if (deleted || finalRead) clearPendingCustomFeedManagement()
                    next = if (!deleted && !finalRead) {
                        next
                    } else if (deleted) {
                        next.copy(
                            managePhase = CustomFeedManagePhase.SUCCEEDED,
                            manageFailureKind = null,
                            manageMessage = null,
                            manageFailureDetails = null,
                            manageCompletionRevision = next.manageCompletionRevision + 1L,
                        )
                    } else {
                        next.copy(
                            managePhase = CustomFeedManagePhase.IDLE,
                            manageFailureKind = null,
                            manageMessage = CUSTOM_FEED_DELETE_NOT_CONFIRMED_MESSAGE,
                            manageFailureDetails = null,
                        )
                    }
                }
            }
        }

        return next
    }

    private fun failCustomFeedCreate(
        kind: CustomFeedCreateFailureKind,
        message: String,
        failureDetails: FailureDetails? = null,
    ) {
        _customFeedsUiState.value = _customFeedsUiState.value.copy(
            createPhase = CustomFeedCreatePhase.FAILED,
            createFailureKind = kind,
            createMessage = message,
            createFailureDetails = failureDetails,
        )
    }
}

internal const val CUSTOM_FEED_CREATE_NOT_CONFIRMED_MESSAGE =
    "Reddit hasn't shown this feed yet. Refresh your feeds before trying Create again."
internal const val CUSTOM_FEED_SAVE_NOT_CONFIRMED_MESSAGE =
    "Reddit hasn't shown these settings yet. Refresh your feeds before trying Save again."
internal const val CUSTOM_FEED_DELETE_NOT_CONFIRMED_MESSAGE =
    "Reddit still shows this feed. Refresh your feeds before trying Delete again."

private val CUSTOM_FEED_RECONCILIATION_DELAYS_MS =
    listOf(500L, 2_000L, 5_000L, 15_000L)

private const val CUSTOM_FEED_POSTS_KEPT_FEEDS = 4
