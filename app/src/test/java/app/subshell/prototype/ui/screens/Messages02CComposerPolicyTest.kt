package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02CComposerPolicyTest {
    @Test
    fun pmReplyTargetsNewestCanonicalT4Bubble() {
        val item = InboxItem(
            id = "t4_root",
            kind = ActivityKind.MESSAGE,
            sender = "peer",
            title = "Subject",
            age = "1m",
            preview = "latest",
            conversation = listOf(
                MessageBubble("t4_root", "peer", "2m", "root"),
                MessageBubble("t4_mine", "me", "1m", "mine", outgoing = true),
            ),
        )
        assertEquals("t4_mine", resolveConversationReplyParentFullname(item))
    }

    @Test
    fun directChatCompatibilityReplyTargetsNewestCanonicalT4Bubble() {
        val item = InboxItem(
            id = "matrix-room",
            kind = ActivityKind.MESSAGE,
            sender = "peer",
            title = "[direct chat room]",
            age = "1m",
            preview = "hello",
            conversation = listOf(
                MessageBubble("t4_old", "peer", "2m", "old"),
                MessageBubble("t4_legacy", "peer", "1m", "hello"),
            ),
        )
        assertEquals("t4_legacy", resolveConversationReplyParentFullname(item))
    }

    @Test
    fun c5r2DirectChatMediaUiIsReopenedWithoutPretendingT4MediaTransportWorks() {
        assertEquals(
            ConversationReplyMediaCapability.IMAGE_AND_GIF,
            conversationReplyMediaCapability(ConversationDetailKind.DIRECT_CHAT),
        )
        assertEquals(
            ConversationReplyMediaCapability.IMAGE_AND_GIF,
            conversationReplyMediaCapability(ConversationDetailKind.PRIVATE_MESSAGE_THREAD),
        )
        assertTrue(
            conversationReplyHasSendableContent(
                kind = ConversationDetailKind.DIRECT_CHAT,
                text = "",
                hasMedia = true,
            ),
        )
        assertTrue(
            conversationReplyHasSendableContent(
                kind = ConversationDetailKind.DIRECT_CHAT,
                text = "text reply",
                hasMedia = false,
            ),
        )
        assertTrue(
            conversationReplyHasSendableContent(
                kind = ConversationDetailKind.PRIVATE_MESSAGE_THREAD,
                text = "",
                hasMedia = true,
            ),
        )
    }

    @Test
    fun confirmedLocalEchoRequiresRecentMatchingOutgoingReply() {
        val state = MessageReplyUiState(
            phase = MessageReplyPhase.SUCCEEDED,
            sentText = "same reply",
            completedAtEpochSeconds = 1_000L,
        )
        assertTrue(
            messageReplyAppearsInConversation(
                messages = listOf(
                    MessageBubble(
                        id = "t4_server",
                        sender = "me",
                        age = "now",
                        body = "same reply",
                        outgoing = true,
                        createdEpochSeconds = 1_001L,
                    ),
                ),
                state = state,
            ),
        )
        assertFalse(
            messageReplyAppearsInConversation(
                messages = listOf(
                    MessageBubble(
                        id = "t4_old",
                        sender = "me",
                        age = "1h",
                        body = "same reply",
                        outgoing = true,
                        createdEpochSeconds = 500L,
                    ),
                ),
                state = state,
            ),
        )
    }
    @Test
    fun c2LatestConversationListIndexTracksServerAndLocalTail() {
        assertEquals(2, latestConversationListIndex(messageCount = 3, hasLocalEcho = false))
        assertEquals(3, latestConversationListIndex(messageCount = 3, hasLocalEcho = true))
        assertEquals(null, latestConversationListIndex(messageCount = 0, hasLocalEcho = false))
        assertEquals(0, latestConversationListIndex(messageCount = 0, hasLocalEcho = true))
    }

    @Test
    fun c2InboxRefreshOnExitIsDirectChatSuccessScoped() {
        val succeeded = MessageReplyUiState(
            phase = MessageReplyPhase.SUCCEEDED,
            completionRevision = 7L,
        )
        val idle = MessageReplyUiState()

        assertTrue(
            shouldRefreshInboxAfterDirectChatExit(
                kind = ConversationDetailKind.DIRECT_CHAT,
                successfulReplyObserved = false,
                state = succeeded,
            ),
        )
        assertTrue(
            shouldRefreshInboxAfterDirectChatExit(
                kind = ConversationDetailKind.DIRECT_CHAT,
                successfulReplyObserved = true,
                state = idle,
            ),
        )
        assertFalse(
            shouldRefreshInboxAfterDirectChatExit(
                kind = ConversationDetailKind.PRIVATE_MESSAGE_THREAD,
                successfulReplyObserved = true,
                state = succeeded,
            ),
        )
        assertFalse(
            shouldRefreshInboxAfterDirectChatExit(
                kind = ConversationDetailKind.DIRECT_CHAT,
                successfulReplyObserved = false,
                state = idle,
            ),
        )
    }

    @Test
    fun consecutiveMatrixMediaConfirmationRequiresTheCurrentMediaId() {
        val state = MessageReplyUiState(
            phase = MessageReplyPhase.SUCCEEDED,
            sentHadMedia = true,
            sentMediaUri =
                "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media_second",
            sentMatrixMediaId = "media_second",
            sentMatrixEventId = "\$event_second",
            completedAtEpochSeconds = 1_000L,
        )
        assertFalse(
            messageReplyAppearsInConversation(
                messages = listOf(
                    MessageBubble(
                        id = "\$event_first",
                        sender = "me",
                        age = "now",
                        body =
                            "sent an image\nhttps://matrix.redditspace.com/_matrix/media/v3/thumbnail/reddit.com/media_first?height=256",
                        outgoing = true,
                        createdEpochSeconds = 1_001L,
                    ),
                ),
                state = state,
            ),
        )
        assertTrue(
            messageReplyAppearsInConversation(
                messages = listOf(
                    MessageBubble(
                        id = "\$event_second",
                        sender = "me",
                        age = "now",
                        body =
                            "sent an image\nhttps://matrix.redditspace.com/_matrix/media/v3/thumbnail/reddit.com/media_second?height=256",
                        outgoing = true,
                        createdEpochSeconds = 1_002L,
                    ),
                ),
                state = state,
            ),
        )
    }

}
