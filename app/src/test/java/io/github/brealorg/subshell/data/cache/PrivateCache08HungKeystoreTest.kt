package io.github.brealorg.subshell.data.cache

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A Keystore whose calls never come back until [release] is called. */
internal class HangingCipher(private val inner: SealCipher) : SealCipher {
    val release = CountDownLatch(1)

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        release.await()
        return inner.encrypt(plaintext, associatedData)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        release.await()
        return inner.decrypt(envelope, associatedData)
    }
}

/**
 * PRIVATEPREFS06_NO_THREAD_WAITS_LONG_FOR_THE_KEYSTORE_V1 (#296, 1.0.1): a Keystore that never answers
 * must not hold the main thread, nor the cache thread the main thread waits for.
 */
class PrivateCache08HungKeystoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val place = CachePayloadSeal.PLACE_INBOX
    private val shortBefore = BoundedKeystoreCall.shortWaitMs
    private val longBefore = BoundedKeystoreCall.longWaitMs

    @Before
    fun shortWaits() {
        BoundedKeystoreCall.shortWaitMs = 300L
        BoundedKeystoreCall.longWaitMs = 600L
    }

    @After
    fun waitsBack() {
        BoundedKeystoreCall.shortWaitMs = shortBefore
        BoundedKeystoreCall.longWaitMs = longBefore
    }

    /** Runs [block] on a thread with the cache thread's name and returns what it threw and how long it took. */
    private fun onTheCacheThread(block: () -> Unit): Pair<Throwable?, Long> {
        val thrown = AtomicReference<Throwable?>()
        val startedAt = System.nanoTime()
        val thread = Thread({ runCatching(block).onFailure(thrown::set) }, "subshell-data03-room")
        thread.start()
        thread.join(10_000)
        assertTrue("the call came back", !thread.isAlive)
        return thrown.get() to (System.nanoTime() - startedAt) / 1_000_000
    }

    private fun seal(keystore: SealCipher, name: String = "key"): CachePayloadSeal {
        val bounded = BoundedSealCipher(keystore)
        val vault = DataKeyVault(bounded, File(folder.root, name))
        return EnvelopeCachePayloadSeal(SoftwareGcmCipher(vault::key), bounded)
    }

    @Test
    fun aHungKeystoreEndsTheCallAtTheLimitAndTheNextCallsFailAtOnce() {
        val hanging = HangingCipher(SoftwareAesCipher(keyByte = 1))
        val seal = seal(hanging)

        val (first, firstMs) = onTheCacheThread { seal.open("e1:AAAAAAAAAAAAAAAAAAAAAAAA", place) }
        val (second, secondMs) = onTheCacheThread { seal.open("e1:AAAAAAAAAAAAAAAAAAAAAAAA", place) }

        assertTrue("the first call gave up: $first", first is KeystoreNotAnsweringException)
        assertTrue("after $firstMs ms, not much more", firstMs in 250..1_500)
        assertTrue("the next call fails at once: $second", second is KeystoreNotAnsweringException)
        assertTrue("took $secondMs ms", secondMs < 150)
        hanging.release.countDown()
    }

    @Test
    fun theDataKeyIsNotLostWhenTheKeystoreHangs() {
        val keystore = SoftwareAesCipher(keyByte = 1)
        val file = File(folder.root, "key")
        val first = seal(keystore)
        val sealed = first.seal("a private value", place)
        val fileBefore = file.readText()

        val hanging = HangingCipher(keystore)
        val afterRestart = seal(hanging)
        val (thrown, _) = onTheCacheThread { afterRestart.open(sealed, place) }

        assertTrue("$thrown", thrown is KeystoreNotAnsweringException)
        assertEquals("the stored data key is kept", fileBefore, file.readText())
        assertTrue("the failure is not read as a lost value", !thrown!!.isDefinitelyUnreadable())
        hanging.release.countDown()
        Thread.sleep(200)
        assertEquals("a private value", seal(keystore).open(sealed, place))
    }

    @Test
    fun aThreadWaitingForTheLockGivesUpLikeOneWaitingForTheKeystore() {
        val hanging = HangingCipher(SoftwareAesCipher(keyByte = 1))
        val seal = seal(hanging)
        // A background thread asks for the key first and holds the vault's lock while it waits.
        val background = Thread({ runCatching { seal.seal("x", place) } }, "background")
        background.start()
        Thread.sleep(100)

        val (thrown, ms) = onTheCacheThread { seal.seal("y", place).let { assertEquals(CachePayloadSeal.UNAVAILABLE_PREFIX, it) } }

        assertNull("sealing reports 'could not seal', it does not throw: $thrown", thrown)
        assertTrue("took $ms ms", ms < 1_500)
        hanging.release.countDown()
        background.join(5_000)
    }

    // The waits must not add up: many reads on the main thread, one after the other, while a background
    // thread has waited for the Keystore for a while, cost almost nothing each.
    @Test
    fun readsThatComeAfterTheFirstWaitDoNotWaitAgain() {
        val hanging = HangingCipher(SoftwareAesCipher(keyByte = 1))
        val seal = seal(hanging)
        val background = Thread({ runCatching { seal.seal("x", place) } }, "background")
        background.start()
        Thread.sleep(BoundedKeystoreCall.shortWaitMs + 150)

        val (thrown, ms) = onTheCacheThread {
            repeat(40) { seal.open("e2:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", place).let { } }
        }

        assertTrue("$thrown", thrown is KeystoreNotAnsweringException)
        assertTrue("the first failure came at once, took $ms ms", ms < 150)
        hanging.release.countDown()
        background.join(5_000)
    }

    @Test
    fun manyFailedReadsInARowCostLittleInTotal() {
        val hanging = HangingCipher(SoftwareAesCipher(keyByte = 1))
        val seal = seal(hanging)
        val (_, ms) = onTheCacheThread {
            repeat(40) { runCatching { seal.open("e2:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", place) } }
        }

        assertTrue("40 failed reads took $ms ms (one wait of ${BoundedKeystoreCall.shortWaitMs} ms, not forty)", ms < 1_200)
        hanging.release.countDown()
    }

    @Test
    fun anAnsweringKeystoreIsNotAffected() {
        val seal = seal(SoftwareAesCipher(keyByte = 1))

        val (thrown, ms) = onTheCacheThread {
            repeat(50) { index -> assertEquals("v$index", seal.open(seal.seal("v$index", place), place)) }
        }

        assertNull(thrown)
        assertTrue("took $ms ms", ms < 1_500)
        assertTrue(TimeUnit.MILLISECONDS.toSeconds(ms) < 2)
    }
}
