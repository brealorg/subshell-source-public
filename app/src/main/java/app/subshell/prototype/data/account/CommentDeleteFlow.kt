package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.error.FailureDetails

enum class CommentDeletePhase { IDLE, AUTHORIZATION_REQUIRED, AUTHORIZING, AWAITING_CALLBACK, SENDING, FAILED, SUCCEEDED }
enum class CommentDeleteFailureKind { CONFIGURATION, ACCOUNT_CHANGED, NOT_OWNER, BROWSER_LAUNCH, CALLBACK, AUTHORIZATION, SESSION, RATE_LIMIT, HTTP, TRANSPORT, PROTOCOL, OUTCOME_NOT_CONFIRMED, ALREADY_DELETING }

data class CommentDeleteRequest(
    val accountStableId: String,
    val postId: String,
    val commentFullname: String,
    val commentAuthor: String,
    val sort: RedditCommentSort,
) {
    init {
        require(accountStableId.isNotBlank()) { "Comment-delete account ID cannot be blank" }
        require(postId.isNotBlank()) { "Comment-delete post ID cannot be blank" }
        require(commentAuthor.isNotBlank()) { "Comment-delete author cannot be blank" }
        require(normalizeCommentEditFullname(commentFullname) == commentFullname) { "Comment-delete target must be a canonical t1 fullname" }
    }
}

data class CommentDeleteUiState(
    val phase: CommentDeletePhase = CommentDeletePhase.IDLE,
    val accountStableId: String? = null,
    val postId: String? = null,
    val commentFullname: String? = null,
    val failureKind: CommentDeleteFailureKind? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val httpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
) {
    val inProgress: Boolean
        get() = phase in setOf(CommentDeletePhase.AUTHORIZING, CommentDeletePhase.AWAITING_CALLBACK, CommentDeletePhase.SENDING)

    // ACCOUNT03_MATCHES_NEVER_THROW_V1: an id the normalizer refuses matches nothing.
    fun matches(accountStableId: String, commentFullname: String): Boolean =
        this.accountStableId == accountStableId &&
            runCatching { normalizeCommentEditFullname(commentFullname) }.getOrNull()
                ?.let { normalized -> this.commentFullname == normalized } == true
}
