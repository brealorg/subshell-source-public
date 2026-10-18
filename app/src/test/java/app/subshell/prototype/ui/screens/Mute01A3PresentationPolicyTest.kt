package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.symbols.Symbols
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A3PresentationPolicyTest {
    @Test
    fun joinedRowsExposeDedicatedMuteAction() {
        val source = File("src/main/java/app/subshell/prototype/ui/screens/CommunitiesScreen.kt").readText()
        assertTrue(source.contains("state.requestCommunityMuteEditor(community.name)"))
        assertTrue(source.contains("Symbols.VolumeOff"))
        assertTrue(source.contains("Symbols.VolumeUp"))
        // Community navigation is owned by NEXT01A5; MUTE01A3 only owns the dedicated mute affordance.
    }

    @Test
    fun appRoutesRequestIntoExistingA2Editor() {
        val source = File("src/main/java/app/subshell/prototype/ui/SubshellApp.kt").readText()
        assertTrue(source.contains("state.requestedCommunityMuteEditorName"))
        assertTrue(source.contains("communityMuteEditorName = requestedCommunityName"))
        assertTrue(source.contains("state.clearRequestedCommunityMuteEditor()"))
    }

    @Test
    fun aboutOnlyReplacesWholePostSection() {
        val source = File("src/main/java/app/subshell/prototype/ui/screens/CommunityScreen.kt").readText()
        assertTrue(source.contains("val mute01a3HidePosts"))
        assertTrue(source.contains("item(\"muted-community-about-only\")"))
        assertTrue(source.contains("MutedCommunityPostsHiddenCard("))
        assertTrue(source.contains("if (mute01a3HidePosts)"))
    }

    @Test
    fun settingsOwnDedicatedAnimatedPreview() {
        val settings = File("src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt").readText()
        val preview = File("src/main/java/app/subshell/prototype/ui/components/MutedCommunityDirectView.kt").readText()
        assertTrue(settings.contains("MutedCommunityDirectViewSetting("))
        assertTrue(preview.contains("When opening a muted community"))
        assertTrue(preview.contains("AnimatedContent("))
        assertTrue(preview.contains("label = \"muted-community-direct-view-preview\""))
        assertTrue(preview.contains("Posts hidden while muted"))
        // MATERIALSYMBOLS01: the preview's volume symbols mirror in right-to-left
        // layouts (it once used the non-mirrored Material icons).
        assertTrue(Symbols.VolumeOff.autoMirror && Symbols.VolumeUp.autoMirror)
    }
}
