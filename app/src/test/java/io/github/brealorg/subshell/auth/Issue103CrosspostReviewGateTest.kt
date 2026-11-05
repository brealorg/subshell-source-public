package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.TextPostWritePhase
import io.github.brealorg.subshell.data.account.TextPostWriteUiState
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedComposerPolicyLoader
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedComposerPolicyResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCrosspostSourceLoader
import io.github.brealorg.subshell.data.auth.reddit.CrosspostSourceLoadResult
import io.github.brealorg.subshell.ui.CrosspostReviewPhase
import io.github.brealorg.subshell.ui.CrosspostSourceEvidence
import io.github.brealorg.subshell.ui.crosspost01A1CDraft
import io.github.brealorg.subshell.ui.crosspost01A1CPolicy
import io.github.brealorg.subshell.ui.crosspostDraftWriteAvailable
import io.github.brealorg.subshell.ui.crosspostRequestForIdentityOnly
import io.github.brealorg.subshell.ui.crosspostWriteAllowsReview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ISSUE103_CROSSPOST_REVIEW_GATE_FOLLOWS_DRAFT_STATE_V1
// (+ ISSUE102: the coordinator gate is account-aware too)
class Issue103CrosspostReviewGateTest {
    private val draft = crosspost01A1CDraft()
    private val ownRequestKey = requireNotNull(draft.crosspostRequestForIdentityOnly()).requestKey

    private fun writer(phase: TextPostWritePhase, account: String, requestKey: String) =
        TextPostWriteUiState(phase = phase, accountStableId = account, requestKey = requestKey)

    private fun reviewWith(writeState: TextPostWriteUiState): CrosspostReviewPhase = runBlocking {
        val coordinator = CrosspostCreateCoordinator(
            sourceLoader = AuthenticatedCrosspostSourceLoader { _, _ ->
                CrosspostSourceLoadResult.Available(
                    CrosspostSourceEvidence("t3_source123", "source", "author", "Original title", false, false),
                )
            },
            policyLoader = AuthenticatedComposerPolicyLoader { _, _ ->
                AuthenticatedComposerPolicyResult.Loaded(crosspost01A1CPolicy())
            },
            activeAccountId = { "owner" },
            writeAvailable = { draft -> crosspostDraftWriteAvailable(writeState, draft) },
            submit = {},
        )
        coordinator.review(draft)
        coordinator.uiState.value.phase
    }

    @Test
    fun anotherRequestsStaleAuthorizationRequiredNeitherShowsNorDisablesReview() {
        val abandonedTextPost = writer(TextPostWritePhase.AUTHORIZATION_REQUIRED, "owner", "text-post-request")

        assertTrue(crosspostWriteAllowsReview(abandonedTextPost, "owner", ownRequestKey))
        assertEquals(CrosspostReviewPhase.READY, reviewWith(abandonedTextPost))
    }

    @Test
    fun thisDraftsOwnAuthorizationRequiredStillBlocksReview() {
        val own = writer(TextPostWritePhase.AUTHORIZATION_REQUIRED, "owner", ownRequestKey)

        assertFalse(crosspostWriteAllowsReview(own, "owner", ownRequestKey))
        assertEquals(CrosspostReviewPhase.FAILED, reviewWith(own))
    }

    @Test
    fun anotherAccountsUnknownOutcomeDoesNotFailTheCoordinatorsReview() {
        val otherAccountUnknown = writer(TextPostWritePhase.OUTCOME_UNKNOWN, "account-a", "text-post-request-a")

        assertEquals(CrosspostReviewPhase.READY, reviewWith(otherAccountUnknown))
    }

    @Test
    fun theOwnAccountsUnknownOutcomeStillFailsTheCoordinatorsReview() {
        val ownAccountUnknown = writer(TextPostWritePhase.OUTCOME_UNKNOWN, "owner", "text-post-request")

        assertEquals(CrosspostReviewPhase.FAILED, reviewWith(ownAccountUnknown))
    }
}
