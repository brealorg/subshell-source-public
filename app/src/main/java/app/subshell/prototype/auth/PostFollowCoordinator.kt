package app.subshell.prototype.auth

import app.subshell.prototype.data.account.PostFollowFailureKind
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.normalizedPostFollowRequest
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostFollowResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostFollowWriter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class PostFollowCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedPostFollowWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(PostFollowUiState())
    private var pendingRequest: PostFollowRequest? = null

    val uiState: StateFlow<PostFollowUiState> = mutableUiState.asStateFlow()

    suspend fun setFollowed(request: PostFollowRequest) {
        val normalized = try {
            request.normalizedPostFollowRequest()
        } catch (_: IllegalArgumentException) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostFollowFailureKind.VALIDATION,
            )
            return
        }

        mutationMutex.withLock {
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = PostFollowFailureKind.ACCOUNT_CHANGED,
                )
                return@withLock
            }

            val configuredWriter = writer
            if (configuredWriter == null) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = PostFollowFailureKind.CONFIGURATION,
                )
                return@withLock
            }

            pendingRequest = normalized
            mutableUiState.value = state(
                phase = PostFollowPhase.SENDING,
                request = normalized,
            )

            val result = try {
                configuredWriter.setFollowed(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedPostFollowResult.Failed(
                    kind = PostFollowFailureKind.TRANSPORT,
                    retryable = true,
                    failureDetails = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }

            when (result) {
                is AuthenticatedPostFollowResult.Updated -> {
                    pendingRequest = null
                    val nextRevision = mutableUiState.value.completionRevision + 1L
                    mutableUiState.value = PostFollowUiState(
                        phase = PostFollowPhase.SUCCEEDED,
                        accountStableId = result.request.accountStableId,
                        postFullname = result.request.postFullname,
                        followed = result.request.followed,
                        completionRevision = nextRevision,
                    )
                }

                AuthenticatedPostFollowResult.AuthorizationRequired -> {
                    pendingRequest = normalized
                    mutableUiState.value = state(
                        phase = PostFollowPhase.AUTHORIZATION_REQUIRED,
                        request = normalized,
                    )
                }

                is AuthenticatedPostFollowResult.Failed -> {
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
                mutableUiState.value.phase == PostFollowPhase.FAILED &&
                    mutableUiState.value.retryable
            }
            ?: return
        setFollowed(request)
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == PostFollowPhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode

        if (configuredCapability == null || configuredClientMode == null) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostFollowFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        if (activeAccountStableId()?.trim() != request.accountStableId) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostFollowFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(
            phase = PostFollowPhase.AUTHORIZING,
            request = request,
        )

        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(
                request.accountStableId,
            )
            mutableUiState.value = state(
                phase = PostFollowPhase.AWAITING_CALLBACK,
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
                kind = PostFollowFailureKind.AUTHORIZATION,
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
            kind = PostFollowFailureKind.BROWSER_LAUNCH,
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
                kind = PostFollowFailureKind.CALLBACK,
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
                failure(it, PostFollowFailureKind.CONFIGURATION)
            } ?: PostFollowUiState(
                completionRevision = mutableUiState.value.completionRevision,
            )
            return true
        }

        if (!configuredCapability.hasStoredCallback()) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = request?.let {
                failure(
                    request = it,
                    kind = PostFollowFailureKind.CALLBACK,
                    retryable = true,
                )
            } ?: PostFollowUiState(
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
                phase = PostFollowPhase.AUTHORIZING,
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
                        kind = PostFollowFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = state(
                    phase = PostFollowPhase.IDLE,
                    request = request,
                )
                setFollowed(request)
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
                    kind = result.kind.toPostFollowFailureKind(),
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun state(
        phase: PostFollowPhase,
        request: PostFollowRequest,
    ): PostFollowUiState = PostFollowUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        postFullname = request.postFullname,
        followed = request.followed,
        completionRevision = mutableUiState.value.completionRevision,
    )

    private fun failure(
        request: PostFollowRequest?,
        kind: PostFollowFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): PostFollowUiState = PostFollowUiState(
        phase = PostFollowPhase.FAILED,
        accountStableId = request?.accountStableId?.trim()?.takeIf(String::isNotEmpty),
        postFullname = request?.postFullname?.trim()?.takeIf(String::isNotEmpty),
        followed = request?.followed,
        failureKind = kind,
        retryable = retryable,
        statusCode = statusCode,
        failureDetails = failureDetails,
        completionRevision = mutableUiState.value.completionRevision,
    )

    private fun resetUiState() {
        mutableUiState.value = PostFollowUiState(
            completionRevision = mutableUiState.value.completionRevision,
        )
    }
}

private fun AuthCommentWriteCapabilityFailureKind.toPostFollowFailureKind():
    PostFollowFailureKind = when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            PostFollowFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            PostFollowFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> PostFollowFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            PostFollowFailureKind.SESSION
    }
