package app.subshell.prototype.data.account
import app.subshell.prototype.data.error.FailureDetails

enum class PostDeletePhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    // H3_POST_DELETE_CHECKING_V1: reading the post back after an unknown outcome.
    CHECKING,
    FAILED,
    SUCCEEDED,
}

enum class PostDeleteFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    NOT_OWNER,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    RATE_LIMIT,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    ALREADY_DELETING,
    OUTCOME_UNKNOWN,
    NOT_DELETED,
}

data class PostDeleteRequest(
    val accountStableId: String,
    val postFullname: String,
    val postAuthor: String,
) {
    init {
        require(accountStableId.isNotBlank()) { "Post-delete account ID cannot be blank" }
        require(postAuthor.isNotBlank()) { "Post-delete author cannot be blank" }
        require(normalizePostEditFullname(postFullname) == postFullname) {
            "Post-delete target must be a canonical t3 fullname"
        }
    }
}

data class PostDeleteUiState(
    val phase: PostDeletePhase = PostDeletePhase.IDLE,
    val accountStableId: String? = null,
    val postFullname: String? = null,
    val failureKind: PostDeleteFailureKind? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val httpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
) {
    val inProgress: Boolean
        get() = phase in setOf(
            PostDeletePhase.AUTHORIZING,
            PostDeletePhase.AWAITING_CALLBACK,
            PostDeletePhase.SENDING,
            PostDeletePhase.CHECKING,
        )

    // ACCOUNT03_MATCHES_NEVER_THROW_V1: an id the normalizer refuses matches nothing.
    fun matches(accountStableId: String, postFullname: String): Boolean =
        this.accountStableId == accountStableId &&
            runCatching { normalizePostEditFullname(postFullname) }.getOrNull()
                ?.let { normalized -> this.postFullname == normalized } == true
}
