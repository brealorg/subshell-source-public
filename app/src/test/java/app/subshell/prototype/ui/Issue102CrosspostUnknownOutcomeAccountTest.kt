package app.subshell.prototype.ui

import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ISSUE102_CROSSPOST_UNKNOWN_OUTCOME_ACTIVE_ACCOUNT_V1
class Issue102CrosspostUnknownOutcomeAccountTest {
    private val ownRequestKey = "crosspost-request-b"

    private fun unknownOutcome(account: String, requestKey: String) = TextPostWriteUiState(
        phase = TextPostWritePhase.OUTCOME_UNKNOWN,
        accountStableId = account,
        requestKey = requestKey,
    )

    @Test
    fun anotherAccountsUnknownOutcomeNeitherShowsNorLocksTheDraft() {
        val aUnknown = unknownOutcome("account-a", "text-post-request-a")

        val shown = crosspostWriteStateForDraft(aUnknown, "account-b", ownRequestKey)

        assertEquals(TextPostWritePhase.IDLE, shown.phase)
        assertTrue(crosspostWriteAllowsReview(aUnknown, "account-b", ownRequestKey))
    }

    @Test
    fun theActiveAccountsUnknownOutcomeStillShowsAndLocks() {
        val bUnknown = unknownOutcome("account-b", "text-post-request-b")

        val shown = crosspostWriteStateForDraft(bUnknown, "account-b", ownRequestKey)

        assertEquals(bUnknown, shown)
        assertFalse(crosspostWriteAllowsReview(bUnknown, "account-b", ownRequestKey))
    }

    @Test
    fun aWriteInProgressStillBlocksEveryAccount() {
        val aSending = TextPostWriteUiState(
            phase = TextPostWritePhase.SENDING,
            accountStableId = "account-a",
            requestKey = "text-post-request-a",
        )

        assertTrue(crosspostWriteStateForDraft(aSending, "account-b", ownRequestKey).inProgress)
        assertFalse(crosspostWriteAllowsReview(aSending, "account-b", ownRequestKey))
    }
}
