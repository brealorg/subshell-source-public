package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.account.ModeratorInviteFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorInviteRequest
import io.github.brealorg.subshell.data.error.FailureDetails

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
