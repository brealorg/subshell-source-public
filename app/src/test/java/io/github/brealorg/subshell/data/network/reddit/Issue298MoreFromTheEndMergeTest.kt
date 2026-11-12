package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1 (#298): the last
 * comments of a more node go between the node and the rows below it.
 */
class Issue298MoreFromTheEndMergeTest {
    private fun c(id: String, parent: String, depth: Int = 0, children: List<Comment> = emptyList()) = Comment(
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

    private fun node(ids: List<String>) = Comment(
        id = "more:t3_p:node",
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 0,
        accent = 0,
        parentId = "t3_p",
        moreChildIds = ids,
        moreCount = ids.size,
    )

    private fun List<Comment>.ids(): List<String> = map { it.id }

    @Test
    fun theLastCommentsGoBelowTheNodeAndTheNodeKeepsTheRest() {
        val thread = listOf(c("top", "t3_p"), node(listOf("a", "b", "c", "d")), c("mine", "t3_p"))
        val merged = mergeExpandedCommentsFromEnd(
            current = thread,
            moreNodeId = "more:t3_p:node",
            expanded = listOf(c("c", "t3_p"), c("d", "t3_p")),
            requestedIds = listOf("c", "d"),
        )
        assertEquals(listOf("t1_top", "more:t3_p:node", "t1_c", "t1_d", "t1_mine"), merged.ids())
        assertEquals(listOf("a", "b"), merged[1].moreChildIds)
        assertEquals(2, merged[1].moreCount)
    }

    @Test
    fun theNodeGoesAwayWhenItsLastCommentsArrive() {
        val thread = listOf(node(listOf("a")), c("mine", "t3_p"))
        val merged = mergeExpandedCommentsFromEnd(thread, "more:t3_p:node", listOf(c("a", "t3_p")), listOf("a"))
        assertEquals(listOf("t1_a", "t1_mine"), merged.ids())
    }

    @Test
    fun aReplyWhoseParentIsStillListedWaitsForItsParent() {
        // The list names "b2", a reply to "b"; asking for the end brings it alone.
        val thread = listOf(node(listOf("a", "b", "c", "b2")), c("mine", "t3_p"))
        val merged = mergeExpandedCommentsFromEnd(
            current = thread,
            moreNodeId = "more:t3_p:node",
            expanded = listOf(c("c", "t3_p"), c("b2", "t1_b", depth = 1)),
            requestedIds = listOf("c", "b2"),
        )
        assertEquals(listOf("more:t3_p:node", "t1_c", "t1_mine"), merged.ids())
        assertEquals("b2 comes with b next time", listOf("a", "b", "b2"), merged[0].moreChildIds)
    }

    @Test
    fun aReplyToACommentInTheThreadGoesUnderIt() {
        val thread = listOf(c("top", "t3_p"), node(listOf("a", "x")), c("mine", "t3_p"))
        val merged = mergeExpandedCommentsFromEnd(
            current = thread,
            moreNodeId = "more:t3_p:node",
            expanded = listOf(c("x", "t1_top", depth = 5)),
            requestedIds = listOf("x"),
        )
        assertEquals(listOf("t1_top", "more:t3_p:node", "t1_mine"), merged.ids())
        assertEquals(listOf("t1_x"), merged[0].children.ids())
        assertEquals(1, merged[0].children[0].depth)
        assertEquals(listOf("a"), merged[1].moreChildIds)
    }

    @Test
    fun idsAlreadyInTheThreadAndIdsThatDidNotComeBackLeaveTheList() {
        // "mine" was grafted from the Inbox; "gone" was deleted.
        val thread = listOf(node(listOf("a", "gone", "mine")), c("mine", "t3_p"))
        val merged = mergeExpandedCommentsFromEnd(thread, "more:t3_p:node", emptyList(), listOf("gone"))
        assertEquals(listOf("a"), merged[0].moreChildIds)
        val done = mergeExpandedCommentsFromEnd(
            listOf(node(listOf("mine")), c("mine", "t3_p")),
            "more:t3_p:node",
            emptyList(),
            emptyList(),
        )
        assertEquals(listOf("t1_mine"), done.ids())
        assertNull(done.firstOrNull { it.moreChildIds.isNotEmpty() })
    }
}
