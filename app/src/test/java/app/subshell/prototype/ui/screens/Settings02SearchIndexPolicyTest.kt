package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class Settings02SearchIndexPolicyTest {
    @Test
    fun searchIndexHasUniqueKeysAndExpectedCoverage() {
        val keys = settingsSearchEntries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.size >= 40)
        assertTrue("about" in keys)
        assertTrue("backup" in keys)
        assertTrue("media-cache" in keys)
        assertTrue("media-loop" in keys)
        assertTrue("hidden-subscriptions-device-unlock" in keys)
    }

    @Test
    fun voteSearchIncludesVoteRelevantSwipeRowsWithoutNoise() {
        val result = settingsSearchResult("vote")
        assertEquals(
            setOf(
                "vote-controls",
                "vote-colours",
                "post-swipe-left",
                "post-swipe-right",
                "comment-swipe-left",
                "comment-swipe-right",
            ),
            result.itemKeys,
        )
        assertEquals(setOf("Appearance", "Navigation"), result.sections)
    }

    @Test
    fun loopSearchTargetsVideoLoopSetting() {
        assertEquals(
            setOf("media-loop"),
            settingsSearchResult("loop video").itemKeys,
        )
    }

    @Test
    fun multiTokenSearchTargetsSpecificRows() {
        assertEquals(
            setOf("video-quality-mobile"),
            settingsSearchResult("video mobile").itemKeys,
        )
        assertEquals(
            setOf("search-keyboard"),
            settingsSearchResult("keyboard search").itemKeys,
        )
        assertEquals(
            setOf("backup"),
            settingsSearchResult("backup export").itemKeys,
        )
    }

    @Test
    fun sectionNameSearchKeepsSectionDiscoverable() {
        val appearance = settingsSearchResult("appearance")
        assertTrue(appearance.hasMatches)
        assertEquals(setOf("Appearance"), appearance.sections)
        assertTrue("theme" in appearance.itemKeys)
        assertTrue("code-font" in appearance.itemKeys)
    }

    @Test
    fun impossibleSearchHasExplicitEmptyResult() {
        val result = settingsSearchResult("definitely-not-a-setting")
        assertFalse(result.hasMatches)
        assertTrue(result.itemKeys.isEmpty())
        assertTrue(result.sections.isEmpty())
    }

    @Test
    fun blankSearchKeepsAllSettingsVisible() {
        val result = settingsSearchResult("   ")
        assertTrue(result.hasMatches)
        assertTrue(result.isItemVisible("about"))
        assertTrue(result.isSectionVisible("Media & links"))
        assertTrue(result.itemKeys.size >= 40)
    }

    // STEP3 (settings02): the query is read without regard to case or the
    // device language; in Turkish "WIFI".lowercase() would be "wıfı", and the
    // dotless ı would split it into "w f".
    @Test
    fun searchIgnoresCaseAndTheDeviceLanguage() {
        assertEquals(setOf("video-quality-mobile"), settingsSearchResult("VIDEO Mobile").itemKeys)
        val wifi = settingsSearchResult("wifi").itemKeys
        assertEquals(setOf("video-quality-wifi"), wifi)

        val device = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(wifi, settingsSearchResult("WIFI").itemKeys)
            assertEquals(setOf("video-quality-mobile"), settingsSearchResult("VIDEO Mobile").itemKeys)
        } finally {
            Locale.setDefault(device)
        }
    }

    // STEP3 (userstatus01a1): Settings search finds the online-status row.
    @Test
    fun onlineStatusSearchFindsTheAccountPrivacyRow() {
        assertTrue("online-visibility" in settingsSearchResult("online status").itemKeys)
    }
}
