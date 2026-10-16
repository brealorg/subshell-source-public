package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.error.FailureDetails

// MODERATION02A5A6_AUTHENTICATED_PERMISSION_UPDATE_BOUNDARY_V1
internal sealed interface AuthenticatedRedditModeratorPermissionUpdateResult {
    data object Acknowledged : AuthenticatedRedditModeratorPermissionUpdateResult
    data object AuthorizationRequired : AuthenticatedRedditModeratorPermissionUpdateResult
    data class Failed(
        val kind: ModeratorPermissionUpdateFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedRedditModeratorPermissionUpdateResult
}

internal interface AuthenticatedRedditModeratorPermissionWriter {
    suspend fun update(
        request: RedditModeratorPermissionUpdateRequest,
    ): AuthenticatedRedditModeratorPermissionUpdateResult
}
