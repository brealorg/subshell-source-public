package app.subshell.prototype.ui

import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.textPostWriteStateForRequest

// CROSSPOST01A1E_EPHEMERAL_REVIEW_IDENTITY_V1
// Review is deliberately NOT in a draft, backup, or saved process snapshot.
enum class CrosspostReviewPhase { IDLE, CHECKING, READY, VERIFYING, DISPATCHED, FAILED }

data class CrosspostSourceEvidence(
    val fullname: String,
    val community: String,
    val author: String,
    val title: String,
    val nsfw: Boolean,
    val spoiler: Boolean,
)

data class CrosspostReviewUiState(
    val phase: CrosspostReviewPhase = CrosspostReviewPhase.IDLE,
    val revision: Long = 0L,
    val draft: PostComposerDraft? = null,
    val request: TextPostWriteRequest? = null,
    val policy: ComposerCommunityPolicy? = null,
    val message: String? = null,
) {
    fun matches(candidate: PostComposerDraft): Boolean = draft == candidate.crosspostReviewIdentity()
    val busy: Boolean
        get() = phase == CrosspostReviewPhase.CHECKING || phase == CrosspostReviewPhase.VERIFYING
}

internal fun PostComposerDraft.crosspostReviewIdentity(): PostComposerDraft =
    copy(createdAtEpochMs = 0L, updatedAtEpochMs = 0L)

// Identity/status matching only. Dispatch always goes through toCrosspostWriteRequest
// with freshly loaded account-bound destination policy AND a fresh source read.
internal fun PostComposerDraft.crosspostRequestForIdentityOnly(): TextPostWriteRequest? {
    if (!isValidSourceBoundCrosspostDraft()) return null
    return runCatching {
        TextPostWriteRequest(
            accountStableId = requireNotNull(accountStableId),
            community = requireNotNull(normalizeCrosspostComposerCommunity(community)),
            title = title.trim(), text = "", sendReplies = sendReplies,
            nsfw = nsfw, spoiler = spoiler,
            flairId = flair?.templateId, flairText = flair?.text,
            crosspostFullname = requireNotNull(crosspostSource).fullname,
        )
    }.getOrNull()
}

internal fun crosspostWritePhaseAllowsReview(phase: TextPostWritePhase): Boolean =
    phase == TextPostWritePhase.IDLE || phase == TextPostWritePhase.FAILED

// ISSUE102_CROSSPOST_UNKNOWN_OUTCOME_ACTIVE_ACCOUNT_V1: the writer keeps one
// state for every account, so an unknown outcome belongs to this draft only
// when it is the active account's; another account's leaves it unlocked.
internal fun crosspostWriteStateForDraft(
    writeState: TextPostWriteUiState,
    activeAccountId: String,
    requestKey: String?,
): TextPostWriteUiState =
    if (writeState.phase == TextPostWritePhase.OUTCOME_UNKNOWN &&
        writeState.accountStableId == activeAccountId
    ) {
        writeState
    } else {
        textPostWriteStateForRequest(writeState, requestKey)
    }

// ISSUE103_CROSSPOST_REVIEW_GATE_FOLLOWS_DRAFT_STATE_V1: Review (screen and
// CrosspostCreateCoordinator) is gated on the state this draft shows, so a
// stale state of another request or account cannot disable it unexplained.
internal fun crosspostWriteAllowsReview(
    writeState: TextPostWriteUiState,
    activeAccountId: String,
    requestKey: String?,
): Boolean = crosspostWritePhaseAllowsReview(
    crosspostWriteStateForDraft(writeState, activeAccountId, requestKey).phase,
)

// The coordinator's gate: the draft's own account and request.
internal fun crosspostDraftWriteAvailable(
    writeState: TextPostWriteUiState,
    draft: PostComposerDraft,
): Boolean = crosspostWriteAllowsReview(
    writeState,
    draft.accountStableId.orEmpty(),
    draft.crosspostRequestForIdentityOnly()?.requestKey,
)

internal fun crosspostSourceValidationMessage(
    draft: PostComposerDraft,
    evidence: CrosspostSourceEvidence,
): String? {
    val source = draft.crosspostSource ?: return "The original post is missing"
    if (source.fullname != evidence.fullname || source.community != evidence.community ||
        source.author != evidence.author || source.title != evidence.title
    ) return "The original post changed. Open it again before crossposting"
    if (evidence.nsfw && !draft.nsfw) return "The original post is NSFW. Enable NSFW before reviewing"
    if (evidence.spoiler && !draft.spoiler) return "The original post is a spoiler. Enable Spoiler before reviewing"
    return null
}

// CROSSPOST01A1E_NEW_WRAPPER_COMPLETION_V1
// A late or unrelated result must not discard another draft or navigate to its source.
internal fun completedCrosspostForDraft(
    draft: PostComposerDraft,
    activeAccountStableId: String,
    activeAccountIsAuthenticated: Boolean,
    author: String,
    requestKey: String,
    submitted: SubmittedTextPost,
): Post? {
    if (!activeAccountIsAuthenticated || draft.accountStableId != activeAccountStableId) return null
    val request = draft.crosspostRequestForIdentityOnly() ?: return null
    val source = draft.crosspostSource ?: return null
    // LOWUI_CROSSPOST_FLAIR_MATCHED_TRIMMED_V1 (OCB-293): the submitted post
    // keeps its flair text trimmed, a Reddit template may carry spaces.
    if (request.requestKey != requestKey || submitted.crosspostFullname != source.fullname ||
        submitted.fullname == source.fullname || submitted.fullname != "t3_${submitted.id}" ||
        normalizeCrosspostComposerCommunity(submitted.community) != request.community ||
        submitted.title != request.title || submitted.nsfw != request.nsfw ||
        submitted.spoiler != request.spoiler || submitted.flairText?.trim() != request.flairText?.trim()
    ) return null
    return Post(
        id = submitted.fullname, community = submitted.community, author = author, age = "0m",
        title = submitted.title, body = "", score = 1, comments = 0, kind = PostKind.CROSSPOST,
        flair = submitted.flairText, nsfw = submitted.nsfw, spoiler = submitted.spoiler,
        permalink = submitted.permalink ?: "/r/${submitted.community}/comments/${submitted.id}/",
        crosspost = CrosspostPreview(
            community = source.community, author = source.author, title = source.title,
            score = 0, id = source.fullname, kind = source.kind,
            nsfw = source.nsfw, spoiler = source.spoiler, permalink = source.permalink,
        ),
    )
}

// CROSSPOST01A2_CANONICAL_POST_PUBLISH_REBIND_V1
// Submit ACKs contain wrapper identity but not the rich crosspost_parent projection.
// Accept only a canonical readback that proves BOTH wrapper and pinned source identity.
internal fun canonicalCrosspostCompletionOrFallback(
    submitted: SubmittedTextPost,
    fallback: Post,
    candidate: Post?,
): Post {
    val sourceFullname = submitted.crosspostFullname ?: return fallback
    val resolved = candidate ?: return fallback
    val resolvedSource = resolved.crosspost ?: return fallback

    if (
        resolved.kind != PostKind.CROSSPOST ||
        !redditTargetMatchesPost(submitted.id, resolved.id) ||
        normalizeCrosspostComposerCommunity(resolved.community) !=
            normalizeCrosspostComposerCommunity(submitted.community) ||
        resolved.title != submitted.title ||
        resolved.nsfw != submitted.nsfw ||
        resolved.spoiler != submitted.spoiler ||
        !redditTargetMatchesPost(sourceFullname, resolvedSource.id)
    ) {
        return fallback
    }

    return resolved
}
