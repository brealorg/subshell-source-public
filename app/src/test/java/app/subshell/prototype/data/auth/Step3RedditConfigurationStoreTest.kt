package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.RedditCompatibilityConfigurationController
import app.subshell.prototype.harness.FakeAndroidKeyStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth07c1, owner approved: tests on the real encrypted store): the
 * Client ID a reader saves for RedReader compatibility is kept by the real
 * store in no-backup storage, sealed in the keystore AES-GCM envelope (never
 * as plain text), and removing it leaves nothing behind. The Android key
 * store is the test stand-in [FakeAndroidKeyStore]; the cipher is the app's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3RedditConfigurationStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val configurationDirectory =
        File(context.noBackupFilesDir, EncryptedAuthSessionStore.AUTH_CONFIGURATION_DIRECTORY_NAME)

    @Before
    fun installKeyStore() {
        FakeAndroidKeyStore.install()
    }

    @After
    fun uninstallKeyStore() {
        EncryptedAuthSessionStore(context).deleteOAuthClientSelection()
        FakeAndroidKeyStore.uninstall()
    }

    @Test
    fun aSavedClientIdIsSealedInNoBackupStorageAndRemovedWithoutTrace() {
        val controller = RedditCompatibilityConfigurationController(context)
        assertFalse(controller.isConfigured())

        controller.save(CLIENT_ID)

        assertTrue(controller.isConfigured())
        // A fresh controller reads the same file: the choice survives a restart.
        assertTrue(RedditCompatibilityConfigurationController(context).isConfigured())
        val stored = EncryptedAuthSessionStore(context).encryptedConfigurationBytesForTest()
        assertTrue("the configuration is written", stored.isNotEmpty())
        assertTrue(
            "the file is the keystore envelope",
            stored.copyOfRange(0, ENVELOPE_MAGIC.size).contentEquals(ENVELOPE_MAGIC),
        )
        assertFalse(
            "the Client ID is never written as plain text",
            String(stored, Charsets.ISO_8859_1).contains(CLIENT_ID),
        )
        assertTrue(configurationDirectory.listFiles().orEmpty().isNotEmpty())
        assertFalse(
            "nothing lands in backed-up app storage",
            File(context.filesDir, EncryptedAuthSessionStore.AUTH_CONFIGURATION_DIRECTORY_NAME).exists(),
        )

        controller.remove()

        assertFalse(controller.isConfigured())
        assertFalse(RedditCompatibilityConfigurationController(context).isConfigured())
        assertTrue(configurationDirectory.listFiles().orEmpty().isEmpty())
    }

    private companion object {
        // A made-up identifier in Reddit's installed-app format, not a real one.
        const val CLIENT_ID = "Step3_Client-Id01"
        val ENVELOPE_MAGIC = "SUBAUTH".toByteArray(Charsets.US_ASCII)
    }
}
