package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.StoredOAuthClientSelection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTHSTART01 (OCB-474): the auth runtime read the stored OAuth client
 * selection (Keystore decrypt) when the ViewModel was built on the main
 * thread. It is prepared on IO and the ViewModel takes the prepared runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthStart01RuntimePreparedOffMainTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val cipher = ThreadRecordingCipher()

    @Before
    fun clear() = AuthRuntimePreparation.clearForTests()

    @After
    fun clearAfter() = AuthRuntimePreparation.clearForTests()

    @Test
    fun theRuntimeIsBuiltOffTheMainThreadAndTheViewModelTakesIt() = runBlocking<Unit> {
        EncryptedAuthSessionStore(application, cipher).saveOAuthClientSelection(
            StoredOAuthClientSelection(
                provider = OAuthProvider.REDDIT,
                mode = OAuthClientMode.REDREADER_COMPATIBILITY,
                clientId = "test-client-id",
            ),
        )
        cipher.reset()

        AuthRuntimePreparation.prepare(application) { cipher }.await()

        assertTrue(cipher.decrypts > 0)
        assertFalse(cipher.decryptedOnMain)

        AuthConnectViewModel(application)

        assertNull("the ViewModel took the prepared runtime", AuthRuntimePreparation.takePrepared())
        assertTrue(AuthRuntimePreparation.liveViewModels.get() > 0)
    }

    private class ThreadRecordingCipher : AuthCipher {
        @Volatile var decrypts = 0
        @Volatile var decryptedOnMain = false

        fun reset() {
            decrypts = 0
            decryptedOnMain = false
        }

        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray {
            decrypts += 1
            if (Looper.myLooper() == Looper.getMainLooper()) decryptedOnMain = true
            return envelope
        }
        override fun deleteKey() = Unit
    }
}
