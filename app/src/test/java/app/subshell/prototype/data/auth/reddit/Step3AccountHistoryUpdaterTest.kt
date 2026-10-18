package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.redditAccountHistoryPostQueryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
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
 * STEP3 (profile01a6): the Upvoted/Downvoted/Hidden lists of a profile come
 * from Reddit's own history listing for that user, page by page, with the
 * account's history authorization; they are kept under the account's post
 * history key so the next open shows them; a missing grant asks to authorize
 * and sends nothing; the comment history never travels this way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3AccountHistoryUpdaterTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database)
    private val account = "reddit:redreader_compatibility:me"
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity read history", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "me_user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val requests = mutableListOf<HttpUrl>()
    private var authorization: AuthSessionResult = AuthSessionResult.Authenticated(session)
    private var reddit: () -> String = { page(after = "t3_b", "a" to "First upvoted", "b" to "Second upvoted") }

    @After
    fun close() = database.close()

    private fun page(after: String?, vararg posts: Pair<String, String>): String {
        val children = posts.joinToString(",") { (id, title) ->
            """{"kind":"t3","data":{"id":"$id","name":"t3_$id","subreddit":"kotlin","author":"someone",
              "title":"$title","score":1,"num_comments":2,"created_utc":1000,
              "permalink":"/r/kotlin/comments/$id/post/"}}"""
        }
        val cursor = after?.let { "\"$it\"" } ?: "null"
        return """{"kind":"Listing","data":{"after":$cursor,"children":[$children]}}"""
    }

    private fun updater() = RedditAccountHistoryUpdater(
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
        cacheStore = cache,
        capability = object : AuthProfileHistoryCapability {
            override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = authorization
        },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun storedTitles(kind: RedditAccountHistoryKind) = cache
        .readPostFeedSnapshot(redditAccountHistoryPostQueryKey(account, "someone", kind))
        ?.posts
        ?.map { it.title }

    // DIAG04_JSON_PATH_REACHES_THE_REPORT_V1: a broken answer names its field on the way to the report.
    @Test
    fun aBrokenHistoryAnswerKeepsTheFieldItFailedOn() = runBlocking {
        reddit = { """{"kind":"Listing","data":{"children":{"x":1}}}""" }

        val result = updater().synchronize(account, "someone", RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)

        val failed = result as AuthenticatedRedditAccountHistoryUpdateResult.Failed
        assertEquals(AuthenticatedRedditAccountHistoryFailureKind.PROTOCOL, failed.kind)
        assertEquals(NetworkProtocolFailureKind.MALFORMED_JSON, failed.details?.protocolKind)
        assertEquals("$.data.children", failed.details?.jsonPath?.value)
    }

    @Test
    fun aRefreshAsksRedditForTheUsersUpvotedPostsAndKeepsThem() = runBlocking {
        val result = updater().synchronize(account, "Someone", RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)

        assertTrue(result is AuthenticatedRedditAccountHistoryUpdateResult.Applied)
        val url = requests.single()
        assertEquals("/user/someone/upvoted", url.encodedPath)
        assertEquals("1", url.queryParameter("raw_json"))
        assertEquals("new", url.queryParameter("sort"))
        assertEquals("links", url.queryParameter("type"))
        assertEquals("25", url.queryParameter("limit"))
        assertNull(url.queryParameter("after"))
        assertEquals(listOf("First upvoted", "Second upvoted"), storedTitles(RedditAccountHistoryKind.UPVOTED))
    }

    @Test
    fun theNextPageStartsAfterTheLastOneAndAFinishedListAsksNothing() = runBlocking {
        val updater = updater()
        updater.synchronize(account, "someone", RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)

        reddit = { page(after = null, "c" to "Third upvoted") }
        val second = updater.synchronize(account, "someone", RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.APPEND)

        assertTrue(second is AuthenticatedRedditAccountHistoryUpdateResult.Applied)
        assertEquals("t3_b", requests[1].queryParameter("after"))
        assertEquals(
            listOf("First upvoted", "Second upvoted", "Third upvoted"),
            storedTitles(RedditAccountHistoryKind.UPVOTED),
        )

        val third = updater.synchronize(account, "someone", RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.APPEND)

        assertEquals(
            AuthenticatedRedditAccountHistoryFailureKind.END_REACHED,
            (third as AuthenticatedRedditAccountHistoryUpdateResult.Failed).kind,
        )
        assertEquals(2, requests.size)
    }

    @Test
    fun aMissingGrantAsksToAuthorizeAndSendsNothing() = runBlocking {
        authorization = AuthSessionResult.ReauthenticationRequired

        val result = updater().synchronize(account, "someone", RedditAccountHistoryKind.HIDDEN, RepositoryLoadOperation.REFRESH)

        assertEquals(AuthenticatedRedditAccountHistoryUpdateResult.AuthorizationRequired, result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun theCommentHistoryNeverTravelsAsPosts() = runBlocking {
        val result = updater().synchronize(account, "someone", RedditAccountHistoryKind.COMMENTS, RepositoryLoadOperation.REFRESH)

        assertEquals(
            AuthenticatedRedditAccountHistoryFailureKind.CLIENT_CONFIGURATION,
            (result as AuthenticatedRedditAccountHistoryUpdateResult.Failed).kind,
        )
        assertTrue(requests.isEmpty())
    }
}
