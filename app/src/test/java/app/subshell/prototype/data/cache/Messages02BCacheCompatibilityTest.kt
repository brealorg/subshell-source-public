package app.subshell.prototype.data.cache

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02BCacheCompatibilityTest {
    @Test
    fun messageBubbleCreatedEpochRoundTripsInsideInboxPayload() {
        val item = InboxItem(
            id = "t4_thread",
            kind = ActivityKind.MESSAGE,
            title = "[direct chat room]",
            sender = "peer",
            age = "1m",
            preview = "hello",
            conversation = listOf(
                MessageBubble(
                    id = "t4_message",
                    sender = "peer",
                    age = "1m",
                    body = "hello",
                    outgoing = false,
                    createdEpochSeconds = 1_777_000_000L,
                ),
            ),
        )

        val decoded = SubshellCacheCodec.decodeInboxItem(
            SubshellCacheCodec.encodeInboxItem(item),
        )

        assertEquals(1_777_000_000L, decoded.conversation.single().createdEpochSeconds)
    }

    @Test
    fun legacyFiveFieldMessageBubbleInsideInboxPayloadRemainsReadable() {
        val legacyBubble = legacyPack(
            "t4_message",
            "peer",
            "2d",
            "legacy body",
            "false",
        )
        val legacyConversation = legacyPack(legacyBubble)
        val legacyInbox = legacyPack(
            "t4_thread",
            "MESSAGE",
            "Legacy subject",
            "peer",
            "2d",
            "legacy body",
            "false",
            null,
            null,
            legacyConversation,
        )

        val decoded = SubshellCacheCodec.decodeInboxItem(legacyInbox)

        assertEquals("legacy body", decoded.conversation.single().body)
        assertNull(decoded.conversation.single().createdEpochSeconds)
    }

    private fun legacyPack(vararg values: String?): String = buildString {
        values.forEach { value ->
            if (value == null) {
                append("-1:")
            } else {
                append(value.length)
                append(':')
                append(value)
            }
        }
    }
}
