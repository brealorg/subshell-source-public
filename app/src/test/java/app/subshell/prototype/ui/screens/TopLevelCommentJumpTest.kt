package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopLevelCommentJumpTest {
    private fun comment(id: String, depth: Int, children: List<Comment> = emptyList(), more: Boolean = false) =
        Comment(
            id = id,
            author = "u",
            age = "1h",
            body = id,
            score = 1,
            depth = depth,
            accent = 0L,
            children = children,
            moreChildIds = if (more) listOf("x") else emptyList(),
        )

    // a (b (c)), d, e (f), more
    private val tree = listOf(
        comment("a", 0, listOf(comment("b", 1, listOf(comment("c", 2))))),
        comment("d", 0),
        comment("e", 0, listOf(comment("f", 1))),
        comment("more", 0, more = true),
    )
    private val rows = visibleCommentRows(comments = tree, collapsedCommentIds = emptySet())
    private fun index(id: String) = rows.indexOfFirst { row -> row.comment.id == id }

    private fun next(at: Int, into: Boolean = false) =
        topLevelCommentJumpTarget(rows, CommentReadingPosition(at, into), TopLevelCommentJumpDirection.NEXT)

    private fun previous(at: Int, into: Boolean = false) =
        topLevelCommentJumpTarget(rows, CommentReadingPosition(at, into), TopLevelCommentJumpDirection.PREVIOUS)

    @Test
    fun nextGoesToTheNextTopLevelRowAndLandsOnLoadMoreLast() {
        assertEquals(index("a"), next(-1))
        assertEquals(index("d"), next(index("a")))
        assertEquals(index("d"), next(index("c"), into = true))
        assertEquals(index("e"), next(index("d")))
        assertEquals(index("more"), next(index("f")))
        assertNull(next(index("more")))
        assertNull(next(rows.size))
    }

    @Test
    fun previousGoesToTheTopOfTheCurrentThreadThenTheOneBefore() {
        assertEquals(index("a"), previous(index("c")))
        assertEquals(index("d"), previous(index("d"), into = true))
        assertEquals(index("a"), previous(index("d")))
        assertEquals(index("more"), previous(rows.size))
        assertNull(previous(index("a")))
        assertNull(previous(-1))
    }

    @Test
    fun collapsedRepliesAreNotTargetsOrStops() {
        val collapsed = visibleCommentRows(comments = tree, collapsedCommentIds = setOf("a"))
        val a = collapsed.indexOfFirst { row -> row.comment.id == "a" }
        val d = collapsed.indexOfFirst { row -> row.comment.id == "d" }
        assertEquals(a + 1, d)
        assertEquals(
            d,
            topLevelCommentJumpTarget(collapsed, CommentReadingPosition(a, false), TopLevelCommentJumpDirection.NEXT),
        )
    }

    @Test
    fun readingPositionIsTheFirstRowBelowTheStuckHeader() {
        val header = VisibleCommentListItem(COMMENT_CONTROLS_ITEM_KEY, offset = 0, size = 48)
        fun row(id: String, offset: Int, size: Int) =
            VisibleCommentListItem(rows[index(id)].key, offset, size)

        // Row b is partly under the header, c starts below it: b is current, scrolled into.
        assertEquals(
            CommentReadingPosition(index("b"), scrolledIntoRow = true),
            commentReadingPosition(rows, listOf(header, row("b", 10, 60), row("c", 70, 60)), viewportStartOffset = 0),
        )
        // Row d starts exactly at the reading line.
        assertEquals(
            CommentReadingPosition(index("d"), scrolledIntoRow = false),
            commentReadingPosition(rows, listOf(header, row("c", -20, 68), row("d", 48, 60)), viewportStartOffset = 0),
        )
        // Header not stuck yet (post still in view): above the comments.
        assertEquals(
            CommentReadingPosition(-1, scrolledIntoRow = false),
            commentReadingPosition(
                rows,
                listOf(VisibleCommentListItem("post-card", 0, 400), header.copy(offset = 400), row("a", 448, 60)),
                viewportStartOffset = 0,
            ),
        )
        // Only the trailing landing space is left: below the comments.
        assertEquals(
            CommentReadingPosition(rows.size, scrolledIntoRow = false),
            commentReadingPosition(
                rows,
                listOf(VisibleCommentListItem(FOCUSED_COMMENT_LANDING_SPACE_ITEM_KEY, 0, 300)),
                viewportStartOffset = 0,
            ),
        )
    }
}
