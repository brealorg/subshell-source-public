package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.DirectChatLiveFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02d1, 02d2a, 02d3b): the live reader against a recording
 * interceptor. The baseline sync carries peer typing and a redaction, the next
 * sync stops the typing, and a 500 ends the stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3MatrixLiveReaderTest {
    private val fixture = MatrixTestFixture()

    private fun syncBody(nextBatch: String, typingUsers: List<String>, timeline: String = "[]") = """
        {"next_batch":"$nextBatch","rooms":{"join":{"${fixture.roomId}":{
          "timeline":{"events":$timeline},
          "ephemeral":{"events":[{"type":"m.typing","content":{"user_ids":${typingUsers.joinToString(",", "[", "]") { "\"$it\"" }}}}]}
        }}}}
    """.trimIndent()

    @Test
    fun theStreamAsksOnlyForTypingAndReceiptsReadsOnlyAndForwardsTypingAndRedactions() = runBlocking {
        fixture.warmCache()
        val redaction = """[{"type":"m.room.redaction","event_id":"${'$'}r1","sender":"${fixture.peerMatrixUserId}","redacts":"${'$'}gone","content":{}}]"""
        fixture.respond = { request ->
            when (request.query["since"]) {
                null -> MatrixAnswer.Json(200, syncBody("s1", listOf(fixture.peerMatrixUserId), redaction))
                "s1" -> MatrixAnswer.Json(200, syncBody("s2", emptyList()))
                else -> MatrixAnswer.Json(500, "{}")
            }
        }
        val reader = RedditChatMatrixLiveReader(
            capability = fixture.capability,
            tokenProvider = fixture.tokenSource,
            client = fixture.client,
            ioDispatcher = Dispatchers.IO,
            sessionCache = fixture.sessionCache,
        )
        val typing = mutableListOf<Boolean>()
        val redactions = mutableListOf<String>()
        var connected = false

        val result = reader.stream(
            accountStableId = fixture.account,
            participantUsername = fixture.participant,
            since = null,
            onConnected = { connected = true },
            onCursor = {},
            onMessages = {},
            onPeerTyping = { typing += it },
            onRedactions = { redactions += it },
        )

        assertEquals(AuthenticatedRedditDirectChatLiveResult.Failed(DirectChatLiveFailureKind.HTTP, 500), result)
        assertTrue(connected)
        assertEquals(listOf(true, false), typing)
        assertEquals(listOf("${'$'}gone"), redactions)
        assertTrue(fixture.requests.all { it.method == "GET" })
        val syncs = fixture.requests.filter { it.path.endsWith("/sync") }
        assertEquals(3, syncs.size)
        syncs.forEach { sync ->
            val filter = Json.parseToJsonElement(requireNotNull(sync.query["filter"])).jsonObject
            val ephemeral = filter.getValue("room").jsonObject.getValue("ephemeral").jsonObject
                .getValue("types").jsonArray.map { it.toString().trim('"') }
            assertEquals(listOf("m.typing", "m.receipt"), ephemeral)
            assertTrue(filter.getValue("presence").jsonObject.getValue("types").jsonArray.isEmpty())
        }
    }
}
