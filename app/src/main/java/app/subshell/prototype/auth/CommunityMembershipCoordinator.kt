package app.subshell.prototype.auth

import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipRequest
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.NOT_CHECKED_MEMBERSHIP_MESSAGE
import app.subshell.prototype.data.account.normalizedCommunityMembershipRequest
import app.subshell.prototype.data.account.normalizeCommunityMembershipName
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommunityMembershipResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommunityMembershipWriter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class CommunityMembershipCoordinator(
    private val capability: AuthCustomFeedWriteCapability?,
    private val writer: AuthenticatedCommunityMembershipWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val onMembershipUpdated: (CommunityMembershipRequest) -> Unit = {},
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(CommunityMembershipUiState())
    private var pendingRequest: CommunityMembershipRequest? = null
    private var listDisagreed = false

    val uiState: StateFlow<CommunityMembershipUiState> =
        mutableUiState.asStateFlow()

    suspend fun update(request: CommunityMembershipRequest) {
        val normalized = try {
            request.normalizedCommunityMembershipRequest()
        } catch (_: IllegalArgumentException) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = CommunityMembershipFailureKind.VALIDATION,
            )
            return
        }

        mutationMutex.withLock {
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = CommunityMembershipFailureKind.ACCOUNT_CHANGED,
                )
                return@withLock
            }

            val configuredWriter = writer
            if (configuredWriter == null) {
                pendingRequest = null
                mutableUiState.value = failure(
                    request = normalized,
                    kind = CommunityMembershipFailureKind.CONFIGURATION,
                )
                return@withLock
            }

            pendingRequest = normalized
            listDisagreed = false
            mutableUiState.value = state(
                phase = CommunityMembershipPhase.SENDING,
                request = normalized,
            )

            val result = try {
                configuredWriter.update(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedCommunityMembershipResult.Failed(
                    kind = CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                    retryable = false,
                    failureDetails = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.UNKNOWN,
                    ),
                )
            }

            when (result) {
                is AuthenticatedCommunityMembershipResult.Updated -> {
                    pendingRequest = null
                    val nextRevision =
                        mutableUiState.value.completionRevision + 1L
                    mutableUiState.value = CommunityMembershipUiState(
                        phase = CommunityMembershipPhase.SUCCEEDED,
                        accountStableId = result.request.accountStableId,
                        communityName = result.request.communityName,
                        action = result.request.action,
                        completionRevision = nextRevision,
                    )
                    onMembershipUpdated(result.request)
                }

                AuthenticatedCommunityMembershipResult.AuthorizationRequired -> {
                    pendingRequest = normalized
                    mutableUiState.value = state(
                        phase = CommunityMembershipPhase.AUTHORIZATION_REQUIRED,
                        request = normalized,
                    )
                }

                is AuthenticatedCommunityMembershipResult.Failed -> {
                    pendingRequest = null
                    mutableUiState.value = failure(
                        request = normalized,
                        kind = result.kind,
                        retryable = result.retryable,
                        statusCode = result.statusCode,
                        failureDetails = result.failureDetails,
                    )
                    if (
                        result.kind ==
                        CommunityMembershipFailureKind.OUTCOME_UNKNOWN
                    ) {
                        onMembershipUpdated(normalized)
                    }
                }
            }
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase ==
                    CommunityMembershipPhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted

        val configuredCapability = capability
        val configuredClientMode = clientMode

        if (
            configuredCapability == null ||
            configuredClientMode == null
        ) {
            mutableUiState.value = failure(
                request = request,
                kind = CommunityMembershipFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        if (activeAccountStableId()?.trim() != request.accountStableId) {
            pendingRequest = null
            mutableUiState.value = failure(
                request = request,
                kind = CommunityMembershipFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(
            phase = CommunityMembershipPhase.AUTHORIZING,
            request = request,
        )

        return try {
            val authorizationUrl =
                configuredCapability.beginAuthorization(
                    request.accountStableId,
                )
            mutableUiState.value = state(
                phase = CommunityMembershipPhase.AWAITING_CALLBACK,
                request = request,
            )
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                request = request,
                kind = CommunityMembershipFailureKind.AUTHORIZATION,
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
            kind = CommunityMembershipFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun cancelPendingAuthorization() {
        capability?.cancelPendingAuthorization()
        pendingRequest = null
        resetUiState()
    }

    suspend fun handleCallbackResultTokenIfOwned(
        resultToken: String,
    ): Boolean {
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }

        val configuredCapability = capability
        val request = pendingRequest

        if (configuredCapability == null) {
            pendingRequest = null
            mutableUiState.value = if (request == null) {
                CommunityMembershipUiState(
                    completionRevision =
                        mutableUiState.value.completionRevision,
                )
            } else {
                failure(
                    request = request,
                    kind = CommunityMembershipFailureKind.CONFIGURATION,
                )
            }
            return true
        }

        if (!configuredCapability.hasStoredCallback()) {
            configuredCapability.cancelPendingAuthorization()
            pendingRequest = null
            mutableUiState.value = if (request == null) {
                CommunityMembershipUiState(
                    completionRevision =
                        mutableUiState.value.completionRevision,
                )
            } else {
                failure(
                    request = request,
                    kind = CommunityMembershipFailureKind.CALLBACK,
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

    fun authoritativeRefreshApplied(
        accountStableId: String,
        joinedCommunityNames: Set<String>,
    ) {
        val account = accountStableId.trim()
        if (account.isEmpty()) return

        val current = mutableUiState.value
        val target = current.communityName ?: return
        val action = current.action ?: return

        val reconciliable =
            current.phase == CommunityMembershipPhase.SUCCEEDED ||
                (
                    current.phase == CommunityMembershipPhase.FAILED &&
                        (
                            current.failureKind ==
                                CommunityMembershipFailureKind.OUTCOME_UNKNOWN ||
                                current.failureKind ==
                                CommunityMembershipFailureKind.NOT_CONFIRMED
                        )
                )

        if (
            !reconciliable ||
            current.accountStableId != account
        ) {
            return
        }

        val normalizedTarget =
            runCatching {
                normalizeCommunityMembershipName(target)
            }.getOrNull() ?: return

        val authoritativeJoined =
            joinedCommunityNames.any { candidate ->
                runCatching {
                    normalizeCommunityMembershipName(candidate)
                }.getOrNull() == normalizedTarget
            }

        val expectedJoined =
            action == CommunityMembershipAction.JOIN

        if (authoritativeJoined == expectedJoined) {
            resetUiState()
            return
        }
        listDisagreed = true

        // ISSUE43_UNCONFIRMED_MEMBERSHIP_STAYS_PENDING_V1: a list that still
        // disagrees leaves an unknown outcome in place; only a confirming
        // list or reconciliationExhausted() ends it.
        if (current.phase == CommunityMembershipPhase.SUCCEEDED) {
            mutableUiState.value = current.copy(
                phase = CommunityMembershipPhase.FAILED,
                failureKind =
                    CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                failureRetryable = false,
                failureStatusCode = null,
                message =
                    "Reddit has not confirmed the membership change yet. " +
                        "subshell is still checking.",
            )
        }
    }

    /**
     * The last reconciliation step ran and the outcome is still unknown:
     * stop blocking the action and say whether Reddit's list disagreed or
     * no list arrived at all. Nothing is re-dispatched; a later list that
     * agrees still clears the state.
     */
    fun reconciliationExhausted(request: CommunityMembershipRequest) {
        val current = mutableUiState.value
        if (
            !current.matches(request.accountStableId, request.communityName) ||
            current.phase != CommunityMembershipPhase.FAILED ||
            current.failureKind != CommunityMembershipFailureKind.OUTCOME_UNKNOWN
        ) {
            return
        }
        mutableUiState.value = current.copy(
            failureKind = CommunityMembershipFailureKind.NOT_CONFIRMED,
            failureRetryable = false,
            failureStatusCode = null,
            message =
                if (listDisagreed) null else NOT_CHECKED_MEMBERSHIP_MESSAGE,
        )
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val request = pendingRequest

        if (request != null) {
            mutableUiState.value = state(
                phase = CommunityMembershipPhase.AUTHORIZING,
                request = request,
            )
        }

        when (
            val result =
                configuredCapability.completePendingAuthorization()
        ) {
            is AuthCustomFeedWriteCapabilityResult.Upgraded -> {
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
                        kind =
                            CommunityMembershipFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }

                mutableUiState.value = state(
                    phase = CommunityMembershipPhase.IDLE,
                    request = request,
                )
                update(request)
            }

            is AuthCustomFeedWriteCapabilityResult.Failed -> {
                if (request == null) {
                    pendingRequest = null
                    resetUiState()
                    return
                }

                pendingRequest = null
                mutableUiState.value = failure(
                    request = request,
                    kind = result.kind.toCommunityMembershipFailureKind(),
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun state(
        phase: CommunityMembershipPhase,
        request: CommunityMembershipRequest,
    ): CommunityMembershipUiState =
        CommunityMembershipUiState(
            phase = phase,
            accountStableId = request.accountStableId,
            communityName = request.communityName,
            action = request.action,
            completionRevision =
                mutableUiState.value.completionRevision,
        )

    private fun failure(
        request: CommunityMembershipRequest?,
        kind: CommunityMembershipFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): CommunityMembershipUiState =
        CommunityMembershipUiState(
            phase = CommunityMembershipPhase.FAILED,
            accountStableId =
                request?.accountStableId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
            communityName =
                request?.communityName
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
            action = request?.action,
            failureKind = kind,
            failureRetryable = retryable,
            failureStatusCode = statusCode,
            failureDetails = failureDetails,
            completionRevision =
                mutableUiState.value.completionRevision,
        )

    private fun resetUiState() {
        mutableUiState.value = CommunityMembershipUiState(
            completionRevision =
                mutableUiState.value.completionRevision,
        )
    }
}

private fun AuthCustomFeedWriteCapabilityFailureKind
    .toCommunityMembershipFailureKind():
    CommunityMembershipFailureKind =
    when (this) {
        AuthCustomFeedWriteCapabilityFailureKind.ACCOUNT ->
            CommunityMembershipFailureKind.ACCOUNT_CHANGED

        AuthCustomFeedWriteCapabilityFailureKind.CALLBACK ->
            CommunityMembershipFailureKind.CALLBACK

        AuthCustomFeedWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCustomFeedWriteCapabilityFailureKind.IDENTITY,
        AuthCustomFeedWriteCapabilityFailureKind.SCOPE,
        ->
            CommunityMembershipFailureKind.AUTHORIZATION

        AuthCustomFeedWriteCapabilityFailureKind.STORAGE ->
            CommunityMembershipFailureKind.SESSION
    }
