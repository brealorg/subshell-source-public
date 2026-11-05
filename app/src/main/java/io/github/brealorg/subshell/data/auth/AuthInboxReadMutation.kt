package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.model.InboxItem

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
