package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE205_THREAD_SAYS_MORE_CAN_LOAD_V1 (#205): the owner's screen recording
 * showed a thread's scroll position indicator hit the bottom at the end of
 * what was loaded and jump up as replies arrived. A thread that is loading or
 * still has a more node tells the indicator that more can load.
 */
class Issue205ThreadMoreToLoadTest {
    private fun row(id: String, kind: CommentViewportRowKind) = CommentViewportRow(
        Comment(id = id, author = "someone", age = "1h", body = id, score = 1, depth = 0, accent = 0),
        kind,
    )

    @Test
    fun aThreadWithAMoreNodeOrStillLoadingHasMoreToLoad() {
        val comments = listOf(row("c1", CommentViewportRowKind.COMMENT), row("c2", CommentViewportRowKind.COMMENT))
        assertTrue(commentThreadHasMoreToLoad(comments + row("m1", CommentViewportRowKind.MORE_REPLIES), loading = false))
        assertTrue(commentThreadHasMoreToLoad(comments, loading = true))
        assertFalse(commentThreadHasMoreToLoad(comments, loading = false))
    }
}
