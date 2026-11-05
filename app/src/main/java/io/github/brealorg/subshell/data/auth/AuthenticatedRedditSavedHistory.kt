package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.network.reddit.RedditSavedHistoryItem

internal enum class AuthenticatedRedditSavedHistoryFailureKind {
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

internal data class AuthenticatedRedditSavedHistorySnapshot(
    val items: List<RedditSavedHistoryItem>,
    val nextCursor: String?,
)

internal sealed interface AuthenticatedRedditSavedHistoryResult {
    data class Loaded(
        val snapshot: AuthenticatedRedditSavedHistorySnapshot,
        val fromCache: Boolean,
    ) : AuthenticatedRedditSavedHistoryResult

    data object AuthorizationRequired : AuthenticatedRedditSavedHistoryResult

    data object NotCached : AuthenticatedRedditSavedHistoryResult

    data class Failed(
        val kind: AuthenticatedRedditSavedHistoryFailureKind,
        val retryable: Boolean = false,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditSavedHistoryResult
}

internal interface AuthenticatedRedditSavedHistoryLoader {
    suspend fun cached(
        accountStableId: String,
        username: String,
    ): AuthenticatedRedditSavedHistoryResult

    suspend fun synchronize(
        accountStableId: String,
        username: String,
    ): AuthenticatedRedditSavedHistoryResult

    suspend fun append(
        accountStableId: String,
        username: String,
        after: String,
    ): AuthenticatedRedditSavedHistoryResult
}
