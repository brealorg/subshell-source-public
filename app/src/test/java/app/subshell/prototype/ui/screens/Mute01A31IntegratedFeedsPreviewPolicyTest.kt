package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A31IntegratedFeedsPreviewPolicyTest {
    @Test
    fun selectedMuteModeFlowsIntoExistingFeedsLivePreview() {
        val settings = File(
            "src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt",
        ).readText()
        val feedsPreview = File(
            "src/main/java/app/subshell/prototype/ui/screens/" +
                "Settings03FeedsPreview.kt",
        ).readText()

        assertTrue(
            settings.contains(
                "mutedCommunityDirectViewMode =",
            ),
        )
        assertTrue(
            feedsPreview.contains(
                "mutedCommunityDirectViewMode: MutedCommunityDirectViewMode",
            ),
        )
        assertTrue(
            feedsPreview.contains(
                "MutedCommunityDirectViewLivePreview(",
            ),
        )
    }

    @Test
    fun standalonePreviewUnderSettingIsRetired() {
        val muteUi = File(
            "src/main/java/app/subshell/prototype/ui/components/" +
                "MutedCommunityDirectView.kt",
        ).readText()

        assertTrue(muteUi.contains("MutedCommunityDirectViewSetting("))
        assertTrue(muteUi.contains("MutedCommunityDirectViewLivePreview("))
        assertTrue(muteUi.contains("AnimatedContent("))
        assertFalse(
            muteUi.contains(
                "MutedCommunityDirectViewPreview(mode = mode)",
            ),
        )
    }
}
