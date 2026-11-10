package io.github.brealorg.subshell.data.cache

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRIVATECACHE01: what one seal or one open costs with the real Keystore, so the choice of sealing
 * on the Main thread or in a loop over rows rests on a number. It reports the medians (stream
 * lines in `am instrument` output) and fails only if an operation costs more than 100 ms.
 */
@RunWith(AndroidJUnit4::class)
class PrivateCache03KeystoreCostInstrumentedTest {
    private val alias = "io.github.brealorg.subshell.cache.instrumented-cost-test"

    @After
    fun deleteTheTestKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private fun medianMicros(times: List<Long>): Long = times.sorted()[times.size / 2] / 1_000

    @Test
    fun reportsTheCostOfASealAndAnOpen() {
        val seal = CipherCachePayloadSeal(AndroidKeystoreSealCipher(alias))
        val firstUse = System.nanoTime().let { seal.seal("first use creates the key", CachePayloadSeal.PLACE_INBOX); System.nanoTime() - it }
        val small = "x".repeat(300)
        val big = "y".repeat(50_000)
        val sealedSmall = seal.seal(small, CachePayloadSeal.PLACE_INBOX)
        val sealedBig = seal.seal(big, CachePayloadSeal.PLACE_INBOX)

        fun time(block: () -> Unit): List<Long> = (1..100).map { System.nanoTime().let { start -> block(); System.nanoTime() - start } }
        val sealSmall = medianMicros(time { seal.seal(small, CachePayloadSeal.PLACE_INBOX) })
        val openSmall = medianMicros(time { seal.open(sealedSmall, CachePayloadSeal.PLACE_INBOX) })
        val sealBig = medianMicros(time { seal.seal(big, CachePayloadSeal.PLACE_INBOX) })
        val openBig = medianMicros(time { seal.open(sealedBig, CachePayloadSeal.PLACE_INBOX) })

        val line = "KEYSTORE_COST_US first_use=${firstUse / 1_000} seal_300B=$sealSmall open_300B=$openSmall seal_50KB=$sealBig open_50KB=$openBig"
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "$line\n") })
        assertTrue(line, listOf(sealSmall, openSmall, sealBig, openBig).all { it < 100_000 })
    }
}
