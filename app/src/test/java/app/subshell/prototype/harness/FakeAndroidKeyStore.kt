package app.subshell.prototype.harness

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * A software stand-in for Android's "AndroidKeyStore" JCA provider, which
 * Robolectric does not have. It offers the two services the encrypted auth
 * store's default cipher uses (a key store and an AES key generator), so a
 * test can drive the real store and the real AES-GCM envelope. Keys live in
 * memory only and are dropped by [install] and [uninstall].
 */
internal object FakeAndroidKeyStore {
    private const val PROVIDER_NAME = "AndroidKeyStore"

    fun install() {
        FakeAndroidKeyStoreKeys.keys.clear()
        removeOurs()
        Security.addProvider(FakeAndroidKeyStoreProvider())
    }

    fun uninstall() {
        FakeAndroidKeyStoreKeys.keys.clear()
        removeOurs()
    }

    // A provider installed from another Robolectric sandbox has another class
    // object, so compare by name.
    private fun removeOurs() {
        val installed = Security.getProvider(PROVIDER_NAME) ?: return
        if (installed.javaClass.name == FakeAndroidKeyStoreProvider::class.java.name) {
            Security.removeProvider(PROVIDER_NAME)
        }
    }
}

internal object FakeAndroidKeyStoreKeys {
    val keys = ConcurrentHashMap<String, SecretKey>()
}

internal class FakeAndroidKeyStoreProvider : Provider(
    "AndroidKeyStore",
    1.0,
    "Test stand-in for the Android key store",
) {
    init {
        put("KeyStore.AndroidKeyStore", FakeAndroidKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", FakeAndroidKeyStoreAesGenerator::class.java.name)
    }
}

internal class FakeAndroidKeyStoreSpi : KeyStoreSpi() {
    private val keys get() = FakeAndroidKeyStoreKeys.keys

    override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]

    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null

    override fun engineGetCertificate(alias: String): Certificate? = null

    override fun engineGetCreationDate(alias: String): Date? = if (keys.containsKey(alias)) Date(0) else null

    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) {
        keys[alias] = key as SecretKey
    }

    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) {
        throw UnsupportedOperationException("Not used by the auth store")
    }

    override fun engineSetCertificateEntry(alias: String, cert: Certificate) {
        throw UnsupportedOperationException("Not used by the auth store")
    }

    override fun engineDeleteEntry(alias: String) {
        keys.remove(alias)
    }

    override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys.toList())

    override fun engineContainsAlias(alias: String): Boolean = keys.containsKey(alias)

    override fun engineSize(): Int = keys.size

    override fun engineIsKeyEntry(alias: String): Boolean = keys.containsKey(alias)

    override fun engineIsCertificateEntry(alias: String): Boolean = false

    override fun engineGetCertificateAlias(cert: Certificate): String? = null

    override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit

    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}

internal class FakeAndroidKeyStoreAesGenerator : KeyGeneratorSpi() {
    private var alias: String? = null
    private var keySizeBits = 256
    private var random = SecureRandom()

    override fun engineInit(random: SecureRandom) {
        this.random = random
    }

    override fun engineInit(params: AlgorithmParameterSpec, random: SecureRandom?) {
        val spec = params as KeyGenParameterSpec
        alias = spec.keystoreAlias
        if (spec.keySize > 0) keySizeBits = spec.keySize
        if (random != null) this.random = random
    }

    override fun engineInit(keysize: Int, random: SecureRandom?) {
        keySizeBits = keysize
        if (random != null) this.random = random
    }

    override fun engineGenerateKey(): SecretKey {
        val bytes = ByteArray(keySizeBits / 8).also(random::nextBytes)
        val key = SecretKeySpec(bytes, "AES")
        alias?.let { FakeAndroidKeyStoreKeys.keys[it] = key }
        return key
    }
}
