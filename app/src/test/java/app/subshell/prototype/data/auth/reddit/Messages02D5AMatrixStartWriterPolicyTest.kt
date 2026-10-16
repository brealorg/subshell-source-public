package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02D5AMatrixStartWriterPolicyTest {
    @Test
    fun createRoomPayloadUsesRedditDmPresetAndOneInvitee() {
        val payload = Json.parseToJsonElement(
            redditChatMatrixCreateRoomPayload("@t2_peer:reddit.com"),
        ) as JsonObject

        assertEquals("reddit_dm", payload["preset"]?.toString()?.trim('"'))
        assertTrue(payload["invite"].toString().contains("@t2_peer:reddit.com"))
    }

    @Test
    fun firstTextMessageUsesStandardRoomMessagePath() {
        assertEquals(
            listOf(
                "_matrix",
                "client",
                "v3",
                "rooms",
                "!room:reddit.com",
                "send",
                "m.room.message",
                "txn-1",
            ),
            redditChatMatrixStartSendPathSegments(
                roomId = "!room:reddit.com",
                transactionId = "txn-1",
            ),
        )
        val payload = redditChatMatrixStartTextPayload("hello")
        assertTrue(payload.contains("\"msgtype\":\"m.text\""))
        assertTrue(payload.contains("\"body\":\"hello\""))
    }

    @Test
    fun directRoomProjectionAcceptsProvenNullAndLegacyEmptyShapesAndFailsClosedOnUnknownShape() {
        // Fresh Reddit Web HAR proof: a successful direct-room lookup with no
        // existing room returns exactly {"rooms":null}.
        assertEquals(
            emptyList<String>(),
            redditChatMatrixDirectRoomIds(
                Json.parseToJsonElement("""{"rooms":null}""") as JsonObject,
            ),
        )
        assertEquals(
            emptyList<String>(),
            redditChatMatrixDirectRoomIds(
                Json.parseToJsonElement("""{"rooms":[]}""") as JsonObject,
            ),
        )
        assertEquals(
            emptyList<String>(),
            redditChatMatrixDirectRoomIds(
                Json.parseToJsonElement("""{"rooms":{}}""") as JsonObject,
            ),
        )
        assertEquals(
            listOf("!room:reddit.com"),
            redditChatMatrixDirectRoomIds(
                Json.parseToJsonElement(
                    """{"rooms":[{"room_id":"!room:reddit.com"}]}""",
                ) as JsonObject,
            ),
        )
        assertNull(
            redditChatMatrixDirectRoomIds(
                Json.parseToJsonElement("""{}""") as JsonObject,
            ),
        )
        assertNull(
            redditChatMatrixDirectRoomIds(
                Json.parseToJsonElement("""{"rooms":"unexpected"}""") as JsonObject,
            ),
        )
        assertNull(
            redditChatMatrixDirectRoomIds(
                Json.parseToJsonElement("""{"rooms":[{"unexpected":"x"}]}""") as JsonObject,
            ),
        )
    }
}
