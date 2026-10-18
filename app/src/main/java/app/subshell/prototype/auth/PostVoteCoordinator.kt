package app.subshell.prototype.auth

import app.subshell.prototype.data.account.PostVoteFailureKind
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.POST_VOTE_SETTLEMENTS_KEPT
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteSettlement
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.normalizedPostVoteRequest
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteWriter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class PostVoteCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedPostVoteWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(PostVoteUiState())
    private var pendingRequest: PostVoteRequest? = null
    private var settled: List<PostVoteSettlement> = emptyList()
    private var nextSettlementSequence = 1L

    val uiState: StateFlow<PostVoteUiState> = mutableUiState.asStateFlow()

    suspend fun vote(request: PostVoteRequest) {
        val normalized = try {
            request.normalizedPostVoteRequest()
        } catch (_: IllegalArgumentException) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostVoteFailureKind.VALIDATION,
            )
            return
        }

        // VOTE02_ONE_PENDING_AUTHORIZATION_V1 (#60): while one vote waits for
        // authorization, a vote on another target is refused (settled as
        // failed) instead of replacing the waiting request.
        val waiting = pendingRequest
        if (
            waiting != null &&
            waiting.postFullname != normalized.postFullname &&
            mutableUiState.value.phase in AUTHORIZATION_PHASES
        ) {
            mutableUiState.value = mutableUiState.value.copy(
                settlements = settle(normalized, succeeded = false),
            )
            return
        }

        mutationMutex.withLock {
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = PostVoteFailureKind.ACCOUNT_CHANGED,
                )
                return@withLock
            }

            val configuredWriter = writer
            if (configuredWriter == null) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = PostVoteFailureKind.CONFIGURATION,
                )
                return@withLock
            }

            pendingRequest = normalized
            mutableUiState.value = state(
                phase = PostVotePhase.SENDING,
                request = normalized,
            )

            val result = try {
                configuredWriter.vote(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedPostVoteResult.Failed(
                    kind = PostVoteFailureKind.TRANSPORT,
                    retryable = true,
                    failureDetails = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }

            when (result) {
                is AuthenticatedPostVoteResult.Voted -> {
                    pendingRequest = null
                    val nextRevision = mutableUiState.value.completionRevision + 1L
                    mutableUiState.value = PostVoteUiState(
                        phase = PostVotePhase.SUCCEEDED,
                        accountStableId = result.request.accountStableId,
                        postFullname = result.request.postFullname,
                        direction = result.request.direction,
                        completionRevision = nextRevision,
                        settlements = settle(result.request, succeeded = true),
                    )
                }

                AuthenticatedPostVoteResult.AuthorizationRequired -> {
                    pendingRequest = normalized
                    mutableUiState.value = state(
                        phase = PostVotePhase.AUTHORIZATION_REQUIRED,
                        request = normalized,
                    )
                }

                is AuthenticatedPostVoteResult.Failed -> {
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
                mutableUiState.value.phase == PostVotePhase.FAILED &&
                    mutableUiState.value.retryable
            }
            ?: return
        vote(request)
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == PostVotePhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode

        if (configuredCapability == null || configuredClientMode == null) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostVoteFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        if (activeAccountStableId()?.trim() != request.accountStableId) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = PostVoteFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(
            phase = PostVotePhase.AUTHORIZING,
            request = request,
        )

        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(
                request.accountStableId,
            )
            mutableUiState.value = state(
                phase = PostVotePhase.AWAITING_CALLBACK,
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
                kind = PostVoteFailureKind.AUTHORIZATION,
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
            kind = PostVoteFailureKind.BROWSER_LAUNCH,
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
                kind = PostVoteFailureKind.CALLBACK,
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
                failure(it, PostVoteFailureKind.CONFIGURATION)
            } ?: PostVoteUiState(
                completionRevision = mutableUiState.value.completionRevision,
                settlements = settled,
            )
            return true
        }

        if (!configuredCapability.hasStoredCallback()) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = request?.let {
                failure(
                    request = it,
                    kind = PostVoteFailureKind.CALLBACK,
                    retryable = true,
                )
            } ?: PostVoteUiState(
                completionRevision = mutableUiState.value.completionRevision,
                settlements = settled,
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
                phase = PostVotePhase.AUTHORIZING,
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
                        kind = PostVoteFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = state(
                    phase = PostVotePhase.IDLE,
                    request = request,
                )
                vote(request)
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
                    kind = result.kind.toPostVoteFailureKind(),
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun state(
        phase: PostVotePhase,
        request: PostVoteRequest,
    ): PostVoteUiState = PostVoteUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        postFullname = request.postFullname,
        direction = request.direction,
        completionRevision = mutableUiState.value.completionRevision,
        settlements = settled,
    )

    private fun failure(
        request: PostVoteRequest?,
        kind: PostVoteFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): PostVoteUiState = PostVoteUiState(
        phase = PostVotePhase.FAILED,
        accountStableId = request?.accountStableId?.trim()?.takeIf(String::isNotEmpty),
        postFullname = request?.postFullname?.trim()?.takeIf(String::isNotEmpty),
        direction = request?.direction,
        failureKind = kind,
        retryable = retryable,
        statusCode = statusCode,
        failureDetails = failureDetails,
        completionRevision = mutableUiState.value.completionRevision,
        settlements = settle(request, succeeded = false),
    )

    // VOTE02_SETTLEMENTS_SURVIVE_CONFLATION_V1 (#60): every end of a vote is
    // recorded, so the in-flight marker of each target is released even when
    // a later state replaces this one before the UI reads it.
    private fun settle(
        request: PostVoteRequest?,
        succeeded: Boolean,
    ): List<PostVoteSettlement> {
        val account = request?.accountStableId?.trim().orEmpty()
        val target = request?.postFullname?.trim().orEmpty()
        if (request == null || account.isEmpty() || target.isEmpty()) return settled
        settled = (
            settled + PostVoteSettlement(
                sequence = nextSettlementSequence++,
                accountStableId = account,
                postFullname = target,
                direction = request.direction,
                succeeded = succeeded,
            )
            ).takeLast(POST_VOTE_SETTLEMENTS_KEPT)
        return settled
    }

    private fun resetUiState() {
        mutableUiState.value = PostVoteUiState(
            completionRevision = mutableUiState.value.completionRevision,
            settlements = settled,
        )
    }
}

private val AUTHORIZATION_PHASES = setOf(
    PostVotePhase.AUTHORIZATION_REQUIRED,
    PostVotePhase.AUTHORIZING,
    PostVotePhase.AWAITING_CALLBACK,
)

private fun AuthCommentWriteCapabilityFailureKind.toPostVoteFailureKind():
    PostVoteFailureKind = when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            PostVoteFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            PostVoteFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> PostVoteFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            PostVoteFailureKind.SESSION
    }
