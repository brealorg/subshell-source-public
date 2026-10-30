package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.postThreadAnswer
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

/**
 * H2: a post edit whose answer is lost (408/421/425/5xx, timeout, broken
 * connection, unreadable 2xx) is read back instead of re-sent. The new text
 * means saved, the old text means not saved, no answer stays unknown.
 */
class H2PostEditOutcomeTest {
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
        data class Status(val code: Int, val body: String = """{"json":{"errors":[]}}""") : Answer
        data class Fails(val error: IOException) : Answer
    }

    private val editRequests = mutableListOf<String>()
    private val infoRequests = mutableListOf<String>()

    private fun editor(edit: Answer, info: List<Answer>): ConfiguredRedditPostEditor {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val answer = if (path.endsWith("/api/editusertext")) {
                editRequests += path
                edit
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
        return ConfiguredRedditPostEditor(
            capability = PostEditCapability(session),
            callFactory = client,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            readBackDelaysMs = listOf(0L, 0L, 0L),
        )
    }

    private fun shows(body: String) = Answer.Status(
        200,
        """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"abc","name":"t3_abc","author":"test-user","title":"A post","selftext":${"\"" + body + "\""}}}]}}""".let(::postThreadAnswer),
    )

    private val request get() = AuthenticatedPostEditRequest(account, "t3_abc", "new text")

    private fun kindOf(result: AuthenticatedPostEditResult) =
        (result as AuthenticatedPostEditResult.Failed).kind

    @Test
    fun a502ThenTheNewTextIsSavedAndNothingIsResent() = runBlocking {
        val result = editor(Answer.Status(502), listOf(shows("new text"))).edit(request)

        assertTrue(result.toString(), result is AuthenticatedPostEditResult.Edited)
        assertEquals(1, editRequests.size)
    }

    @Test
    fun aTimeoutThenTheOldTextIsNotSavedAndSavingAgainIsOffered() = runBlocking {
        val result = editor(Answer.Fails(SocketTimeoutException()), listOf(shows("old text"))).edit(request)

        assertEquals(AuthenticatedPostEditFailureKind.NOT_SAVED, kindOf(result))
        assertTrue((result as AuthenticatedPostEditResult.Failed).retryable)
        assertEquals(1, editRequests.size)
        assertEquals(3, infoRequests.size)
    }

    @Test
    fun aLateReadThatShowsTheNewTextStillCountsAsSaved() = runBlocking {
        val result = editor(Answer.Status(503), listOf(shows("old text"), shows("new text"))).edit(request)

        assertTrue(result.toString(), result is AuthenticatedPostEditResult.Edited)
        assertEquals(2, infoRequests.size)
    }

    @Test
    fun noReadableAnswerStaysUnknownAndIsNotRetryable() = runBlocking {
        val result = editor(Answer.Status(500), listOf(Answer.Status(500))).edit(request)

        assertEquals(AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN, kindOf(result))
        assertFalse((result as AuthenticatedPostEditResult.Failed).retryable)
        assertEquals(1, editRequests.size)
    }

    @Test
    fun anUnreadableSuccessIsReadBack() = runBlocking {
        val result = editor(Answer.Status(200, "not json"), listOf(shows("new text"))).edit(request)

        assertTrue(result.toString(), result is AuthenticatedPostEditResult.Edited)
    }

    // NET03 (#62): a TLS reset while reading the answer is not "never sent".
    @Test
    fun anSslResetWhileReadingTheAnswerIsReadBack() = runBlocking {
        val reset = javax.net.ssl.SSLException("Read error: ssl=0x7b: I/O error during system call, Connection reset by peer")
        val result = editor(Answer.Fails(reset), listOf(shows("new text"))).edit(request)

        assertTrue(result.toString(), result is AuthenticatedPostEditResult.Edited)
        assertEquals(1, editRequests.size)
    }

    @Test
    fun aFailedTlsHandshakeIsNotReadBack() = runBlocking {
        val result = editor(Answer.Fails(javax.net.ssl.SSLHandshakeException("handshake failed")), listOf(shows("new text"))).edit(request)

        assertEquals(AuthenticatedPostEditFailureKind.TRANSPORT, kindOf(result))
        assertEquals(0, infoRequests.size)
    }

    @Test
    fun aRequestThatNeverLeftIsNotReadBack() = runBlocking {
        val result = editor(Answer.Fails(ConnectException()), listOf(shows("new text"))).edit(request)

        assertEquals(AuthenticatedPostEditFailureKind.TRANSPORT, kindOf(result))
        assertEquals(0, infoRequests.size)
    }

    @Test
    fun aDefiniteRejectionIsNotReadBack() = runBlocking {
        val result = editor(Answer.Status(400), listOf(shows("new text"))).edit(request)

        assertEquals(AuthenticatedPostEditFailureKind.HTTP, kindOf(result))
        assertEquals(0, infoRequests.size)
    }

    @Test
    fun checkAgainReadsOnceAndNeverSends() = runBlocking {
        val editor = editor(Answer.Status(200), listOf(shows("new text")))

        val result = editor.verify(request)

        assertTrue(result.toString(), result is AuthenticatedPostEditResult.Edited)
        assertEquals(0, editRequests.size)
        assertEquals(1, infoRequests.size)
    }
}

private class PostEditCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
