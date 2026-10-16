package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditApprovedUser
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditMutedUser
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.error.FailureDetails

// MODERATION02A5A1_AUTHENTICATED_USER_MANAGEMENT_BOUNDARY_V1
internal sealed interface AuthenticatedRedditBannedUsersResult {
    data class Loaded(
        val communityName: String,
        val users: List<RedditBannedUser>,
        val nextCursor: String?,
    ) : AuthenticatedRedditBannedUsersResult

    data object AuthorizationRequired : AuthenticatedRedditBannedUsersResult

    data class Failed(
        val communityName: String,
        val kind: ModeratorUserManagementFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditBannedUsersResult
}

internal sealed interface AuthenticatedRedditMutedUsersResult {
    data class Loaded(
        val communityName: String,
        val users: List<RedditMutedUser>,
        val nextCursor: String?,
    ) : AuthenticatedRedditMutedUsersResult

    data object AuthorizationRequired : AuthenticatedRedditMutedUsersResult

    data class Failed(
        val communityName: String,
        val kind: ModeratorUserManagementFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditMutedUsersResult
}

internal sealed interface AuthenticatedRedditApprovedUsersResult {
    data class Loaded(
        val communityName: String,
        val users: List<RedditApprovedUser>,
        val nextCursor: String?,
    ) : AuthenticatedRedditApprovedUsersResult

    data object AuthorizationRequired : AuthenticatedRedditApprovedUsersResult

    data class Failed(
        val communityName: String,
        val kind: ModeratorUserManagementFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditApprovedUsersResult
}

internal sealed interface AuthenticatedRedditModeratorUserMutationResult {
    data object Acknowledged : AuthenticatedRedditModeratorUserMutationResult
    data object AuthorizationRequired : AuthenticatedRedditModeratorUserMutationResult
    data class Failed(
        val kind: ModeratorUserManagementFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditModeratorUserMutationResult
}

internal interface AuthenticatedRedditBannedUsersReader {
    suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String? = null,
        // MODUSERS01_USER_FILTER_V1: Reddit's user=<name> filter on the list.
        username: String? = null,
    ): AuthenticatedRedditBannedUsersResult
}

internal interface AuthenticatedRedditModeratorUserWriter {
    suspend fun mutate(
        request: RedditModeratorUserBanRequest,
    ): AuthenticatedRedditModeratorUserMutationResult
}

internal interface AuthenticatedRedditMutedUsersReader {
    suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String? = null,
        // MODUSERS01_USER_FILTER_V1: Reddit's user=<name> filter on the list.
        username: String? = null,
    ): AuthenticatedRedditMutedUsersResult
}

internal interface AuthenticatedRedditModeratorUserMuteWriter {
    suspend fun mutate(
        request: RedditModeratorUserMuteRequest,
    ): AuthenticatedRedditModeratorUserMutationResult
}

internal interface AuthenticatedRedditApprovedUsersReader {
    suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String? = null,
        // MODUSERS01_USER_FILTER_V1: Reddit's user=<name> filter on the list.
        username: String? = null,
    ): AuthenticatedRedditApprovedUsersResult
}

internal interface AuthenticatedRedditModeratorUserApprovalWriter {
    suspend fun mutate(
        request: RedditModeratorUserApprovalRequest,
    ): AuthenticatedRedditModeratorUserMutationResult
}
