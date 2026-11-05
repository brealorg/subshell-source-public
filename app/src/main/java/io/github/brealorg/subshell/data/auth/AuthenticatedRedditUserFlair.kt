package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.account.UserFlairFailureKind
import io.github.brealorg.subshell.data.account.UserFlairMutationRequest
import io.github.brealorg.subshell.data.account.UserFlairSnapshot

// USERFLAIR01A1_AUTHENTICATED_BOUNDARY_V1
internal sealed interface AuthenticatedRedditUserFlairLoadResult {
    data class Loaded(val snapshot: UserFlairSnapshot) : AuthenticatedRedditUserFlairLoadResult
    data object AuthorizationRequired : AuthenticatedRedditUserFlairLoadResult
    data class Failed(
        val kind: UserFlairFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        // DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditUserFlairLoadResult
}

internal sealed interface AuthenticatedRedditUserFlairMutationResult {
    data object Acknowledged : AuthenticatedRedditUserFlairMutationResult
    data object AuthorizationRequired : AuthenticatedRedditUserFlairMutationResult
    data class Failed(
        val kind: UserFlairFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditUserFlairMutationResult
}

internal interface AuthenticatedRedditUserFlairManager {
    suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
    ): AuthenticatedRedditUserFlairLoadResult

    suspend fun mutate(
        request: UserFlairMutationRequest,
    ): AuthenticatedRedditUserFlairMutationResult
}
