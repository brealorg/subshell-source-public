package app.subshell.prototype.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE47_FREE_TEXT_PREFERENCES_STAY_TEXT_V1: a content filter list holding
 * just "true" or "false" was stored as a Boolean, and reading it back with
 * getString crashed every launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue47FreeTextPreferenceTest {
    private val prefs = ApplicationProvider.getApplicationContext<Application>()
        .getSharedPreferences("issue47", Context.MODE_PRIVATE)
    private val words = ContentFilterType.entries.first().storageKey

    @Test
    fun aFilterListOfTheWordTrueStaysTextAndReadsBack() {
        prefs.edit { putSubshellPreference(words, "true") }

        assertEquals("true", prefs.getString(words, ""))
        assertEquals("true", prefs.freeTextPreference(words))
    }

    @Test
    fun scopedFilterListsStayTextToo() {
        val scoped = ContentFilterType.entries.first().scopedStorageKey
        prefs.edit { putSubshellPreference(scoped, "false") }

        assertEquals("false", prefs.getString(scoped, ""))
    }

    @Test
    fun aBooleanSettingIsStillStoredAsABoolean() {
        prefs.edit { putSubshellPreference("show_nsfw_posts", "true") }

        assertTrue(prefs.getBoolean("show_nsfw_posts", false))
    }

    @Test
    fun aFilterAnOlderBuildStoredAsABooleanReadsBackAsText() {
        prefs.edit { putBoolean(words, false) }

        assertEquals("false", prefs.freeTextPreference(words))
    }
}
