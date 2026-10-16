package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MODERATION02A5A7_ACCOUNT_COMMUNITY_TARGET_PINNED_REMOVAL_COORDINATOR_V1
internal class ModeratorRemovalCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val writer: AuthenticatedRedditModeratorRemovalWriter?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val activeAccountUsername: () -> String?,
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(ModeratorRemovalUiState())
    private var pendingAuthorizationRequest: RedditModeratorRemovalRequest? = null

    val uiState: StateFlow<ModeratorRemovalUiState> = mutableUiState.asStateFlow()

    fun open(
        accountStableId: String,
        rawCommunityName: String,
        rawUsername: String,
    ) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }.getOrNull()
        val username = runCatching { normalizeRedditSafetyUsername(rawUsername) }.getOrNull()
        val actor = normalizedActiveUsername()
        val validContext = account.isNotEmpty() && community != null && username != null
        val accountChanged = activeAccountStableId()?.trim() != account
        val invalidTarget = actor == null ||
            (username != null && username.equals(actor, ignoreCase = true))
        if (!validContext || accountChanged || invalidTarget) {
            if (pendingAuthorizationRequest != null) capability?.cancelPendingAuthorization()
            pendingAuthorizationRequest = null
            mutableUiState.value = ModeratorRemovalUiState(
                visible = true,
                accountStableId = account.takeIf { validContext },
                communityName = community.takeIf { validContext },
                targetUsername = username.takeIf { validContext },
                failureKind = if (accountChanged) {
                    ModeratorRemovalFailureKind.ACCOUNT_CHANGED
                } else {
                    ModeratorRemovalFailureKind.VALIDATION
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
                removing = false,
                authorizing = false,
                authorizationRequired = false,
                authorizationReady = false,
                failureKind = null,
                failureHttpStatusCode = null,
                failureDetails = null,
                lastRemovedUsername = null,
            )
        } else {
            ModeratorRemovalUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                targetUsername = username,
            )
        }
    }

    suspend fun remove(request: RedditModeratorRemovalRequest) {
        val normalized = try {
            request.normalized()
        } catch (_: IllegalArgumentException) {
            mutableUiState.value = mutableUiState.value.copy(
                removing = false,
                failureKind = ModeratorRemovalFailureKind.VALIDATION,
                failureDetails = null,
            )
            return
        }
        val current = mutableUiState.value
        val active = activeAccountStableId()?.trim()
        val actor = normalizedActiveUsername()
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
                removing = false,
                failureKind = ModeratorRemovalFailureKind.ACCOUNT_CHANGED,
            )
            return
        }
        if (actor == null || normalized.username.equals(actor, ignoreCase = true)) {
            mutableUiState.value = current.copy(
                removing = false,
                failureKind = ModeratorRemovalFailureKind.VALIDATION,
                failureDetails = null,
            )
            return
        }
        if (!mutationMutex.tryLock()) {
            mutableUiState.value = current.copy(
                failureKind = ModeratorRemovalFailureKind.ALREADY_MUTATING,
            )
            return
        }
        try {
            if (mutableUiState.value.busy) return
            val configuredWriter = writer
            if (configuredWriter == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    failureKind = ModeratorRemovalFailureKind.CLIENT_CONFIGURATION,
                )
                return
            }
            pendingAuthorizationRequest = null
            mutableUiState.value = mutableUiState.value.copy(
                removing = true,
                authorizationRequired = false,
                authorizationReady = false,
                failureKind = null,
                failureHttpStatusCode = null,
                failureDetails = null,
            )
            val result = try {
                configuredWriter.remove(normalized)
            } catch (cancelled: CancellationException) {
                mutableUiState.value = mutableUiState.value.copy(
                    removing = false,
                    failureKind = ModeratorRemovalFailureKind.OUTCOME_UNKNOWN,
                )
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModeratorRemovalResult.Failed(
                    ModeratorRemovalFailureKind.OUTCOME_UNKNOWN,
                )
            }
            if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                pendingAuthorizationRequest = null
                mutableUiState.value = mutableUiState.value.copy(
                    removing = false,
                    authorizationRequired = false,
                    failureKind = ModeratorRemovalFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            when (result) {
                AuthenticatedRedditModeratorRemovalResult.Acknowledged -> {
                    pendingAuthorizationRequest = null
                    mutableUiState.value = mutableUiState.value.copy(
                        removing = false,
                        authorizationRequired = false,
                        authorizationReady = false,
                        failureKind = null,
                        failureHttpStatusCode = null,
                        failureDetails = null,
                        completionRevision = mutableUiState.value.completionRevision + 1L,
                        lastRemovedUsername = normalized.username,
                    )
                }
                AuthenticatedRedditModeratorRemovalResult.AuthorizationRequired -> {
                    pendingAuthorizationRequest = normalized
                    mutableUiState.value = mutableUiState.value.copy(
                        removing = false,
                        authorizationRequired = true,
                        authorizationReady = false,
                        failureKind = null,
                        failureDetails = null,
                    )
                }
                is AuthenticatedRedditModeratorRemovalResult.Failed -> {
                    pendingAuthorizationRequest = null
                    mutableUiState.value = mutableUiState.value.copy(
                        removing = false,
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
            normalizedActiveUsername()?.let { actor ->
                request.username.equals(actor, ignoreCase = true)
            } != false ||
            configuredCapability == null ||
            configuredClientMode == null
        ) {
            mutableUiState.value = current.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = ModeratorRemovalFailureKind.CLIENT_CONFIGURATION,
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
                failureKind = ModeratorRemovalFailureKind.AUTHORIZATION,
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
            failureKind = ModeratorRemovalFailureKind.AUTHORIZATION,
        )
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val configuredCapability = capability ?: return false
        if (pendingAuthorizationRequest == null) return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            configuredCapability.cancelPendingAuthorization()
            pendingAuthorizationRequest = null
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = false,
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
            mutableUiState.value = ModeratorRemovalUiState()
        }
    }

    fun dismiss() {
        if (pendingAuthorizationRequest != null) {
            capability?.cancelPendingAuthorization()
        }
        pendingAuthorizationRequest = null
        mutableUiState.value = mutableUiState.value.copy(
            visible = false,
            removing = false,
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
                    normalizedActiveUsername()?.let { actor ->
                        request.username.equals(actor, ignoreCase = true)
                    } != false ||
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
                        failureKind = ModeratorRemovalFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                // Authorization completion never replays moderator removal.
                // The destructive confirmation must be performed again.
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
                    failureKind = result.kind.toModeratorRemovalFailureKind(),
                )
            }
        }
    }

    private fun normalizedActiveUsername(): String? =
        activeAccountUsername()
            ?.trim()
            ?.removePrefix("u/")
            ?.removePrefix("U/")
            ?.takeIf(String::isNotEmpty)
            ?.let { raw -> runCatching { normalizeRedditSafetyUsername(raw) }.getOrNull() }
}

private fun AuthCommentWriteCapabilityFailureKind.toModeratorRemovalFailureKind():
    ModeratorRemovalFailureKind = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
        ModeratorRemovalFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.STORAGE ->
        ModeratorRemovalFailureKind.SESSION
    AuthCommentWriteCapabilityFailureKind.CALLBACK,
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> ModeratorRemovalFailureKind.AUTHORIZATION
}
