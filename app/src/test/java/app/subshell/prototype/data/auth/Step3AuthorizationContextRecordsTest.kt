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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (reddit01c3c1): an authorization context travels with its owner from
 * the pending record through the stored callback, and a callback is completed
 * only by a session manager with the same context — another context is a
 * configuration mismatch and nothing is exchanged. A context without an owner
 * is refused when the manager is built. The records are written and read
 * through the real EncryptedAuthSessionStore with a pass-through cipher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3AuthorizationContextRecordsTest {
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
    private var tokenRequests = 0
    private val tokenClient = OAuthTokenEndpointClient(
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
    )

    private fun manager(owner: String?, context: String?) = OAuthSessionManager(
        configuration = configuration,
        store = store,
        tokenClient = tokenClient,
        stateGenerator = { STATE },
        authorizationOwnerKey = owner,
        authorizationContextKey = context,
    )

    private fun receiveCallback(): AuthCallbackResult =
        OAuthCallbackProcessor(store).process(
            Uri.parse("${configuration.redirectUri}?state=$STATE&code=the-code"),
        )

    @Test
    fun theContextTravelsFromThePendingRecordToTheStoredCallback() = runBlocking {
        manager(owner = "post_manage", context = " FLAIR ").beginAuthorization()

        val pending = store.pendingAuthorization()
        assertEquals("post_manage", pending?.authorizationOwnerKey)
        assertEquals("flair", pending?.authorizationContextKey)

        val result = receiveCallback()
        assertTrue("$result", result is AuthCallbackResult.Accepted)
        val callback = store.callback()
        assertEquals("post_manage", callback?.authorizationOwnerKey)
        assertEquals("flair", callback?.authorizationContextKey)
    }

    @Test
    fun aCallbackIsCompletedOnlyByAManagerWithTheSameContext() = runBlocking {
        manager(owner = "post_manage", context = "flair").beginAuthorization()
        receiveCallback()

        val other = manager(owner = "post_manage", context = "moderation").completePendingCallback()

        assertEquals(AuthSessionResult.ClientConfigurationMismatch, other)
        assertEquals("nothing is exchanged for another context", 0, tokenRequests)

        // The same context does reach the token endpoint.
        manager(owner = "post_manage", context = "flair").beginAuthorization()
        receiveCallback()
        manager(owner = "post_manage", context = "flair").completePendingCallback()
        assertEquals(1, tokenRequests)
    }

    @Test
    fun aContextWithoutAnOwnerIsRefused() {
        try {
            manager(owner = null, context = "flair")
            fail("a context needs an owner")
        } catch (_: IllegalArgumentException) {
        }
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
