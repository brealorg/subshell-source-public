package io.github.brealorg.subshell.debugkeystore

import io.github.brealorg.subshell.data.cache.SealCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SLOWSEAL01_A_DEV_BUILD_CAN_HAVE_A_SLOW_KEYSTORE_V1: the delay file and the cipher that uses it. */
class SlowSealTest {
    @Test
    fun theFileAsksForADelayOnlyWhenItHoldsANumber() {
        assertEquals(0L, parseSealDelayMs(null))
        assertEquals(0L, parseSealDelayMs(""))
        assertEquals(0L, parseSealDelayMs("slow"))
        assertEquals(0L, parseSealDelayMs("-5"))
        assertEquals(50L, parseSealDelayMs(" 50\n"))
        assertEquals(5_000L, parseSealDelayMs("999999"))
        assertEquals(Long.MAX_VALUE, parseSealDelayMs("hang\n"))
    }

    @Test
    fun everyCallWaitsAndTheDataIsUntouched() {
        val inner = object : SealCipher {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = plaintext.reversedArray()
            override fun decrypt(envelope: ByteArray, associatedData: ByteArray) = envelope.reversedArray()
        }
        val cipher = DelayedSealCipher(inner, delayMs = 40)

        val startedAt = System.nanoTime()
        val sealed = cipher.encrypt("abc".toByteArray(), ByteArray(0))
        val opened = cipher.decrypt(sealed, ByteArray(0))
        val waitedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals("abc", String(opened))
        assertTrue("waited $waitedMs ms", waitedMs >= 75)
    }
}
