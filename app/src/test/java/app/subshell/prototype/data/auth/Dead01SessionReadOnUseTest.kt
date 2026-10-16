package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DEAD01 (OCB-479): every OAuthSessionManager read and decrypted the stored
 * session in its constructor for a bearer token nobody asked for; managers are
 * built per call, some on the main thread. The read now happens on first use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Dead01SessionReadOnUseTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aNewManagerDoesNotReadTheStoreUntilItsTokenIsAskedFor() {
        val configuration = OAuthProviderConfiguration(
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
        val cipher = CountingCipher()
        val store = EncryptedAuthSessionStore(application, cipher = cipher)
        store.clearAll()
        store.saveSession(
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), Long.MAX_VALUE),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        cipher.decrypts = 0

        val manager = OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(OkHttpClient(), configuration, "subshell-test/1.0"),
        )
        assertEquals(0, cipher.decrypts)

        assertEquals("access", manager.bearerToken())
        assertEquals(1, cipher.decrypts)
    }

    private class CountingCipher : AuthCipher {
        var decrypts = 0
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray {
            decrypts += 1
            return envelope
        }
        override fun deleteKey() = Unit
    }
}
