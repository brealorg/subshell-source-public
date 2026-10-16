package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.error.FailureDetails

// MODERATION02A5A7_AUTHENTICATED_MODERATOR_REMOVAL_BOUNDARY_V1
internal sealed interface AuthenticatedRedditModeratorRemovalResult {
    data object Acknowledged : AuthenticatedRedditModeratorRemovalResult
    data object AuthorizationRequired : AuthenticatedRedditModeratorRemovalResult
    data class Failed(
        val kind: ModeratorRemovalFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedRedditModeratorRemovalResult
}

internal interface AuthenticatedRedditModeratorRemovalWriter {
    suspend fun remove(
        request: RedditModeratorRemovalRequest,
    ): AuthenticatedRedditModeratorRemovalResult
}
