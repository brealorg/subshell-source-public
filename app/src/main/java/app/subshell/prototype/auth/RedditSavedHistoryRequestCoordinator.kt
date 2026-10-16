package app.subshell.prototype.auth

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.account.refreshReplacesAppend
import app.subshell.prototype.data.account.RedditSavedHistoryUiItem
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryResult
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import app.subshell.prototype.data.network.reddit.RedditSavedHistoryItem
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class RedditSavedHistoryRequestCoordinator(
    private val scope: CoroutineScope,
    private val loader: AuthenticatedRedditSavedHistoryLoader?,
    initialState: RedditSavedHistoryUiState = RedditSavedHistoryUiState(),
) {
    private val mutableUiState = MutableStateFlow(initialState)
    private var activeJob: Job? = null
    private var latestRequestId = 0L

    val uiState: StateFlow<RedditSavedHistoryUiState> = mutableUiState.asStateFlow()

    fun update(
        accountStableId: String,
        username: String,
        operation: RepositoryLoadOperation,
    ) {
        val account = accountStableId.trim()
        val normalizedUsername = runCatching {
            normalizeRedditUsername(username)
        }.getOrNull()
        if (
            account.isEmpty() ||
            normalizedUsername == null ||
            operation !in setOf(
                RepositoryLoadOperation.REFRESH,
                RepositoryLoadOperation.APPEND,
            )
        ) {
            activeJob?.cancel()
            mutableUiState.value = RedditSavedHistoryUiState(
                failureKind = RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }

        val queryKey = runCatching {
            redditMixedSavedHistoryQueryKey(
                accountStableId = account,
                username = normalizedUsername,
            )
        }.getOrNull()
        if (queryKey == null) {
            activeJob?.cancel()
            mutableUiState.value = RedditSavedHistoryUiState(
                failureKind = RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }

        val current = mutableUiState.value
        if (
            current.matches(account, normalizedUsername) &&
            current.inProgress &&
            !refreshReplacesAppend(operation, current.inProgressOperation)
        ) {
            return
        }
        if (
            operation == RepositoryLoadOperation.APPEND &&
            (
                !current.matches(account, normalizedUsername) ||
                    current.nextCursor.isNullOrBlank()
            )
        ) {
            return
        }

        val retained = current.takeIf {
            it.matches(account, normalizedUsername)
        }
        val requestId = nextRequestId()
        activeJob?.cancel()
        mutableUiState.value = RedditSavedHistoryUiState(
            accountStableId = account,
            username = normalizedUsername,
            queryKey = queryKey,
            items = retained?.items.orEmpty(),
            nextCursor = retained?.nextCursor,
            fromCache = retained?.fromCache == true,
            loaded = retained?.loaded == true,
            inProgressOperation = operation,
            contentRevision = retained?.contentRevision ?: 0L,
        )

        startRequest(
            requestId = requestId,
            accountStableId = account,
            username = normalizedUsername,
            queryKey = queryKey,
            operation = operation,
            appendCursor = retained?.nextCursor,
        )
    }

    fun updateAuthorizationState(
        accountStableId: String,
        username: String,
        authorizing: Boolean,
        authorizationRequired: Boolean,
        failureKind: RedditAccountHistoryUiFailureKind? = null,
        failureRetryable: Boolean = false,
    ) {
        val current = mutableUiState.value
        if (!current.matches(accountStableId, username)) return
        mutableUiState.value = current.copy(
            authorizing = authorizing,
            authorizationRequired = authorizationRequired,
            failureKind = failureKind,
            failureRetryable = failureRetryable,
            failureDetails = null,
        )
    }

    fun refreshAfterAuthorization(
        accountStableId: String,
        username: String,
    ) {
        if (!mutableUiState.value.matches(accountStableId, username)) return
        update(
            accountStableId = accountStableId,
            username = username,
            operation = RepositoryLoadOperation.REFRESH,
        )
    }

    fun cancelPending() {
        nextRequestId()
        activeJob?.cancel()
        activeJob = null
    }

    private fun startRequest(
        requestId: Long,
        accountStableId: String,
        username: String,
        queryKey: RepositoryQueryKey,
        operation: RepositoryLoadOperation,
        appendCursor: String?,
    ) {
        val resolvedLoader = loader
        if (resolvedLoader == null) {
            mutableUiState.value = mutableUiState.value.copy(
                inProgressOperation = null,
                failedOperation = operation,
                failureKind = RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }

        activeJob = scope.launch {
            if (
                operation == RepositoryLoadOperation.REFRESH &&
                mutableUiState.value.items.isEmpty()
            ) {
                val cached = try {
                    resolvedLoader.cached(
                        accountStableId = accountStableId,
                        username = username,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AuthenticatedRedditSavedHistoryResult.Failed(
                        kind = AuthenticatedRedditSavedHistoryFailureKind.STORAGE,
                        retryable = true,
                    )
                }
                if (!isCurrent(requestId, accountStableId, username, queryKey)) {
                    return@launch
                }
                if (cached is AuthenticatedRedditSavedHistoryResult.Loaded) {
                    val latest = mutableUiState.value
                    mutableUiState.value = latest.copy(
                        items = cached.snapshot.items.map { item -> item.toUiItem() },
                        nextCursor = cached.snapshot.nextCursor,
                        fromCache = true,
                        loaded = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        contentRevision = latest.contentRevision + 1L,
                    )
                }
            }

            val result = try {
                if (operation == RepositoryLoadOperation.APPEND) {
                    resolvedLoader.append(
                        accountStableId = accountStableId,
                        username = username,
                        after = requireNotNull(appendCursor),
                    )
                } else {
                    resolvedLoader.synchronize(
                        accountStableId = accountStableId,
                        username = username,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditSavedHistoryResult.Failed(
                    kind = AuthenticatedRedditSavedHistoryFailureKind.STORAGE,
                    retryable = true,
                )
            }
            if (!isCurrent(requestId, accountStableId, username, queryKey)) {
                return@launch
            }

            val current = mutableUiState.value
            mutableUiState.value = when (result) {
                is AuthenticatedRedditSavedHistoryResult.Loaded ->
                    current.copy(
                        items = result.snapshot.items.map { item -> item.toUiItem() },
                        nextCursor = result.snapshot.nextCursor,
                        fromCache = result.fromCache,
                        loaded = true,
                        inProgressOperation = null,
                        failedOperation = null,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        authorizing = false,
                        authorizationRequired = false,
                        contentRevision = current.contentRevision + 1L,
                    )
                AuthenticatedRedditSavedHistoryResult.AuthorizationRequired ->
                    current.copy(
                        inProgressOperation = null,
                        failedOperation = operation,
                        authorizing = false,
                        authorizationRequired = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        contentRevision = current.contentRevision + 1L,
                    )
                AuthenticatedRedditSavedHistoryResult.NotCached ->
                    current.copy(
                        inProgressOperation = null,
                        failedOperation = operation,
                        failureKind = RedditAccountHistoryUiFailureKind.STORAGE,
                        failureRetryable = true,
                        contentRevision = current.contentRevision + 1L,
                    )
                is AuthenticatedRedditSavedHistoryResult.Failed ->
                    current.copy(
                        inProgressOperation = null,
                        failedOperation = operation,
                        failureKind = result.kind.toUiFailureKind(),
                        failureRetryable = result.retryable,
                        failureDetails = result.details,
                        contentRevision = current.contentRevision + 1L,
                    )
            }
        }
    }

    private fun isCurrent(
        requestId: Long,
        accountStableId: String,
        username: String,
        queryKey: RepositoryQueryKey,
    ): Boolean =
        requestId == latestRequestId &&
            mutableUiState.value.matches(accountStableId, username) &&
            mutableUiState.value.queryKey == queryKey

    private fun nextRequestId(): Long {
        check(latestRequestId < Long.MAX_VALUE) {
            "Reddit saved history request identity exhausted"
        }
        latestRequestId += 1L
        return latestRequestId
    }
}

private fun AuthenticatedRedditSavedHistoryFailureKind.toUiFailureKind():
    RedditAccountHistoryUiFailureKind = when (this) {
        AuthenticatedRedditSavedHistoryFailureKind.SESSION ->
            RedditAccountHistoryUiFailureKind.SESSION
        AuthenticatedRedditSavedHistoryFailureKind.HTTP ->
            RedditAccountHistoryUiFailureKind.HTTP
        AuthenticatedRedditSavedHistoryFailureKind.TRANSPORT ->
            RedditAccountHistoryUiFailureKind.TRANSPORT
        AuthenticatedRedditSavedHistoryFailureKind.PROTOCOL ->
            RedditAccountHistoryUiFailureKind.PROTOCOL
        AuthenticatedRedditSavedHistoryFailureKind.STORAGE ->
            RedditAccountHistoryUiFailureKind.STORAGE
    }

private fun RedditSavedHistoryItem.toUiItem(): RedditSavedHistoryUiItem =
    when (this) {
        is RedditSavedHistoryItem.PostItem ->
            RedditSavedHistoryUiItem.PostItem(
                fullname = fullname,
                post = post,
            )
        is RedditSavedHistoryItem.CommentItem ->
            RedditSavedHistoryUiItem.CommentItem(
                fullname = fullname,
                comment = comment,
            )
    }
