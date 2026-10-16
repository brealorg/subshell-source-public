package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
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
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.redditUserSubmittedPostFeedQueryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a1): a signed-in reader's view of another user's posts is
 * Reddit's own `/user/<name>/submitted` listing, newest first, kept under the
 * account's user-posts key and paged on from Reddit's cursor; a request for
 * an account that is not the active one sends nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3UserSubmittedFeedUpdaterTest {
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
    private val accountA = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val accountB = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2other")
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
    private val noNetwork = Call.Factory { error("no request in this test") }
    private val requests = mutableListOf<HttpUrl>()
    private var reddit: () -> String = { page(after = "t3_b", "a" to "First by alice", "b" to "Second by alice") }

    @After
    fun close() {
        cache.close()
    }

    private fun page(after: String?, vararg posts: Pair<String, String>): String {
        val children = posts.joinToString(",") { (id, title) ->
            """{"kind":"t3","data":{"id":"$id","name":"t3_$id","subreddit":"kotlin","author":"alice",
              "title":"$title","score":1,"num_comments":2,"created_utc":1000,
              "permalink":"/r/kotlin/comments/$id/post/"}}"""
        }
        val cursor = after?.let { "\"$it\"" } ?: "null"
        return """{"kind":"Listing","data":{"after":$cursor,"children":[$children]}}"""
    }

    private fun record(accountId: String, providerId: String, username: String) = StoredAuthAccountRecord(
        accountId = accountId,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerId,
        username = username,
        createdAtEpochMs = 1L,
        lastUsedAtEpochMs = 1L,
        clientMode = configuration.clientMode,
    )

    private fun session(providerId: String, username: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = providerId, username = username),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    private fun updater(activeAccountId: String): RedditFeedUpdater = runBlocking {
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(accountA, "t2me", "me"), record(accountB, "t2other", "other")),
                activeAccountId = activeAccountId,
            ),
        )
        store.saveSession(accountA, session("t2me", "me"))
        store.saveSession(accountB, session("t2other", "other"))
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = noNetwork, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        RedditFeedUpdater(
            callFactory = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requests += chain.request().url
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .body(reddit().toResponseBody("application/json".toMediaType()))
                        .build()
                },
            ).build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cache,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun storedTitles() = cache
        .readPostFeedSnapshot(redditUserSubmittedPostFeedQueryKey(accountA, "alice"))
        ?.posts
        ?.map { it.title }

    @Test
    fun aRefreshAsksForTheUsersSubmittedPostsNewestFirst() = runBlocking {
        val result = updater(activeAccountId = accountA)
            .synchronize(accountA, RepositoryLoadOperation.REFRESH, AuthenticatedRedditFeedTarget.user("u/Alice"))

        assertTrue(result is AuthenticatedRedditFeedUpdateResult.Applied)
        val url = requests.single()
        assertEquals("/user/alice/submitted", url.encodedPath)
        assertEquals("new", url.queryParameter("sort"))
        assertEquals("1", url.queryParameter("raw_json"))
        assertNull(url.queryParameter("after"))
    }

    @Test
    fun thePostsAreKeptUnderTheUsersKeyAndTheNextPageFollowsRedditsCursor() = runBlocking {
        val updater = updater(activeAccountId = accountA)
        val target = AuthenticatedRedditFeedTarget.user("alice")
        updater.synchronize(accountA, RepositoryLoadOperation.REFRESH, target)

        assertEquals(listOf("First by alice", "Second by alice"), storedTitles())

        reddit = { page(after = null, "c" to "Third by alice") }
        val next = updater.synchronize(accountA, RepositoryLoadOperation.APPEND, target)

        assertTrue(next is AuthenticatedRedditFeedUpdateResult.Applied)
        assertEquals("/user/alice/submitted", requests[1].encodedPath)
        assertEquals("t3_b", requests[1].queryParameter("after"))
        assertEquals(listOf("First by alice", "Second by alice", "Third by alice"), storedTitles())
    }

    @Test
    fun anAccountThatIsNotActiveSendsNothing() = runBlocking {
        val result = updater(activeAccountId = accountB)
            .synchronize(accountA, RepositoryLoadOperation.REFRESH, AuthenticatedRedditFeedTarget.user("alice"))

        assertEquals(
            AuthenticatedRedditFeedUpdateFailureKind.CLIENT_CONFIGURATION,
            (result as AuthenticatedRedditFeedUpdateResult.Failed).kind,
        )
        assertTrue(requests.isEmpty())
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
