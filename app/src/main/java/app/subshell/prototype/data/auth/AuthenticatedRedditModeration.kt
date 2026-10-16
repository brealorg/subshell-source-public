package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.Post

// MOD01A1_AUTHENTICATED_MODERATION_DOMAIN_V1
internal enum class AuthenticatedRedditModerationFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

internal sealed interface AuthenticatedRedditModeratedCommunitiesResult {
    data class Loaded(
        val communityNames: Set<String>,
    ) : AuthenticatedRedditModeratedCommunitiesResult

    data class Failed(
        val kind: AuthenticatedRedditModerationFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditModeratedCommunitiesResult
}

// MOD01A22_AUTHENTICATED_MODERATION_EXACT_TARGET_V1
internal sealed interface AuthenticatedRedditModerationTargetResult {
    data class Loaded(
        val post: Post,
    ) : AuthenticatedRedditModerationTargetResult

    data object NotFound : AuthenticatedRedditModerationTargetResult

    data class Failed(
        val kind: AuthenticatedRedditModerationFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditModerationTargetResult
}

internal sealed interface AuthenticatedRedditModQueueResult {
    data class Loaded(
        val communityName: String,
        val items: List<RedditModeratorQueueItem>,
        val nextCursor: String?,
    ) : AuthenticatedRedditModQueueResult

    data class Failed(
        val communityName: String,
        val kind: AuthenticatedRedditModerationFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditModQueueResult
}

// MODERATION02A2_AUTHENTICATED_REPORT_READ_RESULT_V1
internal sealed interface AuthenticatedRedditReportTargetResult {
    data class Loaded(val item: RedditModeratorQueueItem) : AuthenticatedRedditReportTargetResult
    data object NotFound : AuthenticatedRedditReportTargetResult
    data class Failed(
        val kind: AuthenticatedRedditModerationFailureKind,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditReportTargetResult
}

internal interface AuthenticatedRedditModerationLoader {
    suspend fun loadReportTarget(
        accountStableId: String,
        target: ModeratorReportTarget,
    ): AuthenticatedRedditReportTargetResult = AuthenticatedRedditReportTargetResult.Failed(
        AuthenticatedRedditModerationFailureKind.CLIENT_CONFIGURATION,
    )

    suspend fun loadModeratorCommunities(
        accountStableId: String,
    ): AuthenticatedRedditModeratedCommunitiesResult

    suspend fun loadModQueue(
        accountStableId: String,
        rawCommunityName: String,
        after: String? = null,
    ): AuthenticatedRedditModQueueResult

    suspend fun resolveTargetPost(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditModerationTargetResult
}
