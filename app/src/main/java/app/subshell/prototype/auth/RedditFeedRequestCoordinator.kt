package app.subshell.prototype.auth

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.shouldStartRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.data.auth.authenticatedRedditFeedQueryKey
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the latest-request-wins contract for the live feed.
 *
 * Query identity alone is insufficient when navigation leaves a feed and returns to the same
 * account/feed/sort/time tuple before an older non-cooperative request finishes. The monotonic
 * request ID prevents that older result from being applied even though its query key matches.
 */
internal class RedditFeedRequestCoordinator(
    private val scope: CoroutineScope,
    private val updater: AuthenticatedRedditFeedUpdater?,
    initialState: RedditFeedUiState = RedditFeedUiState(),
    private val onStart: (RepositoryLoadOperation, AuthenticatedRedditFeedTarget) -> Unit = { _, _ -> },
    private val onApplied: (AuthenticatedRedditFeedUpdateResult.Applied) -> Unit = {},
    private val onFailed: (
        RepositoryLoadOperation,
        AuthenticatedRedditFeedUpdateResult.Failed,
    ) -> Unit = { _, _ -> },
) {
    private val mutableUiState = MutableStateFlow(initialState)
    private var activeJob: Job? = null
    private var latestRequestId: Long = 0L

    val uiState: StateFlow<RedditFeedUiState> = mutableUiState.asStateFlow()

    fun update(
        accountStableId: String,
        operation: RepositoryLoadOperation,
        target: AuthenticatedRedditFeedTarget,
    ) {
        val queryKey = authenticatedRedditFeedQueryKey(accountStableId, target)
        if (!mutableUiState.value.shouldStartRequest(accountStableId, queryKey, operation)) return

        val requestId = nextRequestId()
        activeJob?.cancel()
        onStart(operation, target)

        if (updater == null) {
            mutableUiState.value = mutableUiState.value.copy(
                accountStableId = accountStableId,
                queryKey = queryKey,
                inProgressOperation = null,
                failedOperation = operation,
                failureKind = RedditFeedUiFailureKind.CLIENT_CONFIGURATION,
                failureRetryable = false,
                failureDetails = FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.CONFIGURATION,
                ),
                lastAppliedOperation = null,
            )
            return
        }

        mutableUiState.value = mutableUiState.value.copy(
            accountStableId = accountStableId,
            queryKey = queryKey,
            inProgressOperation = operation,
            failedOperation = null,
            failureKind = null,
            failureRetryable = false,
            failureDetails = null,
            lastAppliedOperation = null,
        )
        activeJob = scope.launch {
            val result = try {
                updater.synchronize(accountStableId, operation, target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditFeedUpdateResult.Failed(
                    kind = AuthenticatedRedditFeedUpdateFailureKind.STORAGE,
                    retryable = true,
                    details = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.STORAGE,
                    ),
                )
            }
            if (!isCurrent(requestId, accountStableId, queryKey)) return@launch

            mutableUiState.value = when (result) {
                is AuthenticatedRedditFeedUpdateResult.Applied -> {
                    onApplied(result)
                    mutableUiState.value.copy(
                        inProgressOperation = null,
                        failedOperation = null,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        lastAppliedOperation = operation,
                        contentRevision = mutableUiState.value.contentRevision + 1L,
                    )
                }
                is AuthenticatedRedditFeedUpdateResult.Failed -> {
                    onFailed(operation, result)
                    mutableUiState.value.copy(
                        inProgressOperation = null,
                        failedOperation = operation,
                        failureKind = result.kind.toUiFailureKind(),
                        failureRetryable = result.retryable,
                        failureDetails = result.details,
                    )
                }
            }
        }
    }

    fun cancelPending() {
        nextRequestId()
        activeJob?.cancel()
        activeJob = null
    }

    private fun isCurrent(
        requestId: Long,
        accountStableId: String,
        queryKey: RepositoryQueryKey,
    ): Boolean = requestId == latestRequestId &&
        mutableUiState.value.matches(accountStableId, queryKey)

    private fun nextRequestId(): Long {
        check(latestRequestId < Long.MAX_VALUE) {
            "Live feed request identity exhausted"
        }
        latestRequestId += 1L
        return latestRequestId
    }
}

private fun AuthenticatedRedditFeedUpdateFailureKind.toUiFailureKind(): RedditFeedUiFailureKind =
    when (this) {
        AuthenticatedRedditFeedUpdateFailureKind.CLIENT_CONFIGURATION ->
            RedditFeedUiFailureKind.CLIENT_CONFIGURATION
        AuthenticatedRedditFeedUpdateFailureKind.SESSION ->
            RedditFeedUiFailureKind.SESSION
        AuthenticatedRedditFeedUpdateFailureKind.HTTP ->
            RedditFeedUiFailureKind.HTTP
        AuthenticatedRedditFeedUpdateFailureKind.TRANSPORT ->
            RedditFeedUiFailureKind.TRANSPORT
        AuthenticatedRedditFeedUpdateFailureKind.PROTOCOL ->
            RedditFeedUiFailureKind.PROTOCOL
        AuthenticatedRedditFeedUpdateFailureKind.STORAGE ->
            RedditFeedUiFailureKind.STORAGE
        AuthenticatedRedditFeedUpdateFailureKind.END_REACHED ->
            RedditFeedUiFailureKind.END_REACHED
    }
