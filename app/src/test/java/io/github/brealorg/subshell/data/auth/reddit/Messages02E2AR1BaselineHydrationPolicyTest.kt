package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Test

class Messages02E2AR1BaselineHydrationPolicyTest {
    @Test
    fun oldBaselineMatrixMessagesRemainEligibleForInitialHydration() {
        val messages = listOf(
            bubble(id = "\$oldPeer", outgoing = false, created = 1L),
            bubble(id = "\$oldSelf", outgoing = true, created = 2L),
        )

        assertEquals(
            listOf("\$oldPeer", "\$oldSelf"),
            redditChatMatrixBaselineHydrationMessages(messages).map(MessageBubble::id),
        )
    }

    @Test
    fun baselineHydrationKeepsBothOwnersAndDeduplicatesExactMatrixEvents() {
        val messages = listOf(
            bubble(id = "\$peer", outgoing = false, created = 10L),
            bubble(id = "\$self", outgoing = true, created = 11L),
            bubble(id = "\$peer", outgoing = false, created = 10L),
            bubble(id = "t4_legacy", outgoing = false, created = 10L),
        )

        assertEquals(
            listOf("\$peer", "\$self"),
            redditChatMatrixBaselineHydrationMessages(messages).map(MessageBubble::id),
        )
    }

    private fun bubble(
        id: String,
        outgoing: Boolean,
        created: Long,
    ) = MessageBubble(
        id = id,
        sender = if (outgoing) "self" else "peer",
        age = "old",
        body = "body",
        outgoing = outgoing,
        createdEpochSeconds = created,
    )
}
