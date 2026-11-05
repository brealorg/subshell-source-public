package io.github.brealorg.subshell.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.StoredOAuthClientSelection
import java.io.File
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE56_A_WIPED_KEY_STORE_IS_NOTICED_WHEN_A_READ_FAILS_V1: after the one-time #42 check has
 * settled, a key store that loses its key leaves files whose tag check fails on every read. The
 * runtime then discards them (the reader signs in again) instead of staying on "Couldn't read
 * Reddit setup"; a transient Keystore error keeps them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue56WipedKeyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(envelope: ByteArray) = envelope
        override fun deleteKey() = Unit
    }

    /** Every read fails with [failure]. */
    private class FailingCipher(private val failure: () -> Exception) : AuthCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = throw failure()
        override fun deleteKey() = Unit
    }

    private val authDirectory get() = File(context.noBackupFilesDir, EncryptedAuthSessionStore.AUTH_DIRECTORY_NAME)
    private val configurationDirectory
        get() = File(context.noBackupFilesDir, EncryptedAuthSessionStore.AUTH_CONFIGURATION_DIRECTORY_NAME)

    @Before
    fun storedSetupAndSignIn() {
        EncryptedAuthSessionStore.resetKeyCheckForTests(context)
        authDirectory.deleteRecursively()
        configurationDirectory.deleteRecursively()
        EncryptedAuthSessionStore(context, IdentityCipher()).saveOAuthClientSelection(
            StoredOAuthClientSelection(
                provider = OAuthProvider.REDDIT,
                mode = OAuthClientMode.REDREADER_COMPATIBILITY,
                clientId = "issue56-client",
            ),
        )
        authDirectory.mkdirs()
        File(authDirectory, "session.v2.issue56").writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun filesThatFailTheirTagCheckAreDiscardedAndTheSetupReadsAsFresh() {
        AuthApplicationRuntimeFactory.create(
            context,
            FailingCipher { javax.crypto.BadPaddingException("wrap").apply { initCause(AEADBadTagException("tag")) } },
        )

        assertFalse(authDirectory.exists())
        assertFalse(configurationDirectory.exists())
        assertNull(EncryptedAuthSessionStore(context, IdentityCipher()).oauthClientSelection())
    }

    @Test
    fun aTransientKeyStoreErrorKeepsTheFiles() {
        AuthApplicationRuntimeFactory.create(context, FailingCipher { KeyStoreException("busy") })

        assertTrue(File(authDirectory, "session.v2.issue56").isFile)
        assertTrue(configurationDirectory.list().orEmpty().isNotEmpty())
    }
}
