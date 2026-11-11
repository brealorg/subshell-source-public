package io.github.brealorg.subshell.data.cache

import java.io.File
import java.security.ProviderException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A Keystore stand-in that counts its calls and can be made to fail or to be slow. */
internal class CountingCipher(
    private val inner: SealCipher,
    private val delayMs: Long = 0L,
    @Volatile var failWith: RuntimeException? = null,
) : SealCipher {
    val encrypts = AtomicInteger()
    val decrypts = AtomicInteger()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        encrypts.incrementAndGet()
        failWith?.let { throw it }
        if (delayMs > 0) Thread.sleep(delayMs)
        return inner.encrypt(plaintext, associatedData)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        decrypts.incrementAndGet()
        failWith?.let { throw it }
        if (delayMs > 0) Thread.sleep(delayMs)
        return inner.decrypt(envelope, associatedData)
    }
}

/**
 * PRIVATECACHE06_THE_DATA_KEY_IS_KEPT_IN_MEMORY_V1 (#296, 1.0.1): the e2 seal calls the Keystore once
 * per process, never loses the data key to a bad moment of the Keystore, and still opens e1 values.
 */
class PrivateCache06EnvelopeSealTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val place = CachePayloadSeal.PLACE_INBOX
    private val plain = "t4_abc|u/someone|a private message about a private matter"

    private fun keyFile(): File = File(folder.root, "private-data-key.v1")

    private fun seal(wrapper: SealCipher, file: File = keyFile(), legacy: SealCipher? = wrapper): CachePayloadSeal {
        val vault = DataKeyVault(wrapper, file)
        return EnvelopeCachePayloadSeal(SoftwareGcmCipher(vault::key), legacy)
    }

    @Test
    fun aValueIsSealedAsE2AndOpensAgain() {
        val seal = seal(SoftwareAesCipher(keyByte = 1))

        val sealed = seal.seal(plain, place)

        assertTrue(sealed.startsWith(CachePayloadSeal.ENVELOPE_PREFIX))
        assertFalse(sealed.contains("private"))
        assertEquals(plain, seal.open(sealed, place))
        assertNotEquals("a random IV: the same value is not the same bytes", sealed, seal.seal(plain, place))
    }

    @Test
    fun aValueIsBoundToItsKindOfRowAndDamageDoesNotOpen() {
        val seal = seal(SoftwareAesCipher(keyByte = 1))
        val sealed = seal.seal(plain, place)

        assertThrows(RuntimeException::class.java) { seal.open(sealed, CachePayloadSeal.PLACE_SUBSCRIPTION) }
        val damaged = sealed.dropLast(4) + (if (sealed.takeLast(4) == "AAAA") "BBBB" else "AAAA")
        assertThrows(RuntimeException::class.java) { seal.open(damaged, place) }
        assertEquals("plain text passes through", plain, seal.open(plain, place))
        assertEquals(CachePayloadSeal.UNAVAILABLE_PREFIX, seal(SoftwareAesCipher(1), legacy = null).let {
            val broken = CountingCipher(SoftwareAesCipher(2), failWith = ProviderException("not ready"))
            seal(broken, File(folder.root, "other-key")).seal(plain, place)
        })
    }

    @Test
    fun theKeystoreIsCalledOncePerProcessNoMatterHowManyValues() {
        val wrapper = CountingCipher(SoftwareAesCipher(keyByte = 1))
        val first = seal(wrapper)

        val sealed = (1..500).map { index -> first.seal("value $index", place) }
        sealed.forEachIndexed { index, text -> assertEquals("value ${index + 1}", first.open(text, place)) }

        assertEquals("one call, to wrap the new data key", 1, wrapper.encrypts.get())
        assertEquals(0, wrapper.decrypts.get())

        // A new process: the key is unwrapped once, and then 500 more values cost nothing.
        val afterRestart = seal(wrapper)
        sealed.forEachIndexed { index, text -> assertEquals("value ${index + 1}", afterRestart.open(text, place)) }
        assertEquals("one call, to unwrap it", 1, wrapper.decrypts.get())
        assertEquals(1, wrapper.encrypts.get())
    }

    @Test
    fun aSlowKeystoreDoesNotSlowTheValues() {
        val wrapper = CountingCipher(SoftwareAesCipher(keyByte = 1), delayMs = 150)
        val seal = seal(wrapper)

        val startedAt = System.nanoTime()
        repeat(300) { index -> seal.open(seal.seal("value $index", place), place) }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        // The first seal waits for the Keystore once (150 ms); 600 more calls are software only. With
        // one Keystore call per value (e1) this would be 600 x 150 ms = 90 s.
        assertTrue("took $elapsedMs ms", elapsedMs < 2_500)
    }

    @Test
    fun aBadMomentOfTheKeystoreNeverCostsTheDataKey() {
        val wrapper = CountingCipher(SoftwareAesCipher(keyByte = 1))
        val before = seal(wrapper)
        val sealed = before.seal(plain, place)
        val fileBefore = keyFile().readText()

        // A new process where the Keystore does not answer.
        wrapper.failWith = ProviderException("The key store did not answer")
        val unlucky = seal(wrapper)
        assertThrows(RuntimeException::class.java) { unlucky.open(sealed, place) }
        assertEquals("the stored key is kept", fileBefore, keyFile().readText())
        assertEquals(CachePayloadSeal.UNAVAILABLE_PREFIX, unlucky.seal(plain, place))

        // The next process, the Keystore answers: the same data key, the old value opens.
        wrapper.failWith = null
        assertEquals(plain, seal(wrapper).open(sealed, place))
        assertEquals(fileBefore, keyFile().readText())
    }

    @Test
    fun aLostKeystoreKeyMakesANewDataKeyAndTheOldValuesAreUnreadable() {
        val old = seal(SoftwareAesCipher(keyByte = 1))
        val oldValue = old.seal(plain, place)
        val fileBefore = keyFile().readText()

        // The Keystore key was reset: another wrapping key, so the stored data key cannot be unwrapped.
        val afterReset = seal(SoftwareAesCipher(keyByte = 2))

        assertThrows(RuntimeException::class.java) { afterReset.open(oldValue, place) }
        assertNotEquals(fileBefore, keyFile().readText())
        val fresh = afterReset.seal(plain, place)
        assertTrue(fresh.startsWith(CachePayloadSeal.ENVELOPE_PREFIX))
        assertEquals(plain, seal(SoftwareAesCipher(keyByte = 2)).open(fresh, place))
    }

    @Test
    fun aFileThatIsNotAWrappedKeyIsReplaced() {
        keyFile().writeText("garbage")

        val seal = seal(SoftwareAesCipher(keyByte = 1))
        val sealed = seal.seal(plain, place)

        assertTrue(keyFile().readText().startsWith("k1:"))
        assertEquals(plain, seal(SoftwareAesCipher(keyByte = 1)).open(sealed, place))
    }

    @Test
    fun twoThreadsAtTheFirstStartMakeOneKey() {
        val wrapper = CountingCipher(SoftwareAesCipher(keyByte = 1), delayMs = 30)
        val vault = DataKeyVault(wrapper, keyFile())
        val pool = Executors.newFixedThreadPool(12)
        val start = CountDownLatch(1)

        val keys = (1..12).map { pool.submit<ByteArray> { start.await(); vault.key() } }
        start.countDown()
        val results = keys.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        results.forEach { assertArrayEquals(results.first(), it) }
        assertEquals(1, wrapper.encrypts.get())
        assertArrayEquals(results.first(), DataKeyVault(SoftwareAesCipher(keyByte = 1), keyFile()).key())
    }

    @Test
    fun anE1ValueFromVersion100StillOpensAndNewValuesAreE2() {
        val keystore = SoftwareAesCipher(keyByte = 1)
        val e1 = CipherCachePayloadSeal(keystore).seal(plain, place)
        val seal = seal(keystore)

        assertTrue(e1.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertEquals(plain, seal.open(e1, place))
        assertTrue(seal.seal(plain, place).startsWith(CachePayloadSeal.ENVELOPE_PREFIX))
    }

    @Test
    fun aFailedOpenIsToldApartAWrongKeyFromABadMoment() {
        val e1 = CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 1)).seal(plain, place)

        val wrongKey = runCatching { seal(SoftwareAesCipher(keyByte = 2), File(folder.root, "a")).open(e1, place) }.exceptionOrNull()!!
        val badMoment = runCatching {
            seal(CountingCipher(SoftwareAesCipher(1), failWith = ProviderException("busy")), File(folder.root, "b")).open(e1, place)
        }.exceptionOrNull()!!

        assertTrue("a wrong key is definite", wrongKey.isDefinitelyUnreadable())
        assertFalse("a Keystore that did not answer is not", badMoment.isDefinitelyUnreadable())
    }
}
