package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.continueThreadNode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AUDIT298A_NO_COMMENT_IS_ASKED_FOR_TWICE_V1 / AUDIT298A_ORPHANS_FIND_THEIR_PARENT_V1
 * (audit 2026-11-12): after every merge each comment id is in the tree once,
 * under its own parent, at its parent's depth + 1. A second copy was dropped
 * on screen with its whole subtree (seen on the Pixel as
 * `duplicate_comment_identity`).
 */
class Audit298aUniqueIdsTest {
    private fun c(id: String, parent: String, depth: Int, children: List<Comment> = emptyList()) = Comment(
        id = "t1_$id", author = "a", age = "1h", body = id, score = 1, depth = depth, accent = 0L,
        parentId = parent, children = children,
    )

    private fun node(id: String, parent: String, depth: Int, ids: List<String>) = Comment(
        id = id, author = "", age = "", body = "", score = 0, depth = depth, accent = 0L,
        parentId = parent, moreChildIds = ids, moreCount = ids.size,
    )

    private fun assertWellFormed(tree: List<Comment>) {
        val ids = mutableListOf<String>()
        fun walk(comment: Comment, parent: Comment?) {
            ids += comment.id
            if (parent != null) {
                assertEquals("${comment.id} depth", parent.depth + 1, comment.depth)
                if (!comment.id.startsWith("more:") && !comment.id.startsWith("continue:")) {
                    assertEquals("${comment.id} parent", parent.id, comment.parentId)
                }
            }
            comment.children.forEach { child -> walk(child, comment) }
        }
        tree.forEach { root -> walk(root, null) }
        assertEquals("ids are unique: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun fromTheStartWhatIsShownIsNotAddedAgainAndAnOrphanFindsItsParent() {
        // "x" is shown; the answer brings x again (with a new reply x1), y, and
        // "p1" whose parent "p" is in the thread elsewhere (the batch cut them apart).
        val thread = listOf(
            c("p", "t3_abc", 0),
            c("top", "t3_abc", 0, children = listOf(
                c("x", "t1_top", 1),
                node("more:t1_top:x", "t1_top", 1, listOf("x", "y", "p1")),
            )),
        )
        val merged = mergeExpandedComments(
            current = thread,
            moreNodeId = "more:t1_top:x",
            expanded = listOf(
                c("x", "t1_top", 1, children = listOf(c("x1", "t1_x", 2))),
                c("y", "t1_top", 7), // Reddit's clamped depth
                c("p1", "t1_p", 1),
            ),
            requestedChildCount = 3,
        )
        assertWellFormed(merged)
        assertEquals(listOf("t1_p1"), merged[0].children.map(Comment::id))
        assertEquals(listOf("t1_x", "t1_y"), merged[1].children.map(Comment::id))
        assertEquals(listOf("t1_x1"), merged[1].children[0].children.map(Comment::id))
    }

    @Test
    fun fromTheEndWhatIsShownIsNotAddedAgain() {
        val thread = listOf(c("mine", "t3_abc", 0), node("more:t3_abc:a", "t3_abc", 0, listOf("a", "b")))
        val merged = mergeExpandedCommentsFromEnd(
            current = thread,
            moreNodeId = "more:t3_abc:a",
            expanded = listOf(c("b", "t3_abc", 0), c("mine", "t3_abc", 0)),
            requestedIds = listOf("b"),
        )
        assertWellFormed(merged)
    }

    @Test
    fun aContinuedThreadDoesNotAddTheReadersReplyAgain() {
        // The reader replied under "deep"; its replies continue elsewhere.
        val continueNode = continueThreadNode(parentFullname = "t1_deep", depth = 2, accent = 0L)
        val thread = listOf(
            c("top", "t3_abc", 0, children = listOf(
                c("deep", "t1_top", 1, children = listOf(c("mine", "t1_deep", 2), continueNode)),
            )),
        )
        val focused = listOf(
            c("deep", "t1_top", 0, children = listOf(c("mine", "t1_deep", 1), c("d2", "t1_deep", 1))),
        )
        val merged = mergeContinuedThread(thread, continueNode.id, focused)!!
        assertWellFormed(merged)
        assertEquals(listOf("t1_mine", "t1_d2"), merged[0].children[0].children.map(Comment::id))
    }

    @Test
    fun theNodeSplitAroundAFocusedCommentDoesNotNameItsRepliesAgain() {
        // The node lists the focused comment "f" and its reply "f1" (a descendant).
        val thread = listOf(c("top", "t3_abc", 0, children = listOf(node("more:t1_top:a", "t1_top", 1, listOf("a", "f", "f1", "g")))))
        val focused = listOf(c("top", "t3_abc", 0, children = listOf(c("f", "t1_top", 1, children = listOf(c("f1", "t1_f", 2))))))
        val merged = mergeFocusedCommentContext(current = thread, focused = focused)
        val shape = merged[0].children.map { child ->
            if (child.moreChildIds.isNotEmpty()) "${child.id}=${child.moreChildIds}" else child.id
        }
        assertEquals(listOf("more:t1_top:a=[a]", "t1_f", "more:t1_top:g=[g]"), shape)
        assertWellFormed(merged)
    }

    @Test
    fun fromTheEndALoadThatCannotWaitForParentsStillTakesIdsOff() {
        // AUDIT298A_FROM_THE_END_ALWAYS_MOVES_ON_V1: the last resort places them at the node.
        val thread = listOf(node("more:t3_abc:a", "t3_abc", 0, listOf("a", "b", "b1")), c("mine", "t3_abc", 0))
        val merged = mergeExpandedCommentsFromEnd(
            current = thread,
            moreNodeId = "more:t3_abc:a",
            expanded = listOf(c("b1", "t1_b", 1)),
            requestedIds = listOf("b1"),
            waitForListedParents = false,
        )
        assertEquals(listOf("a", "b"), merged[0].moreChildIds)
        assertEquals("t1_b1", merged[1].id)
    }
}
