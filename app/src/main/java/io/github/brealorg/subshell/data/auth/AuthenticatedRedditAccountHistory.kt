package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.RepositoryLoadOperation
import io.github.brealorg.subshell.data.RepositoryQueryKey

internal enum class AuthenticatedRedditAccountHistoryFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
    END_REACHED,
}

internal sealed interface AuthenticatedRedditAccountHistoryUpdateResult {
    data class Applied(
        val queryKey: RepositoryQueryKey,
        val operation: RepositoryLoadOperation,
        val networkItemCount: Int,
        val storedItemCount: Int,
        val endReached: Boolean,
    ) : AuthenticatedRedditAccountHistoryUpdateResult

    data object AuthorizationRequired : AuthenticatedRedditAccountHistoryUpdateResult

    data class Failed(
        val kind: AuthenticatedRedditAccountHistoryFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditAccountHistoryUpdateResult
}

internal fun interface AuthenticatedRedditAccountHistoryUpdater {
    suspend fun synchronize(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        operation: RepositoryLoadOperation,
    ): AuthenticatedRedditAccountHistoryUpdateResult
}

internal typealias AuthenticatedRedditAccountHistoryCommentResult =
    AuthenticatedRedditProfileCommentsResult

internal interface AuthenticatedRedditAccountHistoryCommentLoader {
    suspend fun cached(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): AuthenticatedRedditAccountHistoryCommentResult

    suspend fun synchronize(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): AuthenticatedRedditAccountHistoryCommentResult

    suspend fun append(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        after: String,
    ): AuthenticatedRedditAccountHistoryCommentResult

    suspend fun resolveTargetPost(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditProfileCommentTargetResult

    /**
     * PROFILESSOT01_HISTORY_FOLLOWS_ITS_CACHE_V1 (#244): the stored comment
     * history each time it changes. Empty is a history not stored.
     */
    fun observeStoredHistory(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): kotlinx.coroutines.flow.Flow<List<io.github.brealorg.subshell.model.ProfileCommentFixture>> =
        kotlinx.coroutines.flow.emptyFlow()

    /**
     * PROFILESSOT01_HISTORY_KEEPS_EDITS_AND_DELETES_V1: a confirmed edit
     * (new row) or delete (null) of one comment, in every stored history.
     */
    suspend fun updateStoredHistoryComment(
        commentFullname: String,
        transform: (io.github.brealorg.subshell.model.ProfileCommentFixture) -> io.github.brealorg.subshell.model.ProfileCommentFixture?,
    ) = Unit
}
