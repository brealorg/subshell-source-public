package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

data class RedditRemovalReason(
    val id: String,
    val title: String,
    val message: String,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
    }
}

enum class RedditRemovalReasonPhase {
    IDLE,
    LOADING,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    LOADED,
    FAILED,
}

enum class RedditRemovalReasonFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

// MOD01A21_REMOVAL_REASON_UI_STATE_V1
data class RedditRemovalReasonUiState(
    val phase: RedditRemovalReasonPhase = RedditRemovalReasonPhase.IDLE,
    val accountStableId: String? = null,
    val communityName: String? = null,
    val reasons: List<RedditRemovalReason> = emptyList(),
    val retryable: Boolean = false,
    val httpStatusCode: Int? = null,
    val failureKind: RedditRemovalReasonFailureKind? = null,
    val failureDetails: FailureDetails? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank())
        require(communityName == null || communityName.isNotBlank())
        require(httpStatusCode == null || httpStatusCode in 100..599)
        require(contentRevision >= 0L)
    }

    val loaded: Boolean
        get() = phase == RedditRemovalReasonPhase.LOADED

    val inProgress: Boolean
        get() = phase in setOf(
            RedditRemovalReasonPhase.LOADING,
            RedditRemovalReasonPhase.AUTHORIZING,
            RedditRemovalReasonPhase.AWAITING_CALLBACK,
        )

    fun matches(accountStableId: String, rawCommunityName: String): Boolean {
        val normalized = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return this.accountStableId == accountStableId && communityName == normalized
    }
}
