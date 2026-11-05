package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.MessageReplyChannel
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * STEP3 (messages02c): a message reply goes to the writer once; an accepted
 * text reply reloads the account's inbox from Reddit; a lost answer is never
 * sent again, also when the reader presses send once more.
 */
class Step3MessageReplyCoordinatorTest {
    private val sent = mutableListOf<MessageReplyRequest>()
    private val reloads = mutableListOf<Pair<String, Boolean>>()

    private val request = MessageReplyRequest(
        accountStableId = "reddit:alice",
        inboxItemId = "t4_y",
        parentFullname = "t4_y",
        channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
        text = "My reply",
    )

    private fun coordinator(answer: (MessageReplyRequest) -> AuthenticatedRedditMessageReplyResult) = MessageReplyCoordinator(
        messageReplyWriter = { request ->
            sent += request
            answer(request)
        },
        loadRedditInbox = { account, force -> reloads += account to force },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun anAcceptedReplyIsSentOnceAndReloadsTheInbox() {
        val coordinator = coordinator { AuthenticatedRedditMessageReplyResult.Accepted(it, it.text, NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null)) }

        coordinator.sendRedditMessageReply(request)

        assertEquals(1, sent.size)
        assertEquals(listOf("reddit:alice" to true), reloads)
        assertEquals(MessageReplyPhase.SUCCEEDED, coordinator.messageReplyUiState.value.phase)
        assertEquals("My reply", coordinator.messageReplyUiState.value.sentText)
    }

    @Test
    fun aLostAnswerIsNotSentAgain() {
        val coordinator = coordinator { AuthenticatedRedditMessageReplyResult.Failed(kind = MessageReplyFailureKind.OUTCOME_UNKNOWN) }

        coordinator.sendRedditMessageReply(request)
        coordinator.sendRedditMessageReply(request)

        assertEquals(1, sent.size)
        assertEquals(MessageReplyPhase.OUTCOME_UNKNOWN, coordinator.messageReplyUiState.value.phase)
        assertEquals(listOf("reddit:alice" to true), reloads)
    }
}
