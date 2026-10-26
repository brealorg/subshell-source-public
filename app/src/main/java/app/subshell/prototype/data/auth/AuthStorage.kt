package app.subshell.prototype.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

internal interface AuthCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(envelope: ByteArray): ByteArray

    fun deleteKey()

    /**
     * SECAUDIT28_AUTH_FILES_ARE_BOUND_TO_THEIR_PLACE_V1: the envelope is bound
     * to [associatedData] (the file's place in the auth folder). A cipher
     * without binding (the tests' plain fakes) ignores it.
     */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = encrypt(plaintext)

    fun decrypt(envelope: ByteArray, associatedData: ByteArray): AuthDecryption =
        AuthDecryption(decrypt(envelope), boundToItsPlace = true)
}

/** [boundToItsPlace] is false for an envelope written before SECAUDIT28; the store rewrites it bound. */
internal class AuthDecryption(val plaintext: ByteArray, val boundToItsPlace: Boolean)

internal class AndroidKeystoreAesGcmCipher(
    private val keyAlias: String = DEFAULT_AUTH_KEY_ALIAS,
) : AuthCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = seal(plaintext, associatedData = null)

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        seal(plaintext, associatedData)

    override fun decrypt(envelope: ByteArray): ByteArray {
        require(envelopeVersion(envelope) == AUTH_ENVELOPE_VERSION) {
            "Auth envelope version is unsupported"
        }
        return open(envelope, associatedData = null)
    }

    // SECAUDIT28_AUTH_FILES_ARE_BOUND_TO_THEIR_PLACE_V1: version 2 carries the
    // file's place as GCM associated data, so a file copied into another
    // account's place fails its tag. Version 1 (written before) is read as
    // before and reported unbound, so the store rewrites it as version 2.
    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): AuthDecryption =
        when (envelopeVersion(envelope)) {
            AUTH_ENVELOPE_VERSION -> AuthDecryption(open(envelope, null), boundToItsPlace = false)
            AUTH_BOUND_ENVELOPE_VERSION -> AuthDecryption(open(envelope, associatedData), boundToItsPlace = true)
            else -> throw IllegalArgumentException("Auth envelope version is unsupported")
        }

    private fun seal(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance(AUTH_CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        associatedData?.let(cipher::updateAAD)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size in 12..16) { "Unexpected AES-GCM IV size" }

        return ByteArrayOutputStream().use { output ->
            output.write(AUTH_ENVELOPE_MAGIC)
            output.write(if (associatedData == null) AUTH_ENVELOPE_VERSION else AUTH_BOUND_ENVELOPE_VERSION)
            output.write(iv.size)
            output.write(iv)
            output.write(ciphertext)
            output.toByteArray()
        }
    }

    private fun envelopeVersion(envelope: ByteArray): Int {
        require(envelope.size > AUTH_ENVELOPE_HEADER_SIZE) { "Auth envelope is truncated" }
        require(
            envelope.copyOfRange(0, AUTH_ENVELOPE_MAGIC.size)
                .contentEquals(AUTH_ENVELOPE_MAGIC),
        ) { "Auth envelope magic does not match" }
        return envelope[AUTH_ENVELOPE_MAGIC.size].toInt()
    }

    private fun open(envelope: ByteArray, associatedData: ByteArray?): ByteArray {
        val ivSize = envelope[AUTH_ENVELOPE_MAGIC.size + 1].toInt() and 0xff
        require(ivSize in 12..16) { "Auth envelope IV size is invalid" }
        val ivStart = AUTH_ENVELOPE_MAGIC.size + 2
        val cipherStart = ivStart + ivSize
        require(cipherStart < envelope.size) { "Auth envelope ciphertext is missing" }

        val cipher = Cipher.getInstance(AUTH_CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(AUTH_GCM_TAG_BITS, envelope.copyOfRange(ivStart, cipherStart)),
        )
        associatedData?.let(cipher::updateAAD)
        return cipher.doFinal(envelope.copyOfRange(cipherStart, envelope.size))
    }

    override fun deleteKey() {
        keyStore().deleteEntry(keyAlias)
    }

    private fun key(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    companion object {
        const val DEFAULT_AUTH_KEY_ALIAS = "app.subshell.prototype.auth.v1"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val AUTH_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AUTH_GCM_TAG_BITS = 128
        private val AUTH_ENVELOPE_MAGIC = "SUBAUTH".toByteArray(Charsets.US_ASCII)
        private const val AUTH_ENVELOPE_VERSION = 1
        private const val AUTH_BOUND_ENVELOPE_VERSION = 2
        private val AUTH_ENVELOPE_HEADER_SIZE = AUTH_ENVELOPE_MAGIC.size + 2
    }
}

internal class EncryptedAuthSessionStore(
    context: Context,
    private val cipher: AuthCipher = AndroidKeystoreAesGcmCipher(),
    private val json: Json = AUTH_STORAGE_JSON,
) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, AUTH_DIRECTORY_NAME)
    private val configurationDirectory = File(
        appContext.noBackupFilesDir,
        AUTH_CONFIGURATION_DIRECTORY_NAME,
    )

    fun pendingAuthorization(): PendingOAuthAuthorization? =
        read(directory, PENDING_FILE_NAME, PendingOAuthAuthorization.serializer())

    fun savePendingAuthorization(value: PendingOAuthAuthorization) {
        write(directory, PENDING_FILE_NAME, PendingOAuthAuthorization.serializer(), value)
    }

    fun deletePendingAuthorization() {
        delete(directory, PENDING_FILE_NAME)
    }

    fun callback(): StoredOAuthCallback? =
        read(directory, CALLBACK_FILE_NAME, StoredOAuthCallback.serializer())

    fun saveCallback(value: StoredOAuthCallback) {
        write(directory, CALLBACK_FILE_NAME, StoredOAuthCallback.serializer(), value)
    }

    fun deleteCallback() {
        delete(directory, CALLBACK_FILE_NAME)
    }

    fun session(): StoredOAuthSession? =
        read(directory, SESSION_FILE_NAME, StoredOAuthSession.serializer())

    fun session(accountId: String): StoredOAuthSession? =
        read(directory, sessionFileName(accountId), StoredOAuthSession.serializer())

    fun saveSession(value: StoredOAuthSession) {
        write(directory, SESSION_FILE_NAME, StoredOAuthSession.serializer(), value)
    }

    fun saveSession(accountId: String, value: StoredOAuthSession) {
        require(value.identity != null) { "Registered OAuth sessions require account identity" }
        write(directory, sessionFileName(accountId), StoredOAuthSession.serializer(), value)
    }

    fun deleteSession() {
        delete(directory, SESSION_FILE_NAME)
    }

    fun deleteSession(accountId: String) {
        delete(directory, sessionFileName(accountId))
    }

    fun accountRegistry(): StoredAuthAccountRegistry? =
        read(directory, ACCOUNT_REGISTRY_FILE_NAME, StoredAuthAccountRegistry.serializer())

    fun saveAccountRegistry(value: StoredAuthAccountRegistry) {
        write(directory, ACCOUNT_REGISTRY_FILE_NAME, StoredAuthAccountRegistry.serializer(), value)
    }

    fun oauthClientSelection(): StoredOAuthClientSelection? = read(
        configurationDirectory,
        OAUTH_CLIENT_SELECTION_FILE_NAME,
        StoredOAuthClientSelection.serializer(),
    )

    fun saveOAuthClientSelection(value: StoredOAuthClientSelection) {
        write(
            configurationDirectory,
            OAUTH_CLIENT_SELECTION_FILE_NAME,
            StoredOAuthClientSelection.serializer(),
            value,
        )
    }

    fun deleteOAuthClientSelection() {
        delete(configurationDirectory, OAUTH_CLIENT_SELECTION_FILE_NAME)
        configurationDirectory.delete()
    }

    fun clearAll(deleteKey: Boolean = false) {
        deletePendingAuthorization()
        deleteCallback()
        deleteSession()
        delete(directory, ACCOUNT_REGISTRY_FILE_NAME)
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
        if (deleteKey) {
            deleteOAuthClientSelection()
            cipher.deleteKey()
        }
    }

    internal fun encryptedBytesForTest(): ByteArray = encryptedDirectoryBytes(directory)

    internal fun encryptedConfigurationBytesForTest(): ByteArray =
        encryptedDirectoryBytes(configurationDirectory)

    private fun encryptedDirectoryBytes(target: File): ByteArray = target
        .listFiles()
        .orEmpty()
        .sortedBy { file -> file.name }
        .fold(ByteArray(0)) { accumulated, file -> accumulated + file.readBytes() }

    private fun <T> write(
        targetDirectory: File,
        name: String,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        targetDirectory.mkdirs()
        check(targetDirectory.isDirectory) { "Could not create private auth directory" }
        val atomicFile = AtomicFile(File(targetDirectory, name))
        val encrypted = cipher.encrypt(
            json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8),
            placeOf(targetDirectory, name),
        )
        // AUTH19_AUTH_FILE_WRITES_SERIALISED_V1 (OCB-604): accounts.v1 and the
        // session files are written from several coroutines and store
        // instances (the inbox worker has its own); two overlapping writes of
        // one AtomicFile shared its .new file and could leave trailing bytes.
        synchronized(AUTH_FILE_WRITE_LOCK) {
            val output = atomicFile.startWrite()
            try {
                output.write(encrypted)
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                atomicFile.failWrite(output)
                throw error
            }
        }
    }

    private fun <T> read(
        targetDirectory: File,
        name: String,
        deserializer: DeserializationStrategy<T>,
    ): T? {
        val file = File(targetDirectory, name)
        if (!file.isFile) return null
        val stored = AtomicFile(file).openRead().use { it.readBytes() }
        val decrypted = cipher.decrypt(stored, placeOf(targetDirectory, name))
        val value = json.decodeFromString(deserializer, decrypted.plaintext.toString(Charsets.UTF_8))
        if (!decrypted.boundToItsPlace) rebind(targetDirectory, name, stored, decrypted.plaintext)
        return value
    }

    /** The file's place in the auth folders, bound into its envelope (SECAUDIT28). */
    private fun placeOf(targetDirectory: File, name: String): ByteArray =
        "subshell-auth-place:${targetDirectory.name}/$name".toByteArray(Charsets.UTF_8)

    // SECAUDIT28_AUTH_FILES_ARE_BOUND_TO_THEIR_PLACE_V1: a file written before
    // the binding is written again bound, once, with the same content. A write
    // that came in between wins; a failed rewrite leaves the readable old file.
    private fun rebind(targetDirectory: File, name: String, stored: ByteArray, plaintext: ByteArray) {
        runCatching {
            val bound = cipher.encrypt(plaintext, placeOf(targetDirectory, name))
            synchronized(AUTH_FILE_WRITE_LOCK) {
                val atomicFile = AtomicFile(File(targetDirectory, name))
                val current = runCatching { atomicFile.openRead().use { it.readBytes() } }.getOrNull()
                if (current == null || !current.contentEquals(stored)) return@synchronized
                val output = atomicFile.startWrite()
                try {
                    output.write(bound)
                    atomicFile.finishWrite(output)
                } catch (error: Throwable) {
                    atomicFile.failWrite(output)
                    throw error
                }
            }
        }
    }

    private fun delete(targetDirectory: File, name: String) {
        AtomicFile(File(targetDirectory, name)).delete()
    }

    private fun sessionFileName(accountId: String): String {
        val normalized = accountId.trim()
        require(normalized.isNotEmpty()) { "Account ID cannot be blank" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        val suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return "session.v2.$suffix"
    }

    companion object {
        const val AUTH_DIRECTORY_NAME = "subshell-auth"
        const val AUTH_CONFIGURATION_DIRECTORY_NAME = "subshell-auth-configuration"
        private const val PENDING_FILE_NAME = "pending.v1"
        private const val CALLBACK_FILE_NAME = "callback.v1"
        private const val SESSION_FILE_NAME = "session.v1"
        private const val ACCOUNT_REGISTRY_FILE_NAME = "accounts.v1"
        private const val OAUTH_CLIENT_SELECTION_FILE_NAME = "reddit-oauth-client.v1"

        /** One process-wide lock for every auth file write (all store instances). */
        private val AUTH_FILE_WRITE_LOCK = Any()
        private val AUTH_STORAGE_JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
            isLenient = false
        }
    }
}
