package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.harness.SUBSHELL_PACKAGE_DIR
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentSave01A31CommentOverflowIconSystemPolicyTest {
    private val repositoryRoot: File by lazy {
        val userDir = System.getProperty("user.dir")
            ?: error("user.dir is unavailable")
        val start = File(userDir).canonicalFile
        generateSequence(start) { current -> current.parentFile }
            .firstOrNull { candidate ->
                File(candidate, "app/src/main/java").isDirectory &&
                    File(candidate, "tools").isDirectory
            }
            ?: error("Could not resolve repository root from ${start.path}")
    }

    private fun source(path: String): String {
        val file = File(repositoryRoot, path)
        assertTrue("Expected source file: ${file.path}", file.isFile)
        return file.readText()
    }

    private fun commentActionRowSource(): String {
        val postScreen = source(
            "app/src/main/java/${SUBSHELL_PACKAGE_DIR}/ui/screens/PostScreen.kt",
        )
        val start = postScreen.indexOf("private fun CommentActionRow(")
        assertTrue("CommentActionRow start marker missing", start >= 0)
        val end = postScreen.indexOf("\n@Composable\nprivate fun SmallCommentAction", start)
        assertTrue("CommentActionRow end marker missing", end > start)
        return postScreen.substring(start, end)
    }

    @Test
    fun everyCommentOverflowActionUsesLeadingIconLanguage() {
        val row = commentActionRowSource()

        assertTrue(row.contains("COMMENTSAVE01A31_COMMENT_OVERFLOW_ICON_SYSTEM_V1"))
        // ISSUE295_YOUR_COMMENT_ACTIONS_IN_ONE_SHEET_V1: Edit and Delete moved
        // into the "Your comment…" sheet; the entry and the sheet rows keep icons.
        assertTrue(row.contains("text = { Text(\"Your comment…\") }"))
        assertTrue(row.contains("imageVector = Symbols.Edit"))
        assertTrue(row.contains("SheetAction(\"Edit comment\", Symbols.Edit)"))
        assertTrue(row.contains("\"Delete comment\""))
        assertTrue(row.contains("Symbols.Delete,"))
        assertTrue(row.contains("Text(if (saved) \"Unsave comment\" else \"Save comment\")"))
        assertTrue(row.contains("Symbols.BookmarkFilled"))
        assertTrue(row.contains("Symbols.Bookmark"))
        assertTrue(row.contains("text = { Text(\"Share comment\") }"))
        assertTrue(row.contains("imageVector = Symbols.Share"))
        assertTrue(row.contains("text = { Text(\"Copy text\") }"))
        assertTrue(row.contains("imageVector = Symbols.ContentCopy"))
        // ISSUE295_MENU_OFFERS_ONLY_WHAT_THE_CARD_DOES_NOT_V1: a tap on the
        // comment collapses it; the menu no longer repeats it.
        assertFalse(row.contains("text = { Text(\"Collapse thread\") }"))
        assertTrue(row.split("leadingIcon =").size - 1 >= 5)
    }

    @Test
    fun destructiveCommentActionKeepsErrorSemantics() {
        val row = commentActionRowSource()

        // ISSUE295_YOUR_COMMENT_ACTIONS_IN_ONE_SHEET_V1: Delete is a destructive
        // sheet row (error colour for text and icon, see ItemActionsSheet).
        assertTrue(row.contains("\"Delete comment\",\n                        Symbols.Delete,\n                        destructive = true,"))
    }

    @Test
    fun a3BookmarkStateSemanticsRemainIntact() {
        val row = commentActionRowSource()

        assertTrue(row.contains("COMMENTSAVE01A3_COMMENT_SAVE_ICON_CONSISTENCY_V1"))
        assertTrue(row.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(row.contains("MaterialTheme.colorScheme.onSurfaceVariant"))
    }
}
