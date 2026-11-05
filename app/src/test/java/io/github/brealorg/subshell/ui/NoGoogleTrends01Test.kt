package io.github.brealorg.subshell.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NOGOOGLETRENDS01_SEARCH_ASKS_ONLY_REDDIT_V1: search no longer reads Google
 * Trends, and the "Trending now" list older versions stored on the device is
 * deleted; nothing else in the same preferences file is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoGoogleTrends01Test {
    @Test
    fun theStoredTrendingListIsDeletedAndNothingElse() {
        val preferences = ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("no-google-trends-01", Context.MODE_PRIVATE)
        preferences.edit()
            .putString(RETIRED_GOOGLE_TRENDS_CACHE_PREFERENCE_KEY, "1700000000000\nS290bGlu|")
            .putString("search_history.reddit:test", "volvo")
            .commit()

        forgetRetiredGoogleTrendsCache(preferences)

        assertFalse(preferences.contains(RETIRED_GOOGLE_TRENDS_CACHE_PREFERENCE_KEY))
        assertEquals("volvo", preferences.getString("search_history.reddit:test", null))

        // A second start finds nothing to delete.
        forgetRetiredGoogleTrendsCache(preferences)
        assertEquals(setOf("search_history.reddit:test"), preferences.all.keys)
    }
}
