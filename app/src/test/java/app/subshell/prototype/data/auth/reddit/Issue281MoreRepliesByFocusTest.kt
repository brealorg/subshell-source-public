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
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE281_MORE_REPLIES_WITHOUT_MORECHILDREN_V1 (#281, measured on the Pixel 6
 * 2026-10-30): Reddit answers /api/morechildren with a 403 "blocked by network
 * security" page while a thread focused on one comment still loads. The
 * replies then load one focused thread per child, at the more node's depth,
 * and later loads in the process go straight there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue281MoreRepliesByFocusTest {
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
    private val requests = mutableListOf<String>()

    private fun more(id: String, parent: String, depth: Int, children: List<String>) = Comment(
        id = id,
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = depth,
        accent = 0L,
        parentId = parent,
        moreChildIds = children,
        moreCount = children.size,
    )

    private val thread = listOf(
        Comment(
            id = "t1_top",
            author = "alice",
            age = "1h",
            body = "Top",
            score = 1,
            depth = 0,
            accent = 0L,
            children = listOf(
                Comment(
                    id = "t1_mid",
                    author = "carol",
                    age = "1h",
                    body = "Mid",
                    score = 1,
                    depth = 1,
                    accent = 0L,
                    parentId = "t1_top",
                    children = listOf(more("more:t1_mid:r1", "t1_mid", 2, listOf("r1", "r2", "gone"))),
                ),
                more("more:t1_top:s1", "t1_top", 1, listOf("s1")),
            ),
        ),
    )

    @Before
    fun reset() {
        moreChildrenRefused = false
    }

    @After
    fun close() {
        moreChildrenRefused = false
        cache.close()
    }

    // A focused thread (comment=<id>, context=0) starts at depth 0 with the
    // focused comment; "gone" is a child Reddit no longer has.
    private fun answer(path: String, comment: String?): Triple<Int, String, String> = when {
        path.endsWith("/api/morechildren") ->
            Triple(403, "text/html", "<body class=theme-beta>You've been blocked by network security.</body>")
        path.endsWith("/comments/abc") && comment != null && comment != "gone" -> Triple(
            200,
            "application/json",
            """[{"kind":"Listing","data":{"children":[]}},{"kind":"Listing","data":{"children":[
              {"kind":"t1","data":{"id":"$comment","name":"t1_$comment","author":"bob","body":"reply $comment",
               "parent_id":"t1_x","link_id":"t3_abc","created_utc":1.0,"depth":0,"score":1}}]}}]""",
        )
        path.endsWith("/comments/abc") ->
            Triple(200, "application/json", """[{"kind":"Listing","data":{"children":[]}},{"kind":"Listing","data":{"children":[]}}]""")
        else -> Triple(404, "application/json", "{}")
    }

    @Test
    fun repliesRedditRefusesThroughMorechildrenLoadThroughFocusedThreads() = runBlocking {
        val loader = loader()
        val result = loader.loadMoreFromSnapshot(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more:t1_mid:r1",
            currentComments = thread,
        )

        assertTrue("$result", result is AuthenticatedRedditCommentResult.Loaded)
        val mid = (result as AuthenticatedRedditCommentResult.Loaded).comments.single().children.first()
        assertEquals(listOf("t1_r1", "t1_r2"), mid.children.map(Comment::id))
        assertEquals(listOf(2, 2), mid.children.map(Comment::depth))
        assertTrue(mid.children.none { it.moreChildIds.isNotEmpty() })
        // The focused reads run in parallel, so their order varies.
        assertEquals("/api/morechildren", requests.first())
        assertEquals(
            setOf("/comments/abc?comment=r1", "/comments/abc?comment=r2", "/comments/abc?comment=gone"),
            requests.drop(1).toSet(),
        )
        assertEquals(4, requests.size)

        // The next load in this process does not ask morechildren again.
        requests.clear()
        val next = loader.loadMoreFromSnapshot(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more:t1_top:s1",
            currentComments = result.comments,
        )
        assertTrue("$next", next is AuthenticatedRedditCommentResult.Loaded)
        assertEquals(listOf("/comments/abc?comment=s1"), requests)
        val top = (next as AuthenticatedRedditCommentResult.Loaded).comments.single()
        assertEquals(listOf("t1_mid", "t1_s1"), top.children.map(Comment::id))
        assertEquals(1, top.children.last().depth)
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
        return RedditCommentLoader(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                val url = chain.request().url
                requests += url.encodedPath + (url.queryParameter("comment")?.let { "?comment=$it" } ?: "")
                val (code, type, body) = answer(url.encodedPath, url.queryParameter("comment"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message(if (code == 200) "OK" else "Forbidden")
                    .body(body.toResponseBody(type.toMediaType()))
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
