package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

// MODERATION02A5A1_BANNED_USER_DOMAIN_V1
data class RedditBannedUser(
    val username: String,
    val accountId: String? = null,
    val relationshipId: String? = null,
    val avatarImageUri: String? = null,
    val note: String? = null,
    val createdEpochSeconds: Long? = null,
)

// MODERATION02A5A2_MUTED_USER_DOMAIN_V1
data class RedditMutedUser(
    val username: String,
    val accountId: String? = null,
    val relationshipId: String? = null,
    val avatarImageUri: String? = null,
    val note: String? = null,
    val createdEpochSeconds: Long? = null,
)

// MODERATION02A5A3_APPROVED_USER_DOMAIN_V1
data class RedditApprovedUser(
    val username: String,
    val accountId: String? = null,
    val relationshipId: String? = null,
    val avatarImageUri: String? = null,
    val createdEpochSeconds: Long? = null,
)

data class RedditModeratorUserBanRequest(
    val accountStableId: String,
    val communityName: String,
    val username: String,
    val banned: Boolean,
    val durationDays: Int? = null,
    val banReason: String? = null,
    val moderatorNote: String? = null,
    val messageToUser: String? = null,
) {
    init {
        require(accountStableId.isNotBlank()) { "Moderator user action account ID cannot be blank" }
        require(normalizeRedditCommunityName(communityName) == communityName) {
            "Moderator user action community must be canonical"
        }
        require(normalizeRedditSafetyUsername(username) == username) {
            "Moderator user action target must be a canonical Reddit username"
        }
        if (banned) {
            require(durationDays == null || durationDays in 1..999) {
                "Temporary ban duration must be in 1..999 days"
            }
            validateOptionalBanField(banReason, MODERATOR_BAN_REASON_MAX_CHARS)
            validateOptionalBanField(moderatorNote, MODERATOR_BAN_NOTE_MAX_CHARS)
            validateOptionalBanField(messageToUser, MODERATOR_BAN_MESSAGE_MAX_CHARS)
        } else {
            require(durationDays == null)
            require(banReason == null)
            require(moderatorNote == null)
            require(messageToUser == null)
        }
    }
}

internal fun RedditModeratorUserBanRequest.normalized(): RedditModeratorUserBanRequest =
    RedditModeratorUserBanRequest(
        accountStableId = accountStableId.trim(),
        communityName = normalizeRedditCommunityName(communityName),
        username = normalizeRedditSafetyUsername(username),
        banned = banned,
        durationDays = durationDays,
        banReason = normalizeBanField(banReason, MODERATOR_BAN_REASON_MAX_CHARS),
        moderatorNote = normalizeBanField(moderatorNote, MODERATOR_BAN_NOTE_MAX_CHARS),
        messageToUser = normalizeBanField(messageToUser, MODERATOR_BAN_MESSAGE_MAX_CHARS),
    )

data class RedditModeratorUserMuteRequest(
    val accountStableId: String,
    val communityName: String,
    val username: String,
    val muted: Boolean,
    val moderatorNote: String? = null,
) {
    init {
        require(accountStableId.isNotBlank()) { "Moderator mute action account ID cannot be blank" }
        require(normalizeRedditCommunityName(communityName) == communityName) {
            "Moderator mute action community must be canonical"
        }
        require(normalizeRedditSafetyUsername(username) == username) {
            "Moderator mute action target must be a canonical Reddit username"
        }
        if (muted) {
            validateOptionalBanField(moderatorNote, MODERATOR_MUTE_NOTE_MAX_CHARS)
        } else {
            require(moderatorNote == null)
        }
    }
}

internal fun RedditModeratorUserMuteRequest.normalized(): RedditModeratorUserMuteRequest =
    RedditModeratorUserMuteRequest(
        accountStableId = accountStableId.trim(),
        communityName = normalizeRedditCommunityName(communityName),
        username = normalizeRedditSafetyUsername(username),
        muted = muted,
        moderatorNote = normalizeBanField(moderatorNote, MODERATOR_MUTE_NOTE_MAX_CHARS),
    )

data class RedditModeratorUserApprovalRequest(
    val accountStableId: String,
    val communityName: String,
    val username: String,
    val approved: Boolean,
) {
    init {
        require(accountStableId.isNotBlank()) {
            "Moderator approved-user action account ID cannot be blank"
        }
        require(normalizeRedditCommunityName(communityName) == communityName) {
            "Moderator approved-user action community must be canonical"
        }
        require(normalizeRedditSafetyUsername(username) == username) {
            "Moderator approved-user action target must be a canonical Reddit username"
        }
    }
}

internal fun RedditModeratorUserApprovalRequest.normalized(): RedditModeratorUserApprovalRequest =
    RedditModeratorUserApprovalRequest(
        accountStableId = accountStableId.trim(),
        communityName = normalizeRedditCommunityName(communityName),
        username = normalizeRedditSafetyUsername(username),
        approved = approved,
    )

enum class ModeratorUserManagementFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    VALIDATION,
    OUTCOME_UNKNOWN,
    ALREADY_MUTATING,
}

enum class ModeratorUserManagementFailureSection { BANNED, MUTED, APPROVED }

data class ModeratorUserManagementUiState(
    val visible: Boolean = false,
    val accountStableId: String? = null,
    val communityName: String? = null,
    val bannedUsers: List<RedditBannedUser> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val nextCursor: String? = null,
    val mutedUsers: List<RedditMutedUser> = emptyList(),
    val mutedLoading: Boolean = false,
    val mutedLoaded: Boolean = false,
    val mutedNextCursor: String? = null,
    val approvedUsers: List<RedditApprovedUser> = emptyList(),
    val approvedLoading: Boolean = false,
    val approvedLoaded: Boolean = false,
    val approvedNextCursor: String? = null,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val mutatingUsername: String? = null,
    val verifiedUsername: String? = null,
    val verifiedBanned: Boolean? = null,
    val verifiedMutedUsername: String? = null,
    val verifiedMuted: Boolean? = null,
    val verifiedApprovedUsername: String? = null,
    val verifiedApproved: Boolean? = null,
    val failureKind: ModeratorUserManagementFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureHttpStatusCode: Int? = null,
    // ISSUE87_FAILURE_KEPT_PER_LIST_V1: the list the failure belongs to, or
    // null for a failure of no list (session, configuration).
    val failureSection: ModeratorUserManagementFailureSection? = null,
    // LOW_NET_USER_MUTATION_KEEPS_REDDITS_ERROR_V1 (OCB-552): Reddit's code and
    // message for a refused write; set only from a write result.
    val failureDetails: FailureDetails? = null,
    val contentRevision: Long = 0L,
) {
    val mutationInProgress: Boolean get() = mutatingUsername != null

    fun matches(accountStableId: String, rawCommunityName: String): Boolean {
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }
            .getOrNull() ?: return false
        return this.accountStableId == accountStableId.trim() && communityName == community
    }
}

private fun validateOptionalBanField(value: String?, maxChars: Int) {
    value ?: return
    require(value.length <= maxChars)
}

private fun normalizeBanField(value: String?, maxChars: Int): String? =
    value?.trim()?.takeIf(String::isNotEmpty)?.also { require(it.length <= maxChars) }

internal const val MODERATOR_BAN_REASON_MAX_CHARS = 100
internal const val MODERATOR_BAN_NOTE_MAX_CHARS = 300
internal const val MODERATOR_MUTE_NOTE_MAX_CHARS = 300
internal const val MODERATOR_BAN_MESSAGE_MAX_CHARS = 1_000
