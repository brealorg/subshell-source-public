package io.github.brealorg.subshell.data.cache

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRIVATECACHE01_PRIVATE_ROWS_ARE_SEALED_V1: the cache's Keystore cipher on a real device. The unit
 * tests use a software AES-GCM cipher; this is the one the app uses, with a key of its own that
 * the test deletes again.
 */
@RunWith(AndroidJUnit4::class)
class PrivateCache01KeystoreInstrumentedTest {
    private val alias = "io.github.brealorg.subshell.cache.instrumented-test"
    private val plain = "t4_abc|a private message about a private matter"

    @After
    fun deleteTheTestKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private fun seal() = CipherCachePayloadSeal(AndroidKeystoreSealCipher(alias))

    @Test
    fun aValueIsSealedUnderTheKeystoreKeyAndOpensAgain() {
        val sealed = seal().seal(plain, CachePayloadSeal.PLACE_INBOX)

        assertTrue(sealed.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(sealed.contains("private"))
        assertEquals(plain, seal().open(sealed, CachePayloadSeal.PLACE_INBOX))
    }

    @Test
    fun theSameValueSealedTwiceIsNotTheSameBytes() {
        assertTrue(
            seal().seal(plain, CachePayloadSeal.PLACE_INBOX) !=
                seal().seal(plain, CachePayloadSeal.PLACE_INBOX),
        )
    }

    @Test
    fun anotherKindOfRowDoesNotOpenIt() {
        val sealed = seal().seal(plain, CachePayloadSeal.PLACE_INBOX)

        assertThrows(RuntimeException::class.java) {
            seal().open(sealed, CachePayloadSeal.PLACE_SUBSCRIPTION)
        }
    }

    @Test
    fun aLostKeyMakesAnEarlierValueUnreadableAndANewOneStillSeals() {
        val sealed = seal().seal(plain, CachePayloadSeal.PLACE_INBOX)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

        assertThrows(RuntimeException::class.java) { seal().open(sealed, CachePayloadSeal.PLACE_INBOX) }
        val fresh = seal().seal(plain, CachePayloadSeal.PLACE_INBOX)
        assertEquals(plain, seal().open(fresh, CachePayloadSeal.PLACE_INBOX))
    }
}
