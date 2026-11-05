package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.account.ModeratorUserManagementFailureKind
import io.github.brealorg.subshell.data.account.RedditApprovedUser
import io.github.brealorg.subshell.data.account.RedditBannedUser
import io.github.brealorg.subshell.data.account.RedditMutedUser
import io.github.brealorg.subshell.data.account.RedditModeratorUserApprovalRequest
import io.github.brealorg.subshell.data.account.RedditModeratorUserBanRequest
import io.github.brealorg.subshell.data.account.RedditModeratorUserMuteRequest
import io.github.brealorg.subshell.data.error.FailureDetails

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
