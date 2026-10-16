package app.subshell.prototype.ui.error

import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Safety01A31ContentSafetyViewportErrorPolicyTest {
    @Test
    fun retryableSafetyFailureUsesDegradedViewportPresentation() {
        val presentation = contentSafetyErrorPresentation(
            ContentSafetyUiState(
                phase = ContentSafetyPhase.FAILED,
                operation = ContentSafetyOperation.SET_POST_HIDDEN,
                accountStableId = "account",
                thingFullname = "t3_abc123",
                hidden = true,
                failureKind = ContentSafetyFailureKind.HTTP,
                retryable = true,
            ),
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
    }

    @Test
    fun unknownReportOutcomeIsDegradedAndCannotBlindRetry() {
        val presentation = contentSafetyErrorPresentation(
            ContentSafetyUiState(
                phase = ContentSafetyPhase.FAILED,
                operation = ContentSafetyOperation.REPORT_CONTENT,
                accountStableId = "account",
                thingFullname = "t1_comment1",
                failureKind = ContentSafetyFailureKind.OUTCOME_UNKNOWN,
                retryable = true,
            ),
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertNull(presentation.primaryAction)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
    }
}
