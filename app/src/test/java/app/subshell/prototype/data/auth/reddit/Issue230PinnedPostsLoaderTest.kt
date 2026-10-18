package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE230_PINNED_POSTS_FIRST_V1: the pinned posts are read from the first Hot
 * page only for the active signed-in account, with its own token, under the
 * read grant; only stickied posts are kept, in Reddit's order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue230PinnedPostsLoaderTest {
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
    private val urls = mutableListOf<String>()

    private fun child(id: String, stickied: Boolean) =
        """{"kind":"t3","data":{"id":"$id","name":"t3_$id","subreddit":"kotlin","title":"$id","author":"mod",""" +
            """"created_utc":1700000000,"permalink":"/r/kotlin/comments/$id/x/","stickied":$stickied}}"""

    private val listing = """{"kind":"Listing","data":{"after":null,"children":[""" +
        listOf(child("rules", true), child("weekly", true), child("hot1", false)).joinToString(",") + "]}}"

    private val recording = OkHttpClient.Builder().addInterceptor { chain ->
        authorizations += chain.request().header("Authorization")
        urls += chain.request().url.toString()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(listing.toResponseBody("application/json".toMediaType()))
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

    private fun loader(configuration: OAuthProviderConfiguration = configuration()): RedditCommunityPinnedPostsLoader {
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
        return RedditCommunityPinnedPostsLoader(
            callFactory = recording,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun theActiveAccountReadsHotWithItsOwnTokenAndKeepsOnlyThePinnedPostsInOrder() = runBlocking {
        val pinned = loader().load(accountB, "kotlin")

        assertEquals(listOf("t3_rules", "t3_weekly"), pinned?.map { it.id })
        assertEquals(setOf<String?>("Bearer token-b"), authorizations.toSet())
        assertTrue(urls.single(), urls.single().contains("/r/kotlin/hot") && urls.single().contains("limit=5"))
    }

    @Test
    fun anAccountThatIsNotActiveSendsNothing() = runBlocking {
        assertNull(loader().load(accountA, "kotlin"))
        assertEquals(emptyList<String?>(), authorizations)
    }

    @Test
    fun anotherGrantThanTheReadGrantSendsNothing() = runBlocking {
        assertNull(loader(configuration(scopes = RedditOAuth.coreReadScopes + "modposts")).load(accountB, "kotlin"))
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
