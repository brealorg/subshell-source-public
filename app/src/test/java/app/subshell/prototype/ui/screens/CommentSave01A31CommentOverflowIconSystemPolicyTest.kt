package app.subshell.prototype.ui.screens

import java.io.File
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
            "app/src/main/java/app/subshell/prototype/ui/screens/PostScreen.kt",
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
        assertTrue(row.contains("text = { Text(\"Edit comment\") }"))
        assertTrue(row.contains("imageVector = Symbols.Edit"))
        assertTrue(row.contains("\"Delete comment\""))
        assertTrue(row.contains("imageVector = Symbols.Delete"))
        assertTrue(row.contains("Text(if (saved) \"Unsave comment\" else \"Save comment\")"))
        assertTrue(row.contains("Symbols.BookmarkFilled"))
        assertTrue(row.contains("Symbols.Bookmark"))
        assertTrue(row.contains("text = { Text(\"Share comment\") }"))
        assertTrue(row.contains("imageVector = Symbols.Share"))
        assertTrue(row.contains("text = { Text(\"Copy text\") }"))
        assertTrue(row.contains("imageVector = Symbols.ContentCopy"))
        assertTrue(row.contains("text = { Text(\"Collapse thread\") }"))
        assertTrue(row.contains("imageVector = Symbols.ExpandLess"))
        assertTrue(row.split("leadingIcon =").size - 1 >= 6)
    }

    @Test
    fun destructiveCommentActionKeepsErrorSemantics() {
        val row = commentActionRowSource()

        assertTrue(row.contains("\"Delete comment\",\n                                color = MaterialTheme.colorScheme.error"))
        assertTrue(row.contains("imageVector = Symbols.Delete"))
        assertTrue(row.contains("tint = MaterialTheme.colorScheme.error"))
    }

    @Test
    fun a3BookmarkStateSemanticsRemainIntact() {
        val row = commentActionRowSource()

        assertTrue(row.contains("COMMENTSAVE01A3_COMMENT_SAVE_ICON_CONSISTENCY_V1"))
        assertTrue(row.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(row.contains("MaterialTheme.colorScheme.onSurfaceVariant"))
    }
}
