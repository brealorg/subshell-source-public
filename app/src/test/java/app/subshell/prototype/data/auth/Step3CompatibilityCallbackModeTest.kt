package app.subshell.prototype.data.auth

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth06b): a callback received for the RedReader compatibility mode
 * is completed only by a session manager in that mode. The same client ID
 * in the native mode is a configuration mismatch: the callback is dropped and
 * nothing is exchanged. Written and read through the real
 * EncryptedAuthSessionStore with a pass-through cipher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CompatibilityCallbackModeTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = PlainCipher).also { it.clearAll() }
    private var tokenRequests = 0

    private fun configuration(mode: OAuthClientMode) = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id-123",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.coreReadScopes,
        permanentAccess = true,
        clientMode = mode,
    )

    private fun manager(mode: OAuthClientMode): OAuthSessionManager {
        val configuration = configuration(mode)
        return OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        tokenRequests += 1
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("test")
                            .body("".toResponseBody())
                            .build()
                    }
                    .build(),
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            stateGenerator = { STATE },
        )
    }

    private suspend fun beginAndReceive(mode: OAuthClientMode) {
        manager(mode).beginAuthorization()
        OAuthCallbackProcessor(store).process(
            Uri.parse("subshell-auth-test://oauth/callback?state=$STATE&code=the-code"),
        )
    }

    @Test
    fun aCompatibilityCallbackIsNotCompletedByTheNativeMode() = runBlocking {
        beginAndReceive(OAuthClientMode.REDREADER_COMPATIBILITY)

        val native = manager(OAuthClientMode.NATIVE_PRODUCTION).completePendingCallback()

        assertEquals(AuthSessionResult.ClientConfigurationMismatch, native)
        assertEquals("nothing is exchanged for another mode", 0, tokenRequests)
        assertNull("the callback is dropped", store.callback())

        // The same mode does reach the token endpoint.
        beginAndReceive(OAuthClientMode.REDREADER_COMPATIBILITY)
        manager(OAuthClientMode.REDREADER_COMPATIBILITY).completePendingCallback()
        assertEquals(1, tokenRequests)
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
