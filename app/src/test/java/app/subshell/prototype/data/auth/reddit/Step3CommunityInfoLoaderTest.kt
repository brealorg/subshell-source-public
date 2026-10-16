package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoTarget
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthAccountIdentityClient
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredAuthAccountRecord
import app.subshell.prototype.data.auth.StoredAuthAccountRegistry
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (communityinfo01a2): community info is read only for the account
 * that is active and signed in, with that account's own token, and only
 * under the read grant Subshell signs in with; anything else sends nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CommunityInfoLoaderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun configuration(scopes: Set<String> = RedditOAuth.coreReadScopes) = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = scopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private val accountA = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "a")
    private val accountB = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "b")
    private val authorizations = mutableListOf<String?>()

    private val recording = OkHttpClient.Builder().addInterceptor { chain ->
        authorizations += chain.request().header("Authorization")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun session(providerAccountId: String, token: String, configuration: OAuthProviderConfiguration) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, "refresh-$token", "bearer", "identity mysubreddits read", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId, "user-$providerAccountId"),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    private fun record(accountId: String, providerAccountId: String) = StoredAuthAccountRecord(
        accountId = accountId,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerAccountId,
        username = "user-$providerAccountId",
        createdAtEpochMs = 1,
        lastUsedAtEpochMs = 1,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private fun loader(configuration: OAuthProviderConfiguration = configuration()): RedditCommunityInfoLoader {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        store.saveSession(accountA, session("a", "token-a", configuration))
        store.saveSession(accountB, session("b", "token-b", configuration))
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(accountA, "a"), record(accountB, "b")),
                activeAccountId = accountB,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OfflineCallFactory,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        // The app reads the account directory at start; the loader reads it as it is.
        runBlocking { coordinator.hydrateDirectory() }
        return RedditCommunityInfoLoader(
            callFactory = recording,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private val kotlin = AuthenticatedRedditCommunityInfoTarget.community("kotlin")

    private fun AuthenticatedRedditCommunityInfoResult.kind() =
        (this as? AuthenticatedRedditCommunityInfoResult.Failed)?.kind

    @Test
    fun aLoadForAnAccountThatIsNotActiveSendsNothing() = runBlocking {
        val result = loader().load(accountA, kotlin)

        assertEquals(AuthenticatedRedditCommunityInfoFailureKind.CLIENT_CONFIGURATION, result.kind())
        assertEquals(emptyList<String?>(), authorizations)
    }

    @Test
    fun aLoadForTheActiveAccountUsesItsOwnToken() = runBlocking {
        val result = loader().load(accountB, kotlin)

        assertTrue("nothing was sent: $result", authorizations.isNotEmpty())
        assertEquals(setOf<String?>("Bearer token-b"), authorizations.toSet())
    }

    @Test
    fun anotherGrantThanTheReadGrantSendsNothing() = runBlocking {
        val result = loader(configuration(scopes = RedditOAuth.coreReadScopes + "modposts")).load(accountB, kotlin)

        assertEquals(AuthenticatedRedditCommunityInfoFailureKind.CLIENT_CONFIGURATION, result.kind())
        assertEquals(emptyList<String?>(), authorizations)
    }

    private object OfflineCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = error("no network in this test")
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
