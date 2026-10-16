package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.CommentCollapseMode
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue13CommentCollapseModePolicyTest {
    @Test
    fun headerIsBackwardCompatibleDefaultAndEveryModeRoundTrips() {
        assertEquals(CommentCollapseMode.HEADER, SubshellSettings().commentCollapseMode)
        assertEquals(
            listOf("Header", "Comment"),
            CommentCollapseMode.entries.map(CommentCollapseMode::label),
        )

        CommentCollapseMode.entries.forEach { mode ->
            val settings = SubshellSettings(commentCollapseMode = mode)
            assertEquals(
                mode,
                SubshellSettings.fromStorageMap(settings.toStorageMap()).commentCollapseMode,
            )
        }

        val legacyWithoutMode =
            SubshellSettings().toStorageMap() - SubshellSettings.COMMENT_COLLAPSE_MODE
        assertEquals(
            CommentCollapseMode.HEADER,
            SubshellSettings.fromStorageMap(legacyWithoutMode).commentCollapseMode,
        )
    }

    @Test
    fun statePersistsCollapseMode() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            persistPreference = { key, value -> persisted[key] = value },
        )

        state.updateCommentCollapseMode(CommentCollapseMode.COMMENT)

        assertEquals(CommentCollapseMode.COMMENT, state.commentCollapseMode)
        assertEquals(
            CommentCollapseMode.COMMENT.name,
            persisted[SubshellSettings.COMMENT_COLLAPSE_MODE],
        )
    }

    @Test
    fun postScreenKeepsHeaderAndBodyTargetsModeGated() {
        val source = source("ui/screens/PostScreen.kt")

        assertTrue(source.contains("state.commentCollapseMode == CommentCollapseMode.HEADER"))
        assertTrue(source.contains("state.commentCollapseMode == CommentCollapseMode.COMMENT"))
        assertTrue(source.contains("Modifier.clickable { onToggleComment(comment.id) }"))
        assertTrue(source.contains("onCollapse = { onToggleComment(comment.id) }"))
        assertTrue(source.contains("CommentContent("))
        assertTrue(source.contains("InlineCommentMedia("))
    }

    @Test
    fun commentsSettingsUseOnlyTheExistingTopLivePreview() {
        val settings = source("ui/screens/SettingsScreen.kt")
        val preview = source("ui/screens/Settings03CommentsPreview.kt")

        assertTrue(settings.contains("title = \"Collapse comments\""))
        // Successor-aware (#225, M3225_SHORT_SINGLE_CHOICE_IS_A_SEGMENTED_ROW_V1): the choice is a segmented row over the entries.
        assertTrue(settings.contains("CommentCollapseMode.entries.forEach") || settings.contains("options = CommentCollapseMode.entries,"))
        assertTrue(settings.contains("collapseMode = state.commentCollapseMode"))
        assertTrue(!settings.contains("CommentCollapseModePreview"))

        assertTrue(preview.contains("collapseMode: CommentCollapseMode"))
        assertTrue(preview.contains("var replyCollapsed by rememberSaveable"))
        assertTrue(preview.contains("collapseMode == CommentCollapseMode.HEADER"))
        assertTrue(preview.contains("collapseMode == CommentCollapseMode.COMMENT"))
        assertTrue(preview.contains("\"Comment collapsed\""))
        assertTrue(preview.contains("LaunchedEffect(collapseMode)"))
        assertTrue(preview.contains("collapseTargetDemoActive"))
        assertTrue(preview.contains("comment-collapse-header-demo"))
        assertTrue(preview.contains("comment-collapse-body-demo"))
        assertTrue(preview.contains("AnimatedVisibility("))
    }

    private fun source(relative: String): String {
        val path = File("src/main/java/app/subshell/prototype/$relative")
        check(path.isFile) { "Missing source fixture: ${path.path}" }
        return path.readText()
    }
}
