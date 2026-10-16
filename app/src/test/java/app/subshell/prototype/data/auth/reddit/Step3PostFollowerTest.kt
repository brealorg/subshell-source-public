package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.PostFollowFailureKind
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (followpost01a1a): following a post is one POST to Reddit's
 * `api/follow_post` with the absolute state, sent as the account itself.
 * Only a 401 asks to sign in again (a 403 is Reddit's refusal), a lost answer
 * is never sent twice, two follows never overlap, and an account without a
 * usable session sends nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3PostFollowerTest {
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "account", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "me1", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val requests = CopyOnWriteArrayList<Request>()
    private val bodies = CopyOnWriteArrayList<String>()
    private val authorizedAccounts = CopyOnWriteArrayList<String>()
    private var answerCode = 200
    private var lostConnection = false
    private var gate: CountDownLatch? = null
    private var authorization: AuthSessionResult = AuthSessionResult.Authenticated(session)

    private fun follower(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
    ) = ConfiguredRedditPostFollower(
        capability = RecordingCapability(),
        callFactory = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                requests += request
                bodies += request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }.orEmpty()
                if (requests.size == 1) gate?.await(10, TimeUnit.SECONDS)
                if (lostConnection) throw IOException("connection lost")
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(answerCode).message("answer")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            },
        ).build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        ioDispatcher = dispatcher,
    )

    private fun form(index: Int): Map<String, String> = bodies[index]
        .split("&")
        .filter(String::isNotEmpty)
        .associate { field ->
            val (name, value) = field.split("=", limit = 2)
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun aFollowIsOnePostWithTheAbsoluteStateAsTheAccount() = runBlocking {
        val followed = follower().setFollowed(PostFollowRequest("reddit:me", "t3_x", followed = true))

        assertEquals(listOf("reddit:me"), authorizedAccounts.toList())
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/follow_post", request.url.encodedPath)
        assertEquals(mapOf("follow" to "true", "fullname" to "t3_x"), form(0))
        assertEquals(
            AuthenticatedPostFollowResult.Updated(PostFollowRequest("reddit:me", "t3_x", followed = true)),
            followed,
        )

        follower().setFollowed(PostFollowRequest("reddit:me", "t3_x", followed = false))
        assertEquals("false", form(1)["follow"])
    }

    @Test
    fun onlyA401AsksToSignInAndALostAnswerIsNeverSentAgain() = runBlocking {
        answerCode = 401
        assertEquals(
            AuthenticatedPostFollowResult.AuthorizationRequired,
            follower().setFollowed(PostFollowRequest("reddit:me", "t3_x", followed = true)),
        )

        answerCode = 403
        val refused = follower().setFollowed(PostFollowRequest("reddit:me", "t3_x", followed = true))
        assertEquals(PostFollowFailureKind.HTTP, (refused as AuthenticatedPostFollowResult.Failed).kind)
        assertEquals(403, refused.statusCode)

        requests.clear()
        bodies.clear()
        lostConnection = true
        val lost = follower().setFollowed(PostFollowRequest("reddit:me", "t3_x", followed = true))
        assertEquals(PostFollowFailureKind.TRANSPORT, (lost as AuthenticatedPostFollowResult.Failed).kind)
        assertEquals("a lost answer is not sent a second time", 1, requests.size)
    }

    @Test
    fun twoFollowsRunOneAfterTheOther() = runBlocking {
        val first = CountDownLatch(1)
        gate = first
        val writer = follower(Dispatchers.IO)

        val a = async(Dispatchers.IO) { writer.setFollowed(PostFollowRequest("reddit:me", "t3_a", followed = true)) }
        val deadline = System.currentTimeMillis() + 10_000
        while (requests.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        val b = async(Dispatchers.IO) { writer.setFollowed(PostFollowRequest("reddit:me", "t3_b", followed = true)) }
        Thread.sleep(300)
        assertEquals("the second follow waits for the first answer", 1, requests.size)

        first.countDown()
        a.await()
        b.await()
        assertEquals(listOf("t3_a", "t3_b"), (0..1).map { form(it)["fullname"] })
    }

    @Test
    fun anAccountWithoutAUsableSessionSendsNothing() = runBlocking {
        authorization = AuthSessionResult.NoPendingCallback

        val result = follower().setFollowed(PostFollowRequest("reddit:me", "t3_x", followed = true))

        assertEquals(PostFollowFailureKind.SESSION, (result as AuthenticatedPostFollowResult.Failed).kind)
        assertTrue(requests.isEmpty())
    }

    private inner class RecordingCapability : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            authorizedAccounts += accountStableId
            return authorization
        }
    }
}
