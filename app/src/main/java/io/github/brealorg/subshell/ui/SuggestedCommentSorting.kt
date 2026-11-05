package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.RedditCommentSort

internal fun resolveCommentSort(
    manualSort: RedditCommentSort?,
    useSuggestedSort: Boolean,
    suggestedSort: RedditCommentSort?,
): RedditCommentSort = manualSort
    ?: suggestedSort.takeIf { useSuggestedSort }
    ?: RedditCommentSort.BEST
