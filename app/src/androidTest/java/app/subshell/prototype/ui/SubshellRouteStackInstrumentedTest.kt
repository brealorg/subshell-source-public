package app.subshell.prototype.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubshellRouteStackInstrumentedTest {
    @Test
    fun canonicalStackPopsNestedRoutesWithoutSideStateReconstruction() {
        val post = SampleData.posts.first { candidate -> candidate.kind == PostKind.VIDEO }
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        state.navigate(MainDestination.SUBS)
        state.openSubreddit(post.community)
        state.openSearch(refocus = false)
        state.updateSearchQuery("device stack")
        state.openFromSearch(post)
        state.openUser(post.author)

        assertEquals(
            listOf("Main", "Main", "Community", "Search", "Post", "User"),
            state.navigationRouteKinds,
        )
        assertTrue(state.navigateBack())
        assertEquals(post.id, state.selectedPost?.id)
        state.openMedia(post)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Media)
        assertTrue(state.navigateBack())
        assertEquals(post.id, state.selectedPost?.id)
        assertTrue(state.navigateBack())
        assertTrue(state.searchVisible)
        assertEquals("device stack", state.searchQuery)
        assertTrue(state.navigateBack())
        assertEquals(post.community, state.selectedSubreddit)
        assertFalse(state.searchVisible)

        Log.i(
            "subshell-feedpost-v2b",
            "FEEDPOST_V2B_CANONICAL_STACK=true SINGLE_POP_BACK=true " +
                "SEARCH_QUERY_RETURN=true ROUTE_DEPTH=${state.navigationRouteDepth}",
        )
        Log.i(
            "subshell-feedpost-v2b",
            "RESULT=SUBSHELL_FEEDPOST_V2B_ROUTE_STACK_RUNTIME_PASS",
        )
    }
}
