package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.ui.CommentHierarchyStyle
import io.github.brealorg.subshell.ui.CommentThreadColours
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #285: coloured thread lines and a card for each top-level thread. ISSUE285_THREAD_COLOURS_V1. */
class Issue285ThreadColoursTest {
    @Test
    fun offIsTheDefaultAndTheChoiceIsStoredAndRestored() {
        assertEquals(CommentThreadColours.OFF, SubshellSettings().commentThreadColours)
        assertEquals(listOf("Off", "By depth", "One colour"), CommentThreadColours.entries.map(CommentThreadColours::label))
        assertEquals("comment_thread_colours", SubshellSettings.COMMENT_THREAD_COLOURS)

        CommentThreadColours.entries.forEach { colours ->
            val stored = SubshellSettings(commentThreadColours = colours).toStorageMap()
            assertEquals(colours, SubshellSettings.fromStorageMap(stored).commentThreadColours)
        }
        // A settings file from before the option leaves it off.
        val legacy = SubshellSettings().toStorageMap() - SubshellSettings.COMMENT_THREAD_COLOURS
        assertEquals(CommentThreadColours.OFF, SubshellSettings.fromStorageMap(legacy).commentThreadColours)
    }

    @Test
    fun theStatePersistsTheChoice() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })

        state.updateCommentThreadColours(CommentThreadColours.BY_DEPTH)

        assertEquals(CommentThreadColours.BY_DEPTH, state.commentThreadColours)
        assertEquals("BY_DEPTH", persisted[SubshellSettings.COMMENT_THREAD_COLOURS])
    }

    @Test
    fun aThreadEndsWhereTheNextTopLevelCommentStarts() {
        fun c(id: String, depth: Int, children: List<Comment> = emptyList()) = Comment(
            id = id,
            author = "u_$id",
            age = "1h",
            body = "text $id",
            score = 1,
            depth = depth,
            accent = 0xFF336699,
            children = children,
        )
        val comments = listOf(
            c("a", 0, listOf(c("a1", 1, listOf(c("a2", 2))), c("a3", 1))),
            c("b", 0, listOf(c("b1", 1))),
            c("c", 0),
        )

        val visuals = commentBranchRailVisualByCommentId(comments)

        // a, a1, a2, a3 are one thread: only the last of them ends it; so does b1 and c.
        assertFalse(visuals.getValue("a").endsThread)
        assertFalse(visuals.getValue("a1").endsThread)
        assertFalse(visuals.getValue("a2").endsThread)
        assertTrue(visuals.getValue("a3").endsThread)
        assertFalse(visuals.getValue("b").endsThread)
        assertTrue(visuals.getValue("b1").endsThread)
        assertTrue(visuals.getValue("c").endsThread)
    }

    @Test
    fun theColoursApplyOnlyWithGuideLines() {
        val state = SubshellState(
            initialCommentThreadColours = CommentThreadColours.BY_DEPTH,
            initialCommentHierarchyStyle = CommentHierarchyStyle.GUIDE_LINES,
        )
        assertEquals(CommentThreadColours.BY_DEPTH, state.effectiveCommentThreadColours)

        state.updateCommentHierarchyStyle(CommentHierarchyStyle.PARENT_CONNECTORS)

        assertEquals(CommentThreadColours.BY_DEPTH, state.commentThreadColours)
        assertEquals(CommentThreadColours.OFF, state.effectiveCommentThreadColours)
    }

    @Test
    fun theCardChangesTheRowsOnlyWhenTheColoursAreEffective() {
        val guide = SubshellState(
            initialCommentThreadColours = CommentThreadColours.BY_DEPTH,
            initialCommentHierarchyStyle = CommentHierarchyStyle.GUIDE_LINES,
        )
        val connectors = SubshellState(
            initialCommentThreadColours = CommentThreadColours.BY_DEPTH,
            initialCommentHierarchyStyle = CommentHierarchyStyle.PARENT_CONNECTORS,
        )

        // What PostScreen draws is read from commentThreadChrome(state.effectiveCommentThreadColours).
        val withCard = commentThreadChrome(guide.effectiveCommentThreadColours)
        assertTrue(withCard.squareRows && withCard.gapBetweenThreads)
        assertFalse(withCard.rowDividers)

        val asBefore = commentThreadChrome(connectors.effectiveCommentThreadColours)
        assertFalse(asBefore.squareRows || asBefore.gapBetweenThreads)
        assertTrue(asBefore.rowDividers)
    }
}
