package app.subshell.prototype.auth

import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCrosspostSourceLoader
import app.subshell.prototype.data.auth.reddit.CrosspostSourceLoadResult
import app.subshell.prototype.ui.ComposerCommunityPolicy
import app.subshell.prototype.ui.CrosspostReviewPhase
import app.subshell.prototype.ui.CrosspostReviewUiState
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.crosspostReviewIdentity
import app.subshell.prototype.ui.crosspostSourceValidationMessage
import app.subshell.prototype.ui.isValidSourceBoundCrosspostDraft
import app.subshell.prototype.ui.toCrosspostWriteRequest
import app.subshell.prototype.ui.validateCrosspostComposerDraftIssue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// CROSSPOST01A1E_REVIEW_THEN_EXPLICIT_PUBLISH_V1
// A reviewed token is single-use, owner/draft/source-bound and never persisted.
// Review, policy refresh and authorization callbacks cannot call the submit sink.
internal class CrosspostCreateCoordinator(
    private val sourceLoader: AuthenticatedCrosspostSourceLoader?,
    private val policyLoader: AuthenticatedComposerPolicyLoader?,
    private val activeAccountId: () -> String?,
    private val writeAvailable: (PostComposerDraft) -> Boolean,
    private val submit: suspend (TextPostWriteRequest) -> Unit,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val mutableState = MutableStateFlow(CrosspostReviewUiState())
    val uiState: StateFlow<CrosspostReviewUiState> = mutableState.asStateFlow()
    private var revision = 0L
    private var reviewedAtMs = 0L

    fun invalidateReview() {
        mutableState.value = CrosspostReviewUiState(revision = ++revision)
    }

    suspend fun review(draft: PostComposerDraft) {
        val token = ++revision
        mutableState.value = CrosspostReviewUiState(
            phase = CrosspostReviewPhase.CHECKING, revision = token, draft = draft.crosspostReviewIdentity(),
        )
        val verified = verify(draft, token) ?: return
        if (!stillCurrent(draft, token)) return
        reviewedAtMs = nowMs()
        mutableState.value = mutableState.value.copy(
            phase = CrosspostReviewPhase.READY, request = verified.request, policy = verified.policy,
        )
    }

    suspend fun publish(draft: PostComposerDraft, reviewedRevision: Long) {
        val reviewed = mutableState.value
        if (reviewed.phase != CrosspostReviewPhase.READY || reviewed.revision != reviewedRevision) return
        if (!reviewed.matches(draft) || activeAccountId() != draft.accountStableId || !writeAvailable(draft)) {
            fail(reviewedRevision, "The draft or account changed. Review the crosspost again")
            return
        }
        if (nowMs() - reviewedAtMs !in 0L..60_000L) {
            fail(reviewedRevision, "The review expired. Check the original post and destination again")
            return
        }
        // Consume before suspending: double-tap and delayed callbacks cannot reuse the review.
        val token = ++revision
        mutableState.value = reviewed.copy(phase = CrosspostReviewPhase.VERIFYING, revision = token)
        val verified = verify(draft, token) ?: return
        if (!stillCurrent(draft, token)) return
        if (verified.request.requestKey != reviewed.request?.requestKey) {
            fail(token, "Posting details changed. Review the crosspost again")
            return
        }
        // CROSSPOST01A1E_ONLY_EXPLICIT_CONFIRM_DISPATCHES_V1
        // This delegates to the existing non-retrying writer and durable attempt store.
        mutableState.value = mutableState.value.copy(phase = CrosspostReviewPhase.DISPATCHED)
        submit(verified.request)
    }

    private data class Verified(val request: TextPostWriteRequest, val policy: ComposerCommunityPolicy)

    private fun stillCurrent(draft: PostComposerDraft, token: Long): Boolean {
        if (revision != token) return false
        if (activeAccountId() != draft.accountStableId || !writeAvailable(draft)) {
            fail(token, "The active account or publishing state changed. Review again")
            return false
        }
        return true
    }

    private fun fail(token: Long, message: String, policy: ComposerCommunityPolicy? = null) {
        if (revision != token) return
        mutableState.value = mutableState.value.copy(
            phase = CrosspostReviewPhase.FAILED, request = null, policy = policy, message = message,
        )
    }

    private suspend fun verify(draft: PostComposerDraft, token: Long): Verified? {
        if (!stillCurrent(draft, token)) return null
        if (!draft.isValidSourceBoundCrosspostDraft()) {
            fail(token, "The source-bound crosspost draft is invalid")
            return null
        }
        val account = requireNotNull(draft.accountStableId)
        val source = requireNotNull(draft.crosspostSource)
        val sources = sourceLoader
        val policies = policyLoader
        if (sources == null || policies == null) {
            fail(token, "Crosspost verification is not configured for this account")
            return null
        }
        return try {
            // CROSSPOST01A1E_RECHECK_SOURCE_AND_DESTINATION_AT_CONFIRM_V1
            // Both Review and explicit Publish take this path. No repository/cache snapshot is proof.
            val sourceResult = sources.load(account, source.fullname)
            if (!stillCurrent(draft, token)) return null
            val evidence = when (sourceResult) {
                is CrosspostSourceLoadResult.Available -> sourceResult.evidence
                CrosspostSourceLoadResult.AuthorizationRequired -> {
                    fail(token, "Reconnect the account to verify the original post")
                    return null
                }
                CrosspostSourceLoadResult.Unavailable -> {
                    fail(token, "Reddit did not confirm that the original post is available for crossposting")
                    return null
                }
            }
            crosspostSourceValidationMessage(draft, evidence)?.let { message ->
                fail(token, message)
                return null
            }
            val policyResult = policies.load(account, draft.community)
            if (!stillCurrent(draft, token)) return null
            val policy = (policyResult as? AuthenticatedComposerPolicyResult.Loaded)?.policy
            if (policy == null) {
                fail(token, "The destination's posting requirements could not be verified")
                return null
            }
            val issue = validateCrosspostComposerDraftIssue(draft, account, true, policy)
            if (issue != null) {
                fail(token, issue.message, policy)
                return null
            }
            val request = draft.toCrosspostWriteRequest(account, true, policy)
            if (request == null) {
                fail(token, "Review the crosspost details before publishing", policy)
                null
            } else Verified(request, policy)
        } catch (cancelled: CancellationException) {
            if (revision == token) invalidateReview()
            throw cancelled
        } catch (_: Exception) {
            fail(token, "Crosspost verification failed. Nothing was published")
            null
        }
    }
}
