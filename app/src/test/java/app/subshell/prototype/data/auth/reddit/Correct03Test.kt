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
import app.subshell.prototype.data.auth.customFeedSlug
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.reddit.mergeFocusedCommentContext
import app.subshell.prototype.data.network.reddit.webSocketRequestOrNull
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CORRECT03: OCB-477 (non-Latin custom feed names), OCB-492 (focused load
 * prefers fresh fields), OCB-493 (full refresh keeps author fullnames),
 * OCB-634 (media websocket URL keeps its encoding).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Correct03Test {
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

    @After
    fun close() {
        cache.close()
    }

    @Test
    fun aNameWithoutLatinLettersGetsAUsableSlug() {
        assertEquals("cats_and_dogs", customFeedSlug("Cats & Dogs"))
        val cyrillic = customFeedSlug("Котики")!!
        assertTrue(cyrillic, cyrillic.matches(Regex("feed_[0-9a-f]{8}")))
        assertTrue(customFeedSlug("A")!!.startsWith("feed_"))
        assertNull(customFeedSlug("   "))
    }

    @Test
    fun theMediaWebSocketAddressKeepsItsEncoding() {
        val request = webSocketRequestOrNull("wss://ws.redditmedia.com/rte/abc?m=a%2Bb%26c")!!
        assertEquals("https", request.url.scheme)
        assertEquals("m=a%2Bb%26c", request.url.encodedQuery)
        assertNull(webSocketRequestOrNull("wss://example.com/x"))
    }

    @Test
    fun aFocusedNetworkLoadShowsTheFreshCopyOfASharedComment() {
        val cached = listOf(comment("t1_a", "old body"))
        val fresh = listOf(comment("t1_a", "edited body"))

        assertEquals("edited body", mergeFocusedCommentContext(cached, fresh, preferFocusedFields = true).single().body)
        assertEquals("old body", mergeFocusedCommentContext(cached, fresh).single().body)
    }

    @Test
    fun theFullDiscussionRefreshKeepsAuthorFullnames() = runBlocking {
        val result = loader().synchronizePreservingFocusedTarget(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            focusedCommentId = "t1_c1",
            currentComments = emptyList(),
        )

        result as AuthenticatedRedditCommentResult.Loaded
        assertTrue(result.authorFullnames.toString(), "t2_alice" in result.authorFullnames.values)
    }

    private fun comment(id: String, body: String) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0L,
    )

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
