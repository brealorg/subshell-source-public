package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02D3ARedactionProjectionPolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun projectsClassicTopLevelRedactionTarget() {
        val element = json.parseToJsonElement(
            """
            {
              "type":"m.room.redaction",
              "event_id":"${'$'}redaction",
              "sender":"@t2_peer:reddit.com",
              "redacts":"${'$'}target",
              "content":{"reason":"deleted"}
            }
            """.trimIndent(),
        )

        val observation = requireNotNull(
            redditChatMatrixRedactionObservation(
                element = element,
                ownUserId = "@t2_self:reddit.com",
            ),
        )

        assertEquals("${'$'}target", observation.targetEventId)
        assertFalse(observation.senderIsSelf)
    }

    @Test
    fun projectsContentRedactionTargetForNewerRoomShape() {
        val element = json.parseToJsonElement(
            """
            {
              "type":"m.room.redaction",
              "event_id":"${'$'}redaction",
              "sender":"@t2_self:reddit.com",
              "content":{"redacts":"${'$'}target"}
            }
            """.trimIndent(),
        )

        val observation = requireNotNull(
            redditChatMatrixRedactionObservation(
                element = element,
                ownUserId = "@t2_self:reddit.com",
            ),
        )

        assertEquals("${'$'}target", observation.targetEventId)
        assertTrue(observation.senderIsSelf)
    }

    @Test
    fun nonRedactionAndMalformedRedactionAreIgnored() {
        assertNull(
            redditChatMatrixRedactionObservation(
                element = json.parseToJsonElement(
                    """{"type":"m.room.message","sender":"@t2_peer:reddit.com","content":{}}""",
                ),
                ownUserId = "@t2_self:reddit.com",
            ),
        )
        assertNull(
            redditChatMatrixRedactionObservation(
                element = json.parseToJsonElement(
                    """{"type":"m.room.redaction","sender":"@t2_peer:reddit.com","content":{}}""",
                ),
                ownUserId = "@t2_self:reddit.com",
            ),
        )
    }

    @Test
    fun syncProjectionKeepsMessagesAndReportsRedactionsSeparately() {
        val projection = requireNotNull(
            redditChatMatrixLiveSyncProjection(
                body = """
                    {
                      "next_batch":"s1",
                      "rooms":{"join":{"!room:reddit.com":{
                        "timeline":{"events":[
                          {
                            "type":"m.room.message",
                            "event_id":"${'$'}message",
                            "sender":"@t2_peer:reddit.com",
                            "origin_server_ts":1000,
                            "content":{"msgtype":"m.text","body":"hello"}
                          },
                          {
                            "type":"m.room.redaction",
                            "event_id":"${'$'}redaction",
                            "sender":"@t2_peer:reddit.com",
                            "redacts":"${'$'}message",
                            "content":{}
                          }
                        ]}
                      }}}
                    }
                """.trimIndent(),
                roomId = "!room:reddit.com",
                ownUserId = "@t2_self:reddit.com",
                ownUsername = "self",
                participantUsername = "peer",
                json = json,
            ),
        )

        assertEquals(listOf("${'$'}message"), projection.messages.map { it.id })
        assertEquals(listOf("${'$'}message"), projection.redactions.map { it.targetEventId })
        assertFalse(projection.redactions.single().senderIsSelf)
    }
}
