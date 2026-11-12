package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AUDIT298B_FOCUSED_PARENTS_LOAD_THEIR_OTHER_REPLIES_V1 (owner's Pixel
 * 2026-11-12: two of the reader's replies to the parent of a linked comment
 * were not shown, with no row saying more exist).
 */
class Audit298bFocusedParentsTest {
    private fun c(id: String, parent: String, depth: Int, children: List<Comment> = emptyList()) = Comment(
        id = "t1_$id", author = "a", age = "1h", body = id, score = 1, depth = depth, accent = 0L,
        parentId = parent, children = children,
    )

    private fun node(id: String, parent: String, depth: Int, ids: List<String>) = Comment(
        id = id, author = "", age = "", body = "", score = 0, depth = depth, accent = 0L,
        parentId = parent, moreChildIds = ids, moreCount = ids.size,
    )

    // The link's answer: P (top level) and its reply X, the linked comment with its own reply.
    private val focused = listOf(c("p", "t3_abc", 0, children = listOf(c("x", "t1_p", 1, children = listOf(c("x1", "t1_x", 2))))))

    private fun shape(comments: List<Comment>): List<String> = comments.flatMap { comment ->
        listOf("${comment.depth}:${comment.id}") + shape(comment.children)
    }

    @Test
    fun aParentAddedFromTheLinkGetsARowThatLoadsItsOtherReplies() {
        val merged = mergeFocusedCommentContext(
            current = listOf(c("top", "t3_abc", 0), node("more:t3_abc:q", "t3_abc", 0, listOf("q", "p"))),
            focused = focused,
            focusedCommentId = "t1_x",
        )
        assertEquals(
            listOf(
                "0:t1_top",
                "0:more:t3_abc:q",
                "0:t1_p",
                "1:t1_x",
                "2:t1_x1",
                "1:continue:t1_p",
            ),
            shape(merged),
        )
    }

    @Test
    fun aParentAlreadyInTheThreadAndTheLinkedCommentAreLeftAsTheyAre() {
        val merged = mergeFocusedCommentContext(
            current = listOf(c("p", "t3_abc", 0, children = listOf(c("y", "t1_p", 1)))),
            focused = focused,
            focusedCommentId = "t1_x",
        )
        assertEquals(listOf("0:t1_p", "1:t1_y", "1:t1_x", "2:t1_x1"), shape(merged))
    }

    @Test
    fun theRowLoadsTheOtherRepliesWithoutAddingTheLinkedOneAgain() {
        val merged = mergeFocusedCommentContext(current = listOf(c("top", "t3_abc", 0)), focused = focused, focusedCommentId = "t1_x")
        // Reddit's answer for the continued thread: P with all its replies.
        val continued = listOf(c("p", "t3_abc", 0, children = listOf(c("x", "t1_p", 1), c("test", "t1_p", 1), c("test2", "t1_p", 1))))
        val loaded = mergeContinuedThread(merged, "continue:t1_p", continued)!!
        assertEquals(
            listOf("0:t1_top", "0:t1_p", "1:t1_x", "2:t1_x1", "1:t1_test", "1:t1_test2"),
            shape(loaded),
        )
    }

    @Test
    fun withoutTheLinkedCommentNothingIsAdded() {
        val merged = mergeFocusedCommentContext(current = listOf(c("top", "t3_abc", 0)), focused = focused)
        assertEquals(listOf("0:t1_top", "0:t1_p", "1:t1_x", "2:t1_x1"), shape(merged))
    }
}
