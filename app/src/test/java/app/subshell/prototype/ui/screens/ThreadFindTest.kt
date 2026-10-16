package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadFindTest {
    private fun comment(id: String, body: String, depth: Int = 0, children: List<Comment> = emptyList(), more: Boolean = false) =
        Comment(
            id = id,
            author = "author-$id",
            age = "1h",
            body = body,
            score = 1,
            depth = depth,
            accent = 0L,
            children = children,
            moreChildIds = if (more) listOf("x") else emptyList(),
        )

    private val thread = listOf(
        comment(
            "a",
            "Top comment about **Kotlin**",
            children = listOf(comment("b", "reply with kotlin inside", depth = 1)),
        ),
        comment("c", "Nothing here"),
        comment("d", "See [the docs](https://kotlinlang.org) please"),
        comment("e", "```\nval kotlin = 1\n```"),
        comment("f", "kotlin in a more node", more = true),
    )

    @Test
    fun matchesTheRenderedBodyInThreadOrderIgnoringCase() {
        assertEquals(listOf("a", "b"), threadFindMatchIds(thread, "KOTLIN"))
    }

    @Test
    fun markdownSyntaxLinkTargetsCodeBlocksAndMoreNodesAreNotMatched() {
        // "**" is syntax, the link target is not shown, code blocks and more nodes are skipped.
        assertEquals(emptyList<String>(), threadFindMatchIds(thread, "**"))
        assertEquals(emptyList<String>(), threadFindMatchIds(thread, "kotlinlang"))
        assertEquals(listOf("d"), threadFindMatchIds(thread, "the docs"))
        assertEquals(emptyList<String>(), threadFindMatchIds(thread, "val kotlin"))
    }

    @Test
    fun blankQueryMatchesNothingAndAuthorsAreNotSearched() {
        assertEquals(emptyList<String>(), threadFindMatchIds(thread, "  "))
        assertEquals(emptyList<String>(), threadFindMatchIds(thread, "author-c"))
    }

    @Test
    fun steppingWrapsAroundAndStartsAtTheEnds() {
        assertEquals(0, threadFindStep(current = -1, size = 3, forward = true))
        assertEquals(2, threadFindStep(current = -1, size = 3, forward = false))
        assertEquals(1, threadFindStep(current = 0, size = 3, forward = true))
        assertEquals(0, threadFindStep(current = 2, size = 3, forward = true))
        assertEquals(2, threadFindStep(current = 0, size = 3, forward = false))
        assertNull(threadFindStep(current = -1, size = 0, forward = true))
    }

    @Test
    fun openingFindMovesTheHeaderUpOnlyWhileThePostIsStillInView() {
        // Post card at the top (index 1), header at index 3: move it up.
        assertEquals(true, threadFindNeedsHeaderAtTop(firstVisibleItemIndex = 1, commentControlsIndex = 3))
        // Header stuck (reading comments): stay put.
        assertEquals(false, threadFindNeedsHeaderAtTop(firstVisibleItemIndex = 3, commentControlsIndex = 3))
        assertEquals(false, threadFindNeedsHeaderAtTop(firstVisibleItemIndex = 40, commentControlsIndex = 3))
    }

    @Test
    fun countLabel() {
        assertEquals("0/0", threadFindCountLabel(active = -1, size = 0))
        assertEquals("–/4", threadFindCountLabel(active = -1, size = 4))
        assertEquals("2/4", threadFindCountLabel(active = 1, size = 4))
        assertEquals("No matches", threadFindCountDescription(active = -1, size = 0))
        assertEquals("4 matches", threadFindCountDescription(active = -1, size = 4))
        assertEquals("Match 2 of 4", threadFindCountDescription(active = 1, size = 4))
    }
}
