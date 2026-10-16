package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.Comment

enum class CommentWritePhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    FAILED,
    OUTCOME_UNKNOWN,
    SUCCEEDED,
}

enum class CommentWriteFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    RATE_LIMIT,
    PARENT_UNAVAILABLE,
    PERMISSION,
    VALIDATION,
    API,
    HTTP,
    TRANSPORT,
    ALREADY_SUBMITTING,
    // COMPOSER01_COMMENT_OUTCOME_READ_BACK_V1: read back after a lost
    // response; the thread holds no such comment, so sending again is safe.
    NOT_POSTED,
    // COMPOSER09_COMMENT_MEDIA_NOT_ALLOWED_V1: Reddit refused the attached
    // image/GIF because the community does not allow it in comments.
    MEDIA_NOT_ALLOWED,
    // LOW2_COMMENT_MEDIA_STAGING_FAILURES_NAMED_V1 (OCB-494)
    MEDIA_TOO_LARGE,
    MEDIA_UNSUPPORTED,
    MEDIA_UNAVAILABLE,
}

data class CommentWriteMediaAttachment(
    val uri: String,
    val mimeType: String,
    val sourceLinkUri: String? = null,
)

data class CommentWriteRequest(
    val accountStableId: String,
    val postId: String,
    val targetStableKey: String,
    val parentFullname: String,
    val text: String,
    val sort: RedditCommentSort,
    val media: CommentWriteMediaAttachment? = null,
) {
    init {
        require(accountStableId.isNotBlank()) { "Comment-write account ID cannot be blank" }
        require(postId.isNotBlank()) { "Comment-write post ID cannot be blank" }
        require(targetStableKey.isNotBlank()) { "Comment-write target key cannot be blank" }
        require(targetStableKey.length <= MAX_TARGET_KEY_CHARS) {
            "Comment-write target key is too long"
        }
        require(text.trim().isNotEmpty() || media != null) {
            "Comment must contain text or image/GIF media"
        }
        require(text.length <= MAX_COMMENT_TEXT_CHARS) { "Comment text is too long" }
        media?.let { attachment ->
            require(attachment.uri.isNotBlank()) { "Comment media URI cannot be blank" }
            require(
                attachment.mimeType.trim().lowercase() in SUPPORTED_COMMENT_MEDIA_MIME_TYPES,
            ) {
                "Comment media must be a JPEG, PNG, WebP, or GIF"
            }
        }
        require(isCanonicalCommentParentFullname(parentFullname)) {
            "Comment parent must be a canonical t1 or t3 fullname"
        }
    }
}

data class CommentWriteUiState(
    val phase: CommentWritePhase = CommentWritePhase.IDLE,
    val accountStableId: String? = null,
    val postId: String? = null,
    val targetStableKey: String? = null,
    val failureKind: CommentWriteFailureKind? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val failureDetails: FailureDetails? = null,
    val submittedComment: Comment? = null,
    val threadUpdated: Boolean = false,
    val completionRevision: Long = 0L,
) {
    init {
        require(completionRevision >= 0L) {
            "Comment-write completion revision cannot be negative"
        }
        require(retryAfterMs == null || retryAfterMs >= 0L) {
            "Comment-write retry delay cannot be negative"
        }
        val identities = listOf(accountStableId, postId, targetStableKey)
        require(identities.all { it == null } || identities.all { !it.isNullOrBlank() }) {
            "Comment-write identities must be absent or present together"
        }
        require(
            phase == CommentWritePhase.SUCCEEDED || submittedComment == null,
        ) {
            "Only a successful comment-write state may expose a submitted comment"
        }
        require(
            phase in setOf(
                CommentWritePhase.FAILED,
                CommentWritePhase.OUTCOME_UNKNOWN,
            ) || failureKind == null,
        ) {
            "Only failed comment-write states may expose a failure kind"
        }
    }

    val inProgress: Boolean
        get() = phase in setOf(
            CommentWritePhase.AUTHORIZING,
            CommentWritePhase.AWAITING_CALLBACK,
            CommentWritePhase.SENDING,
        )

    fun matches(
        accountStableId: String,
        targetStableKey: String,
    ): Boolean = this.accountStableId == accountStableId &&
        this.targetStableKey == targetStableKey
}

private fun isCanonicalCommentParentFullname(value: String): Boolean {
    val normalized = value.trim().lowercase()
    val prefix = when {
        normalized.startsWith(COMMENT_FULLNAME_PREFIX) -> COMMENT_FULLNAME_PREFIX
        normalized.startsWith(POST_FULLNAME_PREFIX) -> POST_FULLNAME_PREFIX
        else -> return false
    }
    val identifier = normalized.removePrefix(prefix)
    return identifier.length in 1..MAX_THING_ID_CHARS &&
        identifier.all(Char::isLetterOrDigit) &&
        normalized == value
}

private const val MAX_TARGET_KEY_CHARS = 512
private const val MAX_COMMENT_TEXT_CHARS = 10_000
private const val MAX_THING_ID_CHARS = 16
private const val COMMENT_FULLNAME_PREFIX = "t1_"
private const val POST_FULLNAME_PREFIX = "t3_"

private val SUPPORTED_COMMENT_MEDIA_MIME_TYPES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/gif",
    "image/*",
)
