package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.ui.CommunityDiscoveryLayout
import app.subshell.prototype.ui.SubshellSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Subs05A1ContextualDiscoveryRowsPolicyTest {
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
    fun discoveryLayoutIsPersistedAndDefaultsToCarousel() {
        val defaults = SubshellSettings()
        assertEquals(CommunityDiscoveryLayout.CAROUSEL, defaults.communityDiscoveryLayout)

        val restoredList = SubshellSettings.fromStorageMap(
            defaults.toStorageMap() + mapOf(
                SubshellSettings.COMMUNITY_DISCOVERY_LAYOUT to
                    CommunityDiscoveryLayout.LIST.name,
            ),
        )
        assertEquals(CommunityDiscoveryLayout.LIST, restoredList.communityDiscoveryLayout)
        assertEquals(
            CommunityDiscoveryLayout.LIST.name,
            restoredList.toStorageMap().getValue(SubshellSettings.COMMUNITY_DISCOVERY_LAYOUT),
        )
    }

    @Test
    fun joinedSurfaceSupportsOriginalCarouselAndContextualListWithSharedOwners() {
        val screen = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/CommunitiesScreen.kt",
        )

        assertTrue(screen.contains("SUBSHELL_SUBS05A1_DISCOVERY_LAYOUT_PREFERENCE_V2"))
        assertTrue(screen.contains("CommunityDiscoveryLayout.CAROUSEL -> LazyRow("))
        assertTrue(screen.contains("private fun InlineCommunityDiscoveryCard("))
        assertTrue(screen.contains("CommunityDiscoveryLayout.LIST -> InlineCommunityDiscoveryRow("))
        assertTrue(screen.contains("Search communities by name or topic"))
        assertTrue(screen.contains("Popular communities"))
        assertTrue(screen.contains("communityMeta(community)"))
        assertTrue(screen.contains("INLINE_COMMUNITY_DISCOVERY_CAROUSEL_LIMIT = 6"))
        assertTrue(screen.contains("INLINE_COMMUNITY_DISCOVERY_LIST_LIMIT = 3"))
        assertTrue(screen.contains("onSearch = { state.openCommunityExplore(focusSearch = true) }"))
        assertTrue(screen.contains("onSeeAll = state::openCommunityExplore"))
        assertTrue(screen.contains("communityMembershipState.projectedJoined("))
        assertTrue(screen.contains("communityMembershipState.blocksActionFor("))
        assertTrue(screen.contains("communityMembershipActionForJoined(joined)"))
    }

    @Test
    fun settingsExposeStableLivePreviewForCarouselAndList() {
        val settings = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt",
        )
        val preview = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/Settings03NavigationPreview.kt",
        )

        assertTrue(settings.contains("title = \"Community discovery\""))
        // Successor-aware (#225, M3225_SHORT_SINGLE_CHOICE_IS_A_SEGMENTED_ROW_V1): the choice is a segmented row over the entries.
        assertTrue(settings.contains("CommunityDiscoveryLayout.entries.forEach") || settings.contains("options = CommunityDiscoveryLayout.entries,"))
        assertTrue(settings.contains("NavigationPreviewContext.COMMUNITY_DISCOVERY.name"))
        assertTrue(settings.contains("state.updateCommunityDiscoveryLayout(layout)"))

        assertTrue(preview.contains("SUBSHELL_SUBS05A1_DISCOVERY_SETTINGS_PREVIEW_V2"))
        assertTrue(preview.contains("NavigationPreviewContext.COMMUNITY_DISCOVERY"))
        assertTrue(preview.contains("communityDiscoveryLayout.label"))
        assertTrue(preview.contains(".height(NavigationSettingsPreviewViewportHeight)"))
        assertTrue(preview.contains("CommunityDiscoveryLayout.CAROUSEL"))
        assertTrue(preview.contains("CommunityDiscoveryLayout.LIST"))
    }

    @Test
    fun candidateWindowsStayBoundedPerSelectedLayout() {
        val candidates = listOf(
            item("joined"),
            item("first"),
            item("FIRST"),
            item("second"),
            item("third"),
            item("fourth"),
            item("fifth"),
            item("sixth"),
            item("seventh"),
        )

        val carousel = inlineCommunityDiscoveryItems(
            items = candidates,
            joinedCommunityNames = setOf("joined"),
            showNsfw = false,
            limit = 6,
        )
        val list = inlineCommunityDiscoveryItems(
            items = candidates,
            joinedCommunityNames = setOf("joined"),
            showNsfw = false,
            limit = 3,
        )

        assertEquals(
            listOf("first", "second", "third", "fourth", "fifth", "sixth"),
            carousel.map { candidate -> candidate.community.name },
        )
        assertEquals(
            listOf("first", "second", "third"),
            list.map { candidate -> candidate.community.name },
        )
    }

    private fun item(name: String): CommunityDiscoveryItem =
        CommunityDiscoveryItem(
            community = Community(
                id = "t5_$name",
                name = name,
                description = "$name community description",
                members = "1k",
                accent = 0xFF884422,
            ),
            nsfw = false,
        )
}
