package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.DirectChatDeleteFailureKind
import app.subshell.prototype.data.account.DirectChatStartFailureKind
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * STEP3 (messages02d2b, 02d3b, 02d5a, 02d5c): the Matrix typing, redaction and
 * start writers driven over a recording interceptor, so their transport rules
 * are behaviour, not source text. A first message that is one image or GIF is
 * staged on the device before anything is asked of Matrix, sent as one
 * `m.image` in the found or created room (never a read receipt), and the
 * staged copy is deleted; a failed staging creates no room and sends nothing;
 * a lost image send is an unknown outcome with one send.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3MatrixWritersTest {
    private val fixture = MatrixTestFixture()

    private fun typingWriter() = RedditChatMatrixTypingWriter(
        capability = fixture.capability,
        tokenProvider = fixture.tokenSource,
        client = fixture.client,
        ioDispatcher = Dispatchers.IO,
        sessionCache = fixture.sessionCache,
    )

    private fun redactionWriter() = RedditChatMatrixRedactionWriter(
        capability = fixture.capability,
        tokenProvider = fixture.tokenSource,
        client = fixture.client,
        ioDispatcher = Dispatchers.IO,
        sessionCache = fixture.sessionCache,
    )

    private fun startWriter() = RedditChatMatrixStartWriter(
        capability = fixture.capability,
        tokenProvider = fixture.tokenSource,
        client = fixture.client,
        ioDispatcher = Dispatchers.IO,
        sessionCache = fixture.sessionCache,
    )

    // Typing (messages02d2b).

    @Test
    fun typingOnAWarmRoomIsOnePutToTheTypingPathWithTheTimeout() = runBlocking {
        fixture.warmCache()

        assertTrue(typingWriter().setTyping(fixture.account, fixture.participant, typing = true))

        val request = fixture.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("/_matrix/client/v3/rooms/${fixture.roomId}/typing/${fixture.ownMatrixUserId}", decode(request.path))
        assertEquals("""{"typing":true,"timeout":15000}""", request.body)
        assertEquals("Bearer ${fixture.webToken}", request.authorization)
    }

    @Test
    fun typingWithoutAWarmRoomSendsNothingAndNeverLooksForARoom() = runBlocking {
        assertFalse(typingWriter().setTyping(fixture.account, fixture.participant, typing = true))

        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun aTypingAnswer404ForgetsTheCachedRoom() = runBlocking {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Json(404, "{}") }

        assertFalse(typingWriter().setTyping(fixture.account, fixture.participant, typing = false))

        assertNull(fixture.cachedRoom())
    }

    @Test
    fun typingWritesAreSerialised() = runBlocking {
        fixture.warmCache()
        val writer = typingWriter()

        listOf(true, false, true).map { typing ->
            async(Dispatchers.IO) { writer.setTyping(fixture.account, fixture.participant, typing) }
        }.awaitAll()

        assertEquals(3, fixture.requests.size)
        assertEquals(1, fixture.maxConcurrentRequests.get())
    }

    @Test
    fun typingLogsNameNoRoomOrUser() = runBlocking {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Json(429, "{}") }
        ShadowLog.clear()

        typingWriter().setTyping(fixture.account, fixture.participant, typing = true)

        val logs = ShadowLog.getLogsForTag("subshell-matrix-typing").map { it.msg }
        assertTrue(logs.isNotEmpty())
        logs.forEach { line ->
            assertFalse(line, line.contains(fixture.roomId) || line.contains(fixture.ownMatrixUserId))
        }
    }

    // Redaction (messages02d3b).

    @Test
    fun aRedactionOnAWarmRoomIsOnePutWithAnEmptyBody() = runBlocking {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Json(200, """{"event_id":"${'$'}redaction"}""") }

        val result = redactionWriter().redact(fixture.account, fixture.participant, "${'$'}target")

        assertEquals(AuthenticatedRedditDirectChatRedactionResult.Accepted("${'$'}redaction"), result)
        val request = fixture.requests.single()
        assertEquals("PUT", request.method)
        assertTrue(decode(request.path), decode(request.path).startsWith("/_matrix/client/v3/rooms/${fixture.roomId}/redact/${'$'}target/"))
        assertEquals("{}", request.body)
    }

    @Test
    fun aLostRedactionAnswerIsAnUnknownOutcomeAndIsNotRetried() = runBlocking {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Transport }

        val result = redactionWriter().redact(fixture.account, fixture.participant, "${'$'}target")

        assertEquals(DirectChatDeleteFailureKind.OUTCOME_UNKNOWN, (result as AuthenticatedRedditDirectChatRedactionResult.Failed).kind)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun aRedactionWithoutAWarmRoomSendsNothing() = runBlocking {
        val result = redactionWriter().redact(fixture.account, fixture.participant, "${'$'}target")

        assertTrue(result is AuthenticatedRedditDirectChatRedactionResult.Failed)
        assertTrue(fixture.requests.isEmpty())
    }

    // Start (messages02d5a).

    private fun startRoutes(lookupBody: String, createRoom: () -> MatrixAnswer, send: () -> MatrixAnswer): (RecordedMatrixRequest) -> MatrixAnswer = { request ->
        val path = decode(request.path)
        when {
            path.endsWith("/account/whoami") -> MatrixAnswer.Json(200, """{"user_id":"${fixture.ownMatrixUserId}"}""")
            path.endsWith("/user_directory/search") ->
                MatrixAnswer.Json(200, """{"results":[{"user_id":"${fixture.peerMatrixUserId}","display_name":"u/peer"}]}""")
            path == "/_matrix/client/v3/rooms" -> MatrixAnswer.Json(200, lookupBody)
            path.endsWith("/createRoom") -> createRoom()
            path.contains("/send/m.room.message/") -> send()
            else -> MatrixAnswer.Json(404, "{}")
        }
    }

    @Test
    fun startingANewChatSearchesLooksUpCreatesSendsAndRemembersTheRoom() = runBlocking {
        fixture.respond = startRoutes(
            lookupBody = """{"rooms":null}""",
            createRoom = { MatrixAnswer.Json(200, """{"room_id":"${fixture.roomId}"}""") },
            send = { MatrixAnswer.Json(200, """{"event_id":"${'$'}first"}""") },
        )

        val result = startWriter().start(DirectChatStartRequest(fixture.account, fixture.participant, "hello"))

        assertTrue(result.toString(), result is AuthenticatedRedditDirectChatStartResult.Accepted)
        assertTrue((result as AuthenticatedRedditDirectChatStartResult.Accepted).roomCreated)
        assertEquals("${'$'}first", result.eventId)
        val paths = fixture.requests.map { decode(it.path) }
        assertEquals("/_matrix/client/v3/account/whoami", paths[0])
        assertEquals("/_matrix/client/v3/user_directory/search", paths[1])
        assertEquals("/_matrix/client/v3/rooms", paths[2])
        assertEquals("/_matrix/client/v3/createRoom", paths[3])
        assertTrue(paths[4], paths[4].startsWith("/_matrix/client/v3/rooms/${fixture.roomId}/send/m.room.message/"))
        val lookup = fixture.requests[2]
        assertEquals("direct", lookup.query["type"])
        assertEquals(fixture.peerMatrixUserId, lookup.query["with_user"])
        assertEquals("state,timeline", lookup.query["include"])
        assertEquals(fixture.roomId, fixture.cachedRoom())
    }

    @Test
    fun twoDirectRoomsForOnePersonFailClosedWithoutCreatingOne() = runBlocking {
        fixture.respond = startRoutes(
            lookupBody = """{"rooms":["!a:reddit.com","!b:reddit.com"]}""",
            createRoom = { error("must not create") },
            send = { error("must not send") },
        )

        val result = startWriter().start(DirectChatStartRequest(fixture.account, fixture.participant, "hello"))

        assertEquals(DirectChatStartFailureKind.PROTOCOL, (result as AuthenticatedRedditDirectChatStartResult.Failed).kind)
        assertFalse(fixture.requests.any { decode(it.path).endsWith("/createRoom") })
    }

    @Test
    fun aLostCreateOrSendAnswerIsAnUnknownOutcomeWithOneAttempt() = runBlocking {
        fixture.respond = startRoutes(
            lookupBody = """{"rooms":null}""",
            createRoom = { MatrixAnswer.Transport },
            send = { error("must not send") },
        )
        val lostCreate = startWriter().start(DirectChatStartRequest(fixture.account, fixture.participant, "hello"))
        assertEquals(DirectChatStartFailureKind.OUTCOME_UNKNOWN, (lostCreate as AuthenticatedRedditDirectChatStartResult.Failed).kind)
        assertEquals(1, fixture.requests.count { decode(it.path).endsWith("/createRoom") })

        fixture.requests.clear()
        fixture.respond = startRoutes(
            lookupBody = """{"rooms":["${fixture.roomId}"]}""",
            createRoom = { error("must not create") },
            send = { MatrixAnswer.Transport },
        )
        val lostSend = startWriter().start(DirectChatStartRequest(fixture.account, fixture.participant, "hello"))
        assertEquals(DirectChatStartFailureKind.OUTCOME_UNKNOWN, (lostSend as AuthenticatedRedditDirectChatStartResult.Failed).kind)
        assertEquals(1, fixture.requests.count { decode(it.path).contains("/send/") })
    }

    // The redaction and start logs carry outcomes, never room, user or event IDs.
    @Test
    fun redactionAndStartLogsNameNoIds() = runBlocking {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Json(200, """{"event_id":"${'$'}redaction"}""") }
        ShadowLog.clear()
        redactionWriter().redact(fixture.account, fixture.participant, "${'$'}target")

        fixture.sessionCache.invalidateRoom(fixture.webToken, fixture.providerAccountId, fixture.participant)
        fixture.respond = startRoutes(
            lookupBody = """{"rooms":null}""",
            createRoom = { MatrixAnswer.Json(200, """{"room_id":"${fixture.roomId}"}""") },
            send = { MatrixAnswer.Json(200, """{"event_id":"${'$'}first"}""") },
        )
        startWriter().start(DirectChatStartRequest(fixture.account, fixture.participant, "hello"))

        val logs = (ShadowLog.getLogsForTag("subshell-matrix-delete") + ShadowLog.getLogsForTag("subshell-matrix-chat-start")).map { it.msg }
        assertTrue(logs.isNotEmpty())
        val ids = listOf(fixture.roomId, fixture.ownMatrixUserId, fixture.peerMatrixUserId, "${'$'}target", "${'$'}redaction", "${'$'}first")
        logs.forEach { line -> ids.forEach { id -> assertFalse(line, line.contains(id)) } }
    }

    // First message with an image (messages02d5c).

    private val mediaDirectory = createTempDirectory("step3-start-media").toFile()
    private val stagedFiles = mutableListOf<File>()
    private val requestsWhenStaged = mutableListOf<Int>()

    @After
    fun cleanUpMedia() {
        mediaDirectory.deleteRecursively()
    }

    private fun mediaStartWriter(
        stage: () -> RedditMediaStageResult = {
            val file = File(mediaDirectory, "first-${stagedFiles.size}.gif").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            stagedFiles += file
            RedditMediaStageResult.Staged(file = file, fileName = "first.gif", mimeType = "image/gif", sizeBytes = 3L)
        },
    ) = RedditChatMatrixStartWriter(
        capability = fixture.capability,
        tokenProvider = fixture.tokenSource,
        client = fixture.client,
        ioDispatcher = Dispatchers.IO,
        sessionCache = fixture.sessionCache,
        mediaWriter = RedditChatMatrixMediaWriter(
            tokenProvider = fixture.tokenSource,
            mediaSource = RedditMediaUploadSource { _, _ ->
                requestsWhenStaged += fixture.requests.size
                stage()
            },
            client = fixture.client,
            ioDispatcher = Dispatchers.IO,
            sessionCache = fixture.sessionCache,
        ),
    )

    private fun gifStart() = DirectChatStartRequest(
        accountStableId = fixture.account,
        participantUsername = fixture.participant,
        text = "",
        media = MessageReplyMediaAttachment(uri = "content://media/first", mimeType = "image/gif"),
    )

    private fun mediaRoutes(send: () -> MatrixAnswer): (RecordedMatrixRequest) -> MatrixAnswer {
        val start = startRoutes(
            lookupBody = """{"rooms":null}""",
            createRoom = { MatrixAnswer.Json(200, """{"room_id":"${fixture.roomId}"}""") },
            send = send,
        )
        return { request ->
            if (decode(request.path) == "/_matrix/media/v3/upload") {
                MatrixAnswer.Json(200, """{"content_uri":"mxc://reddit.com/first123"}""")
            } else {
                start(request)
            }
        }
    }

    @Test
    fun aFirstGifIsStagedFirstThenSentAsOneImageInTheCreatedRoom() = runBlocking {
        fixture.respond = mediaRoutes(send = { MatrixAnswer.Json(200, """{"event_id":"${'$'}firstImage"}""") })

        val result = mediaStartWriter().start(gifStart())

        val accepted = result as AuthenticatedRedditDirectChatStartResult.Accepted
        assertEquals("${'$'}firstImage", accepted.eventId)
        assertTrue(accepted.roomCreated)
        // Staged on the device before anything was asked of Matrix.
        assertEquals(listOf(0), requestsWhenStaged)
        val paths = fixture.requests.map { decode(it.path) }
        assertEquals(
            listOf(
                "/_matrix/client/v3/account/whoami",
                "/_matrix/client/v3/user_directory/search",
                "/_matrix/client/v3/rooms",
                "/_matrix/client/v3/createRoom",
                "/_matrix/media/v3/upload",
            ),
            paths.take(5),
        )
        assertTrue(paths[5], paths[5].startsWith("/_matrix/client/v3/rooms/${fixture.roomId}/send/m.room.message/"))
        assertEquals(6, paths.size)
        assertTrue(requireNotNull(fixture.requests[5].body).contains("\"msgtype\":\"m.image\""))
        assertFalse(paths.any { it.contains("/receipt/") })
        assertTrue(stagedFiles.isNotEmpty())
        assertTrue(stagedFiles.none(File::exists))
    }

    @Test
    fun aFailedStagingCreatesNoRoomAndSendsNothing() = runBlocking {
        fixture.respond = mediaRoutes(send = { error("must not send") })

        val result = mediaStartWriter(stage = { RedditMediaStageResult.Unavailable }).start(gifStart())

        assertEquals(
            DirectChatStartFailureKind.MEDIA_UNAVAILABLE,
            (result as AuthenticatedRedditDirectChatStartResult.Failed).kind,
        )
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun aLostFirstImageSendIsAnUnknownOutcomeWithOneSend() = runBlocking {
        fixture.respond = mediaRoutes(send = { MatrixAnswer.Transport })

        val result = mediaStartWriter().start(gifStart())

        assertEquals(
            DirectChatStartFailureKind.OUTCOME_UNKNOWN,
            (result as AuthenticatedRedditDirectChatStartResult.Failed).kind,
        )
        assertEquals(1, fixture.requests.count { decode(it.path).contains("/send/") })
        assertTrue(stagedFiles.none(File::exists))
    }

    private fun decode(path: String): String = java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
}
