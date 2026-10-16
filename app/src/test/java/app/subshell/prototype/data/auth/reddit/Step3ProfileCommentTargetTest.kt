package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentTargetResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a4b): the post behind a tapped profile comment is read from
 * Reddit with the account's authorization every time — never answered from
 * or written to the cache — and a missing authorization asks for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ProfileCommentTargetTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database)
    private val account = "reddit:redreader_compatibility:me"
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity read history", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val requests = mutableListOf<String>()
    private val authorizations = mutableListOf<String>()

    private val listing = """
        {"kind":"Listing","data":{"children":[{"kind":"t3","data":{
          "id":"abc","name":"t3_abc","subreddit":"kotlin","author":"someone","title":"The target post",
          "score":1,"num_comments":2,"created_utc":1000,"permalink":"/r/kotlin/comments/abc/the_target_post/"}}]}}
    """.trimIndent()

    @After
    fun close() = database.close()

    private fun loader(answer: () -> AuthSessionResult) = RedditProfileCommentsLoader(
        callFactory = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                requests += chain.request().url.toString()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(listing.toResponseBody("application/json".toMediaType()))
                    .build()
            },
        ).build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        cacheStore = cache,
        capability = object : AuthProfileHistoryCapability {
            override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
                authorizations += accountStableId
                return answer()
            }
        },
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun theTargetPostIsReadFromRedditEachTimeAndNotCached() = runBlocking {
        val loader = loader { AuthSessionResult.Authenticated(session) }

        val first = loader.resolveTargetPost(account, "t3_abc")
        val second = loader.resolveTargetPost(account, "abc")

        assertEquals("The target post", (first as AuthenticatedRedditProfileCommentTargetResult.Loaded).post.title)
        assertEquals(false, first.fromCache)
        assertEquals("The target post", (second as AuthenticatedRedditProfileCommentTargetResult.Loaded).post.title)
        assertEquals(2, requests.size)
        assertEquals(listOf(account, account), authorizations)
        assertNull(cache.postForAccount("t3_abc", account))
    }

    @Test
    fun aMissingAuthorizationAsksForItWithoutReadingReddit() = runBlocking {
        val loader = loader { AuthSessionResult.ReauthenticationRequired }

        val result = loader.resolveTargetPost(account, "t3_abc")

        assertEquals(AuthenticatedRedditProfileCommentTargetResult.AuthorizationRequired, result)
        assertEquals(emptyList<String>(), requests)
    }
}
