package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.commentThreadAnswer
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
import app.subshell.prototype.data.network.reddit.redditEditedStamp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EDITREADBACK01: Reddit writes a rich-text (media) edit back as its own
 * markdown. After a lost answer, such an edit counts as saved when Reddit's
 * `edited` stamp moved past the one read before sending, even if the text
 * reads differently. Plain edits and edits inside Reddit's grace window
 * (edited stays false) still compare text.
 */
class EditReadback01EditedStampTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", RedditOAuth.liveCommentEditScopes.joinToString(" "), Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private val editRequests = mutableListOf<String>()
    private val infoRequests = mutableListOf<String>()

    private fun client(editStatus: Int, infos: List<Pair<Int, String>>) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val (code, body) = when {
            path.endsWith("/api/convert_rte_body_format") -> {
                val form = request.body as FormBody
                val markdown = (0 until form.size).first { form.name(it) == "markdown_text" }.let(form::value)
                val blocks = markdown.split("\n\n").joinToString(",") { block ->
                    """{"e":"par","c":[{"e":"text","t":${JsonPrimitive(block)}}]}"""
                }
                200 to """{"output":{"document":[$blocks]},"output_mode":"rtjson","assets":[]}"""
            }
            path.endsWith("/api/editusertext") -> {
                editRequests += path
                editStatus to """{"json":{"errors":[]}}"""
            }
            else -> {
                infoRequests += path
                infos.getOrElse(infoRequests.size - 1) { infos.last() }
            }
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun post(selftext: String, edited: String) = 200 to
        """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"p1","name":"t3_p1","title":"t",
        "selftext":${JsonPrimitive(selftext)},"edited":$edited,
        "media_metadata":{"giphy|abc|downsized":{"status":"invalid"}}}}]}}""".let(::postThreadAnswer)

    private fun comment(body: String, edited: String) = 200 to
        """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"c1","name":"t1_c1","author":"test-user",
        "body":${JsonPrimitive(body)},"edited":$edited,
        "media_metadata":{"giphy|abc|downsized":{"status":"invalid"}}}}]}}""".let(::commentThreadAnswer)

    private fun postEditor(editStatus: Int, infos: List<Pair<Int, String>>) = ConfiguredRedditPostEditor(
        capability = StampTestCapability(session),
        callFactory = client(editStatus, infos),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        readBackDelaysMs = listOf(0L),
    )

    private fun commentEditor(editStatus: Int, infos: List<Pair<Int, String>>) = ConfiguredRedditCommentEditor(
        capability = StampTestCapability(session),
        callFactory = client(editStatus, infos),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        readBackDelaysMs = listOf(0L),
    )

    private val sentMedia = "new **words**\n\n![gif](giphy|abc|downsized)"
    private val rewritten = "new **words**\n\n![gif](giphy|abc|downsized \"re-serialised\")"
    private val mediaPostEdit get() = AuthenticatedPostEditRequest(account, "t3_p1", sentMedia)

    @Test
    fun theEditedStampReadsFalseAsNeverEditedAndRefusesOtherShapes() {
        val json = Json
        assertEquals(0.0, redditEditedStamp(json.parseToJsonElement("false"))!!, 0.0)
        assertEquals(1_727_500_000.5, redditEditedStamp(json.parseToJsonElement("1727500000.5"))!!, 0.0)
        assertNull(redditEditedStamp(null))
        assertNull(redditEditedStamp(json.parseToJsonElement("true")))
        assertNull(redditEditedStamp(json.parseToJsonElement("\"1727500000\"")))
    }

    @Test
    fun aLostRichTextPostEditThatReadsBackRewrittenButNewlyEditedIsSaved() = runBlocking {
        val result = postEditor(502, listOf(post("old", "false"), post(rewritten, "1727500000.0"))).edit(mediaPostEdit)

        assertTrue(result.toString(), result is AuthenticatedPostEditResult.Edited)
        assertEquals(1, editRequests.size)
    }

    @Test
    fun anEditInsideTheGraceWindowStillComparesText() = runBlocking {
        val result = postEditor(502, listOf(post("old", "false"), post(rewritten, "false"))).edit(mediaPostEdit)

        assertEquals(AuthenticatedPostEditFailureKind.NOT_SAVED, (result as AuthenticatedPostEditResult.Failed).kind)
    }

    @Test
    fun anUnchangedStampIsNotSaved() = runBlocking {
        val result = postEditor(502, listOf(post("old", "1727000000.0"), post("old", "1727000000.0"))).edit(mediaPostEdit)

        assertEquals(AuthenticatedPostEditFailureKind.NOT_SAVED, (result as AuthenticatedPostEditResult.Failed).kind)
    }

    @Test
    fun aPlainEditHasNoStampAndStillComparesText() = runBlocking {
        val result = postEditor(502, listOf(post("other words", "1727500000.0")))
            .edit(AuthenticatedPostEditRequest(account, "t3_p1", "plain words"))

        assertEquals(AuthenticatedPostEditFailureKind.NOT_SAVED, (result as AuthenticatedPostEditResult.Failed).kind)
    }

    @Test
    fun checkAgainUsesTheStampReadBeforeTheLostEdit() = runBlocking {
        val editor = postEditor(
            502,
            listOf(post("old", "false"), 500 to "down", post(rewritten, "1727500000.0")),
        )

        val first = editor.edit(mediaPostEdit)
        assertEquals(AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN, (first as AuthenticatedPostEditResult.Failed).kind)

        val checked = editor.verify(mediaPostEdit)

        assertTrue(checked.toString(), checked is AuthenticatedPostEditResult.Edited)
        assertEquals(1, editRequests.size)
    }

    @Test
    fun aLostRichTextCommentEditThatReadsBackRewrittenButNewlyEditedIsSaved() = runBlocking {
        val result = commentEditor(502, listOf(comment("old", "false"), comment(rewritten, "1727500000.0")))
            .edit(AuthenticatedCommentEditRequest(account, "t3_p1", "t1_c1", sentMedia))

        assertTrue(result.toString(), result is AuthenticatedCommentEditResult.Edited)
        assertEquals(1, editRequests.size)
    }
}

private class StampTestCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
