package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.MutedCommunityDirectViewMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A34RichContextualFeedsPreviewPolicyTest {
    @Test
    fun lastChangedSettingStillSelectsTheScene() {
        assertEquals(
            FeedsSettingsPreviewFocus.MUTED_COMMUNITY,
            feedsSettingsPreviewFocusAfterChange(
                currentFocus = FeedsSettingsPreviewFocus.UNREAD,
                previousShowUnreadMarker = true,
                previousShowNsfwPosts = true,
                previousMutedCommunityDirectViewMode =
                    MutedCommunityDirectViewMode.SHOW_POSTS,
                showUnreadMarker = true,
                showNsfwPosts = true,
                mutedCommunityDirectViewMode =
                    MutedCommunityDirectViewMode.ABOUT_ONLY,
            ),
        )
    }

    @Test
    fun previewUsesOneLargeFixedWorkspace() {
        val feeds = File(
            "src/main/java/app/subshell/prototype/ui/screens/" +
                "Settings03FeedsPreview.kt",
        ).readText()

        assertTrue(
            feeds.contains(
                "\"Feed and community behaviour.\"",
            ),
        )
        assertTrue(
            feeds.contains(
                "label = \"feeds-settings-rich-context-preview\"",
            ),
        )
        assertTrue(feeds.contains(".height(176.dp)"))
        assertTrue(feeds.contains("\"Unread on\""))
        assertTrue(feeds.contains("\"NSFW shown\""))
        assertTrue(
            feeds.contains(
                "snapshot.mutedCommunityDirectViewMode.label",
            ),
        )
        assertTrue(feeds.contains("FeedsPreviewHomeCanvas("))
    }

    @Test
    fun mutedSceneLooksLikeADirectCommunityVisit() {
        val muteUi = File(
            "src/main/java/app/subshell/prototype/ui/components/" +
                "MutedCommunityDirectView.kt",
        ).readText()

        assertTrue(muteUi.contains("\"‹   r/example\""))
        assertTrue(muteUi.contains("\"123k members\""))
        assertTrue(muteUi.contains("\"✓ Joined\""))
        assertTrue(muteUi.contains("\"Muted\""))
        assertTrue(muteUi.contains("\"Community post\""))
        assertTrue(
            muteUi.contains(
                "\"Posts hidden while muted\"",
            ),
        )
        assertTrue(
            muteUi.contains(
                "\"Community information remains available.\"",
            ),
        )
    }
}
