package app.subshell.prototype.auth

import app.subshell.prototype.data.account.UserFlairFailureKind
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.data.account.normalizeUserFlairCommunity
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairLoadResult
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairManager
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairMutationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// USERFLAIR01A1_TRANSIENT_ACCOUNT_COMMUNITY_PINNED_COORDINATOR_V1
internal class UserFlairCoordinator(
    private val manager: AuthenticatedRedditUserFlairManager?,
    private val activeAccountStableId: () -> String?,
) {
    private val mutableUiState = MutableStateFlow(UserFlairUiState())
    val uiState: StateFlow<UserFlairUiState> = mutableUiState.asStateFlow()
    private val mutationMutex = Mutex()
    private var target: Pair<String, String>? = null

    suspend fun load(accountStableId: String, rawCommunityName: String, force: Boolean = false) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeUserFlairCommunity(rawCommunityName) }
            .getOrElse {
                mutableUiState.value = UserFlairUiState(failureKind = UserFlairFailureKind.VALIDATION)
                return
            }
        if (activeAccountStableId()?.trim() != account) {
            mutableUiState.value = UserFlairUiState(
                accountStableId = account,
                communityName = community,
                failureKind = UserFlairFailureKind.ACCOUNT_CHANGED,
            )
            return
        }
        val current = mutableUiState.value
        if (!force && current.matches(account, community) && (current.loading || current.loaded)) return
        target = account to community
        mutableUiState.value = UserFlairUiState(
            accountStableId = account,
            communityName = community,
            snapshot = current.takeIf { it.matches(account, community) }?.snapshot,
            loading = true,
            loaded = current.matches(account, community) && current.loaded,
            contentRevision = current.contentRevision,
            completionRevision = current.completionRevision,
        )
        val configured = manager ?: run {
            mutableUiState.value = mutableUiState.value.copy(
                loading = false,
                failureKind = UserFlairFailureKind.CONFIGURATION,
            )
            return
        }
        val result = configured.load(account, community)
        // CONC01_USER_FLAIR_RESULT_FOR_CURRENT_TARGET_V1 (OCB-449): a slow load for
        // another community or account no longer lands under the current one.
        if (target != account to community || !mutableUiState.value.matches(account, community)) return
        when (result) {
            is AuthenticatedRedditUserFlairLoadResult.Loaded ->
                mutableUiState.value = mutableUiState.value.copy(
                    snapshot = result.snapshot,
                    loading = false,
                    loaded = true,
                    authorizationRequired = false,
                    failureKind = null,
                    failureHttpStatusCode = null,
                    contentRevision = mutableUiState.value.contentRevision + 1L,
                )
            AuthenticatedRedditUserFlairLoadResult.AuthorizationRequired ->
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    authorizationRequired = true,
                    failureKind = null,
                )
            is AuthenticatedRedditUserFlairLoadResult.Failed ->
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    failureKind = result.kind,
                    failureHttpStatusCode = result.httpStatusCode,
                )
        }
    }

    suspend fun mutate(request: UserFlairMutationRequest) {
        mutationMutex.withLock {
            val configured = manager ?: run {
                mutableUiState.value = mutableUiState.value.copy(
                    failureKind = UserFlairFailureKind.CONFIGURATION,
                )
                return@withLock
            }
            // No pending mutation is stored. Authorization never replays a flair write.
            mutableUiState.value = mutableUiState.value.copy(
                mutating = true,
                authorizationRequired = false,
                failureKind = null,
            )
            when (val result = configured.mutate(request)) {
                AuthenticatedRedditUserFlairMutationResult.Acknowledged -> {
                    val revision = mutableUiState.value.completionRevision + 1L
                    mutableUiState.value = mutableUiState.value.copy(
                        mutating = false,
                        completionRevision = revision,
                    )
                    load(request.accountStableId, request.communityName, force = true)
                }
                AuthenticatedRedditUserFlairMutationResult.AuthorizationRequired ->
                    mutableUiState.value = mutableUiState.value.copy(
                        mutating = false,
                        authorizationRequired = true,
                    )
                is AuthenticatedRedditUserFlairMutationResult.Failed ->
                    mutableUiState.value = mutableUiState.value.copy(
                        mutating = false,
                        failureKind = result.kind,
                        failureHttpStatusCode = result.httpStatusCode,
                    )
            }
        }
    }

    suspend fun refreshAfterAuthorization() {
        val current = target ?: return
        load(current.first, current.second, force = true)
    }
}
