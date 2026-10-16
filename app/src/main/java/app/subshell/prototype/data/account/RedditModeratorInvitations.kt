package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

// MODERATION02A5A5_TYPED_MODERATOR_INVITATION_V1
enum class RedditModeratorPermission(val wireValue: String) {
    ACCESS("access"),
    CONFIG("config"),
    FLAIR("flair"),
    MAIL("mail"),
    POSTS("posts"),
    WIKI("wiki"),
}

data class RedditModeratorInviteRequest(
    val accountStableId: String,
    val communityName: String,
    val username: String,
    val allPermissions: Boolean = false,
    val permissions: Set<RedditModeratorPermission> = emptySet(),
) {
    init {
        require(accountStableId.isNotBlank()) { "Moderator invitation account ID cannot be blank" }
        require(normalizeRedditCommunityName(communityName) == communityName) {
            "Moderator invitation community must be canonical"
        }
        require(normalizeRedditSafetyUsername(username) == username) {
            "Moderator invitation target must be a canonical Reddit username"
        }
        require(allPermissions || permissions.isNotEmpty()) {
            "Choose at least one moderator permission"
        }
        require(!allPermissions || permissions.isEmpty()) {
            "All permissions cannot be combined with individual permissions"
        }
    }
}

internal fun RedditModeratorInviteRequest.normalized(): RedditModeratorInviteRequest =
    RedditModeratorInviteRequest(
        accountStableId = accountStableId.trim(),
        communityName = normalizeRedditCommunityName(communityName),
        username = normalizeRedditSafetyUsername(username),
        allPermissions = allPermissions,
        permissions = permissions.toSet(),
    )

internal fun RedditModeratorInviteRequest.wirePermissions(): String =
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

enum class ModeratorInviteFailureKind {
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

// MODERATION02A5A5_FAILURE_DETAILS_PRESENTATION_BOUNDARY_V1
data class ModeratorInviteUiState(
    val visible: Boolean = false,
    val accountStableId: String? = null,
    val communityName: String? = null,
    val sending: Boolean = false,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val authorizationReady: Boolean = false,
    val failureKind: ModeratorInviteFailureKind? = null,
    val failureHttpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
    val lastInvitedUsername: String? = null,
    val lastInvitedPermissions: String? = null,
) {
    init {
        require((accountStableId == null) == (communityName == null))
        require(accountStableId == null || accountStableId.isNotBlank())
        require(communityName == null || communityName.isNotBlank())
        require(failureHttpStatusCode == null || failureHttpStatusCode in 100..599)
        require(completionRevision >= 0L)
    }

    val busy: Boolean
        get() = sending || authorizing

    fun matches(accountStableId: String, rawCommunityName: String): Boolean {
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }
            .getOrNull() ?: return false
        return this.accountStableId == accountStableId.trim() && communityName == community
    }
}
