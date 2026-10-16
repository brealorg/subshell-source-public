package app.subshell.prototype.data.account
import app.subshell.prototype.data.error.FailureDetails

enum class PostEditPhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    // H2_POST_EDIT_CHECKING_V1: reading the post back after an unknown outcome.
    CHECKING,
    FAILED,
    SUCCEEDED,
}

enum class PostEditFailureKind {
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

data class PostEditRequest(
    val accountStableId: String,
    val postFullname: String,
    val postAuthor: String,
    val text: String,
) {
    init {
        require(accountStableId.isNotBlank()) { "Post-edit account ID cannot be blank" }
        require(postAuthor.isNotBlank()) { "Post-edit author cannot be blank" }
        require(normalizePostEditFullname(postFullname) == postFullname) {
            "Post-edit target must be a canonical t3 fullname"
        }
        require(text.length <= POST_EDIT_TEXT_MAX_CHARS) {
            "Edited post body is too long"
        }
    }
}

data class PostEditUiState(
    val phase: PostEditPhase = PostEditPhase.IDLE,
    val accountStableId: String? = null,
    val postFullname: String? = null,
    val failureKind: PostEditFailureKind? = null,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
    // POSTEDIT01_SAVED_TEXT_IS_SENT_TEXT_V1: on SUCCEEDED, the body Reddit
    // was sent, not whatever the editor holds by the time it is shown.
    val savedText: String? = null,
) {
    val inProgress: Boolean
        get() = phase in setOf(
            PostEditPhase.AUTHORIZING,
            PostEditPhase.AWAITING_CALLBACK,
            PostEditPhase.SENDING,
            PostEditPhase.CHECKING,
        )

    fun matches(
        accountStableId: String,
        postFullname: String,
    ): Boolean = this.accountStableId == accountStableId &&
        // ACCOUNT03_MATCHES_NEVER_THROW_V1: an id the normalizer refuses matches nothing.
        runCatching { normalizePostEditFullname(postFullname) }.getOrNull()
            ?.let { normalized -> this.postFullname == normalized } == true
}

internal fun normalizePostEditFullname(value: String): String {
    val normalized = value.trim().lowercase()
    val candidate = if (normalized.startsWith(POST_EDIT_FULLNAME_PREFIX)) {
        normalized
    } else {
        "$POST_EDIT_FULLNAME_PREFIX$normalized"
    }
    val identifier = candidate.removePrefix(POST_EDIT_FULLNAME_PREFIX)
    require(identifier.length in 1..POST_EDIT_THING_ID_MAX_CHARS) {
        "Post-edit ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Post-edit ID contains unsupported characters"
    }
    return candidate
}

internal const val POST_EDIT_TEXT_MAX_CHARS = 40_000
private const val POST_EDIT_THING_ID_MAX_CHARS = 16
private const val POST_EDIT_FULLNAME_PREFIX = "t3_"
