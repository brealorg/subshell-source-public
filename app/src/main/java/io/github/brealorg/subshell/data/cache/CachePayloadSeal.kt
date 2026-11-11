package io.github.brealorg.subshell.data.cache

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
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

    /** The prefix of what [seal] writes now ("" for [Plain]); a stored value with another prefix is due to be sealed again. */
    val currentPrefix: String

    fun seal(plain: String, place: String): String

    fun open(stored: String, place: String): String

    object Plain : CachePayloadSeal {
        override val encrypts: Boolean = false
        override val currentPrefix: String = ""

        override fun seal(plain: String, place: String): String = plain

        override fun open(stored: String, place: String): String = stored
    }

    companion object {
        /** e1: one Android Keystore call per value (1.0.0). */
        const val SEALED_PREFIX = "e1:"

        /** e2: software AES under a data key the Keystore unwraps once per process (1.0.1). */
        const val ENVELOPE_PREFIX = "e2:"
        const val UNAVAILABLE_PREFIX = "e0:"

        /** Whether [text] is a sealed value of either kind (not plain text and not the "could not seal" marker). */
        fun isSealed(text: String): Boolean = text.startsWith(SEALED_PREFIX) || text.startsWith(ENVELOPE_PREFIX)

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
        var production: (Context) -> CachePayloadSeal = { context ->
            envelopeSealFor(context, AndroidKeystoreSealCipher(CACHE_KEY_ALIAS))
        }

        /** The e2 seal over [keystore] (what unwraps the data key and opens e1 values); the debug slow-Keystore hook uses it too. */
        internal fun envelopeSealFor(context: Context, keystore: SealCipher): CachePayloadSeal {
            val bounded = BoundedSealCipher(keystore)
            val vault = DataKeyVault.shared(
                File(context.applicationContext.noBackupFilesDir, DataKeyVault.FILE_NAME),
                bounded,
            )
            return EnvelopeCachePayloadSeal(SoftwareGcmCipher(vault::key), bounded)
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
    private val prefix: String = CachePayloadSeal.SEALED_PREFIX,
) : CachePayloadSeal {
    override val encrypts: Boolean = true
    override val currentPrefix: String = prefix

    override fun seal(plain: String, place: String): String =
        try {
            prefix + Base64.getEncoder().encodeToString(
                cipher.encrypt(plain.toByteArray(Charsets.UTF_8), place.toByteArray(Charsets.UTF_8)),
            )
        } catch (_: GeneralSecurityException) {
            CachePayloadSeal.UNAVAILABLE_PREFIX
        } catch (_: IOException) {
            CachePayloadSeal.UNAVAILABLE_PREFIX
        } catch (_: RuntimeException) {
            CachePayloadSeal.UNAVAILABLE_PREFIX
        }

    override fun open(stored: String, place: String): String {
        check(!stored.startsWith(CachePayloadSeal.UNAVAILABLE_PREFIX)) {
            "A cache row could not be sealed and has no readable value"
        }
        if (!stored.startsWith(prefix)) return stored
        val opened = try {
            cipher.decrypt(
                Base64.getDecoder().decode(stored.substring(prefix.length)),
                place.toByteArray(Charsets.UTF_8),
            )
        } catch (error: GeneralSecurityException) {
            throw IllegalStateException("A sealed cache row could not be opened", error)
        } catch (error: IOException) {
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
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = withKey { key ->
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        require(iv.size in IV_SIZES) { "Unexpected AES-GCM IV size" }
        byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        require(envelope.isNotEmpty()) { "Sealed value is empty" }
        val ivSize = envelope[0].toInt() and 0xff
        require(ivSize in IV_SIZES && envelope.size > 1 + ivSize) { "Sealed value is malformed" }
        return withKey { key ->
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, envelope.copyOfRange(1, 1 + ivSize)),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(envelope.copyOfRange(1 + ivSize, envelope.size))
        }
    }

    // PRIVATECACHE04_THE_KEY_HANDLE_IS_KEPT_IN_MEMORY_V1: the key is looked up (a KeyStore instance,
    // a load and a getKey) once per process and alias, not once per value, and created under one
    // lock so two threads at the first start cannot both generate it. A key the Keystore no longer
    // accepts (reset by the system) is dropped and looked up once more.
    private fun <T> withKey(block: (SecretKey) -> T): T {
        try {
            return block(key())
        } catch (_: java.security.InvalidKeyException) {
            keys.remove(keyAlias)
        }
        return block(key())
    }

    private fun key(): SecretKey = keys[keyAlias] ?: synchronized(keys) {
        keys[keyAlias] ?: loadOrCreateKey().also { keys[keyAlias] = it }
    }

    private fun loadOrCreateKey(): SecretKey {
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

        /** Key handles by alias, shared by every cipher of this process. */
        val keys = java.util.concurrent.ConcurrentHashMap<String, SecretKey>()
    }
}
