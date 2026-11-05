package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.account.ModeratorPermissionUpdateFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorPermissionUpdateRequest
import io.github.brealorg.subshell.data.error.FailureDetails

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
