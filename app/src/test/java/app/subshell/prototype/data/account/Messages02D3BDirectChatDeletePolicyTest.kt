package app.subshell.prototype.data.account

import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02D3BDirectChatDeletePolicyTest {
    @Test
    fun onlyOneMatchingOwnLiveMatrixEventCanBeDeleted() {
        val authoritative = MessageBubble(
            id = "t4_legacy",
            sender = "self",
            age = "now",
            body = "delete me",
            outgoing = true,
            createdEpochSeconds = 100L,
        )
        val matrix = authoritative.copy(id = "${'$'}matrix", createdEpochSeconds = 102L)

        assertEquals(
            "${'$'}matrix",
            directChatMatrixDeleteEventId(
                displayed = authoritative,
                live = listOf(matrix),
                redactedEventIds = emptySet(),
            ),
        )
        assertNull(
            directChatMatrixDeleteEventId(
                displayed = authoritative,
                live = listOf(matrix, matrix.copy(id = "${'$'}ambiguous")),
                redactedEventIds = emptySet(),
            ),
        )
        assertNull(
            directChatMatrixDeleteEventId(
                displayed = authoritative.copy(outgoing = false),
                live = listOf(matrix),
                redactedEventIds = emptySet(),
            ),
        )
    }

    @Test
    fun alreadyRedactedEventIsNeverOfferedAgain() {
        val message = MessageBubble(
            id = "${'$'}event",
            sender = "self",
            age = "now",
            body = "gone",
            outgoing = true,
            createdEpochSeconds = 100L,
        )
        assertNull(
            directChatMatrixDeleteEventId(
                displayed = message,
                live = listOf(message),
                redactedEventIds = setOf("${'$'}event"),
            ),
        )
    }

    @Test
    fun redactedLiveEventSuppressesOneMatchingLegacyAuthoritativeBubble() {
        val authoritative = MessageBubble(
            id = "t4_projection",
            sender = "peer",
            age = "now",
            body = "removed elsewhere",
            outgoing = false,
            createdEpochSeconds = 200L,
        )
        val live = authoritative.copy(id = "${'$'}event", createdEpochSeconds = 202L)
        assertTrue(directChatMessagesReferToSameMatrixEvent(authoritative, live))
        assertEquals(
            emptyList<MessageBubble>(),
            suppressRedactedDirectChatAuthoritativeMessages(
                authoritative = listOf(authoritative),
                live = listOf(live),
                redactedEventIds = setOf("${'$'}event"),
            ),
        )
    }

    @Test
    fun ambiguousRedactionNeverDeletesTheWrongLegacyBubble() {
        val first = MessageBubble(
            id = "t4_one",
            sender = "self",
            age = "now",
            body = "same",
            outgoing = true,
            createdEpochSeconds = 300L,
        )
        val second = first.copy(id = "t4_two", createdEpochSeconds = 302L)
        val live = first.copy(id = "${'$'}event", createdEpochSeconds = 301L)
        val kept = suppressRedactedDirectChatAuthoritativeMessages(
            authoritative = listOf(first, second),
            live = listOf(live),
            redactedEventIds = setOf("${'$'}event"),
        )
        assertEquals(listOf(first, second), kept)
        assertFalse(kept.isEmpty())
    }
}
