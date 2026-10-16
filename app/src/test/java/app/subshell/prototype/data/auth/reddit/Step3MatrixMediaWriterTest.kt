package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c5r3, 02c6, 02c6r2, 02c6r3, 02e2a2): a Direct Chat image
 * goes to Matrix as one upload and one `m.image` event (body "Image", url =
 * the upload's content URI); a warm session skips whoami and room discovery
 * and an accepted send returns at once (no sync); an image with text sends
 * the image first, then the text; a lost send is read back once and never
 * sent again; a 401 drops the web token, a 404 forgets the room; a room with
 * three members or a Matrix user of another account is refused before
 * anything is uploaded; a Direct Chat image without a participant is never
 * sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3MatrixMediaWriterTest {
    private val fixture = MatrixTestFixture()
    private val directory = createTempDirectory("step3-matrix-media").toFile()
    private val contentUri = "mxc://reddit.com/media123"
    private val downloadUri = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media123"

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun stagedFile(): File = File(directory, "picture-${System.nanoTime()}.gif").apply {
        writeBytes(byteArrayOf(1, 2, 3))
    }

    private val stager = RedditMediaUploadSource { _, _ ->
        RedditMediaStageResult.Staged(
            file = stagedFile(),
            fileName = "picture.gif",
            mimeType = "image/gif",
            sizeBytes = 3L,
        )
    }

    private fun writer() = RedditChatMatrixMediaWriter(
        tokenProvider = fixture.tokenSource,
        mediaSource = stager,
        client = fixture.client,
        ioDispatcher = Dispatchers.IO,
        sessionCache = fixture.sessionCache,
    )

    private fun request(text: String = "", participant: String? = fixture.participant) = MessageReplyRequest(
        accountStableId = fixture.account,
        inboxItemId = "t4_chat",
        parentFullname = "t4_chat",
        channel = MessageReplyChannel.DIRECT_CHAT,
        text = text,
        media = MessageReplyMediaAttachment(uri = "content://media/1", mimeType = "image/gif"),
        directChatParticipantUsername = participant,
    )

    private fun send(text: String = "") = runBlocking {
        writer().send(request(text), expectedProviderAccountId = fixture.providerAccountId)
    }

    /** Answers for a warm session: the upload, then each m.room.message send in turn. */
    private fun routes(vararg sends: () -> MatrixAnswer): (RecordedMatrixRequest) -> MatrixAnswer {
        var sendIndex = 0
        return { request ->
            val path = decode(request.path)
            when {
                path == "/_matrix/media/v3/upload" ->
                    MatrixAnswer.Json(200, """{"content_uri":"$contentUri"}""")
                path.contains("/send/m.room.message/") -> sends[sendIndex++]()
                else -> MatrixAnswer.Json(404, "{}")
            }
        }
    }

    private fun accepted(eventId: String) = { MatrixAnswer.Json(200, """{"event_id":"$eventId"}""") }

    private fun sends() = fixture.requests.filter { decode(it.path).contains("/send/m.room.message/") }

    private fun msgtype(request: RecordedMatrixRequest): String =
        Json.parseToJsonElement(requireNotNull(request.body)).jsonObject.getValue("msgtype").jsonPrimitive.content

    @Test
    fun aWarmImageIsOneUploadAndOneImageEventWithoutAnyLookupOrSync() {
        fixture.warmCache()
        fixture.respond = routes(accepted("\$image"))

        val result = send()

        val accepted = result as AuthenticatedRedditMessageReplyResult.Accepted
        assertEquals("\$image", accepted.sentMatrixEventId)
        assertEquals("media123", accepted.sentMatrixMediaId)
        assertEquals(downloadUri, accepted.sentMediaUri)
        assertEquals(listOf("POST", "PUT"), fixture.requests.map { it.method })
        assertEquals("/_matrix/media/v3/upload", decode(fixture.requests[0].path))
        val send = fixture.requests[1]
        assertTrue(
            decode(send.path),
            decode(send.path).startsWith("/_matrix/client/v3/rooms/${fixture.roomId}/send/m.room.message/"),
        )
        val body = Json.parseToJsonElement(requireNotNull(send.body)).jsonObject
        assertEquals("m.image", body.getValue("msgtype").jsonPrimitive.content)
        assertEquals("Image", body.getValue("body").jsonPrimitive.content)
        assertEquals(contentUri, body.getValue("url").jsonPrimitive.content)
        assertEquals("Bearer ${fixture.webToken}", send.authorization)
        assertFalse(fixture.requests.any { decode(it.path).endsWith("/account/whoami") })
        assertFalse(fixture.requests.any { decode(it.path).endsWith("/sync") })
    }

    @Test
    fun anImageWithTextSendsTheImageFirstThenTheText() {
        fixture.warmCache()
        fixture.respond = routes(accepted("\$image"), accepted("\$text"))

        val result = send(text = "look at this")

        val accepted = result as AuthenticatedRedditMessageReplyResult.Accepted
        assertEquals("\$image", accepted.sentMatrixEventId)
        assertEquals(listOf("m.image", "m.text"), sends().map(::msgtype))
        val text = Json.parseToJsonElement(requireNotNull(sends()[1].body)).jsonObject
        assertEquals("look at this", text.getValue("body").jsonPrimitive.content)
    }

    @Test
    fun aLostImageSendIsReadBackOnceAndNeverSentAgain() {
        fixture.warmCache()
        fixture.respond = routes({ MatrixAnswer.Transport })

        val result = send()

        assertEquals(
            MessageReplyFailureKind.OUTCOME_UNKNOWN,
            (result as AuthenticatedRedditMessageReplyResult.Failed).kind,
        )
        assertEquals(1, sends().size)
        // The read-back is a read: everything after the send is a GET.
        val afterSend = fixture.requests.dropWhile { !decode(it.path).contains("/send/") }.drop(1)
        assertTrue(afterSend.toString(), afterSend.all { it.method == "GET" })
    }

    @Test
    fun aRefusedTextAfterAnAcceptedImageKeepsTheImageIdentity() {
        fixture.warmCache()
        fixture.respond = routes(accepted("\$image"), { MatrixAnswer.Json(400, "{}") })

        val failed = send(text = "caption") as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.HTTP, failed.kind)
        assertEquals("\$image", failed.sentMatrixEventId)
        assertEquals("media123", failed.sentMatrixMediaId)
        assertEquals(downloadUri, failed.sentMediaUri)
        assertEquals(2, sends().size)
    }

    @Test
    fun aLostTextAfterAnAcceptedImageIsUnknownAndTheTextIsSentOnce() {
        fixture.warmCache()
        fixture.respond = routes(accepted("\$image"), { MatrixAnswer.Transport })

        val failed = send(text = "caption") as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.OUTCOME_UNKNOWN, failed.kind)
        assertEquals("\$image", failed.sentMatrixEventId)
        assertEquals(downloadUri, failed.sentMediaUri)
        assertEquals(listOf("m.image", "m.text"), sends().map(::msgtype))
    }

    @Test
    fun aSend401DropsTheWebTokenAndIsNotRetried() {
        fixture.warmCache()
        fixture.respond = routes({ MatrixAnswer.Json(401, "{}") })

        val failed = send() as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, failed.kind)
        assertEquals(listOf<String?>(fixture.webToken), fixture.invalidatedTokens.toList())
        assertEquals(1, sends().size)
    }

    @Test
    fun aSend404ForgetsTheCachedRoom() {
        fixture.warmCache()
        fixture.respond = routes({ MatrixAnswer.Json(404, "{}") })

        send()

        assertNull(fixture.cachedRoom())
        assertEquals(1, sends().size)
    }

    @Test
    fun aRoomWithThreeMembersIsRefusedBeforeAnythingIsUploaded() {
        val member = { userId: String, name: String ->
            """{"type":"m.room.member","state_key":"$userId","content":{"membership":"join","displayname":"$name"}}"""
        }
        val sync = """{"rooms":{"join":{"${fixture.roomId}":{"state":{"events":[
            ${member(fixture.ownMatrixUserId, "me")},
            ${member(fixture.peerMatrixUserId, fixture.participant)},
            ${member("@t2_third:reddit.com", "third")}
        ]}}}}}"""
        fixture.respond = { request ->
            val path = decode(request.path)
            when {
                path.endsWith("/account/whoami") ->
                    MatrixAnswer.Json(200, """{"user_id":"${fixture.ownMatrixUserId}"}""")
                path.endsWith("/sync") -> MatrixAnswer.Json(200, sync)
                path.endsWith("/members") -> MatrixAnswer.Json(404, "{}")
                else -> error("must not reach $path")
            }
        }

        val failed = send() as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE, failed.kind)
        assertFalse(fixture.requests.any { decode(it.path).contains("/upload") || decode(it.path).contains("/send/") })
        assertNull(fixture.cachedRoom())
    }

    @Test
    fun aMatrixUserOfAnotherAccountDropsTheTokenBeforeAnythingIsUploaded() {
        fixture.respond = { request ->
            val path = decode(request.path)
            when {
                path.endsWith("/account/whoami") -> MatrixAnswer.Json(200, """{"user_id":"@t2_someoneelse:reddit.com"}""")
                else -> error("must not reach $path")
            }
        }

        val failed = send() as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, failed.kind)
        assertEquals(listOf<String?>(fixture.webToken), fixture.invalidatedTokens.toList())
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun aDirectChatImageWithoutAParticipantIsNeverSent() = runBlocking {
        fixture.warmCache()
        val offline = OkHttpClient.Builder().addInterceptor { error("no Reddit request expected") }.build()
        val messageWriter = RedditMessageReplyWriter(
            callFactory = offline,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            capability = fixture.capability,
            directChatMediaWriter = writer(),
            ioDispatcher = Dispatchers.IO,
        )

        val viaReply = messageWriter.send(request(participant = null))
        val direct = writer().send(request(participant = null), expectedProviderAccountId = fixture.providerAccountId)

        assertEquals(
            MessageReplyFailureKind.VALIDATION,
            (viaReply as AuthenticatedRedditMessageReplyResult.Failed).kind,
        )
        assertEquals(
            MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE,
            (direct as AuthenticatedRedditMessageReplyResult.Failed).kind,
        )
        assertTrue(fixture.requests.isEmpty())
    }

    private fun decode(path: String): String = java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
}
