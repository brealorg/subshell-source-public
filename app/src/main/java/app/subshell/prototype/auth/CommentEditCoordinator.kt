package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.CommentEditFailureKind
import app.subshell.prototype.data.account.CommentEditPhase
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.OwnContentType
import app.subshell.prototype.data.account.ownContentActions
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditResult
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class CommentEditCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val editor: AuthenticatedCommentEditor?,
    private val accountDirectory: AccountSessionDirectory?,
    private val clientMode: OAuthClientMode?,
    private val onCommentEdited: (CommentEditRequest) -> Unit = {},
) {
    private val operationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(CommentEditUiState())
    private var pendingRequest: CommentEditRequest? = null
    private var completionRevision = 0L

    val uiState: StateFlow<CommentEditUiState> = mutableUiState.asStateFlow()

    suspend fun edit(request: CommentEditRequest) {
        if (!operationMutex.tryLock()) return
        try {
            pendingRequest = request
            val activeAccount = activeAuthenticatedAccount(request.accountStableId)
            if (activeAccount == null) {
                mutableUiState.value = failure(
                    request = request,
                    kind = CommentEditFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            if (
                !ownContentActions(
                    activeUsername = activeAccount.username,
                    author = request.commentAuthor,
                    contentType = OwnContentType.COMMENT,
                ).canEdit
            ) {
                mutableUiState.value = failure(
                    request = request,
                    kind = CommentEditFailureKind.NOT_OWNER,
                )
                return
            }
            val configuredEditor = editor
            if (configuredEditor == null) {
                mutableUiState.value = failure(
                    request = request,
                    kind = CommentEditFailureKind.CONFIGURATION,
                )
                return
            }

            mutableUiState.value = state(CommentEditPhase.SENDING, request)
            val result = try {
                configuredEditor.edit(
                    AuthenticatedCommentEditRequest(
                        accountStableId = request.accountStableId,
                        commentFullname = request.commentFullname,
                        text = request.text,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // The request may already have reached Reddit.
                AuthenticatedCommentEditResult.Failed(
                    kind = AuthenticatedCommentEditFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN).withExceptionClass(error),
                )
            }
            applyResult(request, result)
        } finally {
            operationMutex.unlock()
        }
    }

    // H1_COMMENT_EDIT_CHECK_AGAIN_READS_ONLY_V1: after an unknown outcome the
    // user can ask again what Reddit shows; the edit is never re-sent here.
    suspend fun checkAgain() {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == CommentEditPhase.FAILED &&
                    mutableUiState.value.failureKind == CommentEditFailureKind.OUTCOME_UNKNOWN
            }
            ?: return
        if (!operationMutex.tryLock()) return
        try {
            if (activeAuthenticatedAccount(request.accountStableId) == null) {
                mutableUiState.value = failure(
                    request = request,
                    kind = CommentEditFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            val configuredEditor = editor ?: return
            mutableUiState.value = state(CommentEditPhase.CHECKING, request)
            val result = try {
                configuredEditor.verify(
                    AuthenticatedCommentEditRequest(
                        accountStableId = request.accountStableId,
                        commentFullname = request.commentFullname,
                        text = request.text,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedCommentEditResult.Failed(
                    kind = AuthenticatedCommentEditFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN).withExceptionClass(error),
                )
            }
            applyResult(request, result)
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf { mutableUiState.value.phase == CommentEditPhase.AUTHORIZATION_REQUIRED }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            mutableUiState.value = failure(
                request = request,
                kind = CommentEditFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (activeAuthenticatedAccount(request.accountStableId) == null) {
            mutableUiState.value = failure(
                request = request,
                kind = CommentEditFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(CommentEditPhase.AUTHORIZING, request)
        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(request.accountStableId)
            mutableUiState.value = state(CommentEditPhase.AWAITING_CALLBACK, request)
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                request = request,
                kind = CommentEditFailureKind.AUTHORIZATION,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val request = pendingRequest ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            request = request,
            kind = CommentEditFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun reset() {
        if (mutableUiState.value.inProgress) return
        pendingRequest = null
        mutableUiState.value = CommentEditUiState(
            completionRevision = completionRevision,
        )
    }

    fun cancelPendingAuthorization() {
        if (
            mutableUiState.value.phase !in setOf(
                CommentEditPhase.AUTHORIZING,
                CommentEditPhase.AWAITING_CALLBACK,
                CommentEditPhase.AUTHORIZATION_REQUIRED,
            )
        ) {
            return
        }
        capability?.cancelPendingAuthorization()
        pendingRequest = null
        mutableUiState.value = CommentEditUiState(
            completionRevision = completionRevision,
        )
    }

    fun ownsStoredOrPendingAuthorization(): Boolean =
        capability?.let { configured ->
            configured.hasStoredCallback() || configured.hasPendingAuthorization()
        } ?: false

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val ownsCallback = ownsStoredOrPendingAuthorization() ||
            mutableUiState.value.phase == CommentEditPhase.AWAITING_CALLBACK
        if (!ownsCallback) return false

        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability
        if (configuredCapability == null || !configuredCapability.hasStoredCallback()) {
            configuredCapability?.cancelPendingAuthorization()
            val request = pendingRequest
            mutableUiState.value = if (request == null) {
                CommentEditUiState(completionRevision = completionRevision)
            } else {
                failure(
                    request = request,
                    kind = CommentEditFailureKind.CALLBACK,
                    retryable = true,
                )
            }
            return true
        }
        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
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
            mutableUiState.value = state(CommentEditPhase.AUTHORIZING, request)
        }
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    mutableUiState.value = CommentEditUiState(
                        completionRevision = completionRevision,
                    )
                    return
                }
                if (
                    result.accountStableId != request.accountStableId ||
                    activeAuthenticatedAccount(request.accountStableId) == null
                ) {
                    mutableUiState.value = failure(
                        request = request,
                        kind = CommentEditFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = state(CommentEditPhase.IDLE, request)
                edit(request)
            }

            is AuthCommentWriteCapabilityResult.Failed -> {
                if (request == null) {
                    mutableUiState.value = CommentEditUiState(
                        completionRevision = completionRevision,
                    )
                } else {
                    mutableUiState.value = failure(
                        request = request,
                        kind = result.kind.toEditFailureKind(),
                        retryable = result.retryable,
                    )
                }
            }
        }
    }

    private fun applyResult(
        request: CommentEditRequest,
        result: AuthenticatedCommentEditResult,
    ) {
        when (result) {
            is AuthenticatedCommentEditResult.Edited -> {
                onCommentEdited(request)
                completionRevision += 1L
                pendingRequest = null
                mutableUiState.value = CommentEditUiState(
                    phase = CommentEditPhase.SUCCEEDED,
                    accountStableId = request.accountStableId,
                    postId = request.postId,
                    commentFullname = request.commentFullname,
                    completionRevision = completionRevision,
                )
            }

            is AuthenticatedCommentEditResult.Failed -> {
                mutableUiState.value = when (result.kind) {
                    AuthenticatedCommentEditFailureKind.AUTHORIZATION_REQUIRED ->
                        state(CommentEditPhase.AUTHORIZATION_REQUIRED, request)
                    else -> failure(
                        request = request,
                        kind = result.kind.toEditFailureKind(),
                        retryable = result.retryable,
                        retryAfterMs = result.retryAfterMs,
                        failureDetails = result.failureDetails,
                    )
                }
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
        phase: CommentEditPhase,
        request: CommentEditRequest,
    ): CommentEditUiState = CommentEditUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        postId = request.postId,
        commentFullname = request.commentFullname,
        completionRevision = completionRevision,
    )

    private fun failure(
        request: CommentEditRequest,
        kind: CommentEditFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        failureDetails: FailureDetails? = null,
    ): CommentEditUiState = CommentEditUiState(
        phase = CommentEditPhase.FAILED,
        accountStableId = request.accountStableId,
        postId = request.postId,
        commentFullname = request.commentFullname,
        failureKind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        failureDetails = failureDetails,
        completionRevision = completionRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toEditFailureKind(): CommentEditFailureKind =
    when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            CommentEditFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            CommentEditFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> CommentEditFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            CommentEditFailureKind.SESSION
    }

private fun AuthenticatedCommentEditFailureKind.toEditFailureKind(): CommentEditFailureKind =
    when (this) {
        AuthenticatedCommentEditFailureKind.VALIDATION ->
            CommentEditFailureKind.VALIDATION
        AuthenticatedCommentEditFailureKind.ALREADY_EDITING ->
            CommentEditFailureKind.ALREADY_EDITING
        AuthenticatedCommentEditFailureKind.AUTHORIZATION_REQUIRED ->
            CommentEditFailureKind.AUTHORIZATION
        AuthenticatedCommentEditFailureKind.SESSION ->
            CommentEditFailureKind.SESSION
        AuthenticatedCommentEditFailureKind.RATE_LIMIT ->
            CommentEditFailureKind.RATE_LIMIT
        AuthenticatedCommentEditFailureKind.API ->
            CommentEditFailureKind.API
        AuthenticatedCommentEditFailureKind.HTTP ->
            CommentEditFailureKind.HTTP
        AuthenticatedCommentEditFailureKind.TRANSPORT ->
            CommentEditFailureKind.TRANSPORT
        AuthenticatedCommentEditFailureKind.OUTCOME_UNKNOWN ->
            CommentEditFailureKind.OUTCOME_UNKNOWN
        AuthenticatedCommentEditFailureKind.NOT_SAVED ->
            CommentEditFailureKind.NOT_SAVED
    }
