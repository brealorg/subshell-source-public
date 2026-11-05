package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.account.RedditModeratorLogEntry
import io.github.brealorg.subshell.data.error.FailureDetails

// MODERATION02A4_AUTHENTICATED_MOD_LOG_DOMAIN_V1
internal sealed interface AuthenticatedRedditModerationLogResult {
    data class Loaded(
        val communityName: String,
        val entries: List<RedditModeratorLogEntry>,
        val nextCursor: String?,
    ) : AuthenticatedRedditModerationLogResult

    data object AuthorizationRequired : AuthenticatedRedditModerationLogResult

    data class Failed(
        val communityName: String,
        val kind: AuthenticatedRedditModerationFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditModerationLogResult
}

internal interface AuthenticatedRedditModerationLogLoader {
    suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String? = null,
    ): AuthenticatedRedditModerationLogResult
}
