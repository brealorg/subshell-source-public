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
        val visible = listOf(VisibleLazyCommentItem(3, "comment-controls", offset = 0, size = 100)) +
            (40..48).map { index -> VisibleLazyCommentItem(index, "comment:c$index", offset = 100 + (index - 40) * 100, size = 100) } +
            listOf(VisibleLazyCommentItem(49, "more:m49", offset = 1_000, size = 1))

        assertEquals(40 to 49, commentViewportVisibleRowLazyIndices(visible))
    }

    // ISSUE269_ROWS_UNDER_THE_HEADER_ARE_NOT_ON_SCREEN_V1
    @Test
    fun aRowWhollyUnderThePinnedHeaderIsNotOnScreen() {
        val visible = listOf(
            VisibleLazyCommentItem(3, "comment-controls", offset = 0, size = 100),
            VisibleLazyCommentItem(39, "more:m39", offset = 99, size = 1),
            VisibleLazyCommentItem(40, "comment:c40", offset = 100, size = 300),
            VisibleLazyCommentItem(41, "comment:c41", offset = 400, size = 300),
        )

        assertEquals(40 to 41, commentViewportVisibleRowLazyIndices(visible))
        // A row only partly under the header is still on screen.
        assertEquals(
            39 to 40,
            commentViewportVisibleRowLazyIndices(
                listOf(
                    VisibleLazyCommentItem(3, "comment-controls", offset = 0, size = 100),
                    VisibleLazyCommentItem(39, "comment:c39", offset = 50, size = 60),
                    VisibleLazyCommentItem(40, "comment:c40", offset = 110, size = 300),
                ),
            ),
        )
    }

    @Test
    fun noVisibleRowsMeansNoRange() {
        assertEquals(
            -1 to -1,
            commentViewportVisibleRowLazyIndices(
                listOf(
                    VisibleLazyCommentItem(0, "post", offset = 0, size = 400),
                    VisibleLazyCommentItem(3, "comment-controls", offset = 400, size = 100),
                ),
            ),
        )
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
