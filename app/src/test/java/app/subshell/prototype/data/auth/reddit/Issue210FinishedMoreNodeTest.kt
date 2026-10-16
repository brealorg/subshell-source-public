package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
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
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE210_A_LOADED_NODE_IS_NOT_A_FAILURE_V1 (#210): the screen can still ask
 * for a more node whose last replies already arrived (it publishes only when
 * the reader stops scrolling). That was PROTOCOL, a "Couldn't load replies"
 * whose Retry failed the same way; now the loader answers with the thread as
 * it is, and asks nothing of Reddit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue210FinishedMoreNodeTest {
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
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))

    private val networkRequests = mutableListOf<String>()

    @After
    fun close() {
        cache.close()
    }

    @Test
    fun aMoreNodeWhoseRepliesAlreadyArrivedIsNotAFailure() = runBlocking {
        val thread = listOf(
            Comment(id = "t1_c1", author = "alice", age = "1h", body = "first", score = 1, depth = 0, accent = 0L),
            Comment(id = "t1_c2", author = "bob", age = "1h", body = "last reply loaded", score = 1, depth = 0, accent = 0L),
        )

        val result = loader().loadMoreFromSnapshot(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more:t3_abc:c3",
            currentComments = thread,
        )

        result as AuthenticatedRedditCommentResult.Loaded
        assertEquals(thread, result.comments)
        assertTrue("asked Reddit: $networkRequests", networkRequests.isEmpty())
    }

    private suspend fun loader(): RedditCommentLoader {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
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
                tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), Long.MAX_VALUE),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val noToken = OkHttpClient.Builder().addInterceptor { error("no token request in this test") }.build()
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = noToken, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        val thread = """[
            {"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"abc","name":"t3_abc",
              "title":"Post","subreddit":"kotlin","author":"op","num_comments":1}}]}},
            {"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"c1","name":"t1_c1",
              "author":"alice","author_fullname":"t2_alice","body":"hi","parent_id":"t3_abc",
              "link_id":"t3_abc","created_utc":1.0,"depth":0,"score":1}}]}}]"""
        return RedditCommentLoader(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                networkRequests += chain.request().url.encodedPath
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(thread.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cache,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
