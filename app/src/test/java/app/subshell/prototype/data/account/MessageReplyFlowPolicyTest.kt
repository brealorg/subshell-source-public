package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReplyFlowPolicyTest {
    @Test
    fun requestNormalizationPreservesTypedThreadAndTrimsBody() {
        val normalized = MessageReplyRequest(
            accountStableId = " reddit:acct ",
            inboxItemId = " t4_root ",
            parentFullname = " T4_AbC123 ",
            channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
            text = "  hello there  ",
        ).normalizedMessageReplyRequest()

        assertEquals("reddit:acct", normalized.accountStableId)
        assertEquals("t4_root", normalized.inboxItemId)
        assertEquals("t4_abc123", normalized.parentFullname)
        assertEquals(MessageReplyChannel.PRIVATE_MESSAGE_THREAD, normalized.channel)
        assertEquals("hello there", normalized.text)
    }


    @Test
    fun directChatMediaUsesMatrixTransportAndRequiresParticipantIdentity() {
        val normalized = MessageReplyRequest(
            accountStableId = " reddit:acct ",
            inboxItemId = " t4_chat ",
            parentFullname = " T4_Chat123 ",
            channel = MessageReplyChannel.DIRECT_CHAT,
            text = "",
            media = MessageReplyMediaAttachment(
                uri = " content://media/external/images/42 ",
                mimeType = " IMAGE/GIF ",
            ),
            directChatParticipantUsername = " subshell_dev ",
        ).normalizedMessageReplyRequest()

        assertEquals(MessageReplyChannel.DIRECT_CHAT, normalized.channel)
        assertTrue(messageReplyMediaSelectionEnabled(normalized.channel))
        assertEquals("subshell_dev", normalized.directChatParticipantUsername)
    }


    @Test(expected = IllegalArgumentException::class)
    fun directChatMediaWithoutParticipantIdentityFailsClosed() {
        MessageReplyRequest(
            accountStableId = "reddit:acct",
            inboxItemId = "t4_chat",
            parentFullname = "t4_chat123",
            channel = MessageReplyChannel.DIRECT_CHAT,
            text = "",
            media = MessageReplyMediaAttachment(
                uri = "content://media/external/images/42",
                mimeType = "image/gif",
            ),
        ).normalizedMessageReplyRequest()
    }

    @Test
    fun mediaOnlyRequestNormalizesWithoutInventingText() {
        val normalized = MessageReplyRequest(
            accountStableId = " reddit:acct ",
            inboxItemId = " t4_root ",
            parentFullname = " T4_AbC123 ",
            channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
            text = "   ",
            media = MessageReplyMediaAttachment(
                uri = " content://media/external/images/42 ",
                mimeType = " IMAGE/GIF ",
                sourceLinkUri = " https://example.invalid/original.gif ",
            ),
        ).normalizedMessageReplyRequest()

        assertEquals("", normalized.text)
        assertEquals("content://media/external/images/42", normalized.media?.uri)
        assertEquals("image/gif", normalized.media?.mimeType)
        assertEquals(
            "https://example.invalid/original.gif",
            normalized.media?.sourceLinkUri,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankReplyWithoutMediaStillFailsValidation() {
        MessageReplyRequest(
            accountStableId = "reddit:acct",
            inboxItemId = "t4_root",
            parentFullname = "t4_abc123",
            channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
            text = " ",
        ).normalizedMessageReplyRequest()
    }

    @Test
    fun onlySendingAndUnknownOutcomeBlockAnotherSend() {
        assertTrue(MessageReplyUiState(phase = MessageReplyPhase.SENDING).blocksSend)
        assertTrue(MessageReplyUiState(phase = MessageReplyPhase.OUTCOME_UNKNOWN).blocksSend)
        assertFalse(MessageReplyUiState(phase = MessageReplyPhase.FAILED).blocksSend)
        assertFalse(MessageReplyUiState(phase = MessageReplyPhase.SUCCEEDED).blocksSend)
    }
}
