package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth01, auth02): an account's session lives only in the app's
 * no-backup folder, under a name that does not reveal the account, and the
 * account registry beside it holds names and ids, never tokens. (That the
 * files are AES-GCM encrypted with an Android Keystore key is a boundary
 * guard: Robolectric has no Android Keystore.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3AuthVaultFilesTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = PlainCipher).also { it.clearAll() }
    private val accountId = "reddit:redreader_compatibility:t2me"

    private fun filesUnder(root: File): List<File> = root.walkTopDown().filter(File::isFile).toList()

    @Test
    fun anAccountsSessionIsStoredOnlyInTheNoBackupFolderUnderAnOpaqueName() {
        val before = filesUnder(application.filesDir).toSet()

        store.saveSession(accountId, session())
        store.saveAccountRegistry(registry())

        val authFiles = filesUnder(File(application.noBackupFilesDir, "subshell-auth")).map(File::getName)
        assertEquals(2, authFiles.size)
        assertTrue(authFiles.toString(), "accounts.v1" in authFiles)
        val sessionFile = authFiles.single { it != "accounts.v1" }
        assertTrue(sessionFile, sessionFile.startsWith("session.v2."))
        assertFalse("the file name does not reveal the account", sessionFile.contains("t2me"))
        assertEquals("nothing is written to the backed-up files folder", before, filesUnder(application.filesDir).toSet())
        assertEquals(session(), store.session(accountId))
    }

    @Test
    fun theAccountRegistryHoldsNoTokens() {
        val fields = StoredAuthAccountRecord::class.java.declaredFields.filterNot { it.isSynthetic }

        assertTrue(fields.isNotEmpty())
        fields.forEach { field ->
            assertFalse(field.name, field.type == OAuthTokenSet::class.java)
            assertFalse(field.name, field.name.contains("token", ignoreCase = true))
        }
        assertFalse(
            StoredAuthAccountRegistry::class.java.declaredFields.any { it.type == OAuthTokenSet::class.java },
        )
    }

    private fun session() = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access-1", "refresh-1", "bearer", "identity read", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        clientIdFingerprint = "fingerprint",
    )

    private fun registry() = StoredAuthAccountRegistry(
        accounts = listOf(
            StoredAuthAccountRecord(
                accountId = accountId,
                provider = OAuthProvider.REDDIT,
                providerAccountId = "t2me",
                username = "me",
                createdAtEpochMs = 1L,
                lastUsedAtEpochMs = 1L,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            ),
        ),
        activeAccountId = accountId,
    )

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
