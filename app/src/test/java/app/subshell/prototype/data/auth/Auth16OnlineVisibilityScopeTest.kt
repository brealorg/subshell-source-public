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
 * AUTH16 (#139): changing online visibility needs the `account` scope. A token
 * connected before it joined the default scopes lacks it, and the service
 * asked the session coordinator for it, which answered ReauthenticationRequired
 * and flagged the whole account "Sign in again" (the account switcher opened
 * by itself). A missing scope now fails only this action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth16OnlineVisibilityScopeTest {
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
    // The core read scopes, connected before `account` joined the default scopes.
    private val scopes = RedditOAuth.coreReadScopes.joinToString(" ")

    private var tokenAnswer: Triple<Int, String, String> = Triple(500, "text/plain", "")
    private var tokenRequests = 0

    @Test
    fun aTokenWithoutTheAccountScopeFailsTheChangeButKeepsTheAccountSignedIn() = runBlocking {
        val coordinator = signedIn()
        var reads = 0
        val service = app.subshell.prototype.data.auth.reddit.ConfiguredRedditOnlineVisibilityService(
            configuration = configuration,
            activeAccountId = coordinator::activeAuthenticatedAccountId,
            ensureSession = coordinator::ensureAccessTokenForAccount,
            networkForSession = {
                object : app.subshell.prototype.data.network.reddit.OnlineVisibilityNetwork {
                    override suspend fun read(): app.subshell.prototype.data.account.OnlineVisibilityResult {
                        reads += 1
                        return app.subshell.prototype.data.account.OnlineVisibilityResult.Known(true)
                    }
                    override suspend fun patch(value: Boolean) =
                        app.subshell.prototype.data.account.OnlineVisibilityResult.Known(value)
                }
            },
            storedSessionGrants = coordinator::storedSessionGrants,
        )

        val result = service.change(
            app.subshell.prototype.data.account.OnlineVisibilityConfirmation(accountId, true, false, 1),
        ) { true }

        assertEquals(
            app.subshell.prototype.data.account.OnlineVisibilityIssue.AUTHORIZATION,
            (result as app.subshell.prototype.data.account.OnlineVisibilityWriteResult.Failed).issue,
        )
        assertEquals(0, reads)
        assertEquals(
            app.subshell.prototype.data.account.AccountSessionState.CONNECTED,
            coordinator.snapshot().accounts.single { it.stableId == accountId }.state,
        )
        assertNotNull(store.session(accountId))
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
