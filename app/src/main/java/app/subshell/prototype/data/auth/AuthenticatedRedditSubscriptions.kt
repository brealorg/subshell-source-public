package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.Community

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
