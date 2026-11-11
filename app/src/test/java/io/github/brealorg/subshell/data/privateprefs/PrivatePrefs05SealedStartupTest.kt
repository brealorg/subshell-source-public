package io.github.brealorg.subshell.data.privateprefs

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import io.github.brealorg.subshell.data.cache.CipherCachePayloadSeal
import io.github.brealorg.subshell.data.cache.SlowCipher
import io.github.brealorg.subshell.data.cache.SoftwareAesCipher
import io.github.brealorg.subshell.ui.userTagsPreferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATEPREFS05_START_DOES_NOT_WAIT_FOR_THE_KEYSTORE_V1 (#296): with a slow Keystore the start-up
 * does not wait for every sealed value, and the plain values an older build stored are sealed
 * afterwards, not before the first screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivatePrefs05SealedStartupTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val raw: SharedPreferences = context.getSharedPreferences("privateprefs05", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val account = "reddit:me"

    @Before
    fun clean() {
        raw.edit().clear().commit()
    }

    @After
    fun stop() {
        scope.cancel()
    }

    private fun fast() = SealedKeysPreferences(raw, { CipherCachePayloadSeal(SoftwareAesCipher(1)) })

    private fun slow(delayMs: Long) = SealedKeysPreferences(
        raw,
        { CipherCachePayloadSeal(SlowCipher(SoftwareAesCipher(1), delayMs)) },
    )

    @Test
    fun theStartDoesNotWaitForEverySealedValueAndLegacyValuesAreSealedAfterwards() {
        val writer = fast()
        repeat(15) { index -> writer.edit().putString(userTagsPreferenceKey("reddit:user$index"), "note $index").commit() }
        val legacyKey = userTagsPreferenceKey("reddit:legacy")
        raw.edit().putString(legacyKey, "an old plain note").commit()
        // 15 sealed values at 100 ms each is 1.5 s to open them all.
        val startup = slow(delayMs = 100)

        val startedAt = System.nanoTime()
        runBlocking { SealedStartup.openThenSealInBackground(listOf(startup), scope, maxWaitMs = 300) }
        val waitedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("the start waited $waitedMs ms", waitedMs < 900)
        // The plain value reads as it is, without the Keystore, before it is sealed.
        assertEquals("an old plain note", startup.getString(legacyKey, ""))
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline && !raw.getString(legacyKey, "").orEmpty().startsWith(CachePayloadSeal.SEALED_PREFIX)) {
            Thread.sleep(50)
        }
        val stored = raw.getString(legacyKey, "").orEmpty()
        assertTrue("sealed in the background", stored.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(stored.contains("old plain note"))
        assertEquals("an old plain note", startup.getString(legacyKey, ""))
    }

    @Test
    fun withAFastKeystoreEverythingIsOpenWhenTheStartReturns() {
        val writer = fast()
        repeat(5) { index -> writer.edit().putString(userTagsPreferenceKey("reddit:user$index"), "note $index").commit() }
        val startup = fast()

        runBlocking { SealedStartup.openThenSealInBackground(listOf(startup), scope) }

        assertEquals(5, startup.status().opens)
        assertEquals("note 3", startup.getString(userTagsPreferenceKey("reddit:user3"), ""))
    }
}
