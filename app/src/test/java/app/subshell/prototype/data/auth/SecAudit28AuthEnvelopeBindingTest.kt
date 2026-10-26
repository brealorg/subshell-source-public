package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.FakeAndroidKeyStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECAUDIT28 (security audit #28, owner): the encrypted auth files had no
 * associated data, so one account's file copied into another account's place
 * decrypted as that account. Each file is now bound to its place (envelope
 * version 2); files written before are read as before and written again bound.
 * The cipher is the app's real AES-GCM envelope over the test key store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit28AuthEnvelopeBindingTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val authDirectory = File(context.noBackupFilesDir, EncryptedAuthSessionStore.AUTH_DIRECTORY_NAME)

    @Before
    fun installKeyStore() = FakeAndroidKeyStore.install()

    @After
    fun uninstallKeyStore() {
        EncryptedAuthSessionStore(context).clearAll()
        FakeAndroidKeyStore.uninstall()
    }

    private fun session(account: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access-$account", "refresh-$account", "bearer", "read identity", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2_$account", username = account),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        clientIdFingerprint = "fingerprint",
    )

    private fun sessionFiles(): Map<String, File> = authDirectory.listFiles().orEmpty()
        .filter { it.name.startsWith("session.v2.") }
        .associateBy { it.name }

    @Test
    fun anAccountsFileCopiedIntoAnotherAccountsPlaceDoesNotOpen() {
        val store = EncryptedAuthSessionStore(context)
        store.saveSession("reddit:a", session("a"))
        val aFile = sessionFiles().values.single()
        store.saveSession("reddit:b", session("b"))
        val bFile = sessionFiles().values.single { it.name != aFile.name }

        assertEquals("b", store.session("reddit:b")?.identity?.username)
        bFile.writeBytes(aFile.readBytes())

        val swapped = runCatching { store.session("reddit:b") }
        assertTrue("a moved file fails its tag: $swapped", swapped.isFailure)
        assertEquals("a", store.session("reddit:a")?.identity?.username)
    }

    @Test
    fun aFileWrittenBeforeTheBindingIsReadAndWrittenAgainBound() {
        val store = EncryptedAuthSessionStore(context)
        store.saveAccountRegistry(StoredAuthAccountRegistry(accounts = emptyList(), activeAccountId = "local"))
        val registry = File(authDirectory, "accounts.v1")
        assertEquals("written bound", 2, registry.readBytes()[ENVELOPE_VERSION_INDEX].toInt())

        // A version 1 envelope, as every file was before SECAUDIT28.
        val legacy = AndroidKeystoreAesGcmCipher().encrypt(
            """{"accounts":[],"activeAccountId":"legacy"}""".toByteArray(Charsets.UTF_8),
        )
        assertEquals(1, legacy[ENVELOPE_VERSION_INDEX].toInt())
        registry.writeBytes(legacy)

        assertEquals("legacy", store.accountRegistry()?.activeAccountId)
        assertEquals("rewritten bound", 2, registry.readBytes()[ENVELOPE_VERSION_INDEX].toInt())
        assertEquals("legacy", store.accountRegistry()?.activeAccountId)
    }

    private companion object {
        const val ENVELOPE_VERSION_INDEX = 7 // after the 7-byte "SUBAUTH" magic
    }
}
