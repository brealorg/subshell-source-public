package app.subshell.prototype.ui

import app.subshell.prototype.data.network.reddit.normalizeRedditPostEditText
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.screens.CommentViewportRow
import app.subshell.prototype.ui.screens.CommentViewportRowKind
import app.subshell.prototype.ui.screens.commentViewportAutoHydrationCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LOWUI8c: LOWUI8_POST_EDIT_KEEPS_LEADING_INDENTATION_V1 (OCB-319),
 * LOWUI8_PREFETCH_INCLUDES_THE_FIRST_ROW_V1 (OCB-180).
 */
class LowUi8cTest {
    @Test
    fun aPostEditKeepsItsLeadingIndentation() {
        assertEquals("    val x = 1\n\nText", normalizeRedditPostEditText("    val x = 1\n\nText  \n"))
    }

    private val moreNode = Comment(
        id = "more_top",
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 0,
        accent = 0,
        moreChildIds = listOf("c1"),
        moreCount = 1,
    )

    private fun candidate(lastVisibleLazyIndex: Int) = commentViewportAutoHydrationCandidate(
        rows = listOf(CommentViewportRow(moreNode, CommentViewportRowKind.MORE_REPLIES)),
        firstVisibleLazyIndex = 0,
        lastVisibleLazyIndex = lastVisibleLazyIndex,
        addCommentVisible = false,
        liveStatusVisible = false,
        fullDiscussionLoaded = true,
        fullDiscussionLoading = false,
        focusedAnchorOwned = false,
        automaticHydrationBudget = 1,
        loadingMoreNodeId = null,
        failedMoreNodeId = null,
        scrollInProgress = false,
        hydrationAnchorPending = false,
    )

    @Test
    fun aFirstRowMoreNodeBelowTheHeaderIsPrefetched() {
        // Only the three header items show (the post body fills the screen).
        assertEquals(moreNode, candidate(lastVisibleLazyIndex = 2))
        // Once the row itself is on screen it is not a below-screen prefetch.
        assertNull(candidate(lastVisibleLazyIndex = 3))
    }
}
