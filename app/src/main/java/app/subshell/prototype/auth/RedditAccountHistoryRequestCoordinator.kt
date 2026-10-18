package app.subshell.prototype.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.account.refreshReplacesAppend
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsResult
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class RedditAccountHistoryRequestCoordinator(
    private val scope: CoroutineScope,
    private val updater: AuthenticatedRedditAccountHistoryUpdater?,
    private val commentLoader: AuthenticatedRedditAccountHistoryCommentLoader? = null,
    initialState: RedditAccountHistoryUiState = RedditAccountHistoryUiState(),
) {
    private val mutableUiState = MutableStateFlow(initialState)
    private var activeJob: Job? = null
    private var latestRequestId = 0L

    val uiState: StateFlow<RedditAccountHistoryUiState> = mutableUiState.asStateFlow()

    fun update(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        operation: RepositoryLoadOperation,
    ) {
        val account = accountStableId.trim()
        val normalizedUsername = runCatching { normalizeRedditUsername(username) }.getOrNull()
        if (
            account.isEmpty() ||
            normalizedUsername == null ||
            operation !in setOf(
                RepositoryLoadOperation.REFRESH,
                RepositoryLoadOperation.APPEND,
            )
        ) {
            activeJob?.cancel()
            mutableUiState.value = RedditAccountHistoryUiState(
                failureKind = RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }

        val queryKey = runCatching {
            redditAccountHistoryQueryKey(
                accountStableId = account,
                username = normalizedUsername,
                kind = kind,
            )
        }.getOrNull()

        if (queryKey == null) {
            activeJob?.cancel()
            mutableUiState.value = RedditAccountHistoryUiState(
                failureKind = RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }

        val current = mutableUiState.value
        if (
            current.matches(account, normalizedUsername, kind) &&
            current.inProgress &&
            !refreshReplacesAppend(operation, current.inProgressOperation)
        ) {
            return
        }

        if (
            !kind.isPostHistory &&
            operation == RepositoryLoadOperation.APPEND &&
            (
                !current.matches(account, normalizedUsername, kind) ||
                    current.nextCursor.isNullOrBlank()
            )
        ) {
            return
        }

        val retained = current.takeIf {
            it.matches(account, normalizedUsername, kind)
        }
        val requestId = nextRequestId()
        activeJob?.cancel()
        mutableUiState.value = RedditAccountHistoryUiState(
            accountStableId = account,
            username = normalizedUsername,
            kind = kind,
            queryKey = queryKey,
            comments = retained?.comments.orEmpty(),
            nextCursor = retained?.nextCursor,
            fromCache = retained?.fromCache == true,
            loaded = retained?.loaded == true,
            inProgressOperation = operation,
            failureDetails = null,
            contentRevision = retained?.contentRevision ?: 0L,
        )

        if (kind.isPostHistory) {
            startPostRequest(
                requestId = requestId,
                accountStableId = account,
                username = normalizedUsername,
                kind = kind,
                queryKey = queryKey,
                operation = operation,
            )
        } else {
            startCommentRequest(
                requestId = requestId,
                accountStableId = account,
                username = normalizedUsername,
                kind = kind,
                queryKey = queryKey,
                operation = operation,
                appendCursor = retained?.nextCursor,
            )
        }
    }

    fun updateAuthorizationState(
        accountStableId: String,
        username: String,
        authorizing: Boolean,
        authorizationRequired: Boolean,
        failureKind: RedditAccountHistoryUiFailureKind? = null,
        failureRetryable: Boolean = false,
        failureDetails: FailureDetails? = null,
    ) {
        val current = mutableUiState.value
        if (!current.matchesUser(accountStableId, username)) return
        mutableUiState.value = current.copy(
            authorizing = authorizing,
            authorizationRequired = authorizationRequired,
            failureKind = failureKind,
            failureRetryable = failureRetryable,
            failureDetails = failureDetails,
        )
    }

    fun refreshAfterAuthorization(
        accountStableId: String,
        username: String,
    ) {
        val current = mutableUiState.value
        val kind = current.kind ?: return
        if (!current.matchesUser(accountStableId, username)) return
        update(
            accountStableId = accountStableId,
            username = username,
            kind = kind,
            operation = RepositoryLoadOperation.REFRESH,
        )
    }

    fun cancelPending() {
        nextRequestId()
        activeJob?.cancel()
        activeJob = null
    }

    private fun startPostRequest(
        requestId: Long,
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        queryKey: RepositoryQueryKey,
        operation: RepositoryLoadOperation,
    ) {
        val resolvedUpdater = updater
        if (resolvedUpdater == null) {
            mutableUiState.value = mutableUiState.value.copy(
                inProgressOperation = null,
                failedOperation = operation,
                failureKind = RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }

        activeJob = scope.launch {
            val result = try {
                resolvedUpdater.synchronize(
                    accountStableId = accountStableId,
                    username = username,
                    kind = kind,
                    operation = operation,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditAccountHistoryUpdateResult.Failed(
                    kind = AuthenticatedRedditAccountHistoryFailureKind.STORAGE,
                    retryable = true,
                )
            }
            if (!isCurrent(requestId, accountStableId, username, kind, queryKey)) {
                return@launch
            }

            mutableUiState.value = when (result) {
                is AuthenticatedRedditAccountHistoryUpdateResult.Applied ->
                    mutableUiState.value.copy(
                        inProgressOperation = null,
                        failedOperation = null,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        authorizing = false,
                        authorizationRequired = false,
                        contentRevision = mutableUiState.value.contentRevision + 1L,
                    )
                AuthenticatedRedditAccountHistoryUpdateResult.AuthorizationRequired ->
                    mutableUiState.value.copy(
                        inProgressOperation = null,
                        failedOperation = operation,
                        authorizing = false,
                        authorizationRequired = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                    )
                is AuthenticatedRedditAccountHistoryUpdateResult.Failed ->
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

    private fun startCommentRequest(
        requestId: Long,
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        queryKey: RepositoryQueryKey,
        operation: RepositoryLoadOperation,
        appendCursor: String?,
    ) {
        val resolvedLoader = commentLoader
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
                mutableUiState.value.comments.isEmpty()
            ) {
                val cached = try {
                    resolvedLoader.cached(
                        accountStableId = accountStableId,
                        username = username,
                        kind = kind,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AuthenticatedRedditProfileCommentsResult.Failed(
                        kind = AuthenticatedRedditProfileCommentsFailureKind.STORAGE,
                        retryable = true,
                    )
                }
                if (!isCurrent(requestId, accountStableId, username, kind, queryKey)) {
                    return@launch
                }
                if (cached is AuthenticatedRedditProfileCommentsResult.Loaded) {
                    val latest = mutableUiState.value
                    mutableUiState.value = latest.copy(
                        comments = cached.snapshot.comments,
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
                        kind = kind,
                        after = requireNotNull(appendCursor),
                    )
                } else {
                    resolvedLoader.synchronize(
                        accountStableId = accountStableId,
                        username = username,
                        kind = kind,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditProfileCommentsResult.Failed(
                    kind = AuthenticatedRedditProfileCommentsFailureKind.STORAGE,
                    retryable = true,
                )
            }
            if (!isCurrent(requestId, accountStableId, username, kind, queryKey)) {
                return@launch
            }

            val current = mutableUiState.value
            mutableUiState.value = when (result) {
                is AuthenticatedRedditProfileCommentsResult.Loaded ->
                    current.copy(
                        comments = result.snapshot.comments,
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
                AuthenticatedRedditProfileCommentsResult.AuthorizationRequired ->
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
                AuthenticatedRedditProfileCommentsResult.NotCached ->
                    current.copy(
                        inProgressOperation = null,
                        failedOperation = operation,
                        failureKind = RedditAccountHistoryUiFailureKind.STORAGE,
                        failureRetryable = true,
                        failureDetails = null,
                        contentRevision = current.contentRevision + 1L,
                    )
                is AuthenticatedRedditProfileCommentsResult.Failed ->
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
        kind: RedditAccountHistoryKind,
        queryKey: RepositoryQueryKey,
    ): Boolean =
        requestId == latestRequestId &&
            mutableUiState.value.matches(accountStableId, username, kind) &&
            mutableUiState.value.queryKey == queryKey

    private fun nextRequestId(): Long {
        check(latestRequestId < Long.MAX_VALUE) {
            "Reddit account history request identity exhausted"
        }
        latestRequestId += 1L
        return latestRequestId
    }
}

private fun AuthenticatedRedditAccountHistoryFailureKind.toUiFailureKind():
    RedditAccountHistoryUiFailureKind = when (this) {
        AuthenticatedRedditAccountHistoryFailureKind.CLIENT_CONFIGURATION ->
            RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION
        AuthenticatedRedditAccountHistoryFailureKind.SESSION ->
            RedditAccountHistoryUiFailureKind.SESSION
        AuthenticatedRedditAccountHistoryFailureKind.HTTP ->
            RedditAccountHistoryUiFailureKind.HTTP
        AuthenticatedRedditAccountHistoryFailureKind.TRANSPORT ->
            RedditAccountHistoryUiFailureKind.TRANSPORT
        AuthenticatedRedditAccountHistoryFailureKind.PROTOCOL ->
            RedditAccountHistoryUiFailureKind.PROTOCOL
        AuthenticatedRedditAccountHistoryFailureKind.STORAGE ->
            RedditAccountHistoryUiFailureKind.STORAGE
        AuthenticatedRedditAccountHistoryFailureKind.END_REACHED ->
            RedditAccountHistoryUiFailureKind.END_REACHED
    }

private fun AuthenticatedRedditProfileCommentsFailureKind.toUiFailureKind():
    RedditAccountHistoryUiFailureKind = when (this) {
        AuthenticatedRedditProfileCommentsFailureKind.SESSION ->
            RedditAccountHistoryUiFailureKind.SESSION
        AuthenticatedRedditProfileCommentsFailureKind.HTTP ->
            RedditAccountHistoryUiFailureKind.HTTP
        AuthenticatedRedditProfileCommentsFailureKind.TRANSPORT ->
            RedditAccountHistoryUiFailureKind.TRANSPORT
        AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL ->
            RedditAccountHistoryUiFailureKind.PROTOCOL
        AuthenticatedRedditProfileCommentsFailureKind.STORAGE ->
            RedditAccountHistoryUiFailureKind.STORAGE
    }
