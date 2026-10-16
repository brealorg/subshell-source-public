package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentSave01A3SaveIconConsistencyPolicyTest {
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

    @Test
    fun commentSaveMenuUsesSameBookmarkIdentityAsPostSave() {
        val postScreen = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/PostScreen.kt",
        )
        val common = source(
            "app/src/main/java/app/subshell/prototype/ui/components/Common.kt",
        )

        assertTrue(postScreen.contains("COMMENTSAVE01A3_COMMENT_SAVE_ICON_CONSISTENCY_V1"))
        assertTrue(postScreen.contains("Icons.Rounded.Bookmark"))
        assertTrue(postScreen.contains("Icons.Rounded.BookmarkBorder"))
        assertTrue(postScreen.contains("leadingIcon ="))
        assertTrue(common.contains("if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder"))
    }

    @Test
    fun commentSaveMenuPreservesFilledAndOutlineSavedSemantics() {
        val postScreen = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/PostScreen.kt",
        )

        assertTrue(postScreen.contains("Text(if (saved) \"Unsave comment\" else \"Save comment\")"))
        assertTrue(postScreen.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(postScreen.contains("MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(postScreen.contains("contentDescription = null"))
    }
}
