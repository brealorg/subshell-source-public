package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02C7LiveConversationPolicyTest {
    @Test
    fun matchingOutgoingLiveImageDoesNotReplaceLocalConfirmationBacking() {
        val state = MessageReplyUiState(
            phase = MessageReplyPhase.SUCCEEDED,
            accountStableId = "account",
            inboxItemId = "t4_thread",
            sentHadMedia = true,
            optimisticMediaUri = "content://selected/gif",
            optimisticMediaMimeType = "image/gif",
            sentMatrixMediaId = "media123",
            sentMatrixEventId = "event123",
            completionRevision = 1L,
        )
        val matching = MessageBubble(
            id = "event123",
            sender = "me",
            age = "now",
            body = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media123",
            outgoing = true,
            createdEpochSeconds = 100L,
        )
        val incoming = matching.copy(
            id = "incoming",
            sender = "peer",
            outgoing = false,
        )

        assertTrue(shouldSuppressDirectChatLiveMessageForLocalMedia(matching, state))
        assertFalse(shouldSuppressDirectChatLiveMessageForLocalMedia(incoming, state))
    }

    @Test
    fun liveActivityRefreshesLegacyInboxProjectionOnDirectChatExit() {
        assertTrue(
            shouldRefreshInboxAfterDirectChatExit(
                kind = ConversationDetailKind.DIRECT_CHAT,
                successfulReplyObserved = false,
                liveActivityObserved = true,
                state = MessageReplyUiState(),
            ),
        )
        assertFalse(
            shouldRefreshInboxAfterDirectChatExit(
                kind = ConversationDetailKind.PRIVATE_MESSAGE_THREAD,
                successfulReplyObserved = false,
                liveActivityObserved = true,
                state = MessageReplyUiState(),
            ),
        )
    }
}
