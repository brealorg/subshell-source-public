package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

// MODERATION02A5A7_TYPED_MODERATOR_REMOVAL_V1
data class RedditModeratorRemovalRequest(
    val accountStableId: String,
    val communityName: String,
    val username: String,
) {
    init {
        require(accountStableId.isNotBlank()) { "Moderator removal account ID cannot be blank" }
        require(normalizeRedditCommunityName(communityName) == communityName) {
            "Moderator removal community must be canonical"
        }
        require(normalizeRedditSafetyUsername(username) == username) {
            "Moderator removal target must be a canonical Reddit username"
        }
    }
}

internal fun RedditModeratorRemovalRequest.normalized(): RedditModeratorRemovalRequest =
    RedditModeratorRemovalRequest(
        accountStableId = accountStableId.trim(),
        communityName = normalizeRedditCommunityName(communityName),
        username = normalizeRedditSafetyUsername(username),
    )

enum class ModeratorRemovalFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    AUTHORIZATION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    VALIDATION,
    OUTCOME_UNKNOWN,
    ALREADY_MUTATING,
    ACCOUNT_CHANGED,
}

// MODERATION02A5A7_MODERATOR_REMOVAL_UI_STATE_V1
data class ModeratorRemovalUiState(
    val visible: Boolean = false,
    val accountStableId: String? = null,
    val communityName: String? = null,
    val targetUsername: String? = null,
    val removing: Boolean = false,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val authorizationReady: Boolean = false,
    val failureKind: ModeratorRemovalFailureKind? = null,
    val failureHttpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
    val lastRemovedUsername: String? = null,
) {
    init {
        require((accountStableId == null) == (communityName == null))
        require((accountStableId == null) == (targetUsername == null))
        require(accountStableId == null || accountStableId.isNotBlank())
        require(communityName == null || communityName.isNotBlank())
        require(targetUsername == null || targetUsername.isNotBlank())
        require(failureHttpStatusCode == null || failureHttpStatusCode in 100..599)
        require(completionRevision >= 0L)
    }

    val busy: Boolean
        get() = removing || authorizing

    fun matches(
        accountStableId: String,
        rawCommunityName: String,
        rawUsername: String? = null,
    ): Boolean {
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }
            .getOrNull() ?: return false
        if (this.accountStableId != accountStableId.trim() || communityName != community) {
            return false
        }
        val username = rawUsername ?: return true
        val normalizedUsername = runCatching { normalizeRedditSafetyUsername(username) }
            .getOrNull() ?: return false
        return targetUsername.equals(normalizedUsername, ignoreCase = true)
    }
}
