package app.subshell.prototype.ui.error

import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class Messages02C4R3R1MessageReplyErrorPresentationPolicyTest {
    @Test
    fun directChatApiFailureUsesCanonicalInlineErrorNoticeCopyInsteadOfRawStateMessage() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.API,
                message = "Reddit rejected this reply.",
            ),
            directChat = true,
        )

        assertEquals(ErrorPresentationKind.INLINE, presentation.kind)
        assertEquals("Couldn't send message", presentation.title)
        assertEquals("Reddit rejected the message.", presentation.message)
        assertNull(presentation.primaryAction)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
        assertEquals("Dismiss", presentation.secondaryAction?.label)
    }

    @Test
    fun privateMessageApiFailureUsesReplyTerminology() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.API,
            ),
            directChat = false,
        )

        assertEquals("Couldn't send reply", presentation.title)
        assertEquals("Reddit rejected the reply.", presentation.message)
    }

    @Test
    fun outcomeUnknownUsesCanonicalMayHaveBeenSentCardWithoutBlindWriteRetry() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.OUTCOME_UNKNOWN,
                failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
            ),
            directChat = true,
            canRefresh = true,
        )

        assertEquals("Message may have been sent", presentation.title)
        assertEquals(
            "Reddit may have received this message. Refresh the conversation before trying again.",
            presentation.message,
        )
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals("Refresh", presentation.primaryAction?.label)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
        assertEquals("Checked", presentation.secondaryAction?.label)
    }

    @Test
    fun authorizationFailuresKeepExistingReconnectAndMediaAccessActions() {
        val reconnect = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.AUTHORIZATION_REQUIRED,
            ),
            directChat = true,
        )
        val media = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED,
            ),
            directChat = true,
        )

        assertEquals(ErrorActionKind.SIGN_IN, reconnect.primaryAction?.kind)
        assertEquals("Reconnect", reconnect.primaryAction?.label)
        assertEquals(ErrorActionKind.SIGN_IN, media.primaryAction?.kind)
        assertEquals("Grant media access", media.primaryAction?.label)
    }

    @Test
    fun partialDirectChatTextFailureExplainsThatMediaWasAlreadySent() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.HTTP,
                sentHadMedia = true,
                completionRevision = 1L,
            ),
            directChat = true,
        )

        assertEquals("Media sent, text not sent", presentation.title)
        assertEquals(
            "The image/GIF was sent, but the message text wasn't sent.",
            presentation.message,
        )
        assertNull(presentation.primaryAction)
    }

    @Test
    fun partialDirectChatUnknownTextOutcomeRequiresRefreshBeforeRetry() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.OUTCOME_UNKNOWN,
                failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
                sentHadMedia = true,
                completionRevision = 1L,
            ),
            directChat = true,
            canRefresh = true,
        )

        assertEquals("Media sent; text may have been sent", presentation.title)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals("Refresh", presentation.primaryAction?.label)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
        assertEquals("Checked", presentation.secondaryAction?.label)
    }

}
