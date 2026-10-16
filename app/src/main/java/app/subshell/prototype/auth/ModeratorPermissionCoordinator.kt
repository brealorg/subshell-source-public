package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.account.wirePermissions
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MODERATION02A5A6_ACCOUNT_COMMUNITY_TARGET_PINNED_PERMISSION_COORDINATOR_V1
internal class ModeratorPermissionCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedRedditModeratorPermissionWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val activeAccountUsername: () -> String?,
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(ModeratorPermissionUpdateUiState())
    private var pendingAuthorizationRequest: RedditModeratorPermissionUpdateRequest? = null

    val uiState: StateFlow<ModeratorPermissionUpdateUiState> = mutableUiState.asStateFlow()

    fun open(
        accountStableId: String,
        rawCommunityName: String,
        rawUsername: String,
    ) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }.getOrNull()
        val username = runCatching { normalizeRedditSafetyUsername(rawUsername) }.getOrNull()
        val activeUsername = activeAccountUsername()
            ?.trim()
            ?.removePrefix("u/")
            ?.removePrefix("U/")
        val validContext = account.isNotEmpty() && community != null && username != null
        if (
            !validContext ||
            activeAccountStableId()?.trim() != account ||
            (activeUsername != null && username.equals(activeUsername, ignoreCase = true))
        ) {
            if (pendingAuthorizationRequest != null) capability?.cancelPendingAuthorization()
            pendingAuthorizationRequest = null
            mutableUiState.value = ModeratorPermissionUpdateUiState(
                visible = true,
                accountStableId = account.takeIf { validContext },
                communityName = community.takeIf { validContext },
                targetUsername = username.takeIf { validContext },
                failureKind = if (activeAccountStableId()?.trim() != account) {
                    ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED
                } else {
                    ModeratorPermissionUpdateFailureKind.VALIDATION
                },
            )
            return
        }
        if (pendingAuthorizationRequest != null) capability?.cancelPendingAuthorization()
        pendingAuthorizationRequest = null
        val current = mutableUiState.value
        mutableUiState.value = if (current.matches(account, community, username)) {
            current.copy(
                visible = true,
                sending = false,
                authorizing = false,
                authorizationRequired = false,
                authorizationReady = false,
                failureKind = null,
                failureHttpStatusCode = null,
                failureDetails = null,
                lastUpdatedUsername = null,
                lastUpdatedPermissions = null,
            )
        } else {
            ModeratorPermissionUpdateUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                targetUsername = username,
            )
        }
    }

    suspend fun update(request: RedditModeratorPermissionUpdateRequest) {
        val normalized = try {
            request.normalized()
        } catch (_: IllegalArgumentException) {
            mutableUiState.value = mutableUiState.value.copy(
                sending = false,
                failureKind = ModeratorPermissionUpdateFailureKind.VALIDATION,
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
            !current.matches(
                normalized.accountStableId,
                normalized.communityName,
                normalized.username,
            ) ||
            active != normalized.accountStableId
        ) {
            mutableUiState.value = current.copy(
                sending = false,
                failureKind = ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED,
            )
            return
        }
        if (
            activeUsername != null &&
            normalized.username.equals(activeUsername, ignoreCase = true)
        ) {
            mutableUiState.value = current.copy(
                sending = false,
                failureKind = ModeratorPermissionUpdateFailureKind.VALIDATION,
                failureDetails = null,
            )
            return
        }
        if (!mutationMutex.tryLock()) {
            mutableUiState.value = current.copy(
                failureKind = ModeratorPermissionUpdateFailureKind.ALREADY_MUTATING,
            )
            return
        }
        try {
            if (mutableUiState.value.busy) return
            val configuredWriter = writer
            if (configuredWriter == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    failureKind = ModeratorPermissionUpdateFailureKind.CLIENT_CONFIGURATION,
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
                configuredWriter.update(normalized)
            } catch (cancelled: CancellationException) {
                mutableUiState.value = mutableUiState.value.copy(
                    sending = false,
                    failureKind = ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
                )
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModeratorPermissionUpdateResult.Failed(
                    ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
                )
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                pendingAuthorizationRequest = null
                mutableUiState.value = mutableUiState.value.copy(
                    sending = false,
                    authorizationRequired = false,
                    failureKind = ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            when (result) {
                AuthenticatedRedditModeratorPermissionUpdateResult.Acknowledged -> {
                    pendingAuthorizationRequest = null
                    mutableUiState.value = mutableUiState.value.copy(
                        sending = false,
                        authorizationRequired = false,
                        authorizationReady = false,
                        failureKind = null,
                        failureHttpStatusCode = null,
                        failureDetails = null,
                        completionRevision = mutableUiState.value.completionRevision + 1L,
                        lastUpdatedUsername = normalized.username,
                        lastUpdatedPermissions = normalized.wirePermissions(),
                    )
                }
                AuthenticatedRedditModeratorPermissionUpdateResult.AuthorizationRequired -> {
                    pendingAuthorizationRequest = normalized
                    mutableUiState.value = mutableUiState.value.copy(
                        sending = false,
                        authorizationRequired = true,
                        authorizationReady = false,
                        failureKind = null,
                        failureDetails = null,
                    )
                }
                is AuthenticatedRedditModeratorPermissionUpdateResult.Failed -> {
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
            !current.matches(request.accountStableId, request.communityName, request.username) ||
            activeAccountStableId()?.trim() != request.accountStableId ||
            configuredCapability == null ||
            configuredClientMode == null
        ) {
            mutableUiState.value = current.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = ModeratorPermissionUpdateFailureKind.CLIENT_CONFIGURATION,
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
                failureKind = ModeratorPermissionUpdateFailureKind.AUTHORIZATION,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        if (pendingAuthorizationRequest == null) return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
            authorizationReady = false,
            failureKind = ModeratorPermissionUpdateFailureKind.AUTHORIZATION,
        )
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val configuredCapability = capability ?: return false
        if (pendingAuthorizationRequest == null) return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            configuredCapability.cancelPendingAuthorization()
            // AUTHCALLBACK11: keep the request, as the invite does. The dialog
            // still shows Authorize (and disables Save); without the request
            // Authorize started nothing and the dialog was a dead end.
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
            mutableUiState.value = ModeratorPermissionUpdateUiState()
        }
    }

    fun dismiss() {
        if (pendingAuthorizationRequest != null) {
            capability?.cancelPendingAuthorization()
        }
        pendingAuthorizationRequest = null
        mutableUiState.value = mutableUiState.value.copy(
            visible = false,
            sending = false,
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
        if (!current.matches(request.accountStableId, request.communityName, request.username)) {
            return
        }
        mutableUiState.value = current.copy(authorizing = true)
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (
                    result.accountStableId != request.accountStableId ||
                    activeAccountStableId()?.trim() != request.accountStableId ||
                    !mutableUiState.value.matches(
                        request.accountStableId,
                        request.communityName,
                        request.username,
                    )
                ) {
                    pendingAuthorizationRequest = null
                    mutableUiState.value = mutableUiState.value.copy(
                        authorizing = false,
                        authorizationRequired = false,
                        authorizationReady = false,
                        failureKind = ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                // Authorization completion never replays a moderator permission mutation.
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
                    failureKind = result.kind.toModeratorPermissionUpdateFailureKind(),
                )
            }
        }
    }
}

private fun AuthCommentWriteCapabilityFailureKind.toModeratorPermissionUpdateFailureKind():
    ModeratorPermissionUpdateFailureKind = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
        ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.STORAGE ->
        ModeratorPermissionUpdateFailureKind.SESSION
    AuthCommentWriteCapabilityFailureKind.CALLBACK,
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> ModeratorPermissionUpdateFailureKind.AUTHORIZATION
}
