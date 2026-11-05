package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.model.Community

internal enum class AuthenticatedRedditSubscriptionsFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

internal sealed interface AuthenticatedRedditSubscriptionsResult {
    data class Loaded(
        val communities: List<Community>,
        val fromCache: Boolean,
        val favoriteCommunityNames: Set<String> = emptySet(),
    ) : AuthenticatedRedditSubscriptionsResult

    data object NotCached : AuthenticatedRedditSubscriptionsResult

    data class Failed(
        val kind: AuthenticatedRedditSubscriptionsFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditSubscriptionsResult
}

internal interface AuthenticatedRedditSubscriptionsLoader {
    suspend fun cached(accountStableId: String): AuthenticatedRedditSubscriptionsResult

    suspend fun synchronize(accountStableId: String): AuthenticatedRedditSubscriptionsResult
}
