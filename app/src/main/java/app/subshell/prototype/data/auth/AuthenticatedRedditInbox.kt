package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post

internal enum class AuthenticatedRedditInboxFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
}

internal data class AuthenticatedRedditInboxSnapshot(
    val items: List<InboxItem>,
    val nextCursor: String?,
    val locallySeenIds: Set<String>,
)

internal sealed interface AuthenticatedRedditInboxResult {
    data class Loaded(
        val snapshot: AuthenticatedRedditInboxSnapshot,
        val fromCache: Boolean,
    ) : AuthenticatedRedditInboxResult

    data object AuthorizationRequired : AuthenticatedRedditInboxResult

    data object NotCached : AuthenticatedRedditInboxResult

    data class Failed(
        val kind: AuthenticatedRedditInboxFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditInboxResult
}

internal sealed interface AuthenticatedRedditInboxTargetResult {
    data class Loaded(
        val post: Post,
        val fromCache: Boolean,
    ) : AuthenticatedRedditInboxTargetResult

    data object AuthorizationRequired : AuthenticatedRedditInboxTargetResult

    data object NotFound : AuthenticatedRedditInboxTargetResult

    data class Failed(
        val kind: AuthenticatedRedditInboxFailureKind,
        val retryable: Boolean,
        // DIAG05_TARGET_LOOKUP_FAILURE_IS_RECORDED_V1
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditInboxTargetResult
}

internal interface AuthenticatedRedditInboxLoader {
    suspend fun cached(accountStableId: String): AuthenticatedRedditInboxResult

    suspend fun synchronize(accountStableId: String): AuthenticatedRedditInboxResult

    suspend fun append(accountStableId: String, after: String): AuthenticatedRedditInboxResult

    suspend fun resolveTargetPost(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditInboxTargetResult
}
