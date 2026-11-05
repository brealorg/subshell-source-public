package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditProfilePostSort
import io.github.brealorg.subshell.data.RedditProfilePostsOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class Issue289ProfilePostsOrderStateTest {
    private fun newState() = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

    @Test
    fun profilesStartWithNew() {
        assertEquals(RedditProfilePostsOrder.DEFAULT, newState().profilePostsOrder)
    }

    @Test
    fun anOrderThatIgnoresTheRangeIsStoredWithoutOne() {
        val state = newState()

        state.updateProfilePostsOrder(RedditProfilePostsOrder(RedditProfilePostSort.HOT, RedditListingTimePeriod.WEEK))

        assertEquals(RedditProfilePostsOrder(RedditProfilePostSort.HOT, RedditListingTimePeriod.ALL), state.profilePostsOrder)
    }
}
