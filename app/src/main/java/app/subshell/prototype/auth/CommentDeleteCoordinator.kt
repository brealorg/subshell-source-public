package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.CommentDeleteFailureKind
import app.subshell.prototype.data.account.CommentDeletePhase
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.OwnContentType
import app.subshell.prototype.data.account.ownContentActions
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleteFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleteRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleteResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class CommentDeleteCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val deleter: AuthenticatedCommentDeleter?,
    private val accountDirectory: AccountSessionDirectory?,
    private val clientMode: OAuthClientMode?,
    private val onCommentDeleted: (CommentDeleteRequest) -> Unit = {},
) {
    private val operationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(CommentDeleteUiState())
    private var pendingRequest: CommentDeleteRequest? = null
    private var completionRevision = 0L
    val uiState: StateFlow<CommentDeleteUiState> = mutableUiState.asStateFlow()

    suspend fun delete(request: CommentDeleteRequest) {
        if (!operationMutex.tryLock()) return
        try {
            pendingRequest = request
            val activeAccount = activeAuthenticatedAccount(request.accountStableId)
            if (activeAccount == null) { mutableUiState.value = failure(request, CommentDeleteFailureKind.ACCOUNT_CHANGED); return }
            if (!ownContentActions(activeAccount.username, request.commentAuthor, OwnContentType.COMMENT).canDelete) {
                mutableUiState.value = failure(request, CommentDeleteFailureKind.NOT_OWNER); return
            }
            val configuredDeleter = deleter ?: run { mutableUiState.value = failure(request, CommentDeleteFailureKind.CONFIGURATION); return }
            mutableUiState.value = state(CommentDeletePhase.SENDING, request)
            val result = try {
                configuredDeleter.delete(AuthenticatedCommentDeleteRequest(request.accountStableId, request.postId, request.commentFullname))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                AuthenticatedCommentDeleteResult.Failed(
                    kind = AuthenticatedCommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
                    retryable = false,
                    failureDetails = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }
            applyResult(request, result)
        } finally { operationMutex.unlock() }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest?.takeIf { mutableUiState.value.phase == CommentDeletePhase.AUTHORIZATION_REQUIRED } ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) { mutableUiState.value = failure(request, CommentDeleteFailureKind.CONFIGURATION); return AuthBrowserLaunchResult.NotStarted }
        if (activeAuthenticatedAccount(request.accountStableId) == null) { mutableUiState.value = failure(request, CommentDeleteFailureKind.ACCOUNT_CHANGED); return AuthBrowserLaunchResult.NotStarted }
        mutableUiState.value = state(CommentDeletePhase.AUTHORIZING, request)
        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(request.accountStableId)
            mutableUiState.value = state(CommentDeletePhase.AWAITING_CALLBACK, request)
            AuthBrowserLaunchResult.Ready(authorizationUrl, configuredClientMode)
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(request, CommentDeleteFailureKind.AUTHORIZATION, retryable = true)
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() { val request = pendingRequest ?: return; capability?.cancelPendingAuthorization(); mutableUiState.value = failure(request, CommentDeleteFailureKind.BROWSER_LAUNCH, retryable = true) }
    fun reset() { if (mutableUiState.value.inProgress) return; pendingRequest = null; mutableUiState.value = CommentDeleteUiState(completionRevision = completionRevision) }
    fun cancelPendingAuthorization() {
        if (mutableUiState.value.phase !in setOf(CommentDeletePhase.AUTHORIZING, CommentDeletePhase.AWAITING_CALLBACK, CommentDeletePhase.AUTHORIZATION_REQUIRED)) return
        capability?.cancelPendingAuthorization(); pendingRequest = null; mutableUiState.value = CommentDeleteUiState(completionRevision = completionRevision)
    }
    fun hasActiveAuthorizationRequest(): Boolean = pendingRequest != null && mutableUiState.value.phase in setOf(CommentDeletePhase.AUTHORIZING, CommentDeletePhase.AWAITING_CALLBACK)

    // LOW9_DELETE_CALLBACK_AFTER_PROCESS_DEATH_IS_CONSUMED_V1 (OCB-407): the
    // in-memory request is gone after process death, but the owner-bound record
    // is not. Like comment edit, the stored callback is completed (the session
    // is upgraded, the screen ends idle) or the record dropped on cancel.
    private fun ownsStoredOrPendingAuthorization(): Boolean =
        capability?.let { configured ->
            configured.hasStoredCallback() || configured.hasPendingAuthorization()
        } ?: false

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        if (!hasActiveAuthorizationRequest() && !ownsStoredOrPendingAuthorization()) return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            capability?.cancelPendingAuthorization()
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability
        if (configuredCapability == null || !configuredCapability.hasStoredCallback()) {
            configuredCapability?.cancelPendingAuthorization(); val request = pendingRequest
            mutableUiState.value = if (request == null) CommentDeleteUiState(completionRevision = completionRevision) else failure(request, CommentDeleteFailureKind.CALLBACK, retryable = true)
            return true
        }
        completeAuthorization(); return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        val configuredCapability = capability ?: return false
        return when { configuredCapability.hasStoredCallback() -> { completeAuthorization(); true }; configuredCapability.hasPendingAuthorization() -> true; else -> false }
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val request = pendingRequest
        if (request != null) mutableUiState.value = state(CommentDeletePhase.AUTHORIZING, request)
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) { mutableUiState.value = CommentDeleteUiState(completionRevision = completionRevision); return }
                if (result.accountStableId != request.accountStableId || activeAuthenticatedAccount(request.accountStableId) == null) { mutableUiState.value = failure(request, CommentDeleteFailureKind.ACCOUNT_CHANGED); return }
                mutableUiState.value = state(CommentDeletePhase.IDLE, request); delete(request)
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = if (request == null) CommentDeleteUiState(completionRevision = completionRevision) else failure(request, result.kind.toDeleteFailureKind(), result.retryable)
            }
        }
    }

    private fun applyResult(request: CommentDeleteRequest, result: AuthenticatedCommentDeleteResult) {
        when (result) {
            is AuthenticatedCommentDeleteResult.Deleted -> {
                onCommentDeleted(request); completionRevision += 1L; pendingRequest = null
                mutableUiState.value = CommentDeleteUiState(CommentDeletePhase.SUCCEEDED, request.accountStableId, request.postId, request.commentFullname, completionRevision = completionRevision)
            }
            is AuthenticatedCommentDeleteResult.Failed -> mutableUiState.value = if (
                result.kind == AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED
            ) {
                state(CommentDeletePhase.AUTHORIZATION_REQUIRED, request)
            } else {
                failure(
                    request = request,
                    kind = result.kind.toDeleteFailureKind(),
                    retryable = result.retryable,
                    retryAfterMs = result.retryAfterMs,
                    httpStatusCode = result.httpStatusCode,
                    failureDetails = result.failureDetails,
                )
            }
        }
    }

    private fun activeAuthenticatedAccount(accountStableId: String) = accountDirectory?.snapshot()?.let { snapshot -> snapshot.accounts.firstOrNull { it.stableId == accountStableId && it.stableId == snapshot.activeAccountId && it.source == AccountSessionSource.AUTHENTICATED } }
    private fun state(phase: CommentDeletePhase, request: CommentDeleteRequest) = CommentDeleteUiState(phase, request.accountStableId, request.postId, request.commentFullname, completionRevision = completionRevision)
    private fun failure(
        request: CommentDeleteRequest,
        kind: CommentDeleteFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = CommentDeleteUiState(
        phase = CommentDeletePhase.FAILED,
        accountStableId = request.accountStableId,
        postId = request.postId,
        commentFullname = request.commentFullname,
        failureKind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        completionRevision = completionRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toDeleteFailureKind() = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT -> CommentDeleteFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.CALLBACK -> CommentDeleteFailureKind.CALLBACK
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE, AuthCommentWriteCapabilityFailureKind.IDENTITY, AuthCommentWriteCapabilityFailureKind.SCOPE -> CommentDeleteFailureKind.AUTHORIZATION
    AuthCommentWriteCapabilityFailureKind.STORAGE -> CommentDeleteFailureKind.SESSION
}
private fun AuthenticatedCommentDeleteFailureKind.toDeleteFailureKind() = when (this) {
    AuthenticatedCommentDeleteFailureKind.VALIDATION -> CommentDeleteFailureKind.PROTOCOL
    AuthenticatedCommentDeleteFailureKind.ALREADY_DELETING -> CommentDeleteFailureKind.ALREADY_DELETING
    AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED -> CommentDeleteFailureKind.AUTHORIZATION
    AuthenticatedCommentDeleteFailureKind.SESSION -> CommentDeleteFailureKind.SESSION
    AuthenticatedCommentDeleteFailureKind.RATE_LIMIT -> CommentDeleteFailureKind.RATE_LIMIT
    AuthenticatedCommentDeleteFailureKind.HTTP -> CommentDeleteFailureKind.HTTP
    AuthenticatedCommentDeleteFailureKind.TRANSPORT -> CommentDeleteFailureKind.TRANSPORT
    AuthenticatedCommentDeleteFailureKind.PROTOCOL -> CommentDeleteFailureKind.PROTOCOL
    AuthenticatedCommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED ->
        CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED
}
