package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.error.FailureDetails

// MODERATION02A5A5_AUTHENTICATED_INVITE_BOUNDARY_V1
internal sealed interface AuthenticatedRedditModeratorInviteResult {
    data object Acknowledged : AuthenticatedRedditModeratorInviteResult
    data object AuthorizationRequired : AuthenticatedRedditModeratorInviteResult
    data class Failed(
        val kind: ModeratorInviteFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedRedditModeratorInviteResult
}

internal interface AuthenticatedRedditModeratorInviteWriter {
    suspend fun invite(
        request: RedditModeratorInviteRequest,
    ): AuthenticatedRedditModeratorInviteResult
}
