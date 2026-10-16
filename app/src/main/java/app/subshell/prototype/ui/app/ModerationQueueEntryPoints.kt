package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.model.Post

/**
 * The moderation-queue runtime as SubshellApp receives it from MainActivity:
 * the moderation and action UI states, the mod-queue and removal-reason
 * entry points and their authorizations. Property names and defaults are
 * the former SubshellApp parameters, unchanged.
 */
data class ModerationQueueEntryPoints(
    val redditModerationState: RedditModerationUiState =
        RedditModerationUiState(),
    val onLoadRedditModQueue: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val onAppendRedditModQueue: (String, String) -> Unit = { _, _ -> },
    val onResolveRedditModerationTarget: (String, String, (Post?) -> Unit) -> Unit =
        { _, _, onResolved -> onResolved(null) },
    val moderationActionState: RedditModeratorActionUiState =
        RedditModeratorActionUiState(),
    val onModerateRedditQueueItem: (RedditModeratorActionRequest) -> Unit = {},
    val onRetryRedditModerationAction: () -> Unit = {},
    val onAuthorizeRedditModerationAction: () -> Unit = {},
    val removalReasonState: RedditRemovalReasonUiState = RedditRemovalReasonUiState(),
    val onLoadRedditRemovalReasons: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val onAuthorizeRedditRemovalReasons: () -> Unit = {},
)
