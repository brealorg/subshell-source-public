package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.ui.CommentIndentSize
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// REPLYCONTEXT03_INDENT_SIZE_IS_A_SETTING_V1, REPLYCONTEXT03_RAIL_STARTS_IN_THE_PARENT_V1
class ReplyContext03IndentSizeAndParentRailTest {
    private fun c(id: String, depth: Int, children: List<Comment> = emptyList()) = Comment(
        id = id,
        author = "u_$id",
        age = "1h",
        body = "text $id",
        score = 1,
        depth = depth,
        accent = 0xFF336699,
        children = children,
    )

    @Test
    fun normalIsTheDefaultAndTheChoiceIsStoredAndRestored() {
        assertEquals(CommentIndentSize.NORMAL, SubshellSettings().commentIndentSize)
        assertEquals(listOf(0, 4, 6, 9), CommentIndentSize.entries.map(CommentIndentSize::stepDp))
        assertEquals(COMMENT_DEPTH_INDENT_STEP_DP, CommentIndentSize.NORMAL.stepDp)
        assertEquals("comment_indent_size", SubshellSettings.COMMENT_INDENT_SIZE)
        CommentIndentSize.entries.forEach { size ->
            val stored = SubshellSettings(commentIndentSize = size).toStorageMap()
            assertEquals(size, SubshellSettings.fromStorageMap(stored).commentIndentSize)
        }
        val legacy = SubshellSettings().toStorageMap() - SubshellSettings.COMMENT_INDENT_SIZE
        assertEquals(CommentIndentSize.NORMAL, SubshellSettings.fromStorageMap(legacy).commentIndentSize)
    }

    @Test
    fun theStatePersistsTheChoice() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })
        state.updateCommentIndentSize(CommentIndentSize.LARGE)
        assertEquals(CommentIndentSize.LARGE, state.commentIndentSize)
        assertEquals("LARGE", persisted[SubshellSettings.COMMENT_INDENT_SIZE])
    }

    @Test
    fun theStepDecidesTheIndentAndNoIndentBringsTheReplyLineBack() {
        assertEquals(27, commentDepthIndentDp(3, 9))
        assertEquals(0, commentDepthIndentDp(5, 0))
        assertEquals(24, commentDepthIndentDp(9, 4))
        assertTrue(commentDepthShowsReplyContext(2, 0))
        assertFalse(commentDepthShowsReplyContext(1, 0))
        assertFalse(commentDepthShowsReplyContext(2, 6))
    }

    @Test
    fun theRailOfAReplyStartsInTheMiddleOfItsParent() {
        val visuals = commentBranchRailVisualByCommentId(
            listOf(c("a", 0, listOf(c("b", 1, listOf(c("d", 2)))))),
        )
        // a has no rail; the rail b starts is drawn from a's middle.
        assertEquals(listOf(0), visuals.getValue("a").startsInNextRowRailIndices)
        assertEquals(setOf("a"), visuals.getValue("b").startedByPreviousRowRailIds)
        // the same rail runs on to d: nothing new starts there.
        assertEquals(emptyList<Int>(), visuals.getValue("b").startsInNextRowRailIndices)
        assertEquals(emptySet<String>(), visuals.getValue("d").startedByPreviousRowRailIds)
    }

    @Test
    fun aSecondSiblingStartsNothingInTheCommentAboveIt() {
        val visuals = commentBranchRailVisualByCommentId(
            listOf(c("a", 0, listOf(c("b", 1), c("e", 1)))),
        )
        // b is a's first reply, e the second: e follows b (same depth), not its parent.
        assertEquals(listOf(0), visuals.getValue("a").startsInNextRowRailIndices)
        assertEquals(emptyList<Int>(), visuals.getValue("b").startsInNextRowRailIndices)
        assertEquals(emptySet<String>(), visuals.getValue("e").startedByPreviousRowRailIds)
    }
}
