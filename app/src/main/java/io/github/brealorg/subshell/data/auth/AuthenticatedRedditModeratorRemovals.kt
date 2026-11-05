package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.account.ModeratorRemovalFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorRemovalRequest
import io.github.brealorg.subshell.data.error.FailureDetails

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
