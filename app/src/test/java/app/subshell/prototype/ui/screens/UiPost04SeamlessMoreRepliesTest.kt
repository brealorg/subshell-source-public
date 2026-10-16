package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * UIPOST04 (ui/ review round 1, finding 3; owner: loading is seamless, never
 * a tap): an idle "more replies" node that is already on screen is loaded
 * automatically once nobody scrolls, without the per-gesture budget. The
 * prefetch candidate below the screen is unchanged (it still never picks a
 * visible node).
 */
class UiPost04SeamlessMoreRepliesTest {
    private val rows = visibleCommentRows(
        comments = listOf(
            comment("t1_visible", depth = 0),
            more("more:visible", depth = 0, childIds = listOf("v")),
            more("more:offscreen", depth = 0, childIds = listOf("o")),
        ),
        collapsedCommentIds = emptySet(),
    )

    private fun visibleNode(
        scrollInProgress: Boolean = false,
        failedMoreNodeId: String? = null,
        loadingMoreNodeId: String? = null,
        focusedAnchorOwned: Boolean = false,
        hydrationAnchorPending: Boolean = false,
    ) = commentViewportVisibleIdleMoreNode(
        rows = rows,
        firstVisibleLazyIndex = 3,
        lastVisibleLazyIndex = 4,
        addCommentVisible = false,
        liveStatusVisible = false,
        fullDiscussionLoaded = true,
        fullDiscussionLoading = false,
        focusedAnchorOwned = focusedAnchorOwned,
        loadingMoreNodeId = loadingMoreNodeId,
        failedMoreNodeId = failedMoreNodeId,
        scrollInProgress = scrollInProgress,
        hydrationAnchorPending = hydrationAnchorPending,
    )

    @Test
    fun aNodeAlreadyOnScreenIsLoadedWithoutATap() {
        assertEquals("more:visible", visibleNode()?.id)
    }

    @Test
    fun itWaitsForScrollingLoadingAndAnchorsAndSkipsAFailedNode() {
        assertNull(visibleNode(scrollInProgress = true))
        assertNull(visibleNode(loadingMoreNodeId = "more:other"))
        assertNull(visibleNode(focusedAnchorOwned = true))
        assertNull(visibleNode(hydrationAnchorPending = true))
        assertNull(visibleNode(failedMoreNodeId = "more:visible"))
    }

    @Test
    fun theBudgetedPrefetchStillNeverPicksTheVisibleNode() {
        val prefetch = commentViewportAutoHydrationCandidate(
            rows = rows,
            firstVisibleLazyIndex = 3,
            lastVisibleLazyIndex = 4,
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
        assertEquals("more:offscreen", prefetch?.id)
    }

    private fun comment(id: String, depth: Int) = Comment(
        id = id, author = "author", age = "1m", body = id, score = 1, depth = depth, accent = 0L,
    )

    private fun more(id: String, depth: Int, childIds: List<String>) = Comment(
        id = id, author = "More replies", age = "", body = "", score = 0, depth = depth, accent = 0L,
        moreChildIds = childIds, moreCount = childIds.size,
    )
}
