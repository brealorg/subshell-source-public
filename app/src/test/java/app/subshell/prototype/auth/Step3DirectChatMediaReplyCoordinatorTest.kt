package app.subshell.prototype.auth

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (messages02c6r2, 02c6r5, 02c6r5r1, 02e2a2): a Direct Chat image shows
 * from the picked file while it is being sent; once Matrix accepts it the
 * reply keeps Matrix's media identity and the picked file as its picture, and
 * the inbox is read again. Text-only replies and private-message threads have
 * no such local picture. When the image was accepted but the following text
 * failed or was lost, the image's identity and the unsent text are kept.
 */
class Step3DirectChatMediaReplyCoordinatorTest {
    private val sent = mutableListOf<MessageReplyRequest>()
    private val reloads = mutableListOf<Pair<String, Boolean>>()
    private val metadata = NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null)
    private val picked = MessageReplyMediaAttachment("content://media/picked/1", "image/gif")

    private fun request(
        channel: MessageReplyChannel = MessageReplyChannel.DIRECT_CHAT,
        text: String = "",
        media: MessageReplyMediaAttachment? = picked,
    ) = MessageReplyRequest(
        accountStableId = "reddit:alice",
        inboxItemId = "t4_chat",
        parentFullname = "t4_chat",
        channel = channel,
        text = text,
        media = media,
        directChatParticipantUsername = "peer".takeIf { channel == MessageReplyChannel.DIRECT_CHAT },
    )

    private fun coordinator(answer: suspend (MessageReplyRequest) -> AuthenticatedRedditMessageReplyResult) =
        MessageReplyCoordinator(
            messageReplyWriter = { request ->
                sent += request
                answer(request)
            },
            loadRedditInbox = { account, force -> reloads += account to force },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    @Test
    fun aDirectChatImageShowsWhileSendingAndKeepsMatrixIdentityOnceAccepted() {
        val gate = CompletableDeferred<AuthenticatedRedditMessageReplyResult>()
        val coordinator = coordinator { gate.await() }

        coordinator.sendRedditMessageReply(request())

        val sending = coordinator.messageReplyUiState.value
        assertEquals(MessageReplyPhase.SENDING, sending.phase)
        assertEquals("content://media/picked/1", sending.optimisticMediaUri)
        assertEquals("image/gif", sending.optimisticMediaMimeType)
        assertEquals(emptyList<Pair<String, Boolean>>(), reloads)

        gate.complete(
            AuthenticatedRedditMessageReplyResult.Accepted(
                request = sent.single(),
                sentBody = "Image",
                metadata = metadata,
                sentMediaUri = "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/x",
                sentMatrixMediaId = "x",
                sentMatrixEventId = "\$e",
            ),
        )

        val accepted = coordinator.messageReplyUiState.value
        assertEquals(MessageReplyPhase.SUCCEEDED, accepted.phase)
        assertTrue(accepted.sentHadMedia)
        assertEquals("https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/x", accepted.sentMediaUri)
        assertEquals("x", accepted.sentMatrixMediaId)
        assertEquals("\$e", accepted.sentMatrixEventId)
        assertEquals("content://media/picked/1", accepted.optimisticMediaUri)
        assertEquals(listOf("reddit:alice" to true), reloads)
        assertEquals(1, sent.size)
    }

    @Test
    fun textOnlyRepliesAndPrivateThreadsHaveNoLocalPicture() {
        val gate = CompletableDeferred<AuthenticatedRedditMessageReplyResult>()
        val chat = coordinator { gate.await() }
        chat.sendRedditMessageReply(request(text = "hello", media = null))
        assertNull(chat.messageReplyUiState.value.optimisticMediaUri)
        gate.complete(AuthenticatedRedditMessageReplyResult.Accepted(sent.last(), "hello", metadata))
        assertEquals(MessageReplyPhase.SUCCEEDED, chat.messageReplyUiState.value.phase)
        assertNull(chat.messageReplyUiState.value.optimisticMediaUri)

        val threadGate = CompletableDeferred<AuthenticatedRedditMessageReplyResult>()
        val thread = coordinator { threadGate.await() }
        thread.sendRedditMessageReply(request(channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD))
        assertEquals(MessageReplyPhase.SENDING, thread.messageReplyUiState.value.phase)
        assertNull(thread.messageReplyUiState.value.optimisticMediaUri)
    }

    @Test
    fun anAcceptedImageWhoseTextFailedKeepsTheImageAndTheText() {
        val coordinator = coordinator {
            AuthenticatedRedditMessageReplyResult.Failed(
                kind = MessageReplyFailureKind.HTTP,
                statusCode = 500,
                sentMediaUri = "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/x",
                sentMatrixMediaId = "x",
                sentMatrixEventId = "\$e",
            )
        }

        coordinator.sendRedditMessageReply(request(text = "and words"))

        val state = coordinator.messageReplyUiState.value
        assertEquals(MessageReplyPhase.FAILED, state.phase)
        assertTrue(state.sentHadMedia)
        assertEquals("and words", state.sentDraftText)
        assertEquals("x", state.sentMatrixMediaId)
        assertEquals("\$e", state.sentMatrixEventId)
        assertEquals("content://media/picked/1", state.optimisticMediaUri)
        assertEquals(listOf("reddit:alice" to true), reloads)
    }

    @Test
    fun anAcceptedImageWhoseTextWasLostKeepsTheImageAndIsNotSentAgain() {
        val coordinator = coordinator {
            AuthenticatedRedditMessageReplyResult.Failed(
                kind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
                sentMediaUri = "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/x",
                sentMatrixMediaId = "x",
                sentMatrixEventId = "\$e",
            )
        }

        coordinator.sendRedditMessageReply(request(text = "and words"))
        coordinator.sendRedditMessageReply(request(text = "and words"))

        val state = coordinator.messageReplyUiState.value
        assertEquals(MessageReplyPhase.OUTCOME_UNKNOWN, state.phase)
        assertTrue(state.sentHadMedia)
        assertEquals("and words", state.sentDraftText)
        assertEquals("x", state.sentMatrixMediaId)
        assertEquals("\$e", state.sentMatrixEventId)
        assertEquals(1, sent.size)
    }
}
