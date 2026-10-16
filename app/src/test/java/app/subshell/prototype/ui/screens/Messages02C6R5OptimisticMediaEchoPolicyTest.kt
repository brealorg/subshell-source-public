package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.messageReplySendingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class Messages02C6R5OptimisticMediaEchoPolicyTest {
    private val media = MessageReplyMediaAttachment(
        uri = "content://picker/direct.gif",
        mimeType = "image/gif",
    )

    private fun request(
        channel: MessageReplyChannel,
        text: String = "",
        media: MessageReplyMediaAttachment? = this.media,
    ) = MessageReplyRequest(
        accountStableId = "reddit:redreader_compatibility:test",
        inboxItemId = "t4_room",
        parentFullname = "t4_parent",
        channel = channel,
        text = text,
        media = media,
        directChatParticipantUsername = if (channel == MessageReplyChannel.DIRECT_CHAT) {
            "subshell_dev"
        } else {
            null
        },
    )

    @Test
    fun directChatMediaPublishesLocalOptimisticUriWhileSending() {
        val state = messageReplySendingUiState(request(MessageReplyChannel.DIRECT_CHAT))

        assertEquals(MessageReplyPhase.SENDING, state.phase)
        assertEquals(media.uri, state.optimisticMediaUri)
        assertEquals(media.mimeType, state.optimisticMediaMimeType)
        val optimistic = optimisticConversationMedia(ConversationDetailKind.DIRECT_CHAT, state)
        assertNotNull(optimistic)
        assertEquals(media.uri, optimistic?.uri)
    }

    @Test
    fun directChatTextAndMediaPublishesOptimisticMediaForFirstMatrixWrite() {
        val state = messageReplySendingUiState(
            request(MessageReplyChannel.DIRECT_CHAT, text = "caption"),
        )

        assertEquals(media.uri, state.optimisticMediaUri)
        assertEquals(media.mimeType, state.optimisticMediaMimeType)
        assertNotNull(optimisticConversationMedia(ConversationDetailKind.DIRECT_CHAT, state))
    }

    @Test
    fun privateMessageMediaDoesNotUseMatrixOptimisticEcho() {
        val state = messageReplySendingUiState(request(MessageReplyChannel.PRIVATE_MESSAGE_THREAD))

        assertNull(state.optimisticMediaUri)
        assertNull(optimisticConversationMedia(ConversationDetailKind.PRIVATE_MESSAGE_THREAD, state))
    }

    @Test
    fun textOnlyDirectChatDoesNotPublishOptimisticMedia() {
        val state = messageReplySendingUiState(
            request(MessageReplyChannel.DIRECT_CHAT, text = "hello", media = null),
        )

        assertNull(state.optimisticMediaUri)
        assertNull(optimisticConversationMedia(ConversationDetailKind.DIRECT_CHAT, state))
    }

    @Test
    fun acceptedDirectChatMediaKeepsLocalBackingUntilAuthoritativeReconciliation() {
        val state = messageReplySendingUiState(request(MessageReplyChannel.DIRECT_CHAT)).copy(
            phase = MessageReplyPhase.SUCCEEDED,
            sentHadMedia = true,
            sentMediaUri = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media-id",
            sentMatrixMediaId = "media-id",
            sentMatrixEventId = "\$event",
            completedAtEpochSeconds = 1_000L,
            completionRevision = 1L,
        )

        val optimistic = optimisticConversationMedia(
            ConversationDetailKind.DIRECT_CHAT,
            state,
        )

        assertNotNull(optimistic)
        assertEquals(media.uri, optimistic?.uri)
        assertEquals(media.mimeType, optimistic?.mimeType)
    }

    @Test
    fun succeededTextOnlyReplyNeverUsesOptimisticMediaBacking() {
        val state = MessageReplyUiState(
            phase = MessageReplyPhase.SUCCEEDED,
            optimisticMediaUri = media.uri,
            optimisticMediaMimeType = media.mimeType,
            sentHadMedia = false,
        )

        assertNull(optimisticConversationMedia(ConversationDetailKind.DIRECT_CHAT, state))
    }


    @Test
    fun partialTextFailureKeepsConfirmedDirectChatMediaBacking() {
        val state = messageReplySendingUiState(
            request(MessageReplyChannel.DIRECT_CHAT, text = "caption"),
        ).copy(
            phase = MessageReplyPhase.FAILED,
            sentHadMedia = true,
            sentMediaUri = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media-id",
            sentMatrixMediaId = "media-id",
            sentMatrixEventId = "event-id",
            completionRevision = 1L,
        )

        val optimistic = optimisticConversationMedia(ConversationDetailKind.DIRECT_CHAT, state)
        assertNotNull(optimistic)
        assertEquals(media.uri, optimistic?.uri)
        assertEquals(media.mimeType, optimistic?.mimeType)
    }

    @Test
    fun partialUnknownTextOutcomeKeepsConfirmedDirectChatMediaBacking() {
        val state = messageReplySendingUiState(
            request(MessageReplyChannel.DIRECT_CHAT, text = "caption"),
        ).copy(
            phase = MessageReplyPhase.OUTCOME_UNKNOWN,
            sentHadMedia = true,
            sentMatrixMediaId = "media-id",
            sentMatrixEventId = "event-id",
            completionRevision = 1L,
        )

        assertNotNull(optimisticConversationMedia(ConversationDetailKind.DIRECT_CHAT, state))
    }

}
