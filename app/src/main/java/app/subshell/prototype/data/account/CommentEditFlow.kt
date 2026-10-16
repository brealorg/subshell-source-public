package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.error.FailureDetails

enum class CommentEditPhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    // H1_COMMENT_EDIT_CHECKING_V1: reading the comment back after an unknown outcome.
    CHECKING,
    FAILED,
    SUCCEEDED,
}

enum class CommentEditFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    NOT_OWNER,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    RATE_LIMIT,
    VALIDATION,
    API,
    HTTP,
    TRANSPORT,
    ALREADY_EDITING,
    OUTCOME_UNKNOWN,
    NOT_SAVED,
}

data class CommentEditRequest(
    val accountStableId: String,
    val postId: String,
    val commentFullname: String,
    val commentAuthor: String,
    val text: String,
    val sort: RedditCommentSort,
) {
    init {
        require(accountStableId.isNotBlank()) { "Comment-edit account ID cannot be blank" }
        require(postId.isNotBlank()) { "Comment-edit post ID cannot be blank" }
        require(commentAuthor.isNotBlank()) { "Comment-edit author cannot be blank" }
        require(normalizeCommentEditFullname(commentFullname) == commentFullname) {
            "Comment-edit target must be a canonical t1 fullname"
        }
        require(text.trim().isNotEmpty()) { "Edited comment text cannot be blank" }
        require(text.length <= COMMENT_EDIT_TEXT_MAX_CHARS) { "Edited comment text is too long" }
    }
}

data class CommentEditUiState(
    val phase: CommentEditPhase = CommentEditPhase.IDLE,
    val accountStableId: String? = null,
    val postId: String? = null,
    val commentFullname: String? = null,
    val failureKind: CommentEditFailureKind? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
) {
    val inProgress: Boolean
        get() = phase in setOf(
            CommentEditPhase.AUTHORIZING,
            CommentEditPhase.AWAITING_CALLBACK,
            CommentEditPhase.SENDING,
            CommentEditPhase.CHECKING,
        )

    fun matches(
        accountStableId: String,
        commentFullname: String,
    ): Boolean = this.accountStableId == accountStableId &&
        // ACCOUNT03_MATCHES_NEVER_THROW_V1: an id the normalizer refuses matches nothing.
        runCatching { normalizeCommentEditFullname(commentFullname) }.getOrNull()
            ?.let { normalized -> this.commentFullname == normalized } == true
}

internal fun normalizeCommentEditFullname(value: String): String {
    val normalized = value.trim().lowercase()
    val candidate = if (normalized.startsWith(COMMENT_EDIT_FULLNAME_PREFIX)) {
        normalized
    } else {
        "$COMMENT_EDIT_FULLNAME_PREFIX$normalized"
    }
    val identifier = candidate.removePrefix(COMMENT_EDIT_FULLNAME_PREFIX)
    require(identifier.length in 1..COMMENT_EDIT_THING_ID_MAX_CHARS) {
        "Comment-edit ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Comment-edit ID contains unsupported characters"
    }
    return candidate
}

internal const val COMMENT_EDIT_TEXT_MAX_CHARS = 10_000
private const val COMMENT_EDIT_THING_ID_MAX_CHARS = 16
private const val COMMENT_EDIT_FULLNAME_PREFIX = "t1_"
