package io.github.brealorg.subshell.data.privateprefs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import io.github.brealorg.subshell.data.cache.CipherCachePayloadSeal
import io.github.brealorg.subshell.data.cache.SoftwareAesCipher
import io.github.brealorg.subshell.ui.hiddenCommunityNamesPreferenceKey
import io.github.brealorg.subshell.ui.userTagsPreferenceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATEPREFS03_PRIVATE_DATA_STATUS_V1: the counts the Diagnostics screen shows say whether a lost
 * key has made private values unreadable, and never carry a value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivatePrefs03StatusTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val raw = context.getSharedPreferences("privateprefs03", Context.MODE_PRIVATE)
    private val hiddenKey = hiddenCommunityNamesPreferenceKey("reddit:me")
    private val notesKey = userTagsPreferenceKey("reddit:me")

    @Before
    fun clean() {
        raw.edit().clear().commit()
    }

    private fun prefs(keyByte: Int) =
        SealedKeysPreferences(raw, { CipherCachePayloadSeal(SoftwareAesCipher(keyByte)) })

    @Test
    fun anEmptyFileCountsNothing() {
        assertEquals(PrivateDataStatus(0, 0, 0), prefs(1).status())
        assertTrue(prefs(1).status().line().contains("Nothing private"))
    }

    @Test
    fun valuesThatOpenAreCountedAndOtherSettingsAreNot() {
        val prefs = prefs(1)
        prefs.edit().putStringSet(hiddenKey, mutableSetOf("a")).putString(notesKey, "n")
            .putBoolean("show_nsfw_posts", true).commit()

        val status = prefs.status()

        assertEquals(PrivateDataStatus(opens = 2, cannotOpen = 0, keptCopies = 0), status)
        assertTrue(status.line().contains("2 sealed values, all of them open"))
    }

    @Test
    fun aLostKeyShowsAsValuesThatCannotBeOpened() {
        prefs(1).edit().putStringSet(hiddenKey, mutableSetOf("a")).putString(notesKey, "n").commit()

        val status = prefs(2).status()

        assertEquals(PrivateDataStatus(opens = 0, cannotOpen = 2, keptCopies = 0), status)
        assertTrue(status.line().contains("2 cannot be opened"))
    }

    @Test
    fun aKeptCopyIsCountedAndTheLineNeverHoldsAValue() {
        prefs(1).edit().putString(notesKey, "very private note").commit()
        val other = prefs(2)
        other.edit().putString(notesKey, "new private text").commit()

        val status = other.status()

        assertEquals(PrivateDataStatus(opens = 1, cannotOpen = 0, keptCopies = 1), status)
        assertFalse(status.line().contains("private note"))
        assertFalse(status.line().contains(CachePayloadSeal.SEALED_PREFIX))
    }
}
