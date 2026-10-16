package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

// MODERATION02A5A6_TYPED_MODERATOR_PERMISSION_UPDATE_V1
data class RedditModeratorPermissionUpdateRequest(
    val accountStableId: String,
    val communityName: String,
    val username: String,
    val allPermissions: Boolean = false,
    val permissions: Set<RedditModeratorPermission> = emptySet(),
) {
    init {
        require(accountStableId.isNotBlank()) { "Moderator permission account ID cannot be blank" }
        require(normalizeRedditCommunityName(communityName) == communityName) {
            "Moderator permission community must be canonical"
        }
        require(normalizeRedditSafetyUsername(username) == username) {
            "Moderator permission target must be a canonical Reddit username"
        }
        require(allPermissions || permissions.isNotEmpty()) {
            "Choose at least one moderator permission"
        }
        require(!allPermissions || permissions.isEmpty()) {
            "All permissions cannot be combined with individual permissions"
        }
    }
}

internal fun RedditModeratorPermissionUpdateRequest.normalized():
    RedditModeratorPermissionUpdateRequest =
    RedditModeratorPermissionUpdateRequest(
        accountStableId = accountStableId.trim(),
        communityName = normalizeRedditCommunityName(communityName),
        username = normalizeRedditSafetyUsername(username),
        allPermissions = allPermissions,
        permissions = permissions.toSet(),
    )

internal fun RedditModeratorPermissionUpdateRequest.wirePermissions(): String =
    if (allPermissions) {
        "+all"
    } else {
        permissions.asSequence()
            .map(RedditModeratorPermission::wireValue)
            .distinct()
            .sorted()
            .joinToString(",") { permission -> "+$permission" }
            .also { require(it.isNotEmpty()) }
    }

enum class ModeratorPermissionUpdateFailureKind {
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

// MODERATION02A5A6_PERMISSION_UPDATE_UI_STATE_V1
data class ModeratorPermissionUpdateUiState(
    val visible: Boolean = false,
    val accountStableId: String? = null,
    val communityName: String? = null,
    val targetUsername: String? = null,
    val sending: Boolean = false,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val authorizationReady: Boolean = false,
    val failureKind: ModeratorPermissionUpdateFailureKind? = null,
    val failureHttpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
    val lastUpdatedUsername: String? = null,
    val lastUpdatedPermissions: String? = null,
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
        get() = sending || authorizing

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
