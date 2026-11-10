package io.github.brealorg.subshell.data.privateprefs

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.brealorg.subshell.data.cache.AndroidKeystoreSealCipher
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import io.github.brealorg.subshell.data.cache.CipherCachePayloadSeal
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRIVATEPREFS01_PRIVATE_SETTINGS_ARE_SEALED_V1 on a real device: a settings file with a real
 * Keystore key of its own (deleted again), a hidden-community set and a note stored sealed and read
 * back, and a plain value from an older build sealed once.
 */
@RunWith(AndroidJUnit4::class)
class PrivatePrefs01KeystoreInstrumentedTest {
    private val alias = "io.github.brealorg.subshell.cache.instrumented-prefs-test"
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raw = context.getSharedPreferences("privateprefs01-instrumented", Context.MODE_PRIVATE)

    @After
    fun clean() {
        raw.edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private fun prefs() = SealedKeysPreferences(raw, { CipherCachePayloadSeal(AndroidKeystoreSealCipher(alias)) })

    @Test
    fun aSetAndANoteAreSealedAndReadBackWithTheRealKeystore() {
        raw.edit().clear().commit()
        prefs().edit()
            .putStringSet("hidden_community_names:reddit:me", mutableSetOf("secretcommunity"))
            .putString("user_tags:reddit:me", "u/someone|a private note")
            .commit()

        val storedSet = raw.getString("hidden_community_names:reddit:me", null)!!
        val storedNote = raw.getString("user_tags:reddit:me", null)!!
        assertTrue(storedSet.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(storedSet.contains("secretcommunity"))
        assertFalse(storedNote.contains("private note"))
        // A new object over the same file and key, as after a restart.
        val again = prefs()
        assertEquals(setOf("secretcommunity"), again.getStringSet("hidden_community_names:reddit:me", mutableSetOf()))
        assertEquals("u/someone|a private note", again.getString("user_tags:reddit:me", ""))
    }

    @Test
    fun aPlainValueFromAnOlderBuildIsSealedOnce() {
        raw.edit().clear().commit()
        raw.edit().putStringSet("community_mutes:reddit:me", setOf("pics|0")).commit()

        val prefs = prefs()
        assertEquals(1, prefs.sealLegacyValues())

        assertTrue(raw.getString("community_mutes:reddit:me", null)!!.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertEquals(setOf("pics|0"), prefs.getStringSet("community_mutes:reddit:me", mutableSetOf()))
    }
}
