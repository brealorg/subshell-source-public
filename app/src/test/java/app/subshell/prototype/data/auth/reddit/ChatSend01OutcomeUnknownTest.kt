package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.DirectChatStartFailureKind
import app.subshell.prototype.data.account.MessageReplyFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CHATSEND01 (#136): a Direct Chat message send answered 5xx/408/421/425 may
 * already be stored by the homeserver, and a resend uses a new transaction id,
 * so it is OUTCOME_UNKNOWN (no resend offered), never a plain HTTP failure.
 */
class ChatSend01OutcomeUnknownTest {
    private val unknown = listOf(408, 421, 425, 500, 502, 504)

    @Test
    fun theFirstMessageOfANewChatIsOutcomeUnknownOnAGatewayError() {
        unknown.forEach { status ->
            assertEquals("$status", DirectChatStartFailureKind.OUTCOME_UNKNOWN, directChatStartSendHttpFailureKind(status))
        }
        assertEquals(DirectChatStartFailureKind.HTTP, directChatStartSendHttpFailureKind(400))
    }

    @Test
    fun imageAndTextSendsAreOutcomeUnknownButWhoamiAndUploadAreNot() {
        for (step in listOf(CHAT_STEP_IMAGE_SEND, CHAT_STEP_TEXT_SEND)) {
            unknown.forEach { status ->
                assertEquals("$step $status", MessageReplyFailureKind.OUTCOME_UNKNOWN, redditChatMatrixHttpFailureKind(status, step))
            }
            assertEquals(MessageReplyFailureKind.HTTP, redditChatMatrixHttpFailureKind(400, step))
            assertEquals(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, redditChatMatrixHttpFailureKind(403, step))
            assertEquals(MessageReplyFailureKind.RATE_LIMIT, redditChatMatrixHttpFailureKind(429, step))
        }
        for (step in listOf(CHAT_STEP_WHOAMI, CHAT_STEP_UPLOAD)) {
            assertEquals("$step", MessageReplyFailureKind.HTTP, redditChatMatrixHttpFailureKind(502, step))
        }
    }
}
