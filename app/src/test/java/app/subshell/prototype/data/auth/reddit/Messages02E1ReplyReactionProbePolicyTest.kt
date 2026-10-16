package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02E1ReplyReactionProbePolicyTest {
    @Test
    fun redditWebThreadReplyShapeIsObserved() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                """
                {
                  "type":"m.room.message",
                  "event_id":"${'$'}reply",
                  "sender":"@self:reddit.com",
                  "origin_server_ts":1700000000000,
                  "content":{
                    "body":"reply body",
                    "msgtype":"m.text",
                    "m.relates_to":{
                      "rel_type":"m.thread",
                      "event_id":"${'$'}threadroot",
                      "m.in_reply_to":{"event_id":"${'$'}original"}
                    }
                  }
                }
                """.trimIndent(),
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )!!

        val reply = projection.timelineRelations.single()
        assertEquals("m.room.message", reply.eventType)
        assertEquals("m.thread", reply.relationType)
        assertTrue(reply.senderIsSelf)
        assertTrue(reply.relationTargetPresent)
        assertTrue(reply.replyTargetPresent)
        assertEquals("m.text", reply.messageType)

        val shape = projection.timelineShapes.single()
        assertTrue(shape.contentRelatesToPresent)
        assertTrue(shape.relationTargetPresent)
        assertTrue(shape.replyTargetPresent)
        assertFalse(shape.unsignedPresent)
    }

    @Test
    fun replyRelationWithoutRelTypeRemainsObservable() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                """
                {
                  "type":"m.room.message",
                  "event_id":"${'$'}reply",
                  "sender":"@peer:reddit.com",
                  "origin_server_ts":1700000000000,
                  "content":{
                    "body":"reply body",
                    "msgtype":"m.text",
                    "m.relates_to":{
                      "m.in_reply_to":{"event_id":"${'$'}original"}
                    }
                  }
                }
                """.trimIndent(),
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )!!

        val reply = projection.timelineRelations.single()
        assertEquals(null, reply.relationType)
        assertFalse(reply.relationTargetPresent)
        assertTrue(reply.replyTargetPresent)
    }

    @Test
    fun standardReactionRelationRemainsObservableWithoutKeyValue() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                """
                {
                  "type":"m.reaction",
                  "event_id":"${'$'}reaction",
                  "sender":"@self:reddit.com",
                  "origin_server_ts":1700000001000,
                  "content":{
                    "m.relates_to":{
                      "rel_type":"m.annotation",
                      "event_id":"${'$'}original",
                      "key":"🔥"
                    }
                  }
                }
                """.trimIndent(),
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )!!

        val reaction = projection.timelineRelations.single()
        assertEquals("m.reaction", reaction.eventType)
        assertEquals("m.annotation", reaction.relationType)
        assertTrue(reaction.reactionKeyPresent)

        val shape = projection.timelineShapes.single()
        assertTrue(shape.contentRelatesToPresent)
        assertFalse(shape.contentReactionNamedStructurePresent)
        assertFalse(shape.unsignedPresent)
    }

    @Test
    fun unsignedMatrixAnnotationAggregationIsDetectedWithoutPayloadValues() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                """
                {
                  "type":"m.room.message",
                  "event_id":"${'$'}message",
                  "sender":"@peer:reddit.com",
                  "origin_server_ts":1700000002000,
                  "content":{"body":"hello","msgtype":"m.text"},
                  "unsigned":{
                    "m.relations":{
                      "m.annotation":{
                        "chunk":[{"type":"m.reaction","key":"🔥","count":1}]
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )!!

        assertTrue(projection.timelineRelations.isEmpty())
        val shape = projection.timelineShapes.single()
        assertEquals("m.room.message", shape.eventType)
        assertTrue(shape.unsignedPresent)
        assertTrue(shape.unsignedRelationsPresent)
        assertTrue(shape.unsignedAnnotationPresent)
        assertFalse(shape.unsignedThreadPresent)
        assertTrue(shape.unsignedReactionNamedStructurePresent)
    }

    @Test
    fun customUnsignedReactionNamedStructureIsDetectedWithoutReadingValue() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                """
                {
                  "type":"m.room.message",
                  "event_id":"${'$'}message",
                  "sender":"@peer:reddit.com",
                  "origin_server_ts":1700000003000,
                  "content":{"body":"hello","msgtype":"m.text"},
                  "unsigned":{"reactions":{"secret":"never logged"}}
                }
                """.trimIndent(),
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )!!

        val shape = projection.timelineShapes.single()
        assertTrue(shape.unsignedPresent)
        assertFalse(shape.unsignedRelationsPresent)
        assertTrue(shape.unsignedReactionNamedStructurePresent)
    }

    @Test
    fun ordinaryTextStillProducesOnlySanitizedShapeObservation() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                """
                {
                  "type":"m.room.message",
                  "event_id":"${'$'}message",
                  "sender":"@peer:reddit.com",
                  "origin_server_ts":1700000004000,
                  "content":{"body":"hello","msgtype":"m.text"}
                }
                """.trimIndent(),
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )!!

        assertEquals(1, projection.messages.size)
        assertTrue(projection.timelineRelations.isEmpty())
        val shape = projection.timelineShapes.single()
        assertEquals("m.room.message", shape.eventType)
        assertEquals("m.text", shape.messageType)
        assertFalse(shape.contentRelatesToPresent)
        assertFalse(shape.unsignedPresent)
        assertFalse(shape.unsignedReactionNamedStructurePresent)
    }

    @Test
    fun editReplacementRemainsSuppressedFromNormalProjection() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = syncBody(
                """
                {
                  "type":"m.room.message",
                  "event_id":"${'$'}edit",
                  "sender":"@peer:reddit.com",
                  "origin_server_ts":1700000005000,
                  "content":{
                    "body":"edited",
                    "msgtype":"m.text",
                    "m.new_content":{"body":"edited","msgtype":"m.text"},
                    "m.relates_to":{"rel_type":"m.replace","event_id":"${'$'}original"}
                  }
                }
                """.trimIndent(),
            ),
            roomId = "!room:reddit.com",
            ownUserId = "@self:reddit.com",
            ownUsername = "self",
            participantUsername = "peer",
        )!!

        assertTrue(projection.messages.isEmpty())
        assertTrue(projection.timelineRelations.isEmpty())
        assertEquals("m.replace", projection.timelineShapes.single().relationType)
    }

    @Test
    fun sanitizerRejectsLogInjectionAndArbitraryValues() {
        assertEquals("m.annotation", sanitizedMatrixProbeToken("m.annotation"))
        assertEquals("m.thread", sanitizedMatrixProbeToken("m.thread"))
        assertEquals("other", sanitizedMatrixProbeToken("m.thread\nsecret"))
        assertEquals("other", sanitizedMatrixProbeToken("🔥"))
    }

    private fun syncBody(event: String): String = """
        {
          "next_batch":"s1",
          "rooms":{
            "join":{
              "!room:reddit.com":{
                "timeline":{"events":[$event]},
                "ephemeral":{"events":[]}
              }
            }
          }
        }
    """.trimIndent()
}
