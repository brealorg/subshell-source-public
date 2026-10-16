package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A34R1FeedsPreviewPolishPolicyTest {
    @Test
    fun directVisitShowPostsUsesOneCompletePreviewPost() {
        val source = File(
            "src/main/java/app/subshell/prototype/ui/components/" +
                "MutedCommunityDirectView.kt",
        ).readText()

        assertTrue(source.contains("\"Community post\""))
        assertFalse(source.contains("\"Another community post\""))
    }

    @Test
    fun richWorkspaceRemainsFixedSizeWithCompactSubtitle() {
        val source = File(
            "src/main/java/app/subshell/prototype/ui/screens/" +
                "Settings03FeedsPreview.kt",
        ).readText()

        assertTrue(source.contains(".height(176.dp)"))
        assertTrue(
            source.contains(
                "\"Feed and community behaviour.\"",
            ),
        )
        assertFalse(
            source.contains(
                "\"Feed, filters and community behaviour.\"",
            ),
        )
    }

    @Test
    fun contextualModelRemainsIntact() {
        val source = File(
            "src/main/java/app/subshell/prototype/ui/screens/" +
                "Settings03FeedsPreview.kt",
        ).readText()

        assertTrue(
            source.contains(
                "FeedsSettingsPreviewFocus.MUTED_COMMUNITY",
            ),
        )
        assertTrue(source.contains("FeedsPreviewHomeCanvas("))
        assertTrue(source.contains("Crossfade("))
        assertTrue(
            source.contains(
                "label = \"feeds-settings-rich-context-preview\"",
            ),
        )
    }
}
