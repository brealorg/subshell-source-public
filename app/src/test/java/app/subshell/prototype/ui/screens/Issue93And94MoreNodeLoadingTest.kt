package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE93_VISIBLE_RANGE_IGNORES_THE_STICKY_HEADER_V1 /
 * ISSUE94_FAILED_NODES_WAIT_FOR_RETRY_V1.
 */
class Issue93And94MoreNodeLoadingTest {
    @Test
    fun thePinnedHeaderDoesNotStretchTheVisibleRangeToTheTop() {
        // Scrolled deep into the thread: the sticky "comment-controls" header
        // (index 3) is pinned above rows 40..48.
        val visible = listOf(3 to "comment-controls") +
            (40..48).map { index -> index to "comment:c$index" } +
            listOf(49 to "more:m49")

        assertEquals(40 to 49, commentViewportVisibleRowLazyIndices(visible))
    }

    @Test
    fun noVisibleRowsMeansNoRange() {
        assertEquals(-1 to -1, commentViewportVisibleRowLazyIndices(listOf(0 to "post", 3 to "comment-controls")))
    }

    private fun comment(id: String, depth: Int = 0) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "Comment $id",
        score = 1,
        depth = depth,
        accent = 0,
    )

    private fun more(id: String) = Comment(
        id = id,
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 1,
        accent = 0,
        parentId = "c0",
        moreChildIds = listOf("x_$id"),
        moreCount = 1,
    )

    @Test
    fun failedNodesAreNotPickedAgainUntilRetried() {
        val rows = listOf(
            CommentViewportRow(comment("c0"), CommentViewportRowKind.COMMENT),
            CommentViewportRow(more("mA"), CommentViewportRowKind.MORE_REPLIES),
            CommentViewportRow(more("mB"), CommentViewportRowKind.MORE_REPLIES),
        )
        fun pick(failedLast: String?, blocked: Set<String>) = commentViewportVisibleIdleMoreNode(
            rows = rows,
            firstVisibleLazyIndex = 3,
            lastVisibleLazyIndex = 5,
            addCommentVisible = false,
            liveStatusVisible = false,
            fullDiscussionLoaded = true,
            fullDiscussionLoading = false,
            focusedAnchorOwned = false,
            loadingMoreNodeId = null,
            failedMoreNodeId = failedLast,
            scrollInProgress = false,
            hydrationAnchorPending = false,
            blockedMoreNodeIds = blocked,
        )?.id

        // B failed last, A failed before it: the old rule picked A again.
        assertEquals("mA", pick(failedLast = "mB", blocked = emptySet()))
        assertNull(pick(failedLast = "mB", blocked = setOf("mA", "mB")))
    }
}
