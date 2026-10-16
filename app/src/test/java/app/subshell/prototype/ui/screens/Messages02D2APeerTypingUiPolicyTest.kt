package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.DirectChatLiveUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02D2APeerTypingUiPolicyTest {
    @Test
    fun directChatShowsPeerTypingLabelOnlyWhileRuntimeStateIsTyping() {
        val typing = DirectChatLiveUiState(peerTyping = true)
        val idle = DirectChatLiveUiState(peerTyping = false)

        assertEquals(
            "u/peer_user is typing…",
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = "peer_user",
                liveState = typing,
            ),
        )
        assertNull(
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = "peer_user",
                liveState = idle,
            ),
        )
    }

    @Test
    fun privateMessageThreadNeverShowsMatrixTypingState() {
        assertNull(
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.PRIVATE_MESSAGE_THREAD,
                participantUsername = "peer_user",
                liveState = DirectChatLiveUiState(peerTyping = true),
            ),
        )
    }

    @Test
    fun typingLabelNormalizesRedditUserPrefixAndRejectsBlankParticipant() {
        assertEquals(
            "u/peer_user is typing…",
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = "u/peer_user",
                liveState = DirectChatLiveUiState(peerTyping = true),
            ),
        )
        assertNull(
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = " ",
                liveState = DirectChatLiveUiState(peerTyping = true),
            ),
        )
    }
}
