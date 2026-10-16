package app.subshell.prototype.auth

import app.subshell.prototype.data.account.PostSaveFailureKind
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.normalizedPostSaveRequest
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveWriter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class PostSaveCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedPostSaveWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(PostSaveUiState())
    private var pendingRequest: PostSaveRequest? = null

    val uiState: StateFlow<PostSaveUiState> = mutableUiState.asStateFlow()

    suspend fun setSaved(request: PostSaveRequest) {
        val normalized = try {
            request.normalizedPostSaveRequest()
        } catch (_: IllegalArgumentException) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostSaveFailureKind.VALIDATION,
            )
            return
        }

        mutationMutex.withLock {
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = PostSaveFailureKind.ACCOUNT_CHANGED,
                )
                return@withLock
            }

            val configuredWriter = writer
            if (configuredWriter == null) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = PostSaveFailureKind.CONFIGURATION,
                )
                return@withLock
            }

            pendingRequest = normalized
            mutableUiState.value = state(
                phase = PostSavePhase.SENDING,
                request = normalized,
            )

            val result = try {
                configuredWriter.setSaved(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedPostSaveResult.Failed(
                    kind = PostSaveFailureKind.TRANSPORT,
                    retryable = true,
                    failureDetails = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.UNKNOWN,
                    ),
                )
            }

            when (result) {
                is AuthenticatedPostSaveResult.Updated -> {
                    pendingRequest = null
                    val nextRevision = mutableUiState.value.completionRevision + 1L
                    mutableUiState.value = PostSaveUiState(
                        phase = PostSavePhase.SUCCEEDED,
                        accountStableId = result.request.accountStableId,
                        postFullname = result.request.postFullname,
                        saved = result.request.saved,
                        completionRevision = nextRevision,
                    )
                }

                AuthenticatedPostSaveResult.AuthorizationRequired -> {
                    pendingRequest = normalized
                    mutableUiState.value = state(
                        phase = PostSavePhase.AUTHORIZATION_REQUIRED,
                        request = normalized,
                    )
                }

                is AuthenticatedPostSaveResult.Failed -> {
                    pendingRequest = normalized.takeIf { result.retryable }
                    mutableUiState.value = failure(
                        request = normalized,
                        kind = result.kind,
                        retryable = result.retryable,
                        statusCode = result.statusCode,
                        failureDetails = result.failureDetails,
                    )
                }
            }
        }
    }

    suspend fun retry() {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == PostSavePhase.FAILED &&
                    mutableUiState.value.retryable
            }
            ?: return
        setSaved(request)
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == PostSavePhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode

        if (configuredCapability == null || configuredClientMode == null) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostSaveFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        if (activeAccountStableId()?.trim() != request.accountStableId) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostSaveFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(
            phase = PostSavePhase.AUTHORIZING,
            request = request,
        )

        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(
                request.accountStableId,
            )
            mutableUiState.value = state(
                phase = PostSavePhase.AWAITING_CALLBACK,
                request = request,
            )
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            pendingRequest = request
            mutableUiState.value = failure(
                request = request,
                kind = PostSaveFailureKind.AUTHORIZATION,
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
            kind = PostSaveFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun cancelPendingAuthorization() {
        val request = pendingRequest
        capability?.cancelPendingAuthorization()
        if (request == null) {
            resetUiState()
        } else {
            mutableUiState.value = failure(
                request = request,
                kind = PostSaveFailureKind.CALLBACK,
                retryable = true,
            )
        }
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }

        val configuredCapability = capability
        val request = pendingRequest
        if (configuredCapability == null) {
            pendingRequest = null
            mutableUiState.value = request?.let {
                failure(it, PostSaveFailureKind.CONFIGURATION)
            } ?: PostSaveUiState(
                completionRevision = mutableUiState.value.completionRevision,
            )
            return true
        }

        if (!configuredCapability.hasStoredCallback()) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = request?.let {
                failure(
                    request = it,
                    kind = PostSaveFailureKind.CALLBACK,
                    retryable = true,
                )
            } ?: PostSaveUiState(
                completionRevision = mutableUiState.value.completionRevision,
            )
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
            mutableUiState.value = state(
                phase = PostSavePhase.AUTHORIZING,
                request = request,
            )
        }

        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    pendingRequest = null
                    resetUiState()
                    return
                }
                if (
                    result.accountStableId != request.accountStableId ||
                    activeAccountStableId()?.trim() != request.accountStableId
                ) {
                    pendingRequest = null
                    mutableUiState.value = failure(
                        request = request,
                        kind = PostSaveFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = state(
                    phase = PostSavePhase.IDLE,
                    request = request,
                )
                setSaved(request)
            }

            is AuthCommentWriteCapabilityResult.Failed -> {
                if (request == null) {
                    pendingRequest = null
                    resetUiState()
                    return
                }
                pendingRequest = request.takeIf { result.retryable }
                mutableUiState.value = failure(
                    request = request,
                    kind = result.kind.toPostSaveFailureKind(),
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun state(
        phase: PostSavePhase,
        request: PostSaveRequest,
    ): PostSaveUiState = PostSaveUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        postFullname = request.postFullname,
        saved = request.saved,
        completionRevision = mutableUiState.value.completionRevision,
    )

    private fun failure(
        request: PostSaveRequest?,
        kind: PostSaveFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): PostSaveUiState = PostSaveUiState(
        phase = PostSavePhase.FAILED,
        accountStableId = request?.accountStableId?.trim()?.takeIf(String::isNotEmpty),
        postFullname = request?.postFullname?.trim()?.takeIf(String::isNotEmpty),
        saved = request?.saved,
        failureKind = kind,
        retryable = retryable,
        statusCode = statusCode,
        failureDetails = failureDetails,
        completionRevision = mutableUiState.value.completionRevision,
    )

    private fun resetUiState() {
        mutableUiState.value = PostSaveUiState(
            completionRevision = mutableUiState.value.completionRevision,
        )
    }
}

private fun AuthCommentWriteCapabilityFailureKind.toPostSaveFailureKind():
    PostSaveFailureKind = when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            PostSaveFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            PostSaveFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> PostSaveFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            PostSaveFailureKind.SESSION
    }
