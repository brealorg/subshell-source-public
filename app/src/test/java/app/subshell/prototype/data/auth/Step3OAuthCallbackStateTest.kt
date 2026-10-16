package app.subshell.prototype.data.auth

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth01): a sign-in callback is accepted only for the authorization
 * this app started, once, and only within ten minutes. A callback with
 * another state is refused and leaves the waiting sign-in alone; with no
 * sign-in waiting it is refused; a second copy of an accepted callback is
 * refused. The records go through the real EncryptedAuthSessionStore with a
 * pass-through cipher; nothing is sent to Reddit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3OAuthCallbackStateTest {
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
    private val store = EncryptedAuthSessionStore(application, cipher = PlainCipher).also { it.clearAll() }
    private var now = 1_000_000L
    private val clock = AuthClock { now }

    private fun beginSignIn() = runBlocking {
        OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OkHttpClient(),
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            clock = clock,
            stateGenerator = { STATE },
        ).beginAuthorization()
    }

    private fun receive(query: String, path: String = "/callback"): AuthCallbackResult =
        OAuthCallbackProcessor(store, clock).process(Uri.parse("subshell-auth-test://oauth$path?$query"))

    @Test
    fun aCallbackWithAnotherStateIsRefusedAndTheSignInKeepsWaiting() {
        beginSignIn()

        val forged = receive("state=${STATE.reversed()}&code=stolen")

        assertEquals(AuthCallbackResult.Rejected(AuthCallbackFailureKind.STATE_MISMATCH), forged)
        assertNull("a refused callback is not stored", store.callback())
        assertNotNull("the real callback can still arrive", store.pendingAuthorization())

        val elsewhere = receive("state=$STATE&code=the-code", path = "/other")
        assertEquals(AuthCallbackResult.Rejected(AuthCallbackFailureKind.REDIRECT_URI_MISMATCH), elsewhere)

        val real = receive("state=$STATE&code=the-code")
        assertTrue("$real", real is AuthCallbackResult.Accepted)
        assertEquals("the-code", store.callback()?.code)
    }

    @Test
    fun aCallbackWithoutAWaitingSignInIsRefused() {
        val result = receive("state=$STATE&code=the-code")

        assertEquals(AuthCallbackResult.Rejected(AuthCallbackFailureKind.NO_PENDING_AUTHORIZATION), result)
        assertNull(store.callback())
    }

    @Test
    fun aSignInWaitsTenMinutesAndNoLonger() {
        beginSignIn()
        now += 10L * 60L * 1_000L
        assertTrue("at exactly ten minutes it is accepted", receive("state=$STATE&code=the-code") is AuthCallbackResult.Accepted)

        store.clearAll()
        beginSignIn()
        now += 10L * 60L * 1_000L + 1L
        val late = receive("state=$STATE&code=the-code")

        assertEquals(AuthCallbackResult.Rejected(AuthCallbackFailureKind.PENDING_AUTHORIZATION_EXPIRED), late)
        assertNull("an expired sign-in is removed", store.pendingAuthorization())
        assertNull(store.callback())
    }

    @Test
    fun anAcceptedCallbackCannotBeUsedTwice() {
        beginSignIn()
        assertTrue(receive("state=$STATE&code=the-code") is AuthCallbackResult.Accepted)
        store.deleteCallback()

        val again = receive("state=$STATE&code=the-code")

        assertEquals(AuthCallbackResult.Rejected(AuthCallbackFailureKind.NO_PENDING_AUTHORIZATION), again)
        assertNull(store.callback())
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private companion object {
        const val STATE = "abcdefghijklmnopqrstuvwxyz012345"
    }
}
