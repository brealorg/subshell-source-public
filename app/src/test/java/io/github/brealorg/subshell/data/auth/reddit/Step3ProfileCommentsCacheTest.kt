package io.github.brealorg.subshell.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthProfileHistoryCapability
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditProfileCommentsResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.cache.SubshellCacheDatabase
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.redditAccountHistoryQueryKey
import io.github.brealorg.subshell.model.ProfileCommentFixture
import io.github.brealorg.subshell.model.ThreadPlacement
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a6a2): a profile's comments are kept under the account's
 * history key — what Reddit returned is there on the next open — and
 * comments stored by the earlier profile-comments flow are still found and
 * moved to the new key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ProfileCommentsCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database)
    private val account = "reddit:redreader_compatibility:me"
    private val user = "test-user"
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity read history", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", user),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private var reddit: () -> Pair<Int, String> = { 500 to "{}" }

    private val listing = """
        {"kind":"Listing","data":{"after":null,"children":[{"kind":"t1","data":{
          "id":"c9","name":"t1_c9","author":"$user","body":"From Reddit","link_id":"t3_abc",
          "parent_id":"t3_abc","subreddit":"kotlin","link_title":"A post","score":3,"created_utc":1000}}]}}
    """.trimIndent()

    @After
    fun close() = database.close()

    private fun loader() = RedditProfileCommentsLoader(
        callFactory = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val (code, body) = reddit()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("answer")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            },
        ).build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        cacheStore = cache,
        capability = object : AuthProfileHistoryCapability {
            override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                AuthSessionResult.Authenticated(session)
        },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun storedUnder(key: String) =
        cache.profileComments(username = key, request = RepositoryPageRequest(limit = RepositoryPageRequest.MAX_LIMIT))

    private fun AuthenticatedRedditProfileCommentsResult.bodies() =
        (this as AuthenticatedRedditProfileCommentsResult.Loaded).snapshot.comments.map(ProfileCommentFixture::body)

    @Test
    fun whatRedditReturnedIsThereOnTheNextOpen() = runBlocking {
        reddit = { 200 to listing }
        val loaded = loader().synchronize(account, user, RedditAccountHistoryKind.COMMENTS)
        assertEquals(listOf("From Reddit"), loaded.bodies())

        reddit = { 500 to "{}" }
        val cached = loader().cached(account, user, RedditAccountHistoryKind.COMMENTS)

        assertEquals(listOf("From Reddit"), cached.bodies())
        assertTrue((cached as AuthenticatedRedditProfileCommentsResult.Loaded).fromCache)
    }

    @Test
    fun commentsFromTheEarlierFlowAreFoundAndMoved() = runBlocking {
        val legacy = ProfileCommentFixture(
            id = "t1_old",
            author = user,
            postId = "t3_abc",
            age = "1d",
            body = "Stored by the earlier flow",
            score = 1,
            placement = ThreadPlacement.TOP,
        )
        cache.cacheProfileComments(username = redditProfileCommentsCacheKey(account, user), values = listOf(legacy))

        val cached = loader().cached(account, user, RedditAccountHistoryKind.COMMENTS)

        assertEquals(listOf("Stored by the earlier flow"), cached.bodies())
        val historyKey = redditAccountHistoryQueryKey(account, user, RedditAccountHistoryKind.COMMENTS).value
        assertEquals(listOf("t1_old"), storedUnder(historyKey).map(ProfileCommentFixture::id))
    }
}
