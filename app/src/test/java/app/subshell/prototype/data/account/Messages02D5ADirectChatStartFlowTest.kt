package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02D5ADirectChatStartFlowTest {
    @Test
    fun requestNormalizesAccountRecipientAndBody() {
        val normalized = DirectChatStartRequest(
            accountStableId = " reddit:account ",
            participantUsername = " u/Target_User ",
            text = " hello there ",
        ).normalizedDirectChatStartRequest()

        assertEquals("reddit:account", normalized.accountStableId)
        assertEquals("Target_User", normalized.participantUsername)
        assertEquals("hello there", normalized.text)
    }

    @Test
    fun blankOrPathShapedRecipientFailsValidation() {
        assertTrue(
            runCatching {
                DirectChatStartRequest("account", "   ", "hello")
                    .normalizedDirectChatStartRequest()
            }.isFailure,
        )
        assertTrue(
            runCatching {
                DirectChatStartRequest("account", "bad/name", "hello")
                    .normalizedDirectChatStartRequest()
            }.isFailure,
        )
    }

    @Test
    fun outcomeUnknownBlocksBlindResendButDefiniteFailureDoesNot() {
        assertTrue(
            DirectChatStartUiState(
                phase = DirectChatStartPhase.OUTCOME_UNKNOWN,
            ).blocksSend,
        )
        assertFalse(
            DirectChatStartUiState(
                phase = DirectChatStartPhase.FAILED,
            ).blocksSend,
        )
    }
}
