package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPendingMutation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.RedditBlockedAccountsUiState
import app.subshell.prototype.data.account.normalizedRedditContentReportRequest
import app.subshell.prototype.data.account.normalizedRedditPostVisibilityRequest
import app.subshell.prototype.data.account.normalizedRedditUserBlockRequest
import app.subshell.prototype.data.account.toUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedBlockedAccountsResult
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// SAFETY01A2_CONTENT_SAFETY_COORDINATOR_OWNER_BOUND_CALLBACK_V1
// USERACTION01A1_COORDINATED_BLOCK_UNBLOCK_V1
internal class ContentSafetyCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedContentSafetyWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val activeAccountUsername: () -> String? = { null },
) {
    private val mutationMutex = Mutex()
    private val blockedAccountsMutex = Mutex()
    private val mutableUiState = MutableStateFlow(ContentSafetyUiState())
    private var pendingMutation: ContentSafetyPendingMutation? = null
    private var pendingRedditUsername: String? = null

    // OCB414_BLOCKED_LIST_CAN_ASK_FOR_AUTHORIZATION_V1 (OCB-414): a blocked
    // list read that needs authorization offered only Retry, which failed the
    // same way. It can now start the authorization itself; after it the list
    // is read again. Kept apart from the hide/report/block pendingMutation.
    private var blockedListAuthorizationAccount: String? = null

    suspend fun beginBlockedAccountsAuthorization(accountStableId: String): AuthBrowserLaunchResult {
        val account = accountStableId.trim()
        val blocked = mutableUiState.value.blockedAccounts
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (
            !blocked.matches(account) ||
            blocked.failureKind != ContentSafetyFailureKind.AUTHORIZATION ||
            blocked.authorizing ||
            pendingMutation != null ||
            configuredCapability == null ||
            configuredClientMode == null ||
            activeAccountStableId()?.trim() != account
        ) {
            return AuthBrowserLaunchResult.NotStarted
        }
        blockedListAuthorizationAccount = account
        setBlockedAuthorizing(true)
        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = configuredCapability.beginAuthorization(account),
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            endBlockedListAuthorization()
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun blockedAccountsAuthorizationBrowserLaunchFailed() {
        if (blockedListAuthorizationAccount == null) return
        capability?.cancelPendingAuthorization()
        endBlockedListAuthorization()
    }

    private fun setBlockedAuthorizing(authorizing: Boolean) {
        mutableUiState.value = mutableUiState.value.copy(
            blockedAccounts = mutableUiState.value.blockedAccounts.copy(authorizing = authorizing),
        )
    }

    // The list keeps its "authorization required" failure, so the action is
    // offered again.
    private fun endBlockedListAuthorization() {
        blockedListAuthorizationAccount = null
        setBlockedAuthorizing(false)
    }

    val uiState: StateFlow<ContentSafetyUiState> = mutableUiState.asStateFlow()

    suspend fun setPostHidden(request: RedditPostVisibilityRequest) {
        val normalized = try {
            request.normalizedRedditPostVisibilityRequest()
        } catch (_: IllegalArgumentException) {
            pendingMutation = null
            mutableUiState.value = ContentSafetyUiState(
                phase = ContentSafetyPhase.FAILED,
                failureKind = ContentSafetyFailureKind.VALIDATION,
                blockedAccounts = mutableUiState.value.blockedAccounts,
                completionRevision = mutableUiState.value.completionRevision,
            )
            return
        }
        capturePendingRedditIdentity()
        execute(ContentSafetyPendingMutation.PostVisibility(normalized))
    }

    suspend fun report(request: RedditContentReportRequest) {
        val normalized = try {
            request.normalizedRedditContentReportRequest()
        } catch (_: IllegalArgumentException) {
            pendingMutation = null
            mutableUiState.value = ContentSafetyUiState(
                phase = ContentSafetyPhase.FAILED,
                failureKind = ContentSafetyFailureKind.VALIDATION,
                blockedAccounts = mutableUiState.value.blockedAccounts,
                completionRevision = mutableUiState.value.completionRevision,
            )
            return
        }
        capturePendingRedditIdentity()
        execute(ContentSafetyPendingMutation.Report(normalized))
    }

    // USERACTION01A1_BLOCK_UNBLOCK_COORDINATOR_V1
    suspend fun setUserBlocked(request: RedditUserBlockRequest) {
        val normalized = try {
            request.normalizedRedditUserBlockRequest()
        } catch (_: IllegalArgumentException) {
            pendingMutation = null
            mutableUiState.value = ContentSafetyUiState(
                phase = ContentSafetyPhase.FAILED,
                operation = ContentSafetyOperation.SET_USER_BLOCKED,
                username = request.username.trim().takeIf(String::isNotEmpty),
                blocked = request.blocked,
                failureKind = ContentSafetyFailureKind.VALIDATION,
                blockedAccounts = mutableUiState.value.blockedAccounts,
                completionRevision = mutableUiState.value.completionRevision,
            )
            return
        }
        capturePendingRedditIdentity()
        execute(ContentSafetyPendingMutation.UserBlock(normalized))
    }

    // USERACTION01A2_BLOCKED_ACCOUNTS_COORDINATOR_V1
    suspend fun refreshBlockedAccounts(accountStableId: String) {
        loadBlockedAccounts(
            accountStableId = accountStableId,
            append = false,
        )
    }

    suspend fun appendBlockedAccounts(accountStableId: String) {
        loadBlockedAccounts(
            accountStableId = accountStableId,
            append = true,
        )
    }

    private suspend fun loadBlockedAccounts(
        accountStableId: String,
        append: Boolean,
    ) {
        blockedAccountsMutex.withLock {
            val normalizedAccountId = accountStableId.trim()
            if (normalizedAccountId.isEmpty()) return@withLock
            if (activeAccountStableId()?.trim() != normalizedAccountId) return@withLock
            val current = mutableUiState.value.blockedAccounts
            val cursor = if (append && current.matches(normalizedAccountId)) {
                current.nextCursor ?: return@withLock
            } else {
                null
            }
            val operation = if (append) {
                RepositoryLoadOperation.APPEND
            } else {
                RepositoryLoadOperation.REFRESH
            }
            mutableUiState.value = mutableUiState.value.copy(
                blockedAccounts = RedditBlockedAccountsUiState(
                    accountStableId = normalizedAccountId,
                    // ISSUE118_BLOCKED_LIST_SURVIVES_ACCOUNT_SWITCH_V1: a
                    // refresh keeps the account's list until the answer
                    // replaces it ("No blocked accounts" flashed before).
                    items = if (current.matches(normalizedAccountId)) {
                        current.items
                    } else {
                        emptyList()
                    },
                    loaded = current.loaded && current.matches(normalizedAccountId),
                    inProgress = true,
                    inProgressOperation = operation,
                    nextCursor = if (append) current.nextCursor else null,
                ),
            )
            val configuredWriter = writer
            if (configuredWriter == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    blockedAccounts = mutableUiState.value.blockedAccounts.copy(
                        inProgress = false,
                        inProgressOperation = null,
                        failureKind = ContentSafetyFailureKind.CONFIGURATION,
                    ),
                )
                return@withLock
            }
            val started = mutableUiState.value.blockedAccounts
            val result = try {
                configuredWriter.loadBlockedAccounts(
                    accountStableId = normalizedAccountId,
                    after = cursor,
                )
            } catch (cancelled: CancellationException) {
                settleAbandonedBlockedAccountsLoad(started)
                throw cancelled
            }
            // ISSUE118_BLOCKED_LIST_SURVIVES_ACCOUNT_SWITCH_V1: an answer for
            // an account that is no longer active is dropped, but the load
            // must not stay "in progress" (the entry refresh waited on it for
            // good and the account came back to an emptied list).
            if (activeAccountStableId()?.trim() != normalizedAccountId) {
                settleAbandonedBlockedAccountsLoad(started)
                return@withLock
            }
            when (result) {
                is AuthenticatedBlockedAccountsResult.Loaded -> {
                    val previousItems = if (
                        append && mutableUiState.value.blockedAccounts.matches(normalizedAccountId)
                    ) {
                        mutableUiState.value.blockedAccounts.items
                    } else {
                        emptyList()
                    }
                    mutableUiState.value = mutableUiState.value.copy(
                        blockedAccounts = RedditBlockedAccountsUiState(
                            accountStableId = normalizedAccountId,
                            items = (previousItems + result.items)
                                .distinctBy { it.username.lowercase() },
                            loaded = true,
                            inProgress = false,
                            nextCursor = result.nextCursor,
                        ),
                    )
                }

                AuthenticatedBlockedAccountsResult.AuthorizationRequired ->
                    mutableUiState.value = mutableUiState.value.copy(
                        blockedAccounts = mutableUiState.value.blockedAccounts.copy(
                            inProgress = false,
                            inProgressOperation = null,
                            failureKind = ContentSafetyFailureKind.AUTHORIZATION,
                            retryable = true,
                        ),
                    )

                is AuthenticatedBlockedAccountsResult.Failed ->
                    mutableUiState.value = mutableUiState.value.copy(
                        blockedAccounts = mutableUiState.value.blockedAccounts.copy(
                            inProgress = false,
                            inProgressOperation = null,
                            failureKind = result.kind,
                            retryable = result.retryable,
                            statusCode = result.statusCode,
                            failureDetails = result.failureDetails,
                        ),
                    )
            }
        }
    }

    private fun settleAbandonedBlockedAccountsLoad(started: RedditBlockedAccountsUiState) {
        val current = mutableUiState.value.blockedAccounts
        if (current.accountStableId != started.accountStableId || !current.inProgress) return
        mutableUiState.value = mutableUiState.value.copy(
            blockedAccounts = started.copy(inProgress = false, inProgressOperation = null),
        )
    }

    suspend fun retry() {
        val mutation = pendingMutation
            ?.takeIf {
                mutableUiState.value.phase == ContentSafetyPhase.FAILED &&
                    mutableUiState.value.retryable
            }
            ?: return
        execute(mutation)
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val mutation = pendingMutation
            ?.takeIf {
                mutableUiState.value.phase ==
                    ContentSafetyPhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            pendingMutation = null
            mutableUiState.value = failure(
                mutation = mutation,
                kind = ContentSafetyFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (
            activeAccountStableId()?.trim() !=
            mutation.accountStableId
        ) {
            pendingMutation = null
            mutableUiState.value = failure(
                mutation = mutation,
                kind = ContentSafetyFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = mutation.toUiState(
            phase = ContentSafetyPhase.AUTHORIZING,
            previous = mutableUiState.value,
        )
        return try {
            val authorizationUrl =
                configuredCapability.beginAuthorization(
                    mutation.accountStableId,
                )
            mutableUiState.value = mutation.toUiState(
                phase = ContentSafetyPhase.AWAITING_CALLBACK,
                previous = mutableUiState.value,
            )
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            pendingMutation = mutation
            mutableUiState.value = failure(
                mutation = mutation,
                kind = ContentSafetyFailureKind.AUTHORIZATION,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val mutation = pendingMutation ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            mutation = mutation,
            kind = ContentSafetyFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun cancelPendingAuthorization() {
        val mutation = pendingMutation
        capability?.cancelPendingAuthorization()
        if (mutation == null) {
            resetUiState()
            endBlockedListAuthorization()
        } else {
            mutableUiState.value = failure(
                mutation = mutation,
                kind = ContentSafetyFailureKind.CALLBACK,
                retryable = true,
            )
        }
    }

    suspend fun handleCallbackResultTokenIfOwned(
        resultToken: String,
    ): Boolean {
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }

        val configuredCapability = capability
        val mutation = pendingMutation
        if (configuredCapability == null) {
            pendingMutation = null
            mutableUiState.value = mutation?.let {
                failure(it, ContentSafetyFailureKind.CONFIGURATION)
            } ?: ContentSafetyUiState(
                blockedAccounts = mutableUiState.value.blockedAccounts,
                completionRevision = mutableUiState.value.completionRevision,
            )
            return true
        }

        if (!configuredCapability.hasStoredCallback()) {
            configuredCapability.cancelPendingAuthorization()
            if (mutation == null) endBlockedListAuthorization()
            mutableUiState.value = mutation?.let {
                failure(
                    mutation = it,
                    kind = ContentSafetyFailureKind.CALLBACK,
                    retryable = true,
                )
            } ?: ContentSafetyUiState(
                blockedAccounts = mutableUiState.value.blockedAccounts,
                completionRevision = mutableUiState.value.completionRevision,
            )
            return true
        }

        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        val configuredCapability = capability ?: return false
        return when {
            configuredCapability.hasStoredCallback() -> {
                completeAuthorization()
                true
            }

            configuredCapability.hasPendingAuthorization() -> true
            else -> false
        }
    }

    // DEAD04 (OCB-415): only this coordinator resets its state; the unused
    // ViewModel reset/cancel forwarders are gone.
    private fun resetUiState() {
        pendingRedditUsername = null
        mutableUiState.value = ContentSafetyUiState(
            blockedAccounts = mutableUiState.value.blockedAccounts,
            completionRevision = mutableUiState.value.completionRevision,
        )
    }

    private suspend fun execute(
        mutation: ContentSafetyPendingMutation,
        ownerAuthorizedContinuation: Boolean = false,
    ) {
        mutationMutex.withLock {
            if (
                !ownerAuthorizedContinuation &&
                activeAccountStableId()?.trim() !=
                mutation.accountStableId
            ) {
                pendingMutation = null
                mutableUiState.value = failure(
                    mutation = mutation,
                    kind = ContentSafetyFailureKind.ACCOUNT_CHANGED,
                )
                return@withLock
            }

            val configuredWriter = writer
            if (configuredWriter == null) {
                pendingMutation = null
                mutableUiState.value = failure(
                    mutation = mutation,
                    kind = ContentSafetyFailureKind.CONFIGURATION,
                )
                return@withLock
            }

            pendingMutation = mutation
            mutableUiState.value = mutation.toUiState(
                phase = ContentSafetyPhase.SENDING,
                previous = mutableUiState.value,
            )

            val result = try {
                when (mutation) {
                    is ContentSafetyPendingMutation.PostVisibility ->
                        configuredWriter.setPostHidden(mutation.request)

                    is ContentSafetyPendingMutation.Report ->
                        configuredWriter.report(mutation.request)

                    is ContentSafetyPendingMutation.UserBlock ->
                        configuredWriter.setUserBlocked(mutation.request)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedContentSafetyResult.Failed(
                    kind = ContentSafetyFailureKind.TRANSPORT,
                    retryable = false,
                    failureDetails = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }

            when (result) {
                is AuthenticatedContentSafetyResult.Updated -> {
                    pendingMutation = null
                    pendingRedditUsername = null
                    mutableUiState.value = mutation.toUiState(
                        phase = ContentSafetyPhase.SUCCEEDED,
                        previous = mutableUiState.value,
                        hiddenHistoryReconciliation =
                            result.hiddenHistoryReconciliation,
                    ).copy(
                        thingFullname = result.thingFullname ?: mutation.thingFullname,
                        username = result.username ?: mutation.username,
                        hidden = result.hidden,
                        blocked = result.blocked,
                        completionRevision =
                            mutableUiState.value.completionRevision + 1L,
                    )
                }

                AuthenticatedContentSafetyResult.AuthorizationRequired -> {
                    pendingMutation = mutation
                    mutableUiState.value = mutation.toUiState(
                        phase = ContentSafetyPhase.AUTHORIZATION_REQUIRED,
                        previous = mutableUiState.value,
                    )
                }

                is AuthenticatedContentSafetyResult.Failed -> {
                    pendingMutation = mutation.takeIf { result.retryable }
                    if (pendingMutation == null) pendingRedditUsername = null
                    mutableUiState.value = failure(
                        mutation = mutation,
                        kind = result.kind,
                        retryable = result.retryable,
                        statusCode = result.statusCode,
                        failureDetails = result.failureDetails,
                    )
                }
            }
        }
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val mutation = pendingMutation
        mutation?.let {
            mutableUiState.value = it.toUiState(
                phase = ContentSafetyPhase.AUTHORIZING,
                previous = mutableUiState.value,
            )
        }

        when (
            val result =
                configuredCapability.completePendingAuthorization()
        ) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (mutation == null) {
                    pendingMutation = null
                    val blockedListAccount = blockedListAuthorizationAccount
                    resetUiState()
                    endBlockedListAuthorization()
                    if (
                        blockedListAccount != null &&
                        result.accountStableId == blockedListAccount &&
                        activeAccountStableId()?.trim() == blockedListAccount
                    ) {
                        refreshBlockedAccounts(blockedListAccount)
                    }
                    return
                }

                // SAFETY01A32_SAME_REDDIT_IDENTITY_STABLE_ID_REBIND_V1
                val expectedUsername = pendingRedditUsername
                val authorizedUsername = normalizeSafetyUsername(result.username)
                val currentUsername = normalizeSafetyUsername(activeAccountUsername())
                if (
                    authorizedUsername == null ||
                    (expectedUsername != null && authorizedUsername != expectedUsername) ||
                    (currentUsername != null && currentUsername != authorizedUsername)
                ) {
                    pendingMutation = null
                    pendingRedditUsername = null
                    mutableUiState.value = failure(
                        mutation = mutation,
                        kind = ContentSafetyFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }

                val resumedMutation =
                    if (result.accountStableId == mutation.accountStableId) {
                        mutation
                    } else if (
                        expectedUsername != null &&
                        authorizedUsername == expectedUsername
                    ) {
                        mutation.withAccountStableId(result.accountStableId)
                    } else {
                        null
                    }

                if (resumedMutation == null) {
                    pendingMutation = null
                    pendingRedditUsername = null
                    mutableUiState.value = failure(
                        mutation = mutation,
                        kind = ContentSafetyFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }

                val currentAccountStableId = activeAccountStableId()?.trim()
                if (
                    currentAccountStableId != null &&
                    currentAccountStableId.isNotEmpty() &&
                    currentAccountStableId != resumedMutation.accountStableId
                ) {
                    pendingMutation = null
                    pendingRedditUsername = null
                    mutableUiState.value = failure(
                        mutation = mutation,
                        kind = ContentSafetyFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }

                pendingMutation = resumedMutation
                mutableUiState.value = resumedMutation.toUiState(
                    phase = ContentSafetyPhase.IDLE,
                    previous = mutableUiState.value,
                )
                execute(
                    mutation = resumedMutation,
                    ownerAuthorizedContinuation = true,
                )
            }

            is AuthCommentWriteCapabilityResult.Failed -> {
                if (mutation == null) {
                    pendingMutation = null
                    resetUiState()
                    endBlockedListAuthorization()
                    return
                }
                pendingMutation = mutation.takeIf { result.retryable }
                mutableUiState.value = failure(
                    mutation = mutation,
                    kind = result.kind.toContentSafetyFailureKind(),
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun capturePendingRedditIdentity() {
        pendingRedditUsername = normalizeSafetyUsername(activeAccountUsername())
    }

    private fun normalizeSafetyUsername(value: String?): String? =
        value
            ?.trim()
            ?.removePrefix("u/")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.lowercase()

    private fun ContentSafetyPendingMutation.withAccountStableId(
        accountStableId: String,
    ): ContentSafetyPendingMutation =
        when (this) {
            is ContentSafetyPendingMutation.PostVisibility ->
                ContentSafetyPendingMutation.PostVisibility(
                    request.copy(accountStableId = accountStableId),
                )

            is ContentSafetyPendingMutation.Report ->
                ContentSafetyPendingMutation.Report(
                    request.copy(accountStableId = accountStableId),
                )

            is ContentSafetyPendingMutation.UserBlock ->
                ContentSafetyPendingMutation.UserBlock(
                    request.copy(accountStableId = accountStableId),
                )
        }

    private fun failure(
        mutation: ContentSafetyPendingMutation,
        kind: ContentSafetyFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): ContentSafetyUiState = mutation.toUiState(
        phase = ContentSafetyPhase.FAILED,
        previous = mutableUiState.value,
        failureKind = kind,
        retryable = retryable,
        statusCode = statusCode,
        failureDetails = failureDetails,
        hiddenHistoryReconciliation =
            HiddenHistoryReconciliation.NOT_APPLICABLE,
    )
}

private fun AuthCommentWriteCapabilityFailureKind
    .toContentSafetyFailureKind(): ContentSafetyFailureKind =
    when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            ContentSafetyFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            ContentSafetyFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> ContentSafetyFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            ContentSafetyFailureKind.SESSION
    }
