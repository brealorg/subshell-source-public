package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.account.isReportControl
import app.subshell.prototype.data.account.isContentControl
import app.subshell.prototype.data.account.removesFromModerationQueue
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationActionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationWriter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MOD01A2_OWNER_BOUND_APPROVE_REMOVE_COORDINATOR_V1
internal class ModerationActionCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedRedditModerationWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val onOptimisticApply: (RedditModeratorActionRequest) -> Unit = {},
    private val onRollback: (RedditModeratorActionRequest) -> Unit = {},
    private val onReconcile: (RedditModeratorActionRequest) -> Unit = {},
    // ISSUE226_REMOVED_COMMENT_IS_MARKED_V1: runs only when Reddit confirmed
    // the action (never on an unknown outcome).
    private val onApplied: (RedditModeratorActionRequest) -> Unit = {},
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(RedditModeratorActionUiState())
    private var pendingRequest: RedditModeratorActionRequest? = null

    val uiState: StateFlow<RedditModeratorActionUiState> = mutableUiState.asStateFlow()

    suspend fun moderate(request: RedditModeratorActionRequest) {
        // Report writes require the separate fresh-read confirmation path below.
        if (request.action.isReportControl()) return
        // MODERATION02A3_CONTENT_CONTROL_REQUIRES_FRESH_CONFIRMATION_V1
        if (request.action.isContentControl()) return
        val normalized = try {
            request.normalized()
        } catch (_: IllegalArgumentException) {
            pendingRequest = null
            mutableUiState.value = RedditModeratorActionUiState(
                phase = RedditModeratorActionPhase.FAILED,
                failureKind = RedditModeratorActionFailureKind.VALIDATION,
                completionRevision = mutableUiState.value.completionRevision,
            )
            return
        }
        execute(normalized)
    }

    suspend fun retry() {
        val request = pendingRequest
            ?.takeIf {
                !it.action.isReportControl() &&
                    !it.action.isContentControl() &&
                    mutableUiState.value.phase == RedditModeratorActionPhase.FAILED &&
                    mutableUiState.value.retryable
            }
            ?: return
        execute(request)
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase ==
                    RedditModeratorActionPhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = RedditModeratorActionFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (activeAccountStableId()?.trim() != request.accountStableId) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = RedditModeratorActionFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        mutableUiState.value = mutableUiState.value.copy(
            phase = RedditModeratorActionPhase.AUTHORIZING,
        )
        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(
                request.accountStableId,
            )
            mutableUiState.value = mutableUiState.value.copy(
                phase = RedditModeratorActionPhase.AWAITING_CALLBACK,
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
                kind = RedditModeratorActionFailureKind.AUTHORIZATION,
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
            kind = RedditModeratorActionFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: a new action while an earlier
    // authorization still waits for the browser drops that authorization, so a
    // late callback belongs to nobody and the earlier action is never sent.
    private fun dropWaitingAuthorization() {
        if (mutableUiState.value.phase != RedditModeratorActionPhase.AWAITING_CALLBACK) return
        capability?.cancelPendingAuthorization()
        pendingRequest = null
    }

    fun cancelPendingAuthorization() {
        val request = pendingRequest
        capability?.cancelPendingAuthorization()
        mutableUiState.value = if (request == null) {
            RedditModeratorActionUiState(
                completionRevision = mutableUiState.value.completionRevision,
            )
        } else {
            failure(
                request = request,
                kind = RedditModeratorActionFailureKind.CALLBACK,
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
                failure(it, RedditModeratorActionFailureKind.CONFIGURATION)
            } ?: RedditModeratorActionUiState(
                completionRevision = mutableUiState.value.completionRevision,
            )
            return true
        }
        if (!configuredCapability.hasStoredCallback()) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = request?.let {
                failure(
                    request = it,
                    kind = RedditModeratorActionFailureKind.CALLBACK,
                    retryable = true,
                )
            } ?: RedditModeratorActionUiState(
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

    fun reset() {
        pendingRequest = null
        mutableUiState.value = RedditModeratorActionUiState(
            completionRevision = mutableUiState.value.completionRevision,
        )
    }

    // MODERATION02A2_SHARED_WRITE_LOCK_FRESH_READ_AND_NO_REPLAY_V1
    suspend fun reportAction(
        request: RedditModeratorActionRequest,
        verifyBeforeWrite: suspend () -> Boolean,
    ): AuthenticatedRedditModerationActionResult {
        fun rejected(kind: RedditModeratorActionFailureKind) =
            AuthenticatedRedditModerationActionResult.Failed(kind = kind)
        if (!request.action.isReportControl()) return rejected(RedditModeratorActionFailureKind.VALIDATION)
        if (!mutationMutex.tryLock()) return rejected(RedditModeratorActionFailureKind.ALREADY_MUTATING)
        try {
            if (mutableUiState.value.inProgress) return rejected(RedditModeratorActionFailureKind.ALREADY_MUTATING)
            dropWaitingAuthorization()
            if (activeAccountStableId()?.trim() != request.accountStableId) {
                return rejected(RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
            }
            val configuredWriter = writer ?: return rejected(RedditModeratorActionFailureKind.CONFIGURATION)
            pendingRequest = null
            mutableUiState.value = RedditModeratorActionUiState(
                phase = RedditModeratorActionPhase.SENDING,
                request = request,
                completionRevision = mutableUiState.value.completionRevision,
            )
            // The shared lock includes the preflight: content actions cannot
            // interleave between the report read and the confirmed report write.
            if (!verifyBeforeWrite() || activeAccountStableId()?.trim() != request.accountStableId) {
                mutableUiState.value = failure(request, RedditModeratorActionFailureKind.VALIDATION)
                return rejected(RedditModeratorActionFailureKind.VALIDATION)
            }
            val result = try {
                configuredWriter.mutate(request.normalized())
            } catch (cancelled: CancellationException) {
                mutableUiState.value = failure(request, RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
                throw cancelled
            } catch (_: Exception) {
                rejected(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
            }
            if (activeAccountStableId()?.trim() != request.accountStableId) {
                pendingRequest = null
                mutableUiState.value = failure(request, RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
                return rejected(RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
            }
            when (result) {
                is AuthenticatedRedditModerationActionResult.Updated -> {
                    mutableUiState.value = RedditModeratorActionUiState(
                        phase = RedditModeratorActionPhase.SUCCEEDED,
                        request = request,
                        completionRevision = mutableUiState.value.completionRevision + 1,
                    )
                    onApplied(request)
                    onReconcile(request)
                }
                AuthenticatedRedditModerationActionResult.AuthorizationRequired -> {
                    pendingRequest = request
                    mutableUiState.value = RedditModeratorActionUiState(
                        phase = RedditModeratorActionPhase.AUTHORIZATION_REQUIRED,
                        request = request,
                        failureKind = RedditModeratorActionFailureKind.AUTHORIZATION,
                        completionRevision = mutableUiState.value.completionRevision,
                    )
                }
                is AuthenticatedRedditModerationActionResult.Failed -> {
                    // No pending retry for report writes, including definite failures.
                    mutableUiState.value = failure(
                        request, result.kind, retryable = false,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                    if (result.kind == RedditModeratorActionFailureKind.OUTCOME_UNKNOWN) onReconcile(request)
                }
            }
            return result
        } catch (cancelled: CancellationException) {
            pendingRequest = null
            mutableUiState.value = failure(request, RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
            throw cancelled
        } catch (_: Exception) {
            pendingRequest = null
            mutableUiState.value = failure(request, RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
            return rejected(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
        } finally {
            mutationMutex.unlock()
        }
    }

    // MODERATION02A3_SHARED_WRITE_LOCK_FRESH_CONTROL_READBACK_V1
    suspend fun contentControlAction(
        request: RedditModeratorActionRequest,
        verifyBeforeWrite: suspend () -> Boolean,
    ): AuthenticatedRedditModerationActionResult {
        fun rejected(kind: RedditModeratorActionFailureKind) =
            AuthenticatedRedditModerationActionResult.Failed(kind = kind)
        if (!request.action.isContentControl()) {
            return rejected(RedditModeratorActionFailureKind.VALIDATION)
        }
        if (!mutationMutex.tryLock()) {
            return rejected(RedditModeratorActionFailureKind.ALREADY_MUTATING)
        }
        try {
            if (mutableUiState.value.inProgress) {
                return rejected(RedditModeratorActionFailureKind.ALREADY_MUTATING)
            }
            dropWaitingAuthorization()
            if (activeAccountStableId()?.trim() != request.accountStableId) {
                return rejected(RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
            }
            val configuredWriter =
                writer ?: return rejected(RedditModeratorActionFailureKind.CONFIGURATION)
            pendingRequest = null
            mutableUiState.value = RedditModeratorActionUiState(
                phase = RedditModeratorActionPhase.SENDING,
                request = request,
                completionRevision = mutableUiState.value.completionRevision,
            )
            if (
                !verifyBeforeWrite() ||
                activeAccountStableId()?.trim() != request.accountStableId
            ) {
                mutableUiState.value =
                    failure(request, RedditModeratorActionFailureKind.VALIDATION)
                return rejected(RedditModeratorActionFailureKind.VALIDATION)
            }
            val result = try {
                configuredWriter.mutate(request.normalized())
            } catch (cancelled: CancellationException) {
                mutableUiState.value =
                    failure(request, RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
                throw cancelled
            } catch (_: Exception) {
                rejected(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
            }
            if (activeAccountStableId()?.trim() != request.accountStableId) {
                pendingRequest = null
                mutableUiState.value =
                    failure(request, RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
                return rejected(RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
            }
            when (result) {
                is AuthenticatedRedditModerationActionResult.Updated -> {
                    mutableUiState.value = RedditModeratorActionUiState(
                        phase = RedditModeratorActionPhase.SUCCEEDED,
                        request = request,
                        completionRevision =
                            mutableUiState.value.completionRevision + 1L,
                    )
                    onApplied(request)
                    onReconcile(request)
                }

                AuthenticatedRedditModerationActionResult.AuthorizationRequired -> {
                    pendingRequest = request
                    mutableUiState.value = RedditModeratorActionUiState(
                        phase = RedditModeratorActionPhase.AUTHORIZATION_REQUIRED,
                        request = request,
                        failureKind = RedditModeratorActionFailureKind.AUTHORIZATION,
                        completionRevision = mutableUiState.value.completionRevision,
                    )
                }

                is AuthenticatedRedditModerationActionResult.Failed -> {
                    mutableUiState.value = failure(
                        request = request,
                        kind = result.kind,
                        retryable = false,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                    if (result.kind == RedditModeratorActionFailureKind.OUTCOME_UNKNOWN) {
                        onReconcile(request)
                    }
                }
            }
            return result
        } catch (cancelled: CancellationException) {
            pendingRequest = null
            mutableUiState.value =
                failure(request, RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
            throw cancelled
        } catch (_: Exception) {
            pendingRequest = null
            mutableUiState.value =
                failure(request, RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
            return rejected(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
        } finally {
            mutationMutex.unlock()
        }
    }

    private suspend fun execute(
        request: RedditModeratorActionRequest,
        ownerAuthorizedContinuation: Boolean = false,
    ) {
        // MODERATION02A1_DROP_BUSY_REQUESTS_NO_QUEUED_REPLAY_V1
        // A second tap must not become a delayed moderation write.
        if (!mutationMutex.tryLock()) return
        try {
            if (!ownerAuthorizedContinuation && mutableUiState.value.inProgress) return
            if (!ownerAuthorizedContinuation) dropWaitingAuthorization()
            if (
                !ownerAuthorizedContinuation &&
                activeAccountStableId()?.trim() != request.accountStableId
            ) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request,
                    RedditModeratorActionFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            val configuredWriter = writer
            if (configuredWriter == null) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request,
                    RedditModeratorActionFailureKind.CONFIGURATION,
                )
                return
            }
            pendingRequest = request
            mutableUiState.value = RedditModeratorActionUiState(
                phase = RedditModeratorActionPhase.SENDING,
                request = request,
                completionRevision = mutableUiState.value.completionRevision,
            )
            val queueDisposition = request.action.removesFromModerationQueue()
            if (queueDisposition) {
                onOptimisticApply(request)
            }
            val result = try {
                configuredWriter.mutate(request)
            } catch (cancelled: CancellationException) {
                if (queueDisposition) {
                    onRollback(request)
                }
                throw cancelled
            } catch (_: Exception) {
                // LOW10_UNEXPECTED_WRITE_ERROR_IS_UNKNOWN_OUTCOME_V1 (OCB-589): the
                // request may have reached Reddit; the queue is read back
                // (reconciled), not rolled back, as report/content-control do.
                AuthenticatedRedditModerationActionResult.Failed(
                    kind = RedditModeratorActionFailureKind.OUTCOME_UNKNOWN,
                    retryable = false,
                    failureDetails = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.UNKNOWN,
                    ),
                )
            }
            when (result) {
                is AuthenticatedRedditModerationActionResult.Updated -> {
                    pendingRequest = null
                    mutableUiState.value = RedditModeratorActionUiState(
                        phase = RedditModeratorActionPhase.SUCCEEDED,
                        request = request,
                        completionRevision = mutableUiState.value.completionRevision + 1L,
                        removalReasonWarning = result.removalReasonWarning,
                        removalMessageOutcome = result.removalMessageOutcome,
                    )
                    onApplied(request)
                    onReconcile(request)
                }

                AuthenticatedRedditModerationActionResult.AuthorizationRequired -> {
                    if (queueDisposition) {
                        onRollback(request)
                    }
                    pendingRequest = request
                    mutableUiState.value = RedditModeratorActionUiState(
                        phase = RedditModeratorActionPhase.AUTHORIZATION_REQUIRED,
                        request = request,
                        failureKind = RedditModeratorActionFailureKind.AUTHORIZATION,
                        retryable = true,
                        completionRevision = mutableUiState.value.completionRevision,
                    )
                }

                is AuthenticatedRedditModerationActionResult.Failed -> {
                    val outcomeUnknown =
                        result.kind == RedditModeratorActionFailureKind.OUTCOME_UNKNOWN
                    if (outcomeUnknown) {
                        onReconcile(request)
                    } else if (queueDisposition) {
                        onRollback(request)
                    }
                    // Unknown outcomes must never retain a retryable write.
                    val retryable = result.retryable && !outcomeUnknown
                    pendingRequest = request.takeIf { retryable }
                    mutableUiState.value = failure(
                        request = request,
                        kind = result.kind,
                        retryable = retryable,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                }
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val request = pendingRequest
        request?.let {
            mutableUiState.value = mutableUiState.value.copy(
                phase = RedditModeratorActionPhase.AUTHORIZING,
            )
        }
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    pendingRequest = null
                    reset()
                    return
                }
                if (
                    result.accountStableId != request.accountStableId ||
                    activeAccountStableId()?.trim() != request.accountStableId
                ) {
                    pendingRequest = null
                    mutableUiState.value = failure(
                        request,
                        RedditModeratorActionFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = RedditModeratorActionUiState(
                    request = request,
                    completionRevision = mutableUiState.value.completionRevision,
                )
                if (request.action.isReportControl()) {
                    // MODERATION02A2_AUTHORIZATION_NEVER_REPLAYS_REPORT_WRITE_V1
                    // Permission is now ready. The report inspector must read the
                    // target again and obtain a new explicit confirmation.
                    pendingRequest = null
                } else if (request.action.isContentControl()) {
                    // MODERATION02A3_AUTHORIZATION_NEVER_REPLAYS_CONTENT_CONTROL_V1
                    // Re-read the exact target and obtain a new confirmation.
                    pendingRequest = null
                } else {
                    execute(request, ownerAuthorizedContinuation = true)
                }
            }

            is AuthCommentWriteCapabilityResult.Failed -> {
                if (request == null) {
                    pendingRequest = null
                    reset()
                    return
                }
                pendingRequest = request.takeIf { result.retryable }
                mutableUiState.value = failure(
                    request = request,
                    kind = result.kind.toModerationFailureKind(),
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun failure(
        request: RedditModeratorActionRequest,
        kind: RedditModeratorActionFailureKind,
        retryable: Boolean = false,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = RedditModeratorActionUiState(
        phase = RedditModeratorActionPhase.FAILED,
        request = request,
        failureKind = kind,
        retryable = retryable && !request.action.isReportControl() &&
            !request.action.isContentControl(),
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        completionRevision = mutableUiState.value.completionRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toModerationFailureKind() = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
        RedditModeratorActionFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.CALLBACK ->
        RedditModeratorActionFailureKind.CALLBACK
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> RedditModeratorActionFailureKind.AUTHORIZATION
    AuthCommentWriteCapabilityFailureKind.STORAGE ->
        RedditModeratorActionFailureKind.SESSION
}
