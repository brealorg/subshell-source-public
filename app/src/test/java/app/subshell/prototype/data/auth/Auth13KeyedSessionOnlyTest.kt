package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTH13 (#134): an account's session manager fell back to the unbound
 * default session file when the account's own file was missing. That file
 * holds an in-progress login, possibly another account's, so account A could
 * be served account C's token. A keyed manager now reads only its own file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth13KeyedSessionOnlyTest {
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
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val scopes = RedditOAuth.coreReadScopes.joinToString(" ")

    private var tokenAnswer: Triple<Int, String, String> = Triple(500, "text/plain", "")
    private var tokenRequests = 0

    @Test
    fun anAccountWithoutItsOwnSessionNeverGetsTheUnboundLoginSession() = runBlocking {
        val coordinator = signedIn()
        store.deleteSession(accountId)
        // Another account's login in progress: the unbound default session.
        store.saveSession(
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access-other", "refresh-other", "bearer", scopes, Long.MAX_VALUE),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )

        val result = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)

        assertEquals(AuthSessionResult.ReauthenticationRequired, result)
        assertEquals("access-other", store.session()?.tokens?.accessToken)
        assertEquals(0, tokenRequests)
    }

    private suspend fun signedIn(expiresAt: Long = Long.MAX_VALUE): AuthAccountSessionCoordinator {
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = accountId,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(
            accountId,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access-old", "refresh-1", "bearer", scopes, expiresAt),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val tokenEndpoint = OkHttpClient.Builder().addInterceptor { chain ->
            tokenRequests += 1
            val (code, type, body) = tokenAnswer
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }.build()
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = tokenEndpoint,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        return coordinator
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
