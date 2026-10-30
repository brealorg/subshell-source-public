package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.commentThreadAnswer
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
 * H1: a comment edit whose answer is lost (408/421/425/5xx, timeout, broken
 * connection, unreadable 2xx) is read back instead of re-sent. The new text
 * means saved, the old text means not saved, no answer stays unknown.
 */
class H1CommentEditOutcomeTest {
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

    private fun editor(edit: Answer, info: List<Answer>): ConfiguredRedditCommentEditor {
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
        return ConfiguredRedditCommentEditor(
            capability = CommentEditCapability(session),
            callFactory = client,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            readBackDelaysMs = listOf(0L, 0L, 0L),
        )
    }

    private fun shows(body: String) = Answer.Status(
        200,
        """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"abc","name":"t1_abc","author":"test-user","body":${"\"" + body + "\""}}}]}}""".let(::commentThreadAnswer),
    )

    private val request get() = AuthenticatedCommentEditRequest(account, "t3_p1", "t1_abc", "new text")

    private fun kindOf(result: AuthenticatedCommentEditResult) =
        (result as AuthenticatedCommentEditResult.Failed).kind

    @Test
    fun a502ThenTheNewTextIsSavedAndNothingIsResent() = runBlocking {
        val result = editor(Answer.Status(502), listOf(shows("new text"))).edit(request)

        assertTrue(result.toString(), result is AuthenticatedCommentEditResult.Edited)
        assertEquals(1, editRequests.size)
    }

    @Test
    fun aTimeoutThenTheOldTextIsNotSavedAndSavingAgainIsOffered() = runBlocking {
        val result = editor(Answer.Fails(SocketTimeoutException()), listOf(shows("old text"))).edit(request)

        assertEquals(AuthenticatedCommentEditFailureKind.NOT_SAVED, kindOf(result))
        assertTrue((result as AuthenticatedCommentEditResult.Failed).retryable)
        assertEquals(1, editRequests.size)
        assertEquals(3, infoRequests.size)
    }

    @Test
    fun aLateReadThatShowsTheNewTextStillCountsAsSaved() = runBlocking {
        val result = editor(Answer.Status(503), listOf(shows("old text"), shows("new text"))).edit(request)

        assertTrue(result.toString(), result is AuthenticatedCommentEditResult.Edited)
        assertEquals(2, infoRequests.size)
    }

    @Test
    fun noReadableAnswerStaysUnknownAndIsNotRetryable() = runBlocking {
        val result = editor(Answer.Status(500), listOf(Answer.Status(500))).edit(request)

        assertEquals(AuthenticatedCommentEditFailureKind.OUTCOME_UNKNOWN, kindOf(result))
        assertFalse((result as AuthenticatedCommentEditResult.Failed).retryable)
        assertEquals(1, editRequests.size)
    }

    @Test
    fun anUnreadableSuccessIsReadBack() = runBlocking {
        val result = editor(Answer.Status(200, "not json"), listOf(shows("new text"))).edit(request)

        assertTrue(result.toString(), result is AuthenticatedCommentEditResult.Edited)
    }

    @Test
    fun aRequestThatNeverLeftIsNotReadBack() = runBlocking {
        val result = editor(Answer.Fails(ConnectException()), listOf(shows("new text"))).edit(request)

        assertEquals(AuthenticatedCommentEditFailureKind.TRANSPORT, kindOf(result))
        assertEquals(0, infoRequests.size)
    }

    @Test
    fun aDefiniteRejectionIsNotReadBack() = runBlocking {
        val result = editor(Answer.Status(400), listOf(shows("new text"))).edit(request)

        assertEquals(AuthenticatedCommentEditFailureKind.HTTP, kindOf(result))
        assertEquals(0, infoRequests.size)
    }

    @Test
    fun checkAgainReadsOnceAndNeverSends() = runBlocking {
        val editor = editor(Answer.Status(200), listOf(shows("new text")))

        val result = editor.verify(request)

        assertTrue(result.toString(), result is AuthenticatedCommentEditResult.Edited)
        assertEquals(0, editRequests.size)
        assertEquals(1, infoRequests.size)
    }

    @Test
    fun trailingWhitespaceAndLineEndingsDoNotMakeASavedEditLookUnsaved() {
        assertTrue(sameEditedText("line one\nline two\n", "line one\r\nline two"))
        assertFalse(sameEditedText("old text", "new text"))
    }
}

private class CommentEditCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
