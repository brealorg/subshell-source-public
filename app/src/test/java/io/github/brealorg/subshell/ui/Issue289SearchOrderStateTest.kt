package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditSearchOrder
import io.github.brealorg.subshell.data.RedditSearchSort
import org.junit.Assert.assertEquals
import org.junit.Test

class Issue289SearchOrderStateTest {
    private fun newState() = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

    @Test
    fun aNewStateSearchesByRelevance() {
        assertEquals(RedditSearchOrder.DEFAULT, newState().searchOrder)
    }

    @Test
    fun anOrderThatIgnoresTheRangeIsStoredWithoutOne() {
        val state = newState()

        state.updateSearchOrder(RedditSearchOrder(RedditSearchSort.NEW, RedditListingTimePeriod.WEEK))

        assertEquals(RedditSearchOrder(RedditSearchSort.NEW, RedditListingTimePeriod.ALL), state.searchOrder)
    }
}
