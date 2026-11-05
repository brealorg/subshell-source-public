package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.harness.SUBSHELL_PACKAGE_DIR
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A31IntegratedFeedsPreviewPolicyTest {
    @Test
    fun selectedMuteModeFlowsIntoExistingFeedsLivePreview() {
        val settings = File(
            "src/main/java/${SUBSHELL_PACKAGE_DIR}/ui/screens/SettingsScreen.kt",
        ).readText()
        val feedsPreview = File(
            "src/main/java/${SUBSHELL_PACKAGE_DIR}/ui/screens/" +
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
            "src/main/java/${SUBSHELL_PACKAGE_DIR}/ui/components/" +
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
