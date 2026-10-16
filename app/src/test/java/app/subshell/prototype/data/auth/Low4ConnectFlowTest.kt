package app.subshell.prototype.data.auth

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.processAuthCallbackOffMain
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW4: the connect flow read the auth store (Keystore decrypts) while it was
 * built on the main thread (OCB-474); a capability's abandoned authorization
 * made sign-in look in progress and its Cancel deleted that authorization
 * (OCB-475); the OAuth callback was processed on the main thread (OCB-478).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low4ConnectFlowTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id-123",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.coreReadScopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val cipher = RecordingCipher()
    private val store = EncryptedAuthSessionStore(application, cipher = cipher).also { it.clearAll() }

    @Test
    fun buildingTheFlowReadsNothingFromTheStore() {
        store.savePendingAuthorization(pending(ownerKey = null))
        cipher.decrypts = 0

        val flow = controller()

        assertEquals(0, cipher.decrypts)
        assertEquals(ConnectAccountPhase.IDLE, flow.uiState.value.phase)
        runBlocking { flow.reset() }
        assertEquals(ConnectAccountPhase.AWAITING_CALLBACK, flow.uiState.value.phase)
    }

    @Test
    fun aCapabilitysAuthorizationIsNotASignInInProgressAndCancelLeavesIt() = runBlocking {
        store.savePendingAuthorization(pending(ownerKey = "moderation_log"))
        store.saveSession(session())
        val flow = controller()

        flow.reset()
        assertEquals(ConnectAccountPhase.IDLE, flow.uiState.value.phase)
        flow.cancel()

        assertEquals("moderation_log", store.pendingAuthorization()?.authorizationOwnerKey)
        assertNotNull(store.session())
    }

    @Test
    fun cancellingASignInStillRemovesItsOwnRecords() = runBlocking {
        store.savePendingAuthorization(pending(ownerKey = null))
        store.saveSession(session())
        val flow = controller()

        flow.cancel()

        assertNull(store.pendingAuthorization())
        assertNull(store.session())
        assertEquals(ConnectAccountPhase.CANCELLED, flow.uiState.value.phase)
    }

    @Test
    fun theCallbackIsProcessedOffTheMainThread() = runBlocking {
        store.savePendingAuthorization(pending(ownerKey = null))
        cipher.decryptedOnMain = false
        cipher.decrypts = 0

        val result = processAuthCallbackOffMain(
            application,
            Uri.parse("subshell-auth-test://oauth/callback?state=state&code=code"),
        ) { store }

        assertTrue("$result", result is AuthCallbackResult.Accepted)
        assertTrue(cipher.decrypts > 0)
        assertFalse(cipher.decryptedOnMain)
    }

    private fun controller() = ConfiguredAuthConnectFlowController(
        sessionManager = OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = OkHttpClient(), configuration = configuration, userAgent = "subshell-test/1.0"),
        ),
        accountCoordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = OkHttpClient(), configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        ),
        store = store,
        providerLabel = "Reddit",
        clientMode = configuration.clientMode,
        storageDispatcher = Dispatchers.Unconfined,
    )

    private fun pending(ownerKey: String?) = PendingOAuthAuthorization(
        provider = OAuthProvider.REDDIT,
        state = "state",
        redirectUri = configuration.redirectUri,
        createdAtEpochMs = System.currentTimeMillis(),
        clientMode = configuration.clientMode,
        authorizationOwnerKey = ownerKey,
    )

    private fun session() = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "read", Long.MAX_VALUE),
        identity = null,
        clientMode = configuration.clientMode,
    )

    private class RecordingCipher : AuthCipher {
        var decrypts = 0
        var decryptedOnMain = false
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray {
            decrypts += 1
            if (Looper.myLooper() == Looper.getMainLooper()) decryptedOnMain = true
            return envelope
        }
        override fun deleteKey() = Unit
    }
}
