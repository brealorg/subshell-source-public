package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails

enum class PostVotePhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    FAILED,
    SUCCEEDED,
}

enum class PostVoteFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    VALIDATION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

data class PostVoteRequest(
    val accountStableId: String,
    val postFullname: String,
    val direction: Int,
) {
    init {
        require(accountStableId.isNotBlank()) {
            "Post-vote account ID cannot be blank"
        }
        require(normalizePostVoteFullname(postFullname) == postFullname) {
            "Vote target must be a canonical t1 or t3 fullname"
        }
        require(direction in POST_VOTE_DIRECTIONS) {
            "Post-vote direction must be -1, 0, or 1"
        }
    }
}

data class PostVoteUiState(
    val phase: PostVotePhase = PostVotePhase.IDLE,
    val accountStableId: String? = null,
    val postFullname: String? = null,
    val direction: Int? = null,
    val failureKind: PostVoteFailureKind? = null,
    val retryable: Boolean = false,
    val statusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
    // VOTE02_SETTLEMENTS_SURVIVE_CONFLATION_V1 (#60): the newest settled votes
    // of every target ride along with each state, so a reader that sees only
    // the latest value (collectAsState) still learns every earlier outcome.
    val settlements: List<PostVoteSettlement> = emptyList(),
) {
    val inProgress: Boolean
        get() = phase in setOf(
            PostVotePhase.AUTHORIZING,
            PostVotePhase.AWAITING_CALLBACK,
            PostVotePhase.SENDING,
        )
}

/** One vote that reached its end: kept by Reddit, or failed/refused. */
data class PostVoteSettlement(
    val sequence: Long,
    val accountStableId: String,
    val postFullname: String,
    val direction: Int,
    val succeeded: Boolean,
)

internal const val POST_VOTE_SETTLEMENTS_KEPT = 64

internal fun PostVoteRequest.normalizedPostVoteRequest(): PostVoteRequest =
    PostVoteRequest(
        accountStableId = accountStableId.trim(),
        postFullname = normalizePostVoteFullname(postFullname),
        direction = normalizePostVoteDirection(direction),
    )

internal fun normalizePostVoteFullname(value: String): String {
    val normalized = value.trim().lowercase()
    val candidate = when {
        normalized.startsWith(COMMENT_VOTE_FULLNAME_PREFIX) -> normalized
        normalized.startsWith(POST_VOTE_FULLNAME_PREFIX) -> normalized
        else -> "$POST_VOTE_FULLNAME_PREFIX$normalized"
    }
    val prefix = if (candidate.startsWith(COMMENT_VOTE_FULLNAME_PREFIX)) {
        COMMENT_VOTE_FULLNAME_PREFIX
    } else {
        POST_VOTE_FULLNAME_PREFIX
    }
    val identifier = candidate.removePrefix(prefix)
    require(identifier.length in 1..POST_VOTE_THING_ID_MAX_CHARS) {
        "Vote ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Vote ID contains unsupported characters"
    }
    return candidate
}

internal fun normalizeCommentVoteFullname(value: String): String {
    val normalized = value.trim().lowercase()
    val candidate = if (normalized.startsWith(COMMENT_VOTE_FULLNAME_PREFIX)) {
        normalized
    } else {
        "$COMMENT_VOTE_FULLNAME_PREFIX$normalized"
    }
    require(normalizePostVoteFullname(candidate) == candidate) {
        "Comment vote target must be a canonical t1 fullname"
    }
    return candidate
}

internal fun normalizePostVoteDirection(direction: Int): Int {
    require(direction in POST_VOTE_DIRECTIONS) {
        "Post-vote direction must be -1, 0, or 1"
    }
    return direction
}

private val POST_VOTE_DIRECTIONS = setOf(-1, 0, 1)
private const val POST_VOTE_THING_ID_MAX_CHARS = 16
private const val POST_VOTE_FULLNAME_PREFIX = "t3_"
private const val COMMENT_VOTE_FULLNAME_PREFIX = "t1_"
