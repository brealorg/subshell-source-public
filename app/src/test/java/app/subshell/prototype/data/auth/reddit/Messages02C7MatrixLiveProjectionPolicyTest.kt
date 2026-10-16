package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02C7MatrixLiveProjectionPolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun syncProjectsOnlyTextAndImageMessagesForPinnedRoom() {
        val body = """
            {
              "next_batch":"s123",
              "rooms":{
                "join":{
                  "!room:reddit.com":{
                    "timeline":{
                      "events":[
                        {
                          "type":"m.room.message",
                          "event_id":"${'$'}incoming",
                          "sender":"@t2_peer:reddit.com",
                          "origin_server_ts":100000,
                          "content":{"msgtype":"m.text","body":"hello from peer"}
                        },
                        {
                          "type":"m.room.message",
                          "event_id":"${'$'}outgoing",
                          "sender":"@t2_self:reddit.com",
                          "origin_server_ts":101000,
                          "content":{"msgtype":"m.text","body":"hello back"}
                        },
                        {
                          "type":"m.room.message",
                          "event_id":"${'$'}image",
                          "sender":"@t2_peer:reddit.com",
                          "origin_server_ts":102000,
                          "content":{
                            "msgtype":"m.image",
                            "body":"Image",
                            "url":"mxc://reddit.com/media123",
                            "info":{"mimetype":"image/gif"}
                          }
                        },
                        {
                          "type":"m.room.message",
                          "event_id":"${'$'}edit",
                          "sender":"@t2_peer:reddit.com",
                          "origin_server_ts":103000,
                          "content":{
                            "msgtype":"m.text",
                            "body":"edited",
                            "m.relates_to":{"rel_type":"m.replace","event_id":"${'$'}incoming"}
                          }
                        },
                        {
                          "type":"m.room.message",
                          "event_id":"${'$'}notice",
                          "sender":"@t2_peer:reddit.com",
                          "origin_server_ts":104000,
                          "content":{"msgtype":"m.notice","body":"ignore"}
                        }
                      ]
                    }
                  },
                  "!other:reddit.com":{
                    "timeline":{
                      "events":[{
                        "type":"m.room.message",
                        "event_id":"${'$'}other",
                        "sender":"@t2_peer:reddit.com",
                        "origin_server_ts":105000,
                        "content":{"msgtype":"m.text","body":"wrong room"}
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val projection = requireNotNull(
            redditChatMatrixLiveSyncProjection(
                body = body,
                roomId = "!room:reddit.com",
                ownUserId = "@t2_self:reddit.com",
                ownUsername = "self_user",
                participantUsername = "peer_user",
                json = json,
            ),
        )

        assertEquals("s123", projection.nextBatch)
        assertFalse(projection.roomLeft)
        assertEquals(listOf("${'$'}incoming", "${'$'}outgoing", "${'$'}image"), projection.messages.map { it.id })
        assertEquals("peer_user", projection.messages[0].sender)
        assertEquals("self_user", projection.messages[1].sender)
        assertTrue(projection.messages[1].outgoing)
        assertEquals(
            "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media123",
            projection.messages[2].body,
        )
        assertEquals(102L, projection.messages[2].createdEpochSeconds)
    }

    @Test
    fun leaveProjectionMarksPinnedRoomUnavailable() {
        val projection = requireNotNull(
            redditChatMatrixLiveSyncProjection(
                body = """{"next_batch":"s2","rooms":{"leave":{"!room:reddit.com":{}}}}""",
                roomId = "!room:reddit.com",
                ownUserId = "@t2_self:reddit.com",
                ownUsername = "self_user",
                participantUsername = "peer_user",
                json = json,
            ),
        )

        assertTrue(projection.roomLeft)
        assertTrue(projection.messages.isEmpty())
    }

    @Test
    fun longPollFilterPinsOneRoomAndTimelineLimit() {
        val filter = json.parseToJsonElement(
            redditChatMatrixLiveSyncFilter("!room:reddit.com", 50),
        ).jsonObject
        val room = filter.getValue("room").jsonObject

        assertEquals(
            "!room:reddit.com",
            room.getValue("rooms").jsonArray.single().jsonPrimitive.content,
        )
        assertEquals(50, room.getValue("timeline").jsonObject.getValue("limit").jsonPrimitive.content.toInt())
        assertTrue(room.getValue("state").jsonObject.getValue("types").jsonArray.isEmpty())
        assertTrue(room.getValue("ephemeral").jsonObject.getValue("types").jsonArray.isEmpty())
    }
}
