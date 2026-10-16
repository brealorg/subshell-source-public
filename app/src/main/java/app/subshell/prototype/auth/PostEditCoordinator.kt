package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.OwnContentType
import app.subshell.prototype.data.account.PostEditFailureKind
import app.subshell.prototype.data.account.PostEditPhase
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.ownContentActions
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditResult
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class PostEditCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val editor: AuthenticatedPostEditor?,
    private val accountDirectory: AccountSessionDirectory?,
    private val clientMode: OAuthClientMode?,
) {
    private val operationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(PostEditUiState())
    private var pendingRequest: PostEditRequest? = null
    private var completionRevision = 0L

    val uiState: StateFlow<PostEditUiState> = mutableUiState.asStateFlow()

    suspend fun edit(request: PostEditRequest) {
        if (!operationMutex.tryLock()) return
        try {
            pendingRequest = request
            val activeAccount = activeAuthenticatedAccount(request.accountStableId)
            if (activeAccount == null) {
                mutableUiState.value = failure(request, PostEditFailureKind.ACCOUNT_CHANGED)
                return
            }
            if (
                !ownContentActions(
                    activeUsername = activeAccount.username,
                    author = request.postAuthor,
                    contentType = OwnContentType.SELF_POST,
                ).canEdit
            ) {
                mutableUiState.value = failure(request, PostEditFailureKind.NOT_OWNER)
                return
            }
            val configuredEditor = editor
            if (configuredEditor == null) {
                mutableUiState.value = failure(request, PostEditFailureKind.CONFIGURATION)
                return
            }

            mutableUiState.value = state(PostEditPhase.SENDING, request)
            val result = try {
                configuredEditor.edit(
                    AuthenticatedPostEditRequest(
                        accountStableId = request.accountStableId,
                        postFullname = request.postFullname,
                        text = request.text,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The request may already have reached Reddit.
                AuthenticatedPostEditResult.Failed(
                    kind = AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN),
                )
            }
            applyResult(request, result)
        } finally {
            operationMutex.unlock()
        }
    }

    // H2_POST_EDIT_CHECK_AGAIN_READS_ONLY_V1: after an unknown outcome the
    // user can ask again what Reddit shows; the edit is never re-sent here.
    suspend fun checkAgain() {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == PostEditPhase.FAILED &&
                    mutableUiState.value.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN
            }
            ?: return
        if (!operationMutex.tryLock()) return
        try {
            if (activeAuthenticatedAccount(request.accountStableId) == null) {
                mutableUiState.value = failure(
                    request = request,
                    kind = PostEditFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            val configuredEditor = editor ?: return
            mutableUiState.value = state(PostEditPhase.CHECKING, request)
            val result = try {
                configuredEditor.verify(
                    AuthenticatedPostEditRequest(
                        accountStableId = request.accountStableId,
                        postFullname = request.postFullname,
                        text = request.text,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedPostEditResult.Failed(
                    kind = AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN),
                )
            }
            applyResult(request, result)
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf { mutableUiState.value.phase == PostEditPhase.AUTHORIZATION_REQUIRED }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            mutableUiState.value = failure(request, PostEditFailureKind.CONFIGURATION)
            return AuthBrowserLaunchResult.NotStarted
        }
        if (activeAuthenticatedAccount(request.accountStableId) == null) {
            mutableUiState.value = failure(request, PostEditFailureKind.ACCOUNT_CHANGED)
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(PostEditPhase.AUTHORIZING, request)
        return try {
            val authorizationUrl =
                configuredCapability.beginAuthorization(request.accountStableId)
            mutableUiState.value = state(PostEditPhase.AWAITING_CALLBACK, request)
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                request,
                PostEditFailureKind.AUTHORIZATION,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val request = pendingRequest ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            request,
            PostEditFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun reset() {
        if (mutableUiState.value.inProgress) return
        pendingRequest = null
        mutableUiState.value = PostEditUiState(
            completionRevision = completionRevision,
        )
    }

    fun cancelPendingAuthorization() {
        if (
            mutableUiState.value.phase !in setOf(
                PostEditPhase.AUTHORIZING,
                PostEditPhase.AWAITING_CALLBACK,
                PostEditPhase.AUTHORIZATION_REQUIRED,
            )
        ) {
            return
        }
        capability?.cancelPendingAuthorization()
        pendingRequest = null
        mutableUiState.value = PostEditUiState(
            completionRevision = completionRevision,
        )
    }

    fun hasActiveAuthorizationRequest(): Boolean =
        pendingRequest != null && mutableUiState.value.phase in setOf(
            PostEditPhase.AUTHORIZING,
            PostEditPhase.AWAITING_CALLBACK,
        )

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        if (!hasActiveAuthorizationRequest()) return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability
        if (configuredCapability == null || !configuredCapability.hasStoredCallback()) {
            configuredCapability?.cancelPendingAuthorization()
            val request = pendingRequest
            mutableUiState.value = if (request == null) {
                PostEditUiState(completionRevision = completionRevision)
            } else {
                failure(
                    request,
                    PostEditFailureKind.CALLBACK,
                    retryable = true,
                )
            }
            return true
        }
        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        if (!hasActiveAuthorizationRequest()) return false
        val configuredCapability = capability ?: return false
        return when {
            configuredCapability.hasStoredCallback() -> {
                completeAuthorization()
                true
            }
            configuredCapability.hasPendingAuthorization() -> true
            else -> false
        }
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val request = pendingRequest
        if (request != null) {
            mutableUiState.value = state(PostEditPhase.AUTHORIZING, request)
        }
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    mutableUiState.value = PostEditUiState(
                        completionRevision = completionRevision,
                    )
                    return
                }
                if (
                    result.accountStableId != request.accountStableId ||
                    activeAuthenticatedAccount(request.accountStableId) == null
                ) {
                    mutableUiState.value =
                        failure(request, PostEditFailureKind.ACCOUNT_CHANGED)
                    return
                }
                mutableUiState.value = state(PostEditPhase.IDLE, request)
                edit(request)
            }

            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = if (request == null) {
                    PostEditUiState(completionRevision = completionRevision)
                } else {
                    failure(
                        request,
                        result.kind.toPostEditFailureKind(),
                        retryable = result.retryable,
                    )
                }
            }
        }
    }

    private fun applyResult(
        request: PostEditRequest,
        result: AuthenticatedPostEditResult,
    ) {
        mutableUiState.value = when (result) {
            is AuthenticatedPostEditResult.Edited -> {
                completionRevision += 1L
                pendingRequest = null
                PostEditUiState(
                    phase = PostEditPhase.SUCCEEDED,
                    accountStableId = request.accountStableId,
                    postFullname = request.postFullname,
                    completionRevision = completionRevision,
                    savedText = result.text,
                )
            }

            is AuthenticatedPostEditResult.Failed -> when (result.kind) {
                AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED ->
                    state(PostEditPhase.AUTHORIZATION_REQUIRED, request)
                else -> failure(
                    request = request,
                    kind = result.kind.toPostEditFailureKind(),
                    retryable = result.retryable,
                    retryAfterMs = result.retryAfterMs,
                    failureDetails = result.failureDetails,
                )
            }
        }
    }

    private fun activeAuthenticatedAccount(accountStableId: String) =
        accountDirectory?.snapshot()?.let { snapshot ->
            snapshot.accounts.firstOrNull { account ->
                account.stableId == accountStableId &&
                    account.stableId == snapshot.activeAccountId &&
                    account.source == AccountSessionSource.AUTHENTICATED
            }
        }

    private fun state(
        phase: PostEditPhase,
        request: PostEditRequest,
    ): PostEditUiState = PostEditUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        postFullname = request.postFullname,
        completionRevision = completionRevision,
    )

    private fun failure(
        request: PostEditRequest,
        kind: PostEditFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        failureDetails: FailureDetails? = null,
    ): PostEditUiState = PostEditUiState(
        phase = PostEditPhase.FAILED,
        accountStableId = request.accountStableId,
        postFullname = request.postFullname,
        failureKind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        failureDetails = failureDetails,
        completionRevision = completionRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toPostEditFailureKind(): PostEditFailureKind =
    when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            PostEditFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            PostEditFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> PostEditFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            PostEditFailureKind.SESSION
    }

private fun AuthenticatedPostEditFailureKind.toPostEditFailureKind(): PostEditFailureKind =
    when (this) {
        AuthenticatedPostEditFailureKind.VALIDATION ->
            PostEditFailureKind.VALIDATION
        AuthenticatedPostEditFailureKind.ALREADY_EDITING ->
            PostEditFailureKind.ALREADY_EDITING
        AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED ->
            PostEditFailureKind.AUTHORIZATION
        AuthenticatedPostEditFailureKind.SESSION ->
            PostEditFailureKind.SESSION
        AuthenticatedPostEditFailureKind.RATE_LIMIT ->
            PostEditFailureKind.RATE_LIMIT
        AuthenticatedPostEditFailureKind.API ->
            PostEditFailureKind.API
        AuthenticatedPostEditFailureKind.HTTP ->
            PostEditFailureKind.HTTP
        AuthenticatedPostEditFailureKind.TRANSPORT ->
            PostEditFailureKind.TRANSPORT
        AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN ->
            PostEditFailureKind.OUTCOME_UNKNOWN
        AuthenticatedPostEditFailureKind.NOT_SAVED ->
            PostEditFailureKind.NOT_SAVED
    }
