package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import io.github.brealorg.subshell.data.auth.reddit.CHAT_STEP_UPLOAD
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.messageReplyErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CHAT01: a Direct Chat media failure that needs Reddit's chat session names
 * the step that failed (shown as details) and no longer offers a "Reconnect"
 * that only renews the OAuth grant; ordinary authorization still does.
 */
class Chat01ChatSessionFailureStepTest {
    @Test
    fun theFailedStepAndStatusBecomeTheErrorDetails() {
        val details = messageReplyFailureDetails(
            AuthenticatedRedditMessageReplyResult.Failed(
                kind = MessageReplyFailureKind.CHAT_SESSION_REQUIRED,
                statusCode = 403,
                providerCode = CHAT_STEP_UPLOAD,
            ),
        )!!

        assertEquals(403, details.httpStatusCode)
        assertEquals(CHAT_STEP_UPLOAD, details.providerCode?.value)
    }

    @Test
    fun aChatSessionFailureShowsItsDetailsAndNoReconnect() {
        val state = MessageReplyUiState(
            phase = MessageReplyPhase.FAILED,
            failureKind = MessageReplyFailureKind.CHAT_SESSION_REQUIRED,
            failureDetails = messageReplyFailureDetails(
                AuthenticatedRedditMessageReplyResult.Failed(
                    kind = MessageReplyFailureKind.CHAT_SESSION_REQUIRED,
                    providerCode = "CHAT_MATRIX_ACCOUNT_MISMATCH",
                ),
            ),
        )

        val presentation = messageReplyErrorPresentation(state, directChat = true)

        assertNotEquals(ErrorActionKind.SIGN_IN, presentation.primaryAction?.kind)
        assertEquals("CHAT_MATRIX_ACCOUNT_MISMATCH", presentation.failureDetails?.providerCode?.value)
    }

    @Test
    fun anOrdinaryAuthorizationFailureStillOffersReconnect() {
        val state = MessageReplyUiState(
            phase = MessageReplyPhase.FAILED,
            failureKind = MessageReplyFailureKind.AUTHORIZATION_REQUIRED,
        )

        val presentation = messageReplyErrorPresentation(state, directChat = true)

        assertEquals(ErrorActionKind.SIGN_IN, presentation.primaryAction?.kind)
        assertNull(presentation.failureDetails)
    }
}
