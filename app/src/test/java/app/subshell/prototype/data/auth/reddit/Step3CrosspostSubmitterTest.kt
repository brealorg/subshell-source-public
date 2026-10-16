package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (crosspost01a1a, crosspost01a1b): a crosspost goes to Reddit as one
 * native submit naming its source (no text, no link); an answer that names
 * the source itself instead of a new post is not a success. After a lost
 * answer, the read-back only reads the account's own submitted posts and
 * accepts only a post whose direct parent is the source; it reads nothing
 * for another username.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CrosspostSubmitterTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "t2_crossposter")
    private val requests = mutableListOf<Request>()
    private val bodies = mutableListOf<String>()

    private val capability = object : AuthCommentWriteTokenSource {
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.liveTextPostWriteScopes.joinToString(" "), Long.MAX_VALUE),
                    identity = AuthAccountIdentity(providerAccountId = "t2_crossposter", username = "crossposter"),
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                ),
            )
    }

    private fun server(body: String) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request
        bodies += Buffer().also { request.body?.writeTo(it) }.readUtf8()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun submitter(body: String) = ConfiguredRedditTextPostSubmitter(
        capability = capability,
        callFactory = server(body),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
    )

    private val request = AuthenticatedTextPostSubmitRequest(
        accountStableId = account,
        community = "target",
        title = "Shared original",
        text = "",
        crosspostFullname = " T3_Source123 ",
        requestKey = "crosspost-1",
    )

    private fun created(name: String) =
        """{"json":{"errors":[],"data":{"name":"$name","id":"${name.removePrefix("t3_")}","url":"https://www.reddit.com/r/target/comments/${name.removePrefix("t3_")}/shared_original/"}}}"""

    @Test
    fun aCrosspostIsOneNativeSubmitNamingItsSource() {
        val result = runBlocking { submitter(created("t3_created123")).submit(request) }

        assertTrue(result.toString(), result is AuthenticatedTextPostSubmitResult.Submitted)
        assertEquals("t3_created123", (result as AuthenticatedTextPostSubmitResult.Submitted).post.fullname)
        val sent = requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/api/submit", sent.url.encodedPath)
        val form = bodies.single()
        assertTrue(form, "kind=crosspost" in form)
        assertTrue(form, "crosspost_fullname=t3_source123" in form)
        assertFalse(form, "text=" in form.split('&').map { it.substringBefore('=') + "=" })
        assertFalse(form, "url=" in form.split('&').map { it.substringBefore('=') + "=" })
    }

    @Test
    fun anAnswerNamingTheSourceItselfIsNotASuccess() {
        val result = runBlocking { submitter(created("t3_source123")).submit(request) }

        assertTrue(result.toString(), result is AuthenticatedTextPostSubmitResult.Failed)
        assertEquals(1, requests.size)
    }

    private val listing = """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
        "id":"created123","name":"t3_created123","subreddit":"target","author":"crossposter",
        "title":"Shared original","created_utc":1005,"crosspost_parent":"t3_source123",
        "permalink":"/r/target/comments/created123/shared_original/"
    }}]}}"""

    @Test
    fun theReadBackOnlyReadsAndFindsTheWrapperByItsParent() {
        val result = runBlocking { submitter(listing).reconcile(request, "crossposter", 1_000_000L) }

        assertTrue(result.toString(), result is AuthenticatedTextPostSubmitResult.Submitted)
        val post = (result as AuthenticatedTextPostSubmitResult.Submitted).post
        assertEquals("t3_created123", post.fullname)
        assertEquals("t3_source123", post.verifiedCrosspostFullname)
        assertEquals(listOf("GET"), requests.map { it.method })
        assertEquals("/user/crossposter/submitted", requests.single().url.encodedPath)
    }

    @Test
    fun aPostWithoutTheParentIsNotTakenAndAnotherUsernameReadsNothing() {
        val withoutParent = listing.replace("\"crosspost_parent\":\"t3_source123\",", "")
        val missing = runBlocking { submitter(withoutParent).reconcile(request, "crossposter", 1_000_000L) }
        assertFalse(missing.toString(), missing is AuthenticatedTextPostSubmitResult.Submitted)

        requests.clear()
        val someoneElse = runBlocking { submitter(listing).reconcile(request, "someoneelse", 1_000_000L) }
        assertTrue(someoneElse is AuthenticatedTextPostSubmitResult.Failed)
        assertTrue(requests.isEmpty())
    }
}
