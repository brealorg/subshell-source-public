package io.github.brealorg.subshell.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.backup.PortableUserTag
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import io.github.brealorg.subshell.data.cache.CipherCachePayloadSeal
import io.github.brealorg.subshell.data.cache.SoftwareAesCipher
import io.github.brealorg.subshell.data.privateprefs.SealedKeysPreferences
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATEPREFS04_BACKUP_AND_SEALED_STORAGE_AGREE_V1: a backup is made from the opened values
 * (plain text, into the folder the reader chose), and a restore writes through the sealing layer, so
 * the settings file never holds the restored private lists in plain text and they read back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivatePrefs04BackupRoundTripComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val sealBefore = CachePayloadSeal.production
    private val raw: SharedPreferences =
        application.getSharedPreferences(SealedKeysPreferences.SETTINGS_PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun softwareSeal() {
        // The Android Keystore is not there under Robolectric: a software key stands in for it.
        CachePayloadSeal.production = { CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 1)) }
        raw.edit().clear().commit()
    }

    @After
    fun sealBack() {
        CachePayloadSeal.production = sealBefore
    }

    private fun launchedState(): SubshellState {
        ensureWorkManagerInitialized(application)
        var state: SubshellState? = null
        composeRule.setContent { state = rememberSubshellState() }
        composeRule.waitForIdle()
        return requireNotNull(state)
    }

    private fun activeAccountId(state: SubshellState): String {
        state.toggleHiddenCommunity("findtheaccount")
        val id = state.captureHiddenCommunityNamesByAccount().entries.first { "findtheaccount" in it.value }.key
        state.toggleHiddenCommunity("findtheaccount")
        return id
    }

    @Test
    fun theBackupSeesPlainValuesWhileTheFileHoldsSealedOnes() {
        val state = launchedState()
        val id = activeAccountId(state)

        state.toggleHiddenCommunity("secretcommunity")

        assertTrue(state.captureHiddenCommunityNamesByAccount().getValue(id).contains("secretcommunity"))
        val stored = raw.getString(hiddenCommunityNamesPreferenceKey(id), null).orEmpty()
        assertTrue("stored sealed", stored.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse("not in the file as text", stored.contains("secretcommunity"))
    }

    @Test
    fun aRestoreWritesTheRestoredPrivateListsSealedAndTheyReadBack() {
        val state = launchedState()
        val id = activeAccountId(state)

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            hiddenCommunityNamesByAccount = mapOf(id to setOf("restoredhidden")),
            userTagsByAccount = mapOf(
                id to mapOf("someone" to PortableUserTag(label = "friend", color = "GREEN", note = "a private note about someone")),
            ),
            savedSearchesByAccount = mapOf(
                id to listOf(SavedSearch(query = "private query", scope = SearchScope.ALL, area = SearchArea.ALL_SUBSHELL)),
            ),
        )

        val keys = listOf(
            hiddenCommunityNamesPreferenceKey(id) to "restoredhidden",
            userTagsPreferenceKey(id) to "private note",
            savedSearchesPreferenceKey(id) to "private query",
        )
        keys.forEach { (key, plain) ->
            val stored = raw.all[key]?.toString().orEmpty()
            assertTrue("$key is sealed", stored.startsWith(CachePayloadSeal.SEALED_PREFIX))
            assertFalse("$key does not hold \"$plain\" as text", stored.contains(plain))
        }
        // The same values as the app shows and as the next backup would hold.
        assertTrue(state.hiddenCommunityNames.contains("restoredhidden"))
        assertEquals(setOf("restoredhidden"), state.captureHiddenCommunityNamesByAccount().getValue(id))
        // After a restart: a fresh layer over the same file opens what was written. (The user tag and
        // saved search exports list signed-in accounts only; the fixture account here is local.)
        val reopened: SharedPreferences = SealedKeysPreferences(raw, { CachePayloadSeal.production(application) })
        assertEquals(setOf("restoredhidden"), reopened.getStringSet(hiddenCommunityNamesPreferenceKey(id), emptySet()))
        assertTrue(reopened.getString(userTagsPreferenceKey(id), "").orEmpty().contains("a private note about someone"))
        assertTrue(reopened.getString(savedSearchesPreferenceKey(id), "").orEmpty().contains("private query"))
    }
}
