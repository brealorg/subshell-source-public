package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue16SwipeActionsSettingsPresentationPolicyTest {
    @Test
    fun navigationAndGesturesOwnsExactlyOneSharedSwipePreview() {
        val screen = source("ui/screens/SettingsScreen.kt")
        val preview = source("ui/screens/Settings03NavigationPreview.kt")
        val swipeUi = source("ui/screens/Settings03SwipeActionsSheet.kt")

        val livePreviewNeedle = "stickyHeader(key = \"navigation-live-preview\")"
        assertEquals(
            1,
            screen.windowed(livePreviewNeedle.length)
                .count { window -> window == livePreviewNeedle },
        )
        assertTrue(screen.contains("ISSUE16_INLINE_EXPANSION_PREVIEW_CONTEXT_OWNERSHIP_V1"))
        assertTrue(screen.contains("expandedSwipeActionsTargetName"))
        assertTrue(screen.contains("NavigationPreviewContext.POST_SWIPE"))
        assertTrue(screen.contains("NavigationPreviewContext.COMMENT_SWIPE"))

        assertTrue(preview.contains("ISSUE16_SINGLE_SHARED_NAVIGATION_SWIPE_PREVIEW_V1"))
        assertTrue(preview.contains("SwipeActionSurface("))
        assertTrue(preview.contains("onLeft = {}"))
        assertTrue(preview.contains("onRight = {}"))
        assertTrue(preview.contains("NavigationPreviewContext.POST_SWIPE -> \"Post\""))
        assertTrue(preview.contains("NavigationPreviewContext.COMMENT_SWIPE -> \"Comment\""))

        assertFalse(swipeUi.contains("SwipeActionSurface("))
    }

    @Test
    fun swipeEditorIsInlineAndUsesSingleChoiceRadioDialogs() {
        val screen = source("ui/screens/SettingsScreen.kt")
        val swipeUi = source("ui/screens/Settings03SwipeActionsSheet.kt")

        assertTrue(screen.contains("ISSUE16_INLINE_EXPANDABLE_SWIPE_SETTINGS_V1"))
        assertTrue(screen.contains("SwipeActionsSettingsSummary("))
        assertTrue(screen.contains("SettingsSection(\"Side menu\")"))

        assertTrue(swipeUi.contains("ISSUE16_INLINE_EXPANDABLE_SWIPE_SETTINGS_V1"))
        assertTrue(swipeUi.contains("ISSUE16_M3_RADIO_SINGLE_CHOICE_SETTINGS_V1"))
        assertTrue(swipeUi.contains("RadioButton("))
        assertTrue(swipeUi.contains("selectableGroup()"))
        assertTrue(swipeUi.contains("role = Role.RadioButton"))
        assertTrue(swipeUi.contains("ListItem("))

        assertFalse(swipeUi.contains("ModalBottomSheet("))
        assertFalse(swipeUi.contains("FilterChip("))
        assertFalse(swipeUi.contains("horizontalScroll"))
        assertFalse(screen.contains("SwipeActionsConfigurationSheet("))
    }

    @Test
    fun previewAndEditorDoNotOwnRedditMutationBoundaries() {
        val preview = source("ui/screens/Settings03NavigationPreview.kt")
        val swipeUi = source("ui/screens/Settings03SwipeActionsSheet.kt")
        val combined = preview + "\n" + swipeUi

        assertFalse(combined.contains("toggleSaved("))
        assertFalse(combined.contains("togglePostHidden("))
        assertFalse(combined.contains("state.vote("))
        assertFalse(combined.contains("RedditPostSaveRequest"))
        assertFalse(combined.contains("RedditVoteRequest"))
    }

    private fun source(relative: String): String =
        File("src/main/java/app/subshell/prototype/$relative").readText()
}
