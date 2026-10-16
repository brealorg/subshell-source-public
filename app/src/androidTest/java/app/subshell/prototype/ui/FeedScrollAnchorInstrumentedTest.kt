package app.subshell.prototype.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedScrollAnchorInstrumentedTest {
    @Test
    fun perSurfaceAnchorsSurvivePostBackAndStableItemReordering() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()
        val homeKey = mainFeedScrollKey(FeedSource.POPULAR, FeedSort.NEW)
        val communityKey = communityFeedScrollKey(post.community, CommunitySort.TOP)
        val searchKey = searchFeedScrollKey(
            destination = MainDestination.SUBS,
            originCommunity = post.community,
            query = "  exact   return ",
            scope = SearchScope.POSTS,
            area = SearchArea.CURRENT_COMMUNITY,
        )

        state.updateFeedScrollAnchor(homeKey, post.id, 7, 41)
        state.updateFeedScrollAnchor(communityKey, post.id, 5, 29)
        state.updateFeedScrollAnchor(searchKey, "search-${post.id}", 4, 17)

        state.navigate(MainDestination.HOME)
        state.feedSource = FeedSource.POPULAR
        state.feedSort = FeedSort.NEW
        state.openFromMainFeed(post)
        state.closePost()

        val restoredHome = state.feedScrollAnchor(homeKey)
        assertEquals(post.id, restoredHome.itemKey)
        assertEquals(41, restoredHome.pixelOffset)
        assertEquals(
            2,
            restoredHome.resolveIndex(listOf("older", "newer", post.id, "tail")),
        )
        assertEquals(3, state.feedScrollAnchorSnapshot().size)
        assertTrue(state.feedScrollAnchor(communityKey) != state.feedScrollAnchor(searchKey))

        Log.i(
            "subshell-feedpost-v2c",
            "FEEDPOST_V2C_KEYED_ANCHORS=true POST_BACK_OFFSET=41 " +
                "STABLE_ITEM_REORDER_INDEX=2 SURFACE_COUNT=${state.feedScrollAnchorSnapshot().size}",
        )
        Log.i(
            "subshell-feedpost-v2c",
            "RESULT=SUBSHELL_FEEDPOST_V2C_SCROLL_ANCHOR_RUNTIME_PASS",
        )
    }
}
