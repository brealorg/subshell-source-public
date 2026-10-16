package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * H3: a post delete whose answer is lost (408/421/425/5xx, timeout, broken
 * connection) is read back instead of reported as "wasn't deleted". Gone or
 * [deleted] means deleted, still there means not deleted (delete again is
 * safe), no answer stays unknown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class H3PostDeleteOutcomeTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            "test-token",
            null,
            "bearer",
            RedditOAuth.liveCommentEditScopes.joinToString(" "),
            Long.MAX_VALUE,
        ),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private sealed interface Answer {
        data class Status(val code: Int, val body: String = "{}") : Answer
        data class Fails(val error: IOException) : Answer
    }

    private val deleteRequests = mutableListOf<String>()
    private val infoRequests = mutableListOf<String>()

    private fun deleter(delete: Answer, info: List<Answer>): ConfiguredRedditPostDeleter {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val answer = if (path.endsWith("/api/del")) {
                deleteRequests += path
                delete
            } else {
                infoRequests += path
                info.getOrElse(infoRequests.size - 1) { info.last() }
            }
            when (answer) {
                is Answer.Fails -> throw answer.error
                is Answer.Status -> Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(answer.code).message(if (answer.code == 200) "OK" else "Error")
                    .body(answer.body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }.build()
        return ConfiguredRedditPostDeleter(
            capability = PostDeleteCapability(session),
            callFactory = client,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            readBackDelaysMs = listOf(0L, 0L, 0L),
        )
    }

    private fun authoredBy(author: String) = Answer.Status(
        200,
        """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"abc","name":"t3_abc","author":"$author","title":"A post"}}]}}""",
    )
    private val gone = Answer.Status(200, """{"kind":"Listing","data":{"children":[]}}""")

    private val request get() = AuthenticatedPostDeleteRequest(account, "t3_abc")

    private fun kindOf(result: AuthenticatedPostDeleteResult) =
        (result as AuthenticatedPostDeleteResult.Failed).kind

    @Test
    fun a502ThenDeletedIsDeletedAndNothingIsResent() = runBlocking {
        val result = deleter(Answer.Status(502), listOf(authoredBy("[deleted]"))).delete(request)

        assertTrue(result.toString(), result is AuthenticatedPostDeleteResult.Deleted)
        assertEquals(1, deleteRequests.size)
    }

    @Test
    fun aTimeoutThenGoneIsDeleted() = runBlocking {
        val result = deleter(Answer.Fails(SocketTimeoutException()), listOf(gone)).delete(request)

        assertTrue(result.toString(), result is AuthenticatedPostDeleteResult.Deleted)
    }

    @Test
    fun aPostStillThereIsNotDeletedAndDeletingAgainIsOffered() = runBlocking {
        val result = deleter(Answer.Status(503), listOf(authoredBy("test-user"))).delete(request)

        assertEquals(AuthenticatedPostDeleteFailureKind.NOT_DELETED, kindOf(result))
        assertTrue((result as AuthenticatedPostDeleteResult.Failed).retryable)
        assertEquals(1, deleteRequests.size)
        assertEquals(3, infoRequests.size)
    }

    @Test
    fun a421IsAlsoReadBack() = runBlocking {
        val result = deleter(Answer.Status(421), listOf(gone)).delete(request)

        assertTrue(result.toString(), result is AuthenticatedPostDeleteResult.Deleted)
    }

    @Test
    fun noReadableAnswerStaysUnknownAndIsNotRetryable() = runBlocking {
        val result = deleter(Answer.Status(500), listOf(Answer.Status(500))).delete(request)

        assertEquals(AuthenticatedPostDeleteFailureKind.OUTCOME_UNKNOWN, kindOf(result))
        assertFalse((result as AuthenticatedPostDeleteResult.Failed).retryable)
        assertEquals(1, deleteRequests.size)
    }

    @Test
    fun aRequestThatNeverLeftIsNotReadBack() = runBlocking {
        val result = deleter(Answer.Fails(ConnectException()), listOf(gone)).delete(request)

        assertEquals(AuthenticatedPostDeleteFailureKind.TRANSPORT, kindOf(result))
        assertEquals(0, infoRequests.size)
    }

    @Test
    fun aDefiniteRejectionIsNotReadBack() = runBlocking {
        val result = deleter(Answer.Status(400), listOf(gone)).delete(request)

        assertEquals(AuthenticatedPostDeleteFailureKind.HTTP, kindOf(result))
        assertEquals(0, infoRequests.size)
    }

    @Test
    fun aCommentDeleteAnswered421IsNotConfirmedEither() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(421).message("Misdirected")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val result = ConfiguredRedditCommentDeleter(
            capability = PostDeleteCapability(session),
            callFactory = client,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
        ).delete(AuthenticatedCommentDeleteRequest(account, "t1_abc"))

        assertEquals(
            AuthenticatedCommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
            (result as AuthenticatedCommentDeleteResult.Failed).kind,
        )
    }

    @Test
    fun checkAgainReadsOnceAndNeverSends() = runBlocking {
        val result = deleter(Answer.Status(200), listOf(gone)).verify(request)

        assertTrue(result.toString(), result is AuthenticatedPostDeleteResult.Deleted)
        assertEquals(0, deleteRequests.size)
        assertEquals(1, infoRequests.size)
    }
}

private class PostDeleteCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
