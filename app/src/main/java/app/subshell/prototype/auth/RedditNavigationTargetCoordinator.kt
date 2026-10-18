package app.subshell.prototype.auth

import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsFailureKind
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.model.Post
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * #215: the Reddit navigation target resolvers moved out of
 * AuthConnectViewModel — the post behind an inbox or deep-link target, and
 * the post behind a comment in the open profile or saved history. A history
 * answer is dropped when that history changed during the read.
 */
internal class RedditNavigationTargetCoordinator(
    private val scope: CoroutineScope,
    private val inboxLoader: AuthenticatedRedditInboxLoader?,
    private val commentLoader: AuthenticatedRedditAccountHistoryCommentLoader?,
    private val redditAccountHistoryCoordinator: RedditAccountHistoryRequestCoordinator,
    private val redditSavedHistoryCoordinator: RedditSavedHistoryRequestCoordinator,
    // DIAG05_TARGET_LOOKUP_FAILURE_IS_RECORDED_V1
    private val recordLookupFailure: (scopeKey: String, details: FailureDetails?) -> Unit = { _, _ -> },
) {
    fun resolveRedditNavigationTargetPost(
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
        val loader = inboxLoader
        if (loader == null) {
            onResolved(null)
            return
        }
        scope.launch {
            val result = try {
                loader.resolveTargetPost(account, targetPostId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedRedditInboxTargetResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                    details = FailureDetails(retryable = true, localKind = LocalFailureKind.UNKNOWN)
                        .withExceptionClass(error),
                )
            }
            when (result) {
                is AuthenticatedRedditInboxTargetResult.Loaded ->
                    onResolved(result.post)
                AuthenticatedRedditInboxTargetResult.AuthorizationRequired,
                AuthenticatedRedditInboxTargetResult.NotFound,
                -> onResolved(null)
                is AuthenticatedRedditInboxTargetResult.Failed -> {
                    recordLookupFailure("link_target_post", result.details)
                    onResolved(null)
                }
            }
        }
    }

    fun resolveRedditProfileCommentTarget(
        accountStableId: String,
        username: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) = resolveHistoryCommentTarget(
        accountStableId = accountStableId,
        username = username,
        postId = postId,
        onResolved = onResolved,
        historyMatches = { account, normalizedUsername ->
            redditAccountHistoryCoordinator.uiState.value.matches(
                accountStableId = account,
                username = normalizedUsername,
                kind = RedditAccountHistoryKind.COMMENTS,
            )
        },
        markHistory = { account, normalizedUsername, authorizationRequired, failureKind, failureRetryable, failureDetails ->
            redditAccountHistoryCoordinator.updateAuthorizationState(
                accountStableId = account,
                username = normalizedUsername,
                authorizing = false,
                authorizationRequired = authorizationRequired,
                failureKind = failureKind,
                failureRetryable = failureRetryable,
                failureDetails = failureDetails,
            )
        },
    )

    fun resolveRedditSavedCommentTarget(
        accountStableId: String,
        username: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) = resolveHistoryCommentTarget(
        accountStableId = accountStableId,
        username = username,
        postId = postId,
        onResolved = onResolved,
        historyMatches = { account, normalizedUsername ->
            redditSavedHistoryCoordinator.uiState.value.matches(
                accountStableId = account,
                username = normalizedUsername,
            )
        },
        markHistory = { account, normalizedUsername, authorizationRequired, failureKind, failureRetryable, failureDetails ->
            redditSavedHistoryCoordinator.updateAuthorizationState(
                accountStableId = account,
                username = normalizedUsername,
                authorizing = false,
                authorizationRequired = authorizationRequired,
                failureKind = failureKind,
                failureRetryable = failureRetryable,
                failureDetails = failureDetails,
            )
        },
    )

    /**
     * The post behind a comment in the open profile or saved history. The
     * answer is dropped when [historyMatches] no longer holds after the read;
     * an authorization need or a failure is written back through [markHistory].
     */
    private fun resolveHistoryCommentTarget(
        accountStableId: String,
        username: String,
        postId: String,
        onResolved: (Post?) -> Unit,
        historyMatches: (account: String, normalizedUsername: String) -> Boolean,
        markHistory: (
            account: String,
            normalizedUsername: String,
            authorizationRequired: Boolean,
            failureKind: RedditAccountHistoryUiFailureKind?,
            failureRetryable: Boolean,
            failureDetails: FailureDetails?,
        ) -> Unit,
    ) {
        val account = accountStableId.trim()
        val normalizedUsername = runCatching {
            normalizeRedditUsername(username)
        }.getOrNull()
        val normalizedPostId = postId
            .trim()
            .removePrefix("t3_")
            .takeIf(String::isNotEmpty)

        if (
            account.isEmpty() ||
            normalizedUsername == null ||
            normalizedPostId == null
        ) {
            onResolved(null)
            return
        }

        val loader = commentLoader
        if (
            loader == null ||
            !historyMatches(account, normalizedUsername)
        ) {
            onResolved(null)
            return
        }

        scope.launch {
            val result = try {
                loader.resolveTargetPost(
                    accountStableId = account,
                    postId = normalizedPostId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind = AuthenticatedRedditProfileCommentsFailureKind.STORAGE,
                    retryable = true,
                )
            }

            if (!historyMatches(account, normalizedUsername)) {
                onResolved(null)
                return@launch
            }

            when (result) {
                is AuthenticatedRedditProfileCommentTargetResult.Loaded ->
                    onResolved(result.post)

                AuthenticatedRedditProfileCommentTargetResult.AuthorizationRequired -> {
                    markHistory(account, normalizedUsername, true, null, false, null)
                    onResolved(null)
                }

                AuthenticatedRedditProfileCommentTargetResult.NotFound ->
                    onResolved(null)

                is AuthenticatedRedditProfileCommentTargetResult.Failed -> {
                    markHistory(
                        account,
                        normalizedUsername,
                        false,
                        result.kind.toAccountHistoryUiFailureKind(),
                        result.retryable,
                        result.details,
                    )
                    onResolved(null)
                }
            }
        }
    }
}

private fun AuthenticatedRedditProfileCommentsFailureKind
    .toAccountHistoryUiFailureKind(): RedditAccountHistoryUiFailureKind =
    when (this) {
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
