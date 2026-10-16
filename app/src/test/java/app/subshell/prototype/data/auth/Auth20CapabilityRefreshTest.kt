package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionState
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTH20 (OCB-476/606): the custom feed and inbox capabilities refreshed an
 * expired token with their own OAuthSessionManager, outside the account
 * coordinator's session mutex, and a refused refresh deleted the session
 * without telling the account directory. The refresh now goes through the
 * coordinator, so the account is marked for sign-in like any other refusal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth20CapabilityRefreshTest {
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
    private val feedConfiguration = configuration.copy(scopes = RedditOAuth.liveCustomFeedWriteScopes)
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")

    @Test
    fun aRefusedCustomFeedRefreshMarksTheAccountForSignIn() = runBlocking {
        val store = EncryptedAuthSessionStore(application, cipher = PlainCipher)
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
                tokens = OAuthTokenSet(
                    "access-old",
                    "refresh-1",
                    "bearer",
                    RedditOAuth.liveCustomFeedWriteScopes.joinToString(" "),
                    1L,
                ),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val refused = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(400).message("Bad Request")
                .body("""{"error":"invalid_grant"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = refused, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        val capability = ConfiguredAuthCustomFeedWriteCapability(
            configuration = feedConfiguration,
            store = store,
            accountCoordinator = coordinator,
        )

        val result = capability.ensureAuthorized(accountId)

        assertEquals(AuthSessionResult.ReauthenticationRequired, result)
        assertEquals(
            AccountSessionState.REAUTHENTICATION_REQUIRED,
            coordinator.snapshot().accounts.single { it.stableId == accountId }.state,
        )
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
