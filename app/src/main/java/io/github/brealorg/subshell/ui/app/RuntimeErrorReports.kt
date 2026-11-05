package io.github.brealorg.subshell.ui.app

import androidx.compose.runtime.Composable
import io.github.brealorg.subshell.data.account.ContentSafetyPhase
import io.github.brealorg.subshell.data.account.ContentSafetyUiState
import io.github.brealorg.subshell.data.account.PostFollowPhase
import io.github.brealorg.subshell.data.account.PostSavePhase
import io.github.brealorg.subshell.data.account.PostSaveUiState
import io.github.brealorg.subshell.data.account.PostVotePhase
import io.github.brealorg.subshell.data.account.PostVoteUiState
import io.github.brealorg.subshell.ui.PostFollowRuntimeEntryPoints
import io.github.brealorg.subshell.ui.error.ReportViewportError
import io.github.brealorg.subshell.ui.error.contentSafetyErrorPresentation
import io.github.brealorg.subshell.ui.error.postFollowErrorPresentation
import io.github.brealorg.subshell.ui.error.postSaveErrorPresentation
import io.github.brealorg.subshell.ui.error.postVoteErrorPresentation

/**
 * #214: the vote, save, follow and content-safety failures reported into the
 * error tray, moved verbatim out of SubshellApp. Called inside the provider of
 * LocalViewportErrorHostState.
 */
@Composable
internal fun RuntimeErrorReports(
    postVoteState: PostVoteUiState,
    onRetryPostVote: () -> Unit,
    postSaveState: PostSaveUiState,
    onRetryPostSave: () -> Unit,
    postFollowRuntime: PostFollowRuntimeEntryPoints,
    contentSafetyState: ContentSafetyUiState,
    onRetryContentSafety: () -> Unit,
) {
    ReportViewportError(
        scopeKey = "reddit-post-vote:${postVoteState.postFullname.orEmpty()}",
        presentation = if (postVoteState.phase == PostVotePhase.FAILED) {
            postVoteErrorPresentation(postVoteState)
        } else {
            null
        },
        failureDetails = postVoteState.failureDetails,
        onRetry = onRetryPostVote.takeIf { postVoteState.retryable },
    )
    ReportViewportError(
        scopeKey = "reddit-post-save:${postSaveState.postFullname.orEmpty()}",
        presentation = if (postSaveState.phase == PostSavePhase.FAILED) {
            postSaveErrorPresentation(postSaveState)
        } else {
            null
        },
        failureDetails = postSaveState.failureDetails,
        onRetry = onRetryPostSave.takeIf { postSaveState.retryable },
    )
    ReportViewportError(
        scopeKey =
            "reddit-post-follow:" +
                postFollowRuntime.state
                    .postFullname
                    .orEmpty(),
        presentation =
            if (
                postFollowRuntime.state.phase ==
                PostFollowPhase.FAILED
            ) {
                postFollowErrorPresentation(
                    postFollowRuntime.state,
                )
            } else {
                null
            },
        failureDetails =
            postFollowRuntime.state.failureDetails,
        onRetry =
            postFollowRuntime.onRetry.takeIf {
                postFollowRuntime.state.retryable
            },
    )
    ReportViewportError(
        scopeKey = "reddit-content-safety:${contentSafetyState.thingFullname.orEmpty()}:" +
            contentSafetyState.operation?.name.orEmpty(),
        presentation = if (contentSafetyState.phase == ContentSafetyPhase.FAILED) {
            contentSafetyErrorPresentation(contentSafetyState)
        } else {
            null
        },
        failureDetails = contentSafetyState.failureDetails,
        onRetry = onRetryContentSafety.takeIf { contentSafetyState.retryable },
    )
}
