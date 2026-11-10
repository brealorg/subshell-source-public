package io.github.brealorg.subshell.data.cache

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRIVATECACHE01_PRIVATE_ROWS_ARE_SEALED_V1: what the seal does to one value, with a real
 * AES-GCM cipher in software (the Android Keystore is not there in a JVM test).
 */
class PrivateCache01SealTest {
    private val seal = CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 1))
    private val plain = "t4_abc|u/someone|a private message about a private matter"

    @Test
    fun aSealedValueIsNotThePlainValueAndOpensAgain() {
        val sealed = seal.seal(plain, CachePayloadSeal.PLACE_INBOX)

        assertTrue(sealed.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(sealed.contains("private"))
        assertFalse(sealed.contains("someone"))
        assertEquals(plain, seal.open(sealed, CachePayloadSeal.PLACE_INBOX))
    }

    @Test
    fun theSameValueSealedTwiceIsNotTheSameBytes() {
        val first = seal.seal(plain, CachePayloadSeal.PLACE_INBOX)
        val second = seal.seal(plain, CachePayloadSeal.PLACE_INBOX)

        assertNotEquals(first, second)
    }

    @Test
    fun aValueFromAnotherKindOfRowDoesNotOpen() {
        val sealed = seal.seal(plain, CachePayloadSeal.PLACE_INBOX)

        assertThrows(RuntimeException::class.java) {
            seal.open(sealed, CachePayloadSeal.PLACE_SUBSCRIPTION)
        }
    }

    @Test
    fun anotherKeyDoesNotOpenIt() {
        val sealed = seal.seal(plain, CachePayloadSeal.PLACE_INBOX)
        val other = CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 2))

        assertThrows(RuntimeException::class.java) { other.open(sealed, CachePayloadSeal.PLACE_INBOX) }
    }

    @Test
    fun aDamagedValueDoesNotOpen() {
        val sealed = seal.seal(plain, CachePayloadSeal.PLACE_INBOX)
        val damaged = sealed.dropLast(4) + (if (sealed.takeLast(4) == "AAAA") "BBBB" else "AAAA")

        assertThrows(RuntimeException::class.java) { seal.open(damaged, CachePayloadSeal.PLACE_INBOX) }
        assertThrows(RuntimeException::class.java) {
            seal.open(CachePayloadSeal.SEALED_PREFIX + "not base64!", CachePayloadSeal.PLACE_INBOX)
        }
    }

    @Test
    fun aValueAnOlderBuildWroteInPlainTextIsReadAsItIs() {
        assertEquals(plain, seal.open(plain, CachePayloadSeal.PLACE_INBOX))
    }

    @Test
    fun aValueThatCouldNotBeSealedIsNeverPlainTextAndNeverOpens() {
        val broken = CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 1, failToSeal = true))

        val stored = broken.seal(plain, CachePayloadSeal.PLACE_INBOX)

        assertEquals(CachePayloadSeal.UNAVAILABLE_PREFIX, stored)
        assertFalse(stored.contains("private"))
        assertThrows(RuntimeException::class.java) { seal.open(stored, CachePayloadSeal.PLACE_INBOX) }
    }

    @Test
    fun thePlainSealChangesNothing() {
        assertEquals(plain, CachePayloadSeal.Plain.seal(plain, CachePayloadSeal.PLACE_INBOX))
        assertEquals(plain, CachePayloadSeal.Plain.open(plain, CachePayloadSeal.PLACE_INBOX))
        assertFalse(CachePayloadSeal.Plain.encrypts)
    }
}

/** A real AES-GCM cipher with a key in memory, for tests. [failToSeal] stands for a Keystore that throws. */
internal class SoftwareAesCipher(
    keyByte: Int,
    private val failToSeal: Boolean = false,
) : SealCipher {
    private val key = SecretKeySpec(ByteArray(32) { keyByte.toByte() }, "AES")

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        if (failToSeal) throw GeneralSecurityException("The key store is not ready")
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(0, 12)))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(envelope.copyOfRange(12, envelope.size))
    }
}
