package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c4r2, messages02c5): a comment or a message-thread reply
 * with an image leases an upload slot, uploads the file, converts the text
 * and sends one `richtext_json` that refers to the image by its asset id
 * (no markdown text, no upload URL); a Direct Chat image goes to Matrix and
 * never to `/api/comment`, and without a Matrix writer it is unavailable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3MediaReplyPipelineTest {
    private val account = "reddit:redreader_compatibility:me"
    private val directory = createTempDirectory("step3-media").toFile()
    private val image = File(directory, "picture.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    private val paths = mutableListOf<String>()
    private val forms = mutableMapOf<String, Map<String, String>>()

    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            "access",
            "refresh",
            "bearer",
            (RedditOAuth.liveCommentEditScopes + RedditOAuth.liveInboxReadScopes).joinToString(" "),
            Long.MAX_VALUE,
        ),
        identity = AuthAccountIdentity(providerAccountId = "me", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val writeCapability = object : AuthCommentWriteTokenSource {
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(session)
    }
    private val stager = RedditMediaUploadSource { _, _ ->
        RedditMediaStageResult.Staged(file = image, fileName = "picture.png", mimeType = "image/png", sizeBytes = 3L)
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private val reddit = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        paths += path
        (request.body as? FormBody)?.let { form ->
            forms[path] = (0 until form.size).associate { form.name(it) to form.value(it) }
        }
        val body = when {
            path.endsWith("/api/media/asset.json") -> """
                {"args":{"action":"//reddit-uploaded-media.s3-accelerate.amazonaws.com",
                 "fields":[{"name":"key","value":"rte_images/abc123"},{"name":"acl","value":"private"}]},
                 "asset":{"asset_id":"abc123"}}
            """.trimIndent()
            path.endsWith("/api/convert_rte_body_format") ->
                """{"output":{"document":[{"e":"par","c":[{"e":"text","t":"look"}]}]}}"""
            path.endsWith("/api/comment") -> """
                {"json":{"errors":[],"data":{"things":[{"kind":"t1","data":{
                  "id":"made","name":"t1_made","author":"me","body":"look","parent_id":"t3_post",
                  "link_id":"t3_post","score":1,"created_utc":1000}}]}}}
            """.trimIndent()
            else -> "{}"
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private val uploads = OkHttpClient.Builder().addInterceptor { chain ->
        paths += "upload:" + chain.request().url.host
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(204).message("No Content")
            .body("".toResponseBody(null))
            .build()
    }.build()

    private fun assertOneRichTextSendReferringToTheAsset() {
        assertEquals(
            listOf(
                "/api/media/asset.json",
                "upload:reddit-uploaded-media.s3-accelerate.amazonaws.com",
                "/api/convert_rte_body_format",
                "/api/comment",
            ),
            paths,
        )
        val sent = forms.getValue("/api/comment")
        val richtext = sent.getValue("richtext_json")
        assertTrue(richtext, richtext.contains("\"e\":\"img\"") && richtext.contains("\"id\":\"abc123\""))
        assertFalse(richtext, richtext.contains("amazonaws"))
        assertFalse(sent.toString(), "text" in sent)
    }

    @Test
    fun aCommentWithAnImageSendsOneRichTextThatRefersToTheUploadedAsset() = runBlocking {
        val submitter = ConfiguredRedditCommentSubmitter(
            capability = writeCapability,
            callFactory = reddit,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            mediaSource = stager,
            mediaUploadCallFactory = uploads,
            mediaWebSocketClient = uploads,
        )

        val result = submitter.submit(
            AuthenticatedCommentSubmitRequest(
                accountStableId = account,
                parentFullname = "t3_post",
                text = "look",
                media = AuthenticatedCommentSubmitMediaAttachment("content://x/1", "image/png"),
            ),
        )

        assertTrue(result.toString(), result is AuthenticatedCommentSubmitResult.Submitted)
        assertOneRichTextSendReferringToTheAsset()
    }

    private fun messageWriter(directChat: RedditChatMatrixMediaWriter? = null) = RedditMessageReplyWriter(
        callFactory = reddit,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        capability = MatrixTestFixture().capability,
        mediaCapability = writeCapability,
        mediaSource = stager,
        mediaUploadCallFactory = uploads,
        mediaWebSocketClient = uploads,
        directChatMediaWriter = directChat,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private val image_ = MessageReplyMediaAttachment(uri = "content://x/1", mimeType = "image/png")

    @Test
    fun aMessageThreadReplyWithAnImageSendsOneRichTextOnTheThread() = runBlocking {
        messageWriter().send(
            MessageReplyRequest(
                accountStableId = account,
                inboxItemId = "t4_root",
                parentFullname = "t4_root",
                channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
                text = "look",
                media = image_,
            ),
        )

        assertOneRichTextSendReferringToTheAsset()
        assertEquals("t4_root", forms.getValue("/api/comment")["thing_id"])
    }

    private fun directChatImage() = MessageReplyRequest(
        accountStableId = account,
        inboxItemId = "t4_chat",
        parentFullname = "t4_chat123",
        channel = MessageReplyChannel.DIRECT_CHAT,
        text = "",
        media = image_,
        directChatParticipantUsername = "peer",
    )

    @Test
    fun aDirectChatImageGoesToMatrixAndNeverToTheCommentEndpoint() = runBlocking {
        val matrix = MatrixTestFixture()
        val writer = messageWriter(
            RedditChatMatrixMediaWriter(
                tokenProvider = matrix.tokenSource,
                mediaSource = stager,
                client = matrix.client,
                ioDispatcher = Dispatchers.Unconfined,
                sessionCache = matrix.sessionCache,
            ),
        )

        writer.send(directChatImage())

        assertTrue("Matrix was not asked", matrix.requests.isNotEmpty())
        assertEquals(emptyList<String>(), paths)
    }

    @Test
    fun aDirectChatImageWithoutAMatrixWriterIsUnavailable() = runBlocking {
        val result = messageWriter(directChat = null).send(directChatImage())

        assertEquals(
            MessageReplyFailureKind.UNAVAILABLE,
            (result as AuthenticatedRedditMessageReplyResult.Failed).kind,
        )
        assertEquals(emptyList<String>(), paths)
    }
}
