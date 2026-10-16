package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Settings03NavigationPolicyTest {
    @Test
    fun rootHasNineTaskBasedDestinationsInProductOrder() {
        assertEquals(
            listOf(
                SettingsDestination.ACCOUNTS,
                SettingsDestination.APPEARANCE,
                SettingsDestination.NAVIGATION,
                SettingsDestination.FEEDS,
                SettingsDestination.MEDIA,
                SettingsDestination.COMMENTS,
                SettingsDestination.BACKUP,
                SettingsDestination.ADVANCED,
                SettingsDestination.ABOUT,
            ),
            settingsRootDestinations,
        )
    }

    @Test
    fun rootWithoutSearchDoesNotRenderTheLegacyScrollWall() {
        val blankSearch = settingsSearchResult("")
        assertFalse(
            settingsSectionVisible(
                destination = SettingsDestination.ROOT,
                query = "",
                searchResult = blankSearch,
                section = "Media & links",
                debug = true,
            ),
        )
    }

    @Test
    fun rowLevelSearchStillSurfacesMatchingLegacyRows() {
        val result = settingsSearchResult("video mobile")
        assertTrue(
            settingsSectionVisible(
                destination = SettingsDestination.ROOT,
                query = "video mobile",
                searchResult = result,
                section = "Media & links",
                debug = true,
            ),
        )
        assertEquals(setOf("video-quality-mobile"), result.itemKeys)
    }

    @Test
    fun taskPagesOwnTheirExpectedLegacySections() {
        val blankSearch = settingsSearchResult("")
        assertTrue(
            settingsSectionVisible(
                SettingsDestination.NAVIGATION,
                "",
                blankSearch,
                "Home & feeds",
                debug = false,
            ),
        )
        assertTrue(
            settingsSectionVisible(
                SettingsDestination.NAVIGATION,
                "",
                blankSearch,
                "Navigation",
                debug = false,
            ),
        )
        assertTrue(
            settingsSectionVisible(
                SettingsDestination.NAVIGATION,
                "",
                blankSearch,
                "Search",
                debug = false,
            ),
        )
        assertFalse(
            settingsSectionVisible(
                SettingsDestination.NAVIGATION,
                "",
                blankSearch,
                "Media & links",
                debug = false,
            ),
        )
    }

    @Test
    fun prototypeStatesAreDebugOnly() {
        val blankSearch = settingsSearchResult("")
        assertFalse(
            settingsSectionVisible(
                SettingsDestination.ADVANCED,
                "",
                blankSearch,
                "Prototype states",
                debug = false,
            ),
        )
        assertTrue(
            settingsSectionVisible(
                SettingsDestination.ADVANCED,
                "",
                blankSearch,
                "Prototype states",
                debug = true,
            ),
        )
    }

    @Test
    fun nestedDestinationsReturnToTheirOpeningTaskPage() {
        assertEquals(
            SettingsDestination.FEEDS,
            settingsDestinationBackTarget(
                destination = SettingsDestination.CONTENT_FILTERS,
                returnDestination = SettingsDestination.FEEDS,
            ),
        )
        assertEquals(
            SettingsDestination.MEDIA,
            settingsDestinationBackTarget(
                destination = SettingsDestination.SENSITIVE_MEDIA_LAB,
                returnDestination = SettingsDestination.MEDIA,
            ),
        )
        assertEquals(
            SettingsDestination.ROOT,
            settingsDestinationBackTarget(
                destination = SettingsDestination.NAVIGATION,
                returnDestination = SettingsDestination.MEDIA,
            ),
        )
    }
}
