package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails

// FOLLOWPOST01A1A_TYPED_NATIVE_FOLLOW_REQUEST_V1
// FOLLOWPOST01A1B_LIVE_FOLLOW_STATE_V1
enum class PostFollowPhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    FAILED,
    SUCCEEDED,
}

enum class PostFollowFailureKind {
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

data class PostFollowUiState(
    val phase: PostFollowPhase = PostFollowPhase.IDLE,
    val accountStableId: String? = null,
    val postFullname: String? = null,
    val followed: Boolean? = null,
    val failureKind: PostFollowFailureKind? = null,
    val retryable: Boolean = false,
    val statusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
) {
    val inProgress: Boolean
        get() = phase in setOf(
            PostFollowPhase.AUTHORIZING,
            PostFollowPhase.AWAITING_CALLBACK,
            PostFollowPhase.SENDING,
        )
}

data class PostFollowRequest(
    val accountStableId: String,
    val postFullname: String,
    val followed: Boolean,
) {
    init {
        require(accountStableId.isNotBlank()) {
            "Post-follow account ID cannot be blank"
        }
        require(postFullname.isNotBlank()) {
            "Post-follow fullname cannot be blank"
        }
    }
}

internal fun PostFollowRequest.normalizedPostFollowRequest(): PostFollowRequest =
    PostFollowRequest(
        accountStableId = accountStableId.trim(),
        postFullname = normalizePostFollowFullname(postFullname),
        followed = followed,
    )

// Post following accepts links only. Reuse the established thing-ID normalization,
// then fail closed unless the canonical target is specifically a t3 post.
internal fun normalizePostFollowFullname(value: String): String =
    normalizePostVoteFullname(value).also { normalized ->
        require(normalized.startsWith("t3_")) {
            "Post-follow target must be a post fullname"
        }
    }
