package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.UserFlairFailureKind
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairSnapshot

// USERFLAIR01A1_AUTHENTICATED_BOUNDARY_V1
internal sealed interface AuthenticatedRedditUserFlairLoadResult {
    data class Loaded(val snapshot: UserFlairSnapshot) : AuthenticatedRedditUserFlairLoadResult
    data object AuthorizationRequired : AuthenticatedRedditUserFlairLoadResult
    data class Failed(
        val kind: UserFlairFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditUserFlairLoadResult
}

internal sealed interface AuthenticatedRedditUserFlairMutationResult {
    data object Acknowledged : AuthenticatedRedditUserFlairMutationResult
    data object AuthorizationRequired : AuthenticatedRedditUserFlairMutationResult
    data class Failed(
        val kind: UserFlairFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
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
