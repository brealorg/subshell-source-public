package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * CHATLIVE02 (#156): Matrix does not check message content, so the other chat
 * member can send fields of any shape. Such an event is skipped; it no longer
 * throws out of the sync projection, which kept the resume cursor and failed
 * the live stream on every reconnect.
 */
class ChatLive02MalformedEventTest {
    @Test
    fun malformedPeerEventsAreSkippedAndTheRestOfTheBatchIsRead() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                timeline = listOf(
                    message("${'$'}bodyObject", """{"msgtype":"m.text","body":{"x":1}}"""),
                    message("${'$'}msgtypeArray", """{"msgtype":["m.text"],"body":"hi"}"""),
                    message("${'$'}urlObject", """{"msgtype":"m.image","url":{"mxc":1}}"""),
                    message(
                        "${'$'}relationObject",
                        """{"msgtype":"m.text","body":"edit","m.relates_to":{"rel_type":{"a":1}}}""",
                    ),
                    """{"type":"m.room.redaction","event_id":"${'$'}r","sender":"@peer:reddit.com","redacts":{"a":1},"content":{"redacts":[1]}}""",
                    message("${'$'}good", """{"msgtype":"m.text","body":"still here"}"""),
                ),
                ephemeral = """{"type":"m.typing","content":{"user_ids":[{"a":1},"@peer:reddit.com"]}}""",
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )

        assertNotNull(projection)
        assertEquals("s1", projection!!.nextBatch)
        val bodies = projection.messages.associate { it.id to it.body }
        assertEquals("still here", bodies["${'$'}good"])
        // A relation type that is not a string is not an edit, so that text shows.
        assertEquals("edit", bodies["${'$'}relationObject"])
        assertEquals(setOf("${'$'}good", "${'$'}relationObject"), bodies.keys)
        assertEquals(true, projection.ephemeral.peerTypingUpdate)
    }

    private fun message(eventId: String, content: String) =
        """{"type":"m.room.message","event_id":"$eventId","sender":"@peer:reddit.com","origin_server_ts":1700000000000,"content":$content}"""

    private fun syncBody(timeline: List<String>, ephemeral: String): String = """
        {
          "next_batch":"s1",
          "rooms":{
            "join":{
              "!room:reddit.com":{
                "timeline":{"events":[${timeline.joinToString(",")}]},
                "ephemeral":{"events":[$ephemeral]}
              }
            }
          }
        }
    """.trimIndent()
}
