package io.github.brealorg.subshell.data.privateprefs

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import io.github.brealorg.subshell.data.cache.CipherCachePayloadSeal
import io.github.brealorg.subshell.data.cache.DataKeyVault
import io.github.brealorg.subshell.data.cache.EnvelopeCachePayloadSeal
import io.github.brealorg.subshell.data.cache.SoftwareAesCipher
import io.github.brealorg.subshell.data.cache.SoftwareGcmCipher
import io.github.brealorg.subshell.ui.communityMutesPreferenceKey
import io.github.brealorg.subshell.ui.hiddenCommunityNamesPreferenceKey
import io.github.brealorg.subshell.ui.userTagsPreferenceKey
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATECACHE06_THE_DATA_KEY_IS_KEPT_IN_MEMORY_V1 (#296, 1.0.1): the reader's settings written as e1
 * by 1.0.0, or in plain text by rc.2, become e2 and read back unchanged; one that cannot be opened is
 * left as it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivatePrefs06MigrationToE2Test {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val raw: SharedPreferences = context.getSharedPreferences("privateprefs06", Context.MODE_PRIVATE)
    private val account = "reddit:me"
    private val hiddenKey = hiddenCommunityNamesPreferenceKey(account)
    private val notesKey = userTagsPreferenceKey(account)
    private val mutesKey = communityMutesPreferenceKey(account)
    private val keystore = SoftwareAesCipher(keyByte = 1)

    @Before
    fun clean() {
        raw.edit().clear().commit()
    }

    private fun e1() = SealedKeysPreferences(raw, { CipherCachePayloadSeal(keystore) })

    private fun e2(wrapper: SoftwareAesCipher = keystore) = SealedKeysPreferences(
        raw,
        {
            EnvelopeCachePayloadSeal(
                SoftwareGcmCipher(DataKeyVault(wrapper, File(folder.root, "key"))::key),
                wrapper,
            )
        },
    )

    @Test
    fun e1AndPlainValuesBecomeE2AndReadBackUnchanged() {
        e1().edit().putStringSet(hiddenKey, mutableSetOf("secretcommunity")).putString(notesKey, "a private note").commit()
        raw.edit().putString(mutesKey, "plain from rc.2").commit()
        assertTrue(raw.getString(hiddenKey, "")!!.startsWith(CachePayloadSeal.SEALED_PREFIX))

        val now = e2()
        assertEquals("e1 values still read before they are swept", setOf("secretcommunity"), now.getStringSet(hiddenKey, mutableSetOf()))
        assertEquals(3, now.sealLegacyValues())

        listOf(hiddenKey, notesKey, mutesKey).forEach { key ->
            assertTrue("$key is e2", raw.getString(key, "")!!.startsWith(CachePayloadSeal.ENVELOPE_PREFIX))
        }
        val reopened = e2()
        assertEquals(setOf("secretcommunity"), reopened.getStringSet(hiddenKey, mutableSetOf()))
        assertEquals("a private note", reopened.getString(notesKey, ""))
        assertEquals("plain from rc.2", reopened.getString(mutesKey, ""))
        assertEquals(0, reopened.sealLegacyValues())
        assertEquals(3, reopened.status().opens)
    }

    @Test
    fun aValueThatCannotBeOpenedIsLeftAsItIs() {
        SealedKeysPreferences(raw, { CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 9)) })
            .edit().putString(notesKey, "sealed by a key that is gone").commit()
        val stored = raw.getString(notesKey, "")

        val now = e2()
        assertEquals(0, now.sealLegacyValues())

        assertEquals(stored, raw.getString(notesKey, ""))
        assertEquals(1, now.status().cannotOpen)
    }
}
