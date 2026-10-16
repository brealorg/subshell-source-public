package app.subshell.prototype.ui

import app.subshell.prototype.data.RedditCommentSort

internal fun resolveCommentSort(
    manualSort: RedditCommentSort?,
    useSuggestedSort: Boolean,
    suggestedSort: RedditCommentSort?,
): RedditCommentSort = manualSort
    ?: suggestedSort.takeIf { useSuggestedSort }
    ?: RedditCommentSort.BEST
