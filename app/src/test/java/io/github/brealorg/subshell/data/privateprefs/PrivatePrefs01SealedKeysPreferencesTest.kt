package io.github.brealorg.subshell.data.privateprefs

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import io.github.brealorg.subshell.data.cache.CipherCachePayloadSeal
import io.github.brealorg.subshell.data.cache.SoftwareAesCipher
import io.github.brealorg.subshell.data.drafts.ComposerDraftStore
import io.github.brealorg.subshell.data.moderation.ModeratorSavedResponseStore
import io.github.brealorg.subshell.ui.communityMutesPreferenceKey
import io.github.brealorg.subshell.ui.hiddenCommunityNamesPreferenceKey
import io.github.brealorg.subshell.ui.savedSearchesPreferenceKey
import io.github.brealorg.subshell.ui.userTagsPreferenceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATEPREFS01_PRIVATE_SETTINGS_ARE_SEALED_V1: the keys about the reader are stored sealed in the
 * settings file, the rest of the file is untouched, and nothing the reader wrote is lost when a
 * value cannot be opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivatePrefs01SealedKeysPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val raw: SharedPreferences = context.getSharedPreferences("privateprefs01", Context.MODE_PRIVATE)
    private val account = "reddit:me"
    private val hiddenKey = hiddenCommunityNamesPreferenceKey(account)
    private val notesKey = userTagsPreferenceKey(account)

    @Before
    fun clean() {
        raw.edit().clear().commit()
    }

    private fun seal(keyByte: Int, failToSeal: Boolean = false): () -> CachePayloadSeal =
        { CipherCachePayloadSeal(SoftwareAesCipher(keyByte, failToSeal)) }

    private fun prefs(keyByte: Int = 1, failToSeal: Boolean = false) =
        SealedKeysPreferences(raw, seal(keyByte, failToSeal))

    @Test
    fun everyKeyTheAppWritesAboutTheReaderIsOneOfTheSealedKeys() {
        val aboutTheReader = listOf(
            hiddenKey,
            communityMutesPreferenceKey(account),
            notesKey,
            savedSearchesPreferenceKey(account),
            ComposerDraftStore.KEY,
            ComposerDraftStore.CROSSPOST_KEY,
            ModeratorSavedResponseStore.KEY,
            ModeratorSavedResponseStore.UNREADABLE_KEY,
        )
        aboutTheReader.forEach { assertTrue("$it is sealed", isPrivatePreferenceKey(it)) }
        listOf("show_nsfw_posts", "theme_mode", "active_account_id", "post_layout", "app_text_scale")
            .forEach { assertFalse("$it is not sealed", isPrivatePreferenceKey(it)) }
    }

    @Test
    fun aSetAndATextAreStoredSealedAndComeBackAsTheTypeTheyWere() {
        val prefs = prefs()
        prefs.edit()
            .putStringSet(hiddenKey, mutableSetOf("secretcommunity", "another"))
            .putString(notesKey, "u/someone|private note about someone")
            .commit()

        val storedSet = raw.getString(hiddenKey, null)
        val storedText = raw.getString(notesKey, null)
        assertTrue(storedSet!!.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertTrue(storedText!!.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(storedSet.contains("secretcommunity"))
        assertFalse(storedText.contains("someone"))
        assertEquals(setOf("secretcommunity", "another"), prefs.getStringSet(hiddenKey, mutableSetOf()))
        assertEquals("u/someone|private note about someone", prefs.getString(notesKey, ""))
        assertTrue(prefs.contains(hiddenKey))
    }

    @Test
    fun anEmptySetAndAnEmptyTextAreKeptAndAMissingKeyKeepsItsDefault() {
        val prefs = prefs()
        prefs.edit().putStringSet(hiddenKey, mutableSetOf()).commit()

        assertEquals(emptySet<String>(), prefs.getStringSet(hiddenKey, mutableSetOf("default")))
        assertEquals("default", prefs.getString(notesKey, "default"))
        assertNull(prefs.getStringSet(communityMutesPreferenceKey(account), null))
    }

    @Test
    fun otherKeysAreNotTouched() {
        val prefs = prefs()
        prefs.edit()
            .putBoolean("show_nsfw_posts", true)
            .putString("theme", "dark")
            .putInt("ui_size", 110)
            .putStringSet("some_unrelated_set", mutableSetOf("pics"))
            .commit()

        assertEquals("dark", raw.getString("theme", null))
        assertEquals(true, raw.getBoolean("show_nsfw_posts", false))
        assertEquals(110, raw.getInt("ui_size", 0))
        assertEquals(setOf("pics"), raw.getStringSet("some_unrelated_set", null))
        assertEquals(setOf("pics"), prefs.getStringSet("some_unrelated_set", null))
    }

    @Test
    fun removeAndClearWork() {
        val prefs = prefs()
        prefs.edit().putString(notesKey, "note").putStringSet(hiddenKey, mutableSetOf("a")).commit()
        prefs.edit().remove(notesKey).commit()

        assertFalse(prefs.contains(notesKey))
        assertTrue(prefs.contains(hiddenKey))

        prefs.edit().clear().commit()
        assertTrue(raw.all.isEmpty())
    }

    @Test
    fun allShowsTheOpenedValuesForPrefixScans() {
        val prefs = prefs()
        prefs.edit().putStringSet(hiddenKey, mutableSetOf("a")).putString("theme", "dark").commit()

        val all = prefs.all
        assertEquals(setOf("a"), all[hiddenKey])
        assertEquals("dark", all["theme"])
        assertTrue(all.keys.any { it.startsWith(hiddenCommunityNamesPreferenceKey("")) })
    }

    @Test
    fun valuesAnOlderBuildWroteInPlainTextAreReadAndSealedOnce() {
        raw.edit()
            .putStringSet(hiddenKey, setOf("oldhidden"))
            .putString(notesKey, "old note")
            .putString("theme", "dark")
            .commit()
        val prefs = prefs()

        // Read as they are, before they are sealed.
        assertEquals(setOf("oldhidden"), prefs.getStringSet(hiddenKey, mutableSetOf()))
        assertEquals("old note", prefs.getString(notesKey, ""))

        assertEquals(2, prefs.sealLegacyValues())

        assertTrue(raw.getString(hiddenKey, null)!!.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertTrue(raw.getString(notesKey, null)!!.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertEquals("dark", raw.getString("theme", null))
        assertEquals(setOf("oldhidden"), prefs.getStringSet(hiddenKey, mutableSetOf()))
        assertEquals("old note", prefs.getString(notesKey, ""))
        assertEquals(0, prefs.sealLegacyValues())
    }

    @Test
    fun aValueThatCannotBeOpenedReadsAsMissingAndIsKeptWhenWrittenOver() {
        prefs(keyByte = 1).edit().putStringSet(hiddenKey, mutableSetOf("secretcommunity")).commit()
        val sealedBefore = raw.getString(hiddenKey, null)!!

        val otherKey = prefs(keyByte = 2)
        assertEquals(emptySet<String>(), otherKey.getStringSet(hiddenKey, mutableSetOf()))
        assertNull(otherKey.getString(hiddenKey, null))

        otherKey.edit().putStringSet(hiddenKey, mutableSetOf("new")).commit()

        // The new value is there; the old one is kept, still sealed, under its own key.
        assertEquals(setOf("new"), otherKey.getStringSet(hiddenKey, mutableSetOf()))
        assertEquals(sealedBefore, raw.getString(SealedKeysPreferences.UNREADABLE_KEY_PREFIX + hiddenKey, null))
        // Written over again, the first kept value stays.
        otherKey.edit().putStringSet(hiddenKey, mutableSetOf("newer")).commit()
        assertEquals(sealedBefore, raw.getString(SealedKeysPreferences.UNREADABLE_KEY_PREFIX + hiddenKey, null))
    }

    @Test
    fun removingAValueAlsoRemovesTheCopyKeptWhenItCouldNotBeOpened() {
        prefs(keyByte = 1).edit().putStringSet(hiddenKey, mutableSetOf("secretcommunity")).commit()
        val otherKey = prefs(keyByte = 2)
        otherKey.edit().putStringSet(hiddenKey, mutableSetOf("new")).commit()
        val keptKey = SealedKeysPreferences.UNREADABLE_KEY_PREFIX + hiddenKey
        assertTrue(raw.contains(keptKey))

        otherKey.edit().remove(hiddenKey).commit()

        assertFalse(raw.contains(hiddenKey))
        assertFalse("the kept copy goes with the value", raw.contains(keptKey))
    }

    @Test
    fun settingAValueToNullRemovesItsKeptCopyToo() {
        prefs(keyByte = 1).edit().putString(notesKey, "private").commit()
        val otherKey = prefs(keyByte = 2)
        otherKey.edit().putString(notesKey, "new").commit()
        assertTrue(raw.contains(SealedKeysPreferences.UNREADABLE_KEY_PREFIX + notesKey))

        otherKey.edit().putString(notesKey, null).commit()

        assertFalse(raw.contains(notesKey))
        assertFalse(raw.contains(SealedKeysPreferences.UNREADABLE_KEY_PREFIX + notesKey))
    }

    @Test
    fun aValueThatCannotBeSealedIsNotWrittenAndTheOldOneStays() {
        prefs().edit().putStringSet(hiddenKey, mutableSetOf("kept")).commit()
        val sealedBefore = raw.getString(hiddenKey, null)

        val broken = prefs(failToSeal = true)
        broken.edit().putStringSet(hiddenKey, mutableSetOf("lost")).putString(notesKey, "never plain").commit()

        assertEquals(sealedBefore, raw.getString(hiddenKey, null))
        assertFalse(raw.contains(notesKey))
        assertFalse(raw.all.values.any { it.toString().contains("never plain") || it.toString().contains("lost") })
    }

    @Test
    fun theDraftsAndTheSavedResponsesGoThroughTheSameLayer() {
        val prefs = prefs()
        prefs.edit().putString(ComposerDraftStore.KEY, "[{\"title\":\"a private draft\"}]").commit()
        prefs.edit().putString(ModeratorSavedResponseStore.KEY, "[{\"body\":\"a saved reply\"}]").commit()

        assertFalse(raw.getString(ComposerDraftStore.KEY, "")!!.contains("private draft"))
        assertFalse(raw.getString(ModeratorSavedResponseStore.KEY, "")!!.contains("saved reply"))
        assertTrue(prefs.getString(ComposerDraftStore.KEY, "")!!.contains("a private draft"))
        assertTrue(prefs.getString(ModeratorSavedResponseStore.KEY, "")!!.contains("a saved reply"))
    }

    @Test
    fun aWriteToTheSameKeyManyTimesKeepsOneRememberedValue() {
        val prefs = prefs()
        repeat(50) { index -> prefs.edit().putString(notesKey, "draft $index").commit() }

        assertEquals("draft 49", prefs.getString(notesKey, ""))
    }
}
