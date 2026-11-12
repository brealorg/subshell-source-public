package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.account.RedditCommentsUiState

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
    // AUDIT298C_SMALL_NODES_LOAD_TOGETHER_V1: null = one node per round.
    val onLoadMoreRedditCommentsTogether: (
        (
            String,
            String,
            RedditCommentSort,
            List<String>,
        ) -> Boolean
    )? = null,
    // ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1: null = only from the start.
    val onLoadMoreRedditCommentsFromEnd: (
        (
            String,
            String,
            RedditCommentSort,
            String,
        ) -> Boolean
    )? = null,
)
