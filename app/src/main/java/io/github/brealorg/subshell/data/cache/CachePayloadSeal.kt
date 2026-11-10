package io.github.brealorg.subshell.data.cache

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * PRIVATECACHE01_PRIVATE_ROWS_ARE_SEALED_V1: what the cache keeps about the reader's own
 * account (inbox items and the joined-community list) is stored as an AES-GCM envelope under a
 * Keystore key of its own, bound to the kind of row it belongs to. Public Reddit content
 * (posts, comments, communities) stays plain.
 *
 * The rules, in the order they matter:
 * - A row that cannot be sealed is never written as plain text: [seal] returns [UNAVAILABLE_PREFIX],
 *   which no reader can open, so it is a cache miss until the next network write replaces it.
 * - A row that cannot be opened (another key, a damaged value, another place) throws a
 *   [RuntimeException], which the store already reads as a cache miss (CACHE06).
 * - A value without the sealed prefix is a row an older build wrote in plain text. It is read
 *   as it is and [RoomSubshellCacheStore.sealLegacyPayloads] seals it.
 */
internal interface CachePayloadSeal {
    /** Whether [seal] hides anything; false only for [Plain], which tests use. */
    val encrypts: Boolean

    fun seal(plain: String, place: String): String

    fun open(stored: String, place: String): String

    object Plain : CachePayloadSeal {
        override val encrypts: Boolean = false

        override fun seal(plain: String, place: String): String = plain

        override fun open(stored: String, place: String): String = stored
    }

    companion object {
        const val SEALED_PREFIX = "e1:"
        const val UNAVAILABLE_PREFIX = "e0:"

        /** The Keystore alias of the cache key; the sign-in uses another one. */
        const val CACHE_KEY_ALIAS = "io.github.brealorg.subshell.cache.v1"

        const val PLACE_INBOX = "cache/inbox"
        const val PLACE_SUBSCRIPTION = "cache/subscription"
        const val PLACE_POST_SCOPED = "cache/post-scoped"
        const val PLACE_SAVED_HISTORY = "file/saved-history"
        const val PLACE_READING = "file/reading"

        /**
         * The seal the app uses; Keystore-backed. Tests that cannot reach the Android Keystore
         * replace it (a Robolectric test that builds the process database).
         */
        @Volatile
        var production: () -> CachePayloadSeal = {
            CipherCachePayloadSeal(AndroidKeystoreSealCipher(CACHE_KEY_ALIAS))
        }
    }
}

/** One AES-GCM key and nothing else; [associatedData] binds the envelope to the kind of row. */
internal interface SealCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray
}

internal class CipherCachePayloadSeal(
    private val cipher: SealCipher,
) : CachePayloadSeal {
    override val encrypts: Boolean = true

    override fun seal(plain: String, place: String): String =
        try {
            CachePayloadSeal.SEALED_PREFIX + Base64.getEncoder().encodeToString(
                cipher.encrypt(plain.toByteArray(Charsets.UTF_8), place.toByteArray(Charsets.UTF_8)),
            )
        } catch (_: GeneralSecurityException) {
            CachePayloadSeal.UNAVAILABLE_PREFIX
        } catch (_: RuntimeException) {
            CachePayloadSeal.UNAVAILABLE_PREFIX
        }

    override fun open(stored: String, place: String): String {
        check(!stored.startsWith(CachePayloadSeal.UNAVAILABLE_PREFIX)) {
            "A cache row could not be sealed and has no readable value"
        }
        if (!stored.startsWith(CachePayloadSeal.SEALED_PREFIX)) return stored
        val opened = try {
            cipher.decrypt(
                Base64.getDecoder().decode(stored.substring(CachePayloadSeal.SEALED_PREFIX.length)),
                place.toByteArray(Charsets.UTF_8),
            )
        } catch (error: GeneralSecurityException) {
            throw IllegalStateException("A sealed cache row could not be opened", error)
        }
        return String(opened, Charsets.UTF_8)
    }
}

/**
 * The cache's own Keystore cipher: AES-256-GCM, a key that never leaves the Keystore, a random IV
 * per value. It is separate from the sign-in's cipher on purpose: the cache never sees auth
 * (UI_AND_CACHE_NEVER_SEE_AUTH).
 *
 * The key protects more than a cache now: the reader's hidden communities, notes, drafts and saved
 * searches (SealedKeysPreferences) exist only on the device. Losing the key (a damaged Keystore,
 * an OS fault) loses those values; what is only a copy of Reddit's state (inbox, votes, saved
 * lists, joined communities) is fetched again. The opt-in backup is what keeps the local-only
 * values safe.
 * Envelope: one byte of IV length, the IV, the ciphertext with its tag.
 */
internal class AndroidKeystoreSealCipher(
    private val keyAlias: String,
) : SealCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        require(iv.size in IV_SIZES) { "Unexpected AES-GCM IV size" }
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        require(envelope.isNotEmpty()) { "Sealed value is empty" }
        val ivSize = envelope[0].toInt() and 0xff
        require(ivSize in IV_SIZES && envelope.size > 1 + ivSize) { "Sealed value is malformed" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, envelope.copyOfRange(1, 1 + ivSize)),
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(envelope.copyOfRange(1 + ivSize, envelope.size))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        val IV_SIZES = 12..16
    }
}
