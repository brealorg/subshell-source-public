package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.symbols.Symbols
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A2VisibilityManagementPolicyTest {
    /** SubshellApp plus the composables it extracts into under ui/app. */
    private fun appSource(): String {
        val extracted = File("src/main/java/app/subshell/prototype/ui/app")
            .listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .sortedBy { file -> file.name }
            .joinToString("") { file -> file.readText() }
        return source("ui/SubshellApp.kt") + extracted
    }

    private fun source(relative: String): String {
        val path = File("src/main/java/app/subshell/prototype/$relative")
        check(path.isFile) { "Missing source fixture: ${path.path}" }
        return path.readText()
    }

    @Test
    fun communityPageShowsPersistentMuteStatusAndOpensSettings() {
        val source = source("ui/screens/CommunityScreen.kt")

        assertTrue(source.contains("currentCommunityMute = state.communityMuteFor"))
        assertTrue(source.contains("communityMuteStatusText("))
        assertTrue(source.contains("Hidden from Home, Popular and All"))
        assertTrue(source.contains("Open mute settings"))
        assertTrue(source.contains("currentMute = currentCommunityMute"))
    }

    @Test
    fun drawerRetainsCommunityNavigationAndAddsMuteManagement() {
        val source = appSource()

        assertTrue(source.contains("label = \"Muted communities\""))
        assertTrue(source.contains("activeCommunityMutes.size.toString()"))
        assertTrue(source.contains("badge = if (state.isCommunityMuted(community.name))"))
        assertTrue(source.contains("Mute settings for r/${'$'}{community.name}"))
        assertTrue(source.contains("state.openSubreddit(community.name)"))
        assertTrue(source.contains("MutedCommunitiesSheet("))
    }

    @Test
    fun managementSheetCanChangeOrUnmuteEveryEntry() {
        val source = source("ui/components/MutedCommunitiesSheet.kt")

        assertTrue(source.contains("communityMuteStatusText("))
        assertTrue(source.contains("communityMuteRemainingText("))
        assertTrue(source.contains("onChange(mute.communityName)"))
        assertTrue(source.contains("onUnmute(mute.communityName)"))
        assertTrue(source.contains("No muted communities"))
    }

    @Test
    fun editorShowsCurrentStatusAndExplicitUnmute() {
        val source = source("ui/components/CommunityMuteSheet.kt")

        assertTrue(source.contains("currentMute: CommunityMute? = null"))
        assertTrue(source.contains("\"Mute settings\""))
        assertTrue(source.contains("text = \"Change duration\""))
        assertTrue(source.contains("content = { Text(\"Unmute community\") }"))
    }

    @Test
    fun volumeIconsUseAutoMirroredMaterialApi() {
        val sources = listOf(
            source("ui/SubshellApp.kt"),
            source("ui/components/CommunityMuteSheet.kt"),
            source("ui/components/MutedCommunitiesSheet.kt"),
            source("ui/screens/CommunityScreen.kt"),
        )

        // MATERIALSYMBOLS01: the volume symbols mirror in right-to-left layouts.
        assertTrue(Symbols.VolumeOff.autoMirror && Symbols.VolumeUp.autoMirror)
        assertTrue(sources.any { source -> source.contains("Symbols.VolumeOff") })
        assertTrue(sources.any { source -> source.contains("Symbols.VolumeUp") })
    }

    @Test
    fun expiryIsObservedGloballyAndDebugProjectionReportsSuppression() {
        val app = appSource()
        val home = source("ui/screens/HomeScreen.kt")

        assertTrue(app.contains("val nextCommunityMuteExpiry = state.nextCommunityMuteExpiryEpochMillis"))
        assertTrue(app.contains("state.refreshCommunityMuteClock()"))
        assertTrue(home.contains("\"SubshellMute\""))
        assertTrue(home.contains("phase=projection"))
        assertTrue(home.contains("suppressed="))
        assertTrue(home.contains("matches="))
    }
}
