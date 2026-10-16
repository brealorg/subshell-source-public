package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.account.wirePermissions
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MODERATION02A5A5_ACCOUNT_COMMUNITY_PINNED_INVITE_COORDINATOR_V1
internal class ModeratorInviteCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedRedditModeratorInviteWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val activeAccountUsername: () -> String?,
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(ModeratorInviteUiState())
    private var pendingAuthorizationRequest: RedditModeratorInviteRequest? = null

    val uiState: StateFlow<ModeratorInviteUiState> = mutableUiState.asStateFlow()

    fun open(accountStableId: String, rawCommunityName: String) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }.getOrNull()
        if (account.isEmpty() || community == null || activeAccountStableId()?.trim() != account) {
            pendingAuthorizationRequest = null
            val validContext = account.isNotEmpty() && community != null
            mutableUiState.value = ModeratorInviteUiState(
                visible = true,
                accountStableId = account.takeIf { validContext },
                communityName = community.takeIf { validContext },
                failureKind = ModeratorInviteFailureKind.ACCOUNT_CHANGED,
            )
            return
        }
        val current = mutableUiState.value
        // LOW10_REOPEN_KEEPS_AN_INVITE_IN_FLIGHT_V1 (OCB-427): while an invite
        // still runs (it holds mutationMutex) the reopened dialog shows it
        // sending, and its result lands as usual.
        if (current.matches(account, community) && mutationMutex.isLocked) {
            mutableUiState.value = current.copy(visible = true)
            return
        }
        mutableUiState.value = if (current.matches(account, community)) {
            current.copy(
                visible = true,
                sending = false,
                authorizing = false,
                authorizationRequired = false,
                authorizationReady = false,
                failureKind = null,
                failureHttpStatusCode = null,
                failureDetails = null,
                lastInvitedUsername = null,
                lastInvitedPermissions = null,
            )
        } else {
            ModeratorInviteUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
            )
        }
        pendingAuthorizationRequest = null
    }

    suspend fun invite(request: RedditModeratorInviteRequest) {
        val normalized = try {
            request.normalized()
        } catch (_: IllegalArgumentException) {
            mutableUiState.value = mutableUiState.value.copy(
                sending = false,
                failureKind = ModeratorInviteFailureKind.VALIDATION,
                failureDetails = null,
            )
            return
        }
        val current = mutableUiState.value
        val active = activeAccountStableId()?.trim()
        val activeUsername = activeAccountUsername()
            ?.trim()
            ?.removePrefix("u/")
            ?.removePrefix("U/")
        if (
            !current.visible ||
            !current.matches(normalized.accountStableId, normalized.communityName) ||
            active != normalized.accountStableId
        ) {
            mutableUiState.value = current.copy(
                sending = false,
                failureKind = ModeratorInviteFailureKind.ACCOUNT_CHANGED,
            )
            return
        }
        if (
            activeUsername != null &&
            normalizeRedditSafetyUsername(normalized.username)
                .equals(activeUsername, ignoreCase = true)
        ) {
            mutableUiState.value = current.copy(
                sending = false,
                failureKind = ModeratorInviteFailureKind.VALIDATION,
                failureDetails = null,
            )
            return
        }
        if (!mutationMutex.tryLock()) {
            mutableUiState.value = current.copy(
                failureKind = ModeratorInviteFailureKind.ALREADY_MUTATING,
            )
            return
        }
        try {
            if (mutableUiState.value.busy) return
            val configuredWriter = writer
            if (configuredWriter == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    failureKind = ModeratorInviteFailureKind.CLIENT_CONFIGURATION,
                )
                return
            }
            pendingAuthorizationRequest = null
            mutableUiState.value = mutableUiState.value.copy(
                sending = true,
                authorizationRequired = false,
                authorizationReady = false,
                failureKind = null,
                failureHttpStatusCode = null,
                failureDetails = null,
            )
            val result = try {
                configuredWriter.invite(normalized)
            } catch (cancelled: CancellationException) {
                mutableUiState.value = mutableUiState.value.copy(
                    sending = false,
                    failureKind = ModeratorInviteFailureKind.OUTCOME_UNKNOWN,
                )
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModeratorInviteResult.Failed(
                    ModeratorInviteFailureKind.OUTCOME_UNKNOWN,
                )
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                pendingAuthorizationRequest = null
                mutableUiState.value = mutableUiState.value.copy(
                    sending = false,
                    authorizationRequired = false,
                    failureKind = ModeratorInviteFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            when (result) {
                AuthenticatedRedditModeratorInviteResult.Acknowledged -> {
                    pendingAuthorizationRequest = null
                    mutableUiState.value = mutableUiState.value.copy(
                        sending = false,
                        authorizationRequired = false,
                        authorizationReady = false,
                        failureKind = null,
                        failureHttpStatusCode = null,
                        failureDetails = null,
                        completionRevision = mutableUiState.value.completionRevision + 1L,
                        lastInvitedUsername = normalized.username,
                        lastInvitedPermissions = normalized.wirePermissions(),
                    )
                }
                AuthenticatedRedditModeratorInviteResult.AuthorizationRequired -> {
                    pendingAuthorizationRequest = normalized
                    mutableUiState.value = mutableUiState.value.copy(
                        sending = false,
                        authorizationRequired = true,
                        authorizationReady = false,
                        failureKind = null,
                        failureDetails = null,
                    )
                }
                is AuthenticatedRedditModeratorInviteResult.Failed -> {
                    pendingAuthorizationRequest = null
                    mutableUiState.value = mutableUiState.value.copy(
                        sending = false,
                        authorizationRequired = false,
                        authorizationReady = false,
                        failureKind = result.kind,
                        failureHttpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                }
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingAuthorizationRequest ?: return AuthBrowserLaunchResult.NotStarted
        val current = mutableUiState.value
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (
            !current.visible ||
            !current.matches(request.accountStableId, request.communityName) ||
            activeAccountStableId()?.trim() != request.accountStableId ||
            configuredCapability == null ||
            configuredClientMode == null
        ) {
            mutableUiState.value = current.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = ModeratorInviteFailureKind.CLIENT_CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        mutableUiState.value = current.copy(
            authorizing = true,
            authorizationRequired = true,
            authorizationReady = false,
            failureKind = null,
        )
        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = configuredCapability.beginAuthorization(request.accountStableId),
                clientMode = configuredClientMode,
                freshAccountSession = true,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = ModeratorInviteFailureKind.AUTHORIZATION,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        capability?.cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
            authorizationReady = false,
            failureKind = ModeratorInviteFailureKind.AUTHORIZATION,
        )
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val configuredCapability = capability ?: return false
        // A5A6 shares the MODERATION_TEAM authorization owner. Do not claim a callback
        // unless this coordinator actually owns an in-memory pending invitation.
        if (pendingAuthorizationRequest == null) return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                authorizationReady = false,
            )
            return true
        }
        if (!configuredCapability.hasStoredCallback()) return false
        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        val configuredCapability = capability ?: return false
        if (pendingAuthorizationRequest == null) return false
        return when {
            configuredCapability.hasStoredCallback() -> {
                completeAuthorization()
                true
            }
            configuredCapability.hasPendingAuthorization() -> true
            else -> false
        }
    }

    fun accountChanged(accountStableId: String) {
        val current = mutableUiState.value
        if (current.accountStableId != null && current.accountStableId != accountStableId.trim()) {
            if (pendingAuthorizationRequest != null) {
                capability?.cancelPendingAuthorization()
            }
            pendingAuthorizationRequest = null
            mutableUiState.value = ModeratorInviteUiState()
        }
    }

    fun dismiss() {
        if (pendingAuthorizationRequest != null) {
            capability?.cancelPendingAuthorization()
        }
        pendingAuthorizationRequest = null
        val inFlight = mutationMutex.isLocked
        mutableUiState.value = mutableUiState.value.copy(
            visible = false,
            sending = mutableUiState.value.sending && inFlight,
            authorizing = false,
            authorizationRequired = false,
            authorizationReady = false,
            failureKind = null,
            failureHttpStatusCode = null,
        )
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val request = pendingAuthorizationRequest ?: return
        val current = mutableUiState.value
        if (!current.matches(request.accountStableId, request.communityName)) return
        mutableUiState.value = current.copy(authorizing = true)
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (
                    result.accountStableId != request.accountStableId ||
                    activeAccountStableId()?.trim() != request.accountStableId ||
                    !mutableUiState.value.matches(request.accountStableId, request.communityName)
                ) {
                    pendingAuthorizationRequest = null
                    mutableUiState.value = mutableUiState.value.copy(
                        authorizing = false,
                        authorizationRequired = false,
                        authorizationReady = false,
                        failureKind = ModeratorInviteFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                // Authorization completion never replays a moderator invitation.
                // A new explicit Invite moderator confirmation is required.
                pendingAuthorizationRequest = null
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = false,
                    authorizationReady = true,
                    failureKind = null,
                    failureHttpStatusCode = null,
                    failureDetails = null,
                )
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = true,
                    authorizationReady = false,
                    failureKind = result.kind.toModeratorInviteFailureKind(),
                )
            }
        }
    }
}

private fun AuthCommentWriteCapabilityFailureKind.toModeratorInviteFailureKind():
    ModeratorInviteFailureKind = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT -> ModeratorInviteFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.STORAGE -> ModeratorInviteFailureKind.SESSION
    AuthCommentWriteCapabilityFailureKind.CALLBACK,
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> ModeratorInviteFailureKind.AUTHORIZATION
}
