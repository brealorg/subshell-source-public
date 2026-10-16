package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.InboxItem

internal sealed interface AuthInboxReadMutationResult {
    data class Applied(
        val snapshot: AuthenticatedRedditInboxSnapshot,
    ) : AuthInboxReadMutationResult

    data object AuthorizationRequired : AuthInboxReadMutationResult

    data class Failed(
        val kind: AuthenticatedRedditInboxFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
    ) : AuthInboxReadMutationResult
}

internal interface AuthInboxReadMutationWriter {
    suspend fun markRead(
        accountStableId: String,
        item: InboxItem,
    ): AuthInboxReadMutationResult
}
