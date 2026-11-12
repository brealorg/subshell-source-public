package io.github.brealorg.subshell.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import io.github.brealorg.subshell.data.auth.OAuthAccountIdentityClient
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.OAuthTokenEndpointClient
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredAuthAccountRecord
import io.github.brealorg.subshell.data.auth.StoredAuthAccountRegistry
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.auth.authenticatedAccountId
import io.github.brealorg.subshell.data.cache.MutableCacheClock
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.cache.SubshellCacheDatabase
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.model.Comment
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
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit 2026-11-12 (#298), the loader with Reddit's answers:
 * - AUDIT298A_CONTINUE_NODES_LOAD_FROM_EITHER_SIDE_V1
 * - AUDIT298A_FROM_THE_END_ALWAYS_MOVES_ON_V1
 * - AUDIT298A_NO_COMMENT_IS_ASKED_FOR_TWICE_V1
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Audit298aMoreLoaderTest {
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

    /** Who each comment answers; morechildren returns exactly the ids asked for. */
    private val parents = mutableMapOf<String, String>()

    @Before
    fun reset() {
        moreChildrenRefusedFor.clear()
    }

    @After
    fun close() {
        moreChildrenRefusedFor.clear()
        cache.close()
    }

    private fun thing(id: String, parent: String, depth: Int) =
        """{"kind":"t1","data":{"id":"$id","name":"t1_$id","author":"bob","body":"c $id",
           "parent_id":"$parent","link_id":"t3_abc","created_utc":1.0,"depth":$depth,"score":1}}"""

    private fun answer(path: String, comment: String?, children: String?): Triple<Int, String, String> = when {
        path.endsWith("/api/morechildren") -> {
            val things = children.orEmpty().split(",").filter { it.isNotBlank() }.map { id ->
                val parent = parents.getValue(id)
                thing(id, parent, if (parent.startsWith("t3_")) 0 else 1)
            }
            Triple(200, "application/json", """{"json":{"errors":[],"data":{"things":[${things.joinToString(",")}]}}}""")
        }
        // The continued thread: the parent with its replies (context=0).
        path.endsWith("/comments/abc") && comment == "deep" -> Triple(
            200,
            "application/json",
            """[{"kind":"Listing","data":{"children":[]}},{"kind":"Listing","data":{"children":[
              {"kind":"t1","data":{"id":"deep","name":"t1_deep","author":"bob","body":"deep",
               "parent_id":"t1_top","link_id":"t3_abc","created_utc":1.0,"depth":0,"score":1,
               "replies":{"kind":"Listing","data":{"children":[${thing("d1", "t1_deep", 1)}]}}}}]}}]""",
        )
        else -> Triple(404, "application/json", "{}")
    }

    private fun c(id: String, parent: String, depth: Int, children: List<Comment> = emptyList()) = Comment(
        id = "t1_$id", author = "a", age = "1h", body = id, score = 1, depth = depth, accent = 0L,
        parentId = parent, children = children,
    )

    private fun node(id: String, parent: String, depth: Int, ids: List<String>) = Comment(
        id = id, author = "", age = "", body = "", score = 0, depth = depth, accent = 0L,
        parentId = parent, moreChildIds = ids, moreCount = ids.size,
    )

    private fun List<Comment>.allIds(): List<String> = flatMap { listOf(it.id) + it.children.allIds() }

    @Test
    fun aContinueNodeReachedFromBelowLoadsItsThread() = runBlocking {
        val thread = listOf(
            c("top", "t3_abc", 0, children = listOf(
                c("deep", "t1_top", 1, children = listOf(continueNodeFor("t1_deep", 2))),
            )),
            c("mine", "t3_abc", 0),
        )
        val result = loader().loadMoreFromSnapshotFromEnd(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = continueNodeFor("t1_deep", 2).id,
            currentComments = thread,
        )
        assertTrue("$result", result is AuthenticatedRedditCommentResult.Loaded)
        val deep = (result as AuthenticatedRedditCommentResult.Loaded).comments.first().children.single()
        assertEquals(listOf("t1_d1"), deep.children.map(Comment::id))
    }

    @Test
    fun fromTheEndAWindowInsideOneSubtreeMovesBackToItsParent() = runBlocking {
        // The node lists a, b and thirty replies to b; the last 25 are all b's.
        val replies = (1..30).map { "b$it" }
        parents["a"] = "t3_abc"
        parents["b"] = "t3_abc"
        replies.forEach { parents[it] = "t1_b" }
        val thread = listOf(c("top", "t3_abc", 0), node("more:t3_abc:a", "t3_abc", 0, listOf("a", "b") + replies), c("mine", "t3_abc", 0))

        val result = loader().loadMoreFromSnapshotFromEnd(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more:t3_abc:a",
            currentComments = thread,
        )

        val comments = (result as AuthenticatedRedditCommentResult.Loaded).comments
        assertEquals("two requests: the window, then back to b", 2, requests.count { it.startsWith("/api/morechildren") })
        assertEquals(listOf("t1_top", "more:t3_abc:a", "t1_b", "t1_mine"), comments.map(Comment::id))
        assertEquals(listOf("a") + replies.drop(24), comments[1].moreChildIds)
        assertEquals((1..24).map { "t1_b$it" }, comments[2].children.map(Comment::id))
        assertEquals(comments.allIds().size, comments.allIds().toSet().size)
    }

    @Test
    fun fromTheStartAnIdAlreadyInTheThreadIsNotAskedForAgain() = runBlocking {
        parents["x"] = "t3_abc"
        parents["y"] = "t3_abc"
        // "x" is on screen already (it came as the focused comment).
        val thread = listOf(c("x", "t3_abc", 0), node("more:t3_abc:x", "t3_abc", 0, listOf("x", "y")))

        val result = loader().loadMoreFromSnapshot(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more:t3_abc:x",
            currentComments = thread,
        )

        assertEquals(listOf("/api/morechildren?children=y"), requests)
        val comments = (result as AuthenticatedRedditCommentResult.Loaded).comments
        assertEquals(listOf("t1_x", "t1_y"), comments.map(Comment::id))
        assertFalse(comments.any { it.moreChildIds.isNotEmpty() })
    }

    private fun continueNodeFor(parent: String, depth: Int) =
        io.github.brealorg.subshell.model.continueThreadNode(parentFullname = parent, depth = depth, accent = 0L)

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
                requests += url.encodedPath +
                    (url.queryParameter("comment")?.let { "?comment=$it" } ?: "") +
                    (url.queryParameter("children")?.let { "?children=$it" } ?: "")
                val (code, type, body) = answer(url.encodedPath, url.queryParameter("comment"), url.queryParameter("children"))
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
