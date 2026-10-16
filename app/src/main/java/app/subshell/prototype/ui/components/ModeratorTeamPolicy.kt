package app.subshell.prototype.ui.components

import app.subshell.prototype.data.account.RedditCommunityInfoSection
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditModeratorPermission
import app.subshell.prototype.model.CommunityModerator

// MODERATION02A5A4_REPORTED_PERMISSIONS_ONLY_V1
internal enum class ModeratorTeamNotice {
    ACCESS_UNAVAILABLE,
    MODERATORS_UNAVAILABLE,
    REFRESH_FAILED,
}

internal data class ModeratorTeamProjection(
    val moderators: List<CommunityModerator> = emptyList(),
    val loading: Boolean = false,
    val ready: Boolean = false,
    val notice: ModeratorTeamNotice? = null,
)

internal fun moderatorTeamProjection(
    uiState: RedditCommunityInfoUiState,
    accountStableId: String,
    communityName: String,
    canRead: Boolean,
): ModeratorTeamProjection {
    if (!canRead) {
        return ModeratorTeamProjection(notice = ModeratorTeamNotice.ACCESS_UNAVAILABLE)
    }
    if (!uiState.matches(accountStableId, communityName)) {
        // Never project another account's or community's cached moderators.
        return ModeratorTeamProjection(loading = true)
    }
    val snapshot = uiState.snapshot?.takeIf {
        it.communityName.equals(communityName, ignoreCase = true)
    }
    val unavailable = RedditCommunityInfoSection.MODERATORS in uiState.unavailableSections
    if (unavailable) {
        return ModeratorTeamProjection(
            loading = uiState.loading,
            notice = ModeratorTeamNotice.MODERATORS_UNAVAILABLE,
        )
    }
    if (snapshot == null) {
        return ModeratorTeamProjection(
            loading = uiState.loading || (!uiState.loaded && uiState.failureKind == null),
            notice = if (uiState.failureKind != null || uiState.loaded) {
                ModeratorTeamNotice.MODERATORS_UNAVAILABLE
            } else null,
        )
    }
    return ModeratorTeamProjection(
        moderators = snapshot.moderators,
        loading = uiState.loading,
        ready = true,
        notice = if (uiState.failureKind != null) {
            ModeratorTeamNotice.REFRESH_FAILED
        } else null,
    )
}

internal fun moderatorTeamFilteredRows(
    moderators: List<CommunityModerator>,
    query: String,
): List<CommunityModerator> {
    val term = query.trim().removePrefix("u/").removePrefix("U/")
    return if (term.isEmpty()) moderators else moderators.filter {
        it.username.contains(term, ignoreCase = true)
    }
}

internal fun moderatorTeamIsCurrentUser(username: String, activeUsername: String): Boolean =
    username.equals(
        activeUsername.trim().removePrefix("u/").removePrefix("U/"),
        ignoreCase = true,
    )

internal fun moderatorTeamPermissionLabels(permissions: List<String>): List<String> =
    permissions.map { it.trim() }.filter(String::isNotEmpty).distinct().map { permission ->
        when (permission) {
            "all" -> "All permissions"
            "access" -> "Access"
            "config" -> "Configuration"
            "flair" -> "Flair"
            "mail" -> "Modmail"
            "posts" -> "Posts"
            "wiki" -> "Wiki"
            // Unknown server codes stay visible; they do not grant inferred powers.
            else -> permission
        }
    }

// MODERATION02A5A5_EXPLICIT_FULL_PERMISSION_INVITE_GATE_V1
internal fun moderatorTeamCanInvite(
    moderators: List<CommunityModerator>,
    activeUsername: String,
): Boolean {
    val actor = activeUsername.trim().removePrefix("u/").removePrefix("U/")
    if (actor.isEmpty()) return false
    return moderators.firstOrNull { moderator ->
        moderator.username.equals(actor, ignoreCase = true)
    }?.permissions?.any { permission ->
        permission.trim().equals("all", ignoreCase = true)
    } == true
}

// MODERATION02A5A6_SAFE_PERMISSION_EDITOR_GATE_V1
internal fun moderatorTeamPermissionSetIsEditable(permissions: List<String>): Boolean {
    val normalized = permissions
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::lowercase)
        .distinct()
    if (normalized.isEmpty()) return false
    val supported = RedditModeratorPermission.entries
        .mapTo(mutableSetOf("all")) { it.wireValue }
    return normalized.all { it in supported }
}

internal fun moderatorTeamCanEditPermissions(
    moderators: List<CommunityModerator>,
    activeUsername: String,
    target: CommunityModerator,
): Boolean {
    if (moderatorTeamIsCurrentUser(target.username, activeUsername)) return false
    if (!moderatorTeamCanInvite(moderators, activeUsername)) return false
    return moderatorTeamPermissionSetIsEditable(target.permissions)
}

// MODERATION02A5A7_SAFE_MODERATOR_REMOVAL_GATE_V1
internal fun moderatorTeamCanRemove(
    moderators: List<CommunityModerator>,
    activeUsername: String,
    target: CommunityModerator,
): Boolean {
    if (moderatorTeamIsCurrentUser(target.username, activeUsername)) return false
    return moderatorTeamCanInvite(moderators, activeUsername)
}
