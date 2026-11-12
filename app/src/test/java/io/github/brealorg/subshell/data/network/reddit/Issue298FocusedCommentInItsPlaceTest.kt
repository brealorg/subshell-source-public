package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ISSUE298_FOCUSED_COMMENT_SITS_IN_ITS_PLACE_IN_A_MORE_NODE_V1 (#298): a
 * focused comment a more node lists goes where the list puts it.
 */
class Issue298FocusedCommentInItsPlaceTest {
    private fun c(id: String, parent: String, depth: Int = 1, children: List<Comment> = emptyList()) = Comment(
        id = "t1_$id",
        author = "a",
        age = "1h",
        body = id,
        score = 1,
        depth = depth,
        accent = 0,
        parentId = parent,
        children = children,
    )

    private fun node(ids: List<String>, count: Int = ids.size) = Comment(
        id = "more:t1_rick:${ids.first()}",
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 1,
        accent = 0,
        parentId = "t1_rick",
        moreChildIds = ids,
        moreCount = count,
    )

    private fun thread(vararg replies: Comment) = listOf(c("rick", "t3_p", depth = 0, children = replies.toList()))

    private fun Comment.shape(): List<String> = children.map { child ->
        if (child.moreChildIds.isNotEmpty()) "${child.id}=${child.moreChildIds}" else child.id
    }

    @Test
    fun theFirstListedCommentGoesAboveTheRestOfTheNode() {
        // The owner's Pixel: the focused reply is the node's first id.
        val merged = mergeFocusedCommentContext(
            current = thread(c("early", "t1_rick"), node(listOf("focus", "s2", "s3"))),
            focused = thread(c("focus", "t1_rick", children = listOf(c("r", "t1_focus", depth = 2)))),
        )
        assertEquals(
            listOf("t1_early", "t1_focus", "more:t1_rick:s2=[s2, s3]"),
            merged[0].shape(),
        )
        assertEquals(listOf("t1_r"), merged[0].children[1].children.map { it.id })
    }

    @Test
    fun aCommentInTheMiddleSplitsTheNodeAroundIt() {
        val original = node(listOf("a", "b", "focus", "d", "e"), count = 9)
        val merged = mergeFocusedCommentContext(
            current = thread(original),
            focused = thread(c("focus", "t1_rick")),
        )
        assertEquals(
            listOf("${original.id}=[a, b]", "t1_focus", "more:t1_rick:d=[d, e]"),
            merged[0].shape(),
        )
        assertEquals("the node above keeps its id", original.id, merged[0].children[0].id)
        assertEquals(2, merged[0].children[0].moreCount)
        assertEquals("Reddit's uncounted rest stays with the last node", 6, merged[0].children[2].moreCount)
    }

    @Test
    fun theLastListedCommentLeavesNoNodeBelowIt() {
        // The Inbox case: the reader's own comment at the end of the list.
        val merged = mergeFocusedCommentContext(
            current = thread(node(listOf("a", "mine"))),
            focused = thread(c("mine", "t1_rick")),
        )
        assertEquals(listOf("more:t1_rick:a=[a]", "t1_mine"), merged[0].shape())
    }

    @Test
    fun aCommentNoNodeListsIsStillAddedAfterItsSiblings() {
        val merged = mergeFocusedCommentContext(
            current = thread(c("early", "t1_rick"), node(listOf("a", "b"))),
            focused = thread(c("elsewhere", "t1_rick")),
        )
        assertEquals(listOf("t1_early", "more:t1_rick:a=[a, b]", "t1_elsewhere"), merged[0].shape())
    }
}
