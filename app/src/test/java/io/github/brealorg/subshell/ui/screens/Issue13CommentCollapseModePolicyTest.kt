package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.harness.SUBSHELL_PACKAGE_DIR
import io.github.brealorg.subshell.ui.CommentCollapseMode
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.SubshellState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue13CommentCollapseModePolicyTest {
    @Test
    fun commentIsTheDefaultAndEveryModeRoundTrips() {
        assertEquals(CommentCollapseMode.COMMENT, SubshellSettings().commentCollapseMode)
        assertEquals(
            listOf("Header", "Comment", "Header and comment"),
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
            CommentCollapseMode.COMMENT,
            SubshellSettings.fromStorageMap(legacyWithoutMode).commentCollapseMode,
        )
    }

    @Test
    fun bothCollapsesFromTheHeaderAndFromTheComment() {
        assertTrue(CommentCollapseMode.BOTH.collapsesFromHeader)
        assertTrue(CommentCollapseMode.BOTH.collapsesFromComment)
        assertTrue(CommentCollapseMode.HEADER.collapsesFromHeader)
        assertTrue(!CommentCollapseMode.HEADER.collapsesFromComment)
        assertTrue(!CommentCollapseMode.COMMENT.collapsesFromHeader)
        assertTrue(CommentCollapseMode.COMMENT.collapsesFromComment)
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

        assertTrue(source.contains("state.commentCollapseMode.collapsesFromHeader"))
        assertTrue(source.contains("state.commentCollapseMode.collapsesFromComment"))
        assertTrue(source.contains("Modifier.clickable { onToggleComment(comment.id) }"))
        // ISSUE295_MENU_OFFERS_ONLY_WHAT_THE_CARD_DOES_NOT_V1: the comment menu's
        // "Collapse thread" (onCollapse) is gone; the header's collapse button and
        // the mode-gated tap targets above still toggle the comment.
        assertTrue(!source.contains("onCollapse = { onToggleComment(comment.id) }"))
        assertTrue(source.contains("contentDescription = if (collapsed) \"Expand thread\" else \"Collapse thread\""))
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
        assertTrue(preview.contains("collapseMode.collapsesFromHeader"))
        assertTrue(preview.contains("collapseMode.collapsesFromComment"))
        assertTrue(preview.contains("\"Comment collapsed\""))
        assertTrue(preview.contains("LaunchedEffect(collapseMode)"))
        assertTrue(preview.contains("collapseTargetDemoActive"))
        assertTrue(preview.contains("comment-collapse-header-demo"))
        assertTrue(preview.contains("comment-collapse-body-demo"))
        assertTrue(preview.contains("AnimatedVisibility("))
    }

    private fun source(relative: String): String {
        val path = File("src/main/java/${SUBSHELL_PACKAGE_DIR}/$relative")
        check(path.isFile) { "Missing source fixture: ${path.path}" }
        return path.readText()
    }
}
