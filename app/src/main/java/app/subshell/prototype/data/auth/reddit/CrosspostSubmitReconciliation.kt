package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.normalizeCrosspostSourceFullname
import app.subshell.prototype.data.network.reddit.RedditSubmittedTextPost
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostCommunity
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostTitle

// CROSSPOST01A1B_SOURCE_VERIFIED_RECONCILIATION_V1
// Same dispatch window as legacy reconciliation. No title/link/source-card inference of parent.
internal fun selectSourceVerifiedCrosspostCandidate(
    request: AuthenticatedTextPostSubmitRequest,
    username: String,
    dispatchedAtEpochMs: Long,
    candidates: List<RedditSubmitReconciliationCandidate>,
): RedditSubmitReconciliationCandidate? {
    val source = runCatching { request.validatedCrosspostFullname() }.getOrNull() ?: return null
    val author = username.trim().takeIf {
        it.matches(Regex("[A-Za-z0-9_-]{1,100}"))
    } ?: return null
    if (dispatchedAtEpochMs <= 0L) return null
    val community = runCatching { normalizeRedditTextPostCommunity(request.community) }
        .getOrNull() ?: return null
    val title = runCatching { normalizeRedditTextPostTitle(request.title) }
        .getOrNull() ?: return null
    val dispatchedSeconds = dispatchedAtEpochMs / 1_000L
    val window = (dispatchedSeconds - SUBMIT_READBACK_CLOCK_SKEW_SECONDS)..(dispatchedSeconds + 300L)

    return candidates.asSequence().filter { candidate ->
        // Malformed candidates cannot abort the entire readback or identify the source as the wrapper.
        runCatching {
            candidate.kind == RedditSubmitReconciliationKind.CROSSPOST &&
                candidate.crosspostFullname == source &&
                candidate.fullname == "t3_${candidate.id}" &&
                normalizeCrosspostSourceFullname(candidate.fullname) == candidate.fullname &&
                candidate.fullname != source &&
                candidate.author.equals(author, ignoreCase = true) &&
                normalizeRedditTextPostCommunity(candidate.community) == community &&
                normalizeRedditTextPostTitle(candidate.title) == title &&
                // SUBMITREADBACK01_REDDIT_MUTABLE_MARKS_IGNORED_V1: marks and flair not compared.
                candidate.createdUtcEpochSeconds in window
        }.getOrDefault(false)
    }.take(2).toList().singleOrNull()
}

// CROSSPOST01A1B_CREATED_WRAPPER_PROJECTION_V1
// The caller supplies only a candidate returned by the matcher above (or the legacy matcher).
internal fun RedditSubmitReconciliationCandidate.toReconciledSubmittedPost(): RedditSubmittedTextPost {
    val crosspost = kind == RedditSubmitReconciliationKind.CROSSPOST
    return RedditSubmittedTextPost(
        fullname = fullname,
        id = id,
        community = community,
        title = title,
        // Source bodies/URLs/media are not new content owned by the created crosspost.
        text = if (crosspost) "" else text,
        permalink = if (crosspost) "/r/$community/comments/$id/" else permalink,
        verifiedCrosspostFullname = if (crosspost) crosspostFullname else null,
    )
}
