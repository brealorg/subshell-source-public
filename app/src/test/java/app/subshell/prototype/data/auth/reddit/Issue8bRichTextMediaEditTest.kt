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
import app.subshell.prototype.data.network.reddit.RedditMediaImageSourceDto
import app.subshell.prototype.data.network.reddit.RedditMediaMetadataDto
import app.subshell.prototype.data.network.reddit.redditMediaEditElementKind
import app.subshell.prototype.data.network.reddit.redditMediaEditMarkdown
import app.subshell.prototype.data.network.reddit.redditMediaEditRichTextJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: Reddit refuses media placeholders in a
 * plain-text edit (INVALID_COMMENT_SUBMISSION), so text with Giphy, images or
 * emotes is converted by Reddit (convert_rte_body_format) and sent as
 * `richtext_json` with the media elements put back.
 */
class Issue8bRichTextMediaEditTest {
    private val json = Json

    // --- the RTJSON rewrite -------------------------------------------------

    @Test
    fun placeholdersBecomeTokensThatHideNothingElse() {
        val edit = redditMediaEditMarkdown("**so** good ![img](emote|t5_x|1) and\n\n![gif](giphy|abc|downsized)")!!

        assertEquals(listOf("emote|t5_x|1", "giphy|abc|downsized"), edit.targets.values.toList())
        assertFalse(edit.markdown.contains("!["))
        assertTrue(edit.markdown.startsWith("**so** good "))
        assertNull(redditMediaEditMarkdown("plain **text** only"))
    }

    @Test
    fun anEmoteGoesInlineWithFormattingSplitAndALoneGifBecomesItsOwnBlock() {
        val edit = redditMediaEditMarkdown("so good ![img](emote|t5_x|1) really\n\n![gif](giphy|abc|downsized)")!!
        val (emoteToken, gifToken) = edit.targets.keys.toList()
        // What Reddit's converter returns: "so good" bold across the token.
        val text = "so good $emoteToken really"
        val document = json.parseToJsonElement(
            """[
              {"e":"par","c":[{"e":"text","t":"$text","f":[[1,0,${text.length}]]}]},
              {"e":"par","c":[{"e":"text","t":"$gifToken"}]}
            ]""",
        ).jsonArray

        val richtext = redditMediaEditRichTextJson(document, edit) { target ->
            if (target.startsWith("giphy|")) "gif" else "img"
        }!!
        val blocks = json.parseToJsonElement(richtext).jsonObject.getValue("document").jsonArray

        val paragraph = blocks[0].jsonObject.getValue("c").jsonArray
        assertEquals(3, paragraph.size)
        assertEquals("so good ", paragraph[0].jsonObject.getValue("t").jsonPrimitive.content)
        assertEquals("[[1,0,8]]", paragraph[0].jsonObject.getValue("f").toString())
        assertEquals("""{"e":"img","id":"emote|t5_x|1"}""", paragraph[1].toString())
        assertEquals(" really", paragraph[2].jsonObject.getValue("t").jsonPrimitive.content)
        assertEquals("[[1,0,7]]", paragraph[2].jsonObject.getValue("f").toString())
        assertEquals("""{"e":"gif","id":"giphy|abc|downsized"}""", blocks[1].toString())
    }

    @Test
    fun aTokenWhereMediaCannotGoOrAMissingTokenRefusesTheEdit() {
        val edit = redditMediaEditMarkdown("![gif](giphy|abc)")!!
        val token = edit.targets.keys.single()
        val inCode = json.parseToJsonElement("""[{"e":"code","c":[{"e":"raw","t":"$token"}]}]""").jsonArray
        val missing = json.parseToJsonElement("""[{"e":"par","c":[{"e":"text","t":"gone"}]}]""").jsonArray

        assertNull(redditMediaEditRichTextJson(inCode, edit) { "gif" })
        assertNull(redditMediaEditRichTextJson(missing, edit) { "gif" })
    }

    @Test
    fun theElementTypeFollowsMediaMetadataAndGiphyIsAlwaysAGif() {
        fun meta(kind: String, mime: String) = RedditMediaMetadataDto(
            status = "valid",
            mediaKind = kind,
            mimeType = mime,
            source = RedditMediaImageSourceDto(url = "https://i.redd.it/x.png"),
        )
        val metadata = mapOf(
            "anim1" to meta("AnimatedImage", "image/gif"),
            "pic1" to meta("Image", "image/png"),
            "vid1" to meta("RedditVideo", "video/mp4"),
            "emote|t5_x|1" to meta("Image", "image/png"),
        )
        assertEquals("gif", redditMediaEditElementKind("giphy|abc|downsized", emptyMap()))
        assertEquals("gif", redditMediaEditElementKind("ANIM1", metadata))
        assertEquals("img", redditMediaEditElementKind("pic1", metadata))
        assertEquals("video", redditMediaEditElementKind("vid1", metadata))
        assertEquals("img", redditMediaEditElementKind("emote|t5_x|1", metadata))
    }

    // --- the editors over HTTP ------------------------------------------------

    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", RedditOAuth.liveCommentEditScopes.joinToString(" "), Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private val calls = mutableListOf<Pair<String, Map<String, String>>>()
    private var convertStatus = 200

    private fun client(info: String) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val form = (request.body as? FormBody)?.let { body ->
            (0 until body.size).associate { index -> body.name(index) to body.value(index) }
        }.orEmpty()
        calls += path to form
        val (code, body) = when {
            path.endsWith("/api/convert_rte_body_format") -> {
                // Echo the markdown as one paragraph per block, like Reddit does.
                val blocks = form.getValue("markdown_text").split("\n\n").joinToString(",") { block ->
                    """{"e":"par","c":[{"e":"text","t":${JsonPrimitiveString(block)}}]}"""
                }
                convertStatus to """{"output":{"document":[$blocks]},"output_mode":"rtjson","assets":[]}"""
            }
            path.endsWith("/api/editusertext") -> 200 to """{"id":"abc","name":"t1_abc","body":"edited"}"""
            else -> 200 to info
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun JsonPrimitiveString(value: String) = kotlinx.serialization.json.JsonPrimitive(value).toString()

    private val commentInfo = """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"abc","name":"t1_abc",
        "author":"test-user","body":"old ![gif](giphy|abc|downsized)",
        "media_metadata":{"giphy|abc|downsized":{"status":"invalid"}}}}]}}"""

    private fun commentEditor(info: String = commentInfo) = ConfiguredRedditCommentEditor(
        capability = TestCapability(session),
        callFactory = client(info),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        readBackDelaysMs = listOf(0L),
    )

    @Test
    fun aCommentWithAGiphyIsEditedAsRichTextWithTheGifKept() = runBlocking {
        val result = commentEditor().edit(
            AuthenticatedCommentEditRequest(account, "t1_abc", "edited text\n\n![gif](giphy|abc|downsized)"),
        )

        assertTrue(result.toString(), result is AuthenticatedCommentEditResult.Edited)
        assertEquals(
            listOf("/api/info", "/api/convert_rte_body_format", "/api/editusertext"),
            calls.map { (path, _) -> path },
        )
        assertFalse(calls[1].second.getValue("markdown_text").contains("giphy"))
        val edit = calls[2].second
        assertFalse(edit.containsKey("text"))
        assertEquals("t1_abc", edit.getValue("thing_id"))
        val document = json.parseToJsonElement(edit.getValue("richtext_json")).jsonObject.getValue("document") as JsonArray
        assertEquals("""{"e":"par","c":[{"e":"text","t":"edited text"}]}""", document[0].toString())
        assertEquals("""{"e":"gif","id":"giphy|abc|downsized"}""", document[1].toString())
    }

    @Test
    fun aFailedConversionSendsNothingAndSavingAgainIsSafe() = runBlocking {
        convertStatus = 500
        val result = commentEditor().edit(
            AuthenticatedCommentEditRequest(account, "t1_abc", "edited ![gif](giphy|abc|downsized)"),
        )

        val failed = result as AuthenticatedCommentEditResult.Failed
        assertEquals(AuthenticatedCommentEditFailureKind.NOT_SAVED, failed.kind)
        assertTrue(failed.retryable)
        assertFalse(calls.any { (path, _) -> path.endsWith("/api/editusertext") })
    }

    @Test
    fun plainTextStillGoesAsTextWithoutAConversion() = runBlocking {
        val result = commentEditor().edit(AuthenticatedCommentEditRequest(account, "t1_abc", "just **words**"))

        assertTrue(result.toString(), result is AuthenticatedCommentEditResult.Edited)
        assertEquals(listOf("/api/editusertext"), calls.map { (path, _) -> path })
        assertEquals("just **words**", calls.single().second.getValue("text"))
    }

    @Test
    fun aSelfPostWithAnInlineImageIsEditedAsRichText() = runBlocking {
        val postInfo = """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"p1","name":"t3_p1",
            "title":"t","selftext":"old","media_metadata":{"pic123":{"status":"valid","e":"Image","m":"image/png",
            "s":{"u":"https://i.redd.it/pic123.png"}}}}}]}}"""
        val editor = ConfiguredRedditPostEditor(
            capability = TestCapability(session),
            callFactory = client(postInfo),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            readBackDelaysMs = listOf(0L),
        )

        val result = editor.edit(AuthenticatedPostEditRequest(account, "t3_p1", "new words\n\n![img](pic123)"))

        assertTrue(result.toString(), result is AuthenticatedPostEditResult.Edited)
        val document = json.parseToJsonElement(calls.last().second.getValue("richtext_json"))
            .jsonObject.getValue("document") as JsonArray
        assertEquals("""{"e":"img","id":"pic123"}""", document[1].toString())
        assertTrue(document[0] is JsonObject)
    }
}

private class TestCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
