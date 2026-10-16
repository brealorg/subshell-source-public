package app.subshell.prototype.ui.app

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.RedditCommentsUiState

/**
 * The comment-thread loading runtime as SubshellApp receives it from
 * MainActivity: the comments UI state and its live, cached, focused-target,
 * refresh-preserving and load-more entry points. Property names and defaults
 * are the former SubshellApp parameters, unchanged.
 */
data class CommentLoadingEntryPoints(
    val redditCommentsState: RedditCommentsUiState = RedditCommentsUiState(),
    val onLoadRedditComments: (String, String, RedditCommentSort) -> Unit =
        { _, _, _ -> },
    val onLoadCachedRedditComments: (String, String, RedditCommentSort) -> Unit =
        { _, _, _ -> },
    val onRefreshRedditCommentsPreservingFocusedTarget: (
        String,
        String,
        RedditCommentSort,
        String,
    ) -> Unit = { _, _, _, _ -> },
    val onLoadFocusedRedditComment: (
        String,
        String,
        RedditCommentSort,
        String,
    ) -> Unit = { _, _, _, _ -> },
    // OCB581_REFUSED_LOAD_IS_NOT_AWAITED_V1: true when the load started.
    val onLoadMoreRedditComments: (
        String,
        String,
        RedditCommentSort,
        String,
    ) -> Boolean = { _, _, _, _ -> false },
)
