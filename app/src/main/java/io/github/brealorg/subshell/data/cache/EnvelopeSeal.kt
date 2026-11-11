package io.github.brealorg.subshell.data.cache

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PRIVATECACHE06_THE_DATA_KEY_IS_KEPT_IN_MEMORY_V1 (#296, 1.0.1): the e1 seal made one Android Keystore
 * call per value. On a phone whose Keystore answers in tens or hundreds of milliseconds a feed page of
 * account-scoped posts (25 rows written, 25 read) took seconds, and the start after an update (every
 * old row sealed) blocked the app. The e2 seal does the AES-GCM in software with one data key that
 * lives in memory; the Keystore is called once per process, to unwrap that key.
 *
 * The data key is 32 random bytes, wrapped by the same Keystore key as before (AES-GCM, the place
 * "seal/data-key/v1" as its associated data) and stored in a small file in the no-backup directory.
 * Anyone who copies the files off the phone has the wrapped key and cannot unwrap it. An e2 value is
 * `e2:` + Base64(12-byte IV, ciphertext, tag) with the row kind as associated data, exactly as e1 was
 * bound to its place.
 */
internal class SoftwareGcmCipher(private val key: () -> ByteArray) : SealCipher {
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(), "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        require(envelope.size > IV_BYTES + TAG_BITS / 8) { "Sealed value is malformed" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key(), "AES"),
            GCMParameterSpec(TAG_BITS, envelope.copyOfRange(0, IV_BYTES)),
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(envelope.copyOfRange(IV_BYTES, envelope.size))
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * The data key and where it is kept. [wrapper] is the Keystore cipher; [file] holds the wrapped key.
 *
 * The data key is replaced only when the stored one can definitely not be unwrapped: the wrapping
 * Keystore key is gone (a wrong key makes the GCM tag fail, [AEADBadTagException]) or the file is not a
 * wrapped key. Everything sealed under the old data key is unreadable then, exactly as it was when
 * the Keystore key itself was lost. Any other failure (the Keystore did not answer, the file could not
 * be read) is not that: the stored key is kept and the caller gets the exception, so a bad moment
 * never costs the reader's data.
 */
internal class DataKeyVault(
    private val wrapper: SealCipher,
    private val file: File,
) {
    private val lock = java.util.concurrent.locks.ReentrantLock()

    @Volatile
    private var cached: ByteArray? = null

    /** The data key; a thread that must not wait long (see [BoundedKeystoreCall]) gives up on the lock as it would on the Keystore. */
    fun key(): ByteArray {
        cached?.let { return it }
        if (BoundedKeystoreCall.shouldFailAtOnce()) {
            throw KeystoreNotAnsweringException("A Keystore call has been running for a while")
        }
        if (!lock.tryLock(BoundedKeystoreCall.currentWaitMs(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            throw KeystoreNotAnsweringException("The data key is being fetched by a thread that waits for the Keystore")
        }
        try {
            return cached ?: load().also { cached = it }
        } finally {
            lock.unlock()
        }
    }

    private fun load(): ByteArray {
        if (file.exists()) {
            val text = file.readText()
            val unwrapped = try {
                unwrap(text)
            } catch (_: AEADBadTagException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            if (unwrapped != null) return unwrapped
        }
        return create()
    }

    private fun unwrap(text: String): ByteArray {
        require(text.startsWith(FILE_PREFIX)) { "Not a wrapped data key" }
        val key = wrapper.decrypt(Base64.getDecoder().decode(text.removePrefix(FILE_PREFIX).trim()), ASSOCIATED_DATA)
        require(key.size == KEY_BYTES) { "Not a data key" }
        return key
    }

    private fun create(): ByteArray {
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val text = FILE_PREFIX + Base64.getEncoder().encodeToString(wrapper.encrypt(key, ASSOCIATED_DATA))
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: IOException) {
            temporary.delete()
            throw error
        }
        return key
    }

    companion object {
        const val FILE_NAME = "private-data-key.v1"
        private const val FILE_PREFIX = "k1:"
        private const val KEY_BYTES = 32
        private val ASSOCIATED_DATA = "seal/data-key/v1".toByteArray(Charsets.UTF_8)
        private val vaults = ConcurrentHashMap<String, DataKeyVault>()

        /** One vault per file in a process, so two threads at the first start cannot make two keys. */
        fun shared(file: File, wrapper: SealCipher): DataKeyVault =
            vaults.getOrPut(file.absolutePath) { DataKeyVault(wrapper, file) }
    }
}

/**
 * The seal the app uses: new values are e2 (software AES under the data key); e1 values an older
 * build wrote (a Keystore call each) are still opened through [legacy] and are replaced by e2 when
 * they are next written or swept.
 */
internal class EnvelopeCachePayloadSeal(
    envelope: SealCipher,
    legacy: SealCipher?,
) : CachePayloadSeal {
    private val v2 = CipherCachePayloadSeal(envelope, CachePayloadSeal.ENVELOPE_PREFIX)
    private val v1 = legacy?.let { CipherCachePayloadSeal(it, CachePayloadSeal.SEALED_PREFIX) }

    override val encrypts: Boolean = true
    override val currentPrefix: String = CachePayloadSeal.ENVELOPE_PREFIX

    override fun seal(plain: String, place: String): String = v2.seal(plain, place)

    override fun open(stored: String, place: String): String = when {
        stored.startsWith(CachePayloadSeal.SEALED_PREFIX) ->
            (v1 ?: throw IllegalStateException("An e1 value and no cipher to open it")).open(stored, place)
        else -> v2.open(stored, place)
    }
}

/** Whether a failed open says the value itself is lost (a wrong key or damage), not that the Keystore had a bad moment. */
internal fun Throwable.isDefinitelyUnreadable(): Boolean =
    this is IllegalArgumentException || cause is AEADBadTagException || cause is IllegalArgumentException
