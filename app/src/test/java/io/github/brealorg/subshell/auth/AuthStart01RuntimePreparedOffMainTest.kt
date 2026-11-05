package io.github.brealorg.subshell.auth

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.StoredOAuthClientSelection
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTHSTART01 (OCB-474): the auth runtime read the stored OAuth client
 * selection (Keystore decrypt) when the ViewModel was built on the main
 * thread. MainActivity gets it on IO and hands it to the ViewModel; a failed
 * build, a second window or a window opened before the last one's ViewModel was
 * cleared never builds it on the main thread either.
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

    private fun storeASelection() {
        EncryptedAuthSessionStore(application, cipher).saveOAuthClientSelection(
            StoredOAuthClientSelection(
                provider = OAuthProvider.REDDIT,
                mode = OAuthClientMode.REDREADER_COMPATIBILITY,
                clientId = "test-client-id",
            ),
        )
        cipher.reset()
    }

    @Test
    fun theRuntimeIsBuiltOffTheMainThreadAndTheViewModelUsesIt() = runBlocking<Unit> {
        storeASelection()

        val runtime = AuthRuntimePreparation.obtain(application) { cipher }

        assertTrue(cipher.decrypts > 0)
        assertFalse(cipher.decryptedOnMain)
        cipher.reset()
        val store = ViewModelStore()
        ViewModelProvider.create(store, AuthConnectViewModel.factory(runtime), MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        })[AuthConnectViewModel::class]
        assertEquals("the ViewModel built no runtime of its own", 0, cipher.decrypts)
        store.clear()
    }

    /** AUTHSTART01_EVERY_WINDOW_BUILDS_ITS_RUNTIME_OFF_MAIN_V1 */
    @Test
    fun aFailedBuildIsTriedOnceMoreOffTheMainThread() = runBlocking<Unit> {
        storeASelection()
        val threads = mutableListOf<Boolean>()
        var calls = 0

        AuthRuntimePreparation.obtain(application) {
            threads += Looper.myLooper() == Looper.getMainLooper()
            calls += 1
            if (calls == 1) error("the key store is not ready yet")
            cipher
        }

        assertEquals(listOf(false, false), threads)
        assertFalse(cipher.decryptedOnMain)
    }

    @Test
    fun aRuntimeAnotherWindowTookIsBuiltAgainOffTheMainThread() = runBlocking<Unit> {
        storeASelection()
        val release = java.util.concurrent.CountDownLatch(1)
        val slowCipher = {
            release.await()
            cipher
        }

        // Two windows wait for the same preparation; one takes it, the other builds its own.
        val first = async { AuthRuntimePreparation.obtain(application, slowCipher) }
        val second = async { AuthRuntimePreparation.obtain(application, slowCipher) }
        yield()
        release.countDown()

        assertNotSame(first.await(), second.await())
        assertTrue(cipher.decrypts > 1)
        assertFalse(cipher.decryptedOnMain)
    }

    @Test
    fun theGateBelongsToOneWindowsStore() {
        val recreated = ViewModelStore()
        ViewModelProvider.create(recreated)[AuthStartGate::class].authViewModelMade = true

        assertTrue(ViewModelProvider.create(recreated)[AuthStartGate::class].authViewModelMade)
        assertFalse(ViewModelProvider.create(ViewModelStore())[AuthStartGate::class].authViewModelMade)
    }

    /** AUTHSTART01_THE_AUTH_STORE_TOUCHES_NO_DISK_WHEN_MADE_V1 */
    @Test
    fun makingTheAuthStoreTouchesNoDisk() {
        var noBackupLookups = 0
        val counting = object : android.content.ContextWrapper(application) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getNoBackupFilesDir(): java.io.File {
                noBackupLookups += 1
                return super.getNoBackupFilesDir()
            }
        }

        val store = EncryptedAuthSessionStore(counting, cipher)
        assertEquals(0, noBackupLookups)

        store.oauthClientSelection()
        assertTrue(noBackupLookups > 0)
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
