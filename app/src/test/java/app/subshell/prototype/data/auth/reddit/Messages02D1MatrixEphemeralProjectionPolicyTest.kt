package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02D1MatrixEphemeralProjectionPolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun explicitEphemeralFilterIncludesOnlyTypingAndReceiptsWhilePresenceStaysOff() {
        val filter = json.parseToJsonElement(
            redditChatMatrixLiveSyncFilter(
                roomId = "!room:reddit.com",
                timelineLimit = 50,
                ephemeralTypes = listOf("m.typing", "m.receipt"),
            ),
        ).jsonObject

        val room = filter.getValue("room").jsonObject
        val ephemeralTypes = room
            .getValue("ephemeral")
            .jsonObject
            .getValue("types")
            .jsonArray
            .map { it.toString().trim('"') }

        assertEquals(listOf("m.typing", "m.receipt"), ephemeralTypes)
        assertTrue(filter.getValue("presence").jsonObject.getValue("types").jsonArray.isEmpty())
    }

    @Test
    fun c7DefaultFilterStillSuppressesEphemeralEvents() {
        val filter = json.parseToJsonElement(
            redditChatMatrixLiveSyncFilter("!room:reddit.com", 50),
        ).jsonObject
        val room = filter.getValue("room").jsonObject

        assertTrue(room.getValue("ephemeral").jsonObject.getValue("types").jsonArray.isEmpty())
        assertTrue(filter.getValue("presence").jsonObject.getValue("types").jsonArray.isEmpty())
    }

    @Test
    fun projectionReportsPeerTypingStartAndStopWithoutChangingMessageTimeline() {
        val start = requireNotNull(
            redditChatMatrixLiveSyncProjection(
                body = """
                    {
                      "next_batch":"s1",
                      "rooms":{"join":{"!room:reddit.com":{
                        "timeline":{"events":[]},
                        "ephemeral":{"events":[{
                          "type":"m.typing",
                          "content":{"user_ids":["@t2_peer:reddit.com"]}
                        }]}
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
        assertEquals(1, start.ephemeral.typingEventCount)
        assertEquals(true, start.ephemeral.peerTypingUpdate)
        assertTrue(start.messages.isEmpty())

        val stop = requireNotNull(
            redditChatMatrixLiveSyncProjection(
                body = """
                    {
                      "next_batch":"s2",
                      "rooms":{"join":{"!room:reddit.com":{
                        "timeline":{"events":[]},
                        "ephemeral":{"events":[{
                          "type":"m.typing",
                          "content":{"user_ids":[]}
                        }]}
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
        assertEquals(false, stop.ephemeral.peerTypingUpdate)
        assertTrue(stop.messages.isEmpty())
    }

    @Test
    fun projectionReportsPeerPublicReadReceiptWithoutTreatingOwnPrivateReceiptAsPeer() {
        val projection = requireNotNull(
            redditChatMatrixLiveSyncProjection(
                body = """
                    {
                      "next_batch":"s3",
                      "rooms":{"join":{"!room:reddit.com":{
                        "timeline":{"events":[]},
                        "ephemeral":{"events":[{
                          "type":"m.receipt",
                          "content":{
                            "${'$'}event1":{
                              "m.read":{
                                "@t2_peer:reddit.com":{"ts":123456}
                              },
                              "m.read.private":{
                                "@t2_self:reddit.com":{"ts":123457}
                              }
                            }
                          }
                        }]}
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

        assertEquals(1, projection.ephemeral.receiptEventCount)
        assertEquals("${'$'}event1", projection.ephemeral.peerPublicReadReceipt?.eventId)
        assertEquals(123456L, projection.ephemeral.peerPublicReadReceipt?.timestampMs)
        assertFalse(projection.ephemeral.peerPublicReadReceipt?.threaded ?: true)
        assertFalse(projection.ephemeral.peerPrivateReadReceiptObserved)
        assertTrue(projection.messages.isEmpty())
    }

    @Test
    fun noEphemeralEventMeansTypingStateIsUnchangedNotFalse() {
        val projection = requireNotNull(
            redditChatMatrixLiveSyncProjection(
                body = """{"next_batch":"s4","rooms":{"join":{"!room:reddit.com":{"timeline":{"events":[]}}}}}""",
                roomId = "!room:reddit.com",
                ownUserId = "@t2_self:reddit.com",
                ownUsername = "self",
                participantUsername = "peer",
                json = json,
            ),
        )

        assertNull(projection.ephemeral.peerTypingUpdate)
        assertEquals(0, projection.ephemeral.typingEventCount)
        assertEquals(0, projection.ephemeral.receiptEventCount)
    }
}
