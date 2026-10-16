package app.subshell.prototype.data.account

import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02E2ADirectChatReactionPolicyTest {
    @Test
    fun peerAndOwnDisplayedBubblesMapOnlyToOneLiveMatrixTarget() {
        val peerDisplayed = bubble("t4_legacy_peer", false, "hello", 100)
        val ownDisplayed = bubble("t4_legacy_own", true, "hi", 101)
        val live = listOf(
            bubble("\$peer", false, "hello", 100),
            bubble("\$own", true, "hi", 101),
        )

        assertEquals("\$peer", directChatMatrixReactionTargetEventId(peerDisplayed, live, emptySet()))
        assertEquals("\$own", directChatMatrixReactionTargetEventId(ownDisplayed, live, emptySet()))
    }

    @Test
    fun ambiguousOrRedactedTargetFailsClosed() {
        val displayed = bubble("t4_legacy", false, "same", 100)
        val ambiguous = listOf(
            bubble("\$one", false, "same", 99),
            bubble("\$two", false, "same", 101),
        )
        assertNull(directChatMatrixReactionTargetEventId(displayed, ambiguous, emptySet()))

        val exact = listOf(bubble("\$one", false, "same", 100))
        assertNull(
            directChatMatrixReactionTargetEventId(
                displayed = displayed,
                live = exact,
                redactedEventIds = setOf("\$one"),
            ),
        )
    }

    @Test
    fun serverConfirmedReactionEventsProduceBoundedDisplaySummaries() {
        val reactions = listOf(
            DirectChatReaction("\$r1", "\$m", "🔥", senderIsSelf = true),
            DirectChatReaction("\$r2", "\$m", "🔥", senderIsSelf = false),
            DirectChatReaction("\$r3", "\$m", "😂", senderIsSelf = false),
            DirectChatReaction("\$r4", "\$other", "👍", senderIsSelf = true),
        )
        val summaries = directChatReactionSummaries("\$m", reactions, emptySet())
        assertEquals(2, summaries.size)
        assertEquals(DirectChatReactionSummary("🔥", 2, self = true), summaries[0])
        assertEquals(DirectChatReactionSummary("😂", 1, self = false), summaries[1])
    }

    @Test
    fun redactedReactionEventIsNotPresented() {
        val summaries = directChatReactionSummaries(
            targetEventId = "\$m",
            reactions = listOf(DirectChatReaction("\$r1", "\$m", "🔥", senderIsSelf = true)),
            redactedEventIds = setOf("\$r1"),
        )
        assertTrue(summaries.isEmpty())
    }

    private fun bubble(
        id: String,
        outgoing: Boolean,
        body: String,
        created: Long,
    ) = MessageBubble(
        id = id,
        sender = if (outgoing) "self" else "peer",
        age = "now",
        body = body,
        outgoing = outgoing,
        createdEpochSeconds = created,
    )

    @Test
    fun authoritativeRelationsReplaceStaleTargetStateAndPreserveNewLocalWrite() {
        val current = listOf(
            DirectChatReaction("\$stale", "\$m1", "g6akcitga7081.gif", senderIsSelf = true),
            DirectChatReaction("\$untouched", "\$m2", "foyijyyga7081.gif", senderIsSelf = true),
            DirectChatReaction("\$newLocal", "\$m1", "8r21ukpfa7081.gif", senderIsSelf = true),
        )
        val authoritative = listOf(
            DirectChatReaction("\$server", "\$m1", "jvuspmbga7081.gif", senderIsSelf = false),
        )

        assertEquals(
            listOf("\$untouched", "\$newLocal", "\$server"),
            reconcileDirectChatAuthoritativeReactions(
                current = current,
                authoritativeTargetEventIds = setOf("\$m1"),
                authoritative = authoritative,
                preserveReactionEventIds = setOf("\$newLocal"),
                redactedEventIds = emptySet(),
                maxEvents = 200,
            ).map(DirectChatReaction::reactionEventId),
        )
    }
}
