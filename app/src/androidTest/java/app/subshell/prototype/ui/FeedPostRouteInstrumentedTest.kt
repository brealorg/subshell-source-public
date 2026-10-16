package app.subshell.prototype.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedPostRouteInstrumentedTest {
    @Test
    fun typedOriginNestedReturnAndProcessRestore() {
        val post = SampleData.posts.first()
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        state.feedSource = FeedSource.POPULAR
        state.feedSort = FeedSort.NEW
        state.openFromMainFeed(post)
        state.openSubreddit(post.community)
        state.closeSubreddit()
        assertEquals(post.id, state.selectedPost?.id)
        state.closePost()
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(FeedSort.NEW, state.feedSort)

        state.openSubreddit(post.community)
        state.openSearch(refocus = false)
        state.updateSearchQuery("runtime route")
        state.openFromSearch(post)
        assertFalse(state.searchVisible)
        val snapshot = navigationRouteRestoreSnapshot(state)

        val restored = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialSelectedPostId = snapshot.selectedPostId,
            initialPostOriginToken = snapshot.postOriginToken,
            initialFocusedCommentId = snapshot.focusedCommentId,
            initialPostVisitId = snapshot.postVisitId,
        )
        assertEquals(post.id, restored.selectedPost?.id)
        restored.closePost()
        assertTrue(restored.searchVisible)
        assertEquals("runtime route", restored.searchQuery)
        assertEquals(post.community, restored.searchOriginCommunity)
        assertNull(restored.selectedPost)

        Log.i(
            "subshell-feedpost-v2",
            "FEEDPOST_V2A_TYPED_ORIGIN=true NESTED_POST_COMMUNITY_RETURN=true " +
                "PROCESS_RESTORE_SEARCH_RETURN=true FEED_SOURCE=${state.feedSource} FEED_SORT=${state.feedSort}",
        )
        Log.i("subshell-feedpost-v2", "RESULT=SUBSHELL_FEEDPOST_V2A_ROUTE_ORIGIN_RUNTIME_PASS")
    }
}
