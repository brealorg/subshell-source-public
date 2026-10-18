package app.subshell.prototype.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.account.RedditRemovalReasonFailureKind
import app.subshell.prototype.data.account.RedditRemovalReasonPhase
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditRemovalReasonLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditRemovalReasonResult
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MOD01A21_OWNER_BOUND_REMOVAL_REASON_COORDINATOR_V1
internal class RemovalReasonCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val loader: AuthenticatedRedditRemovalReasonLoader?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
) {
    private val mutableUiState = MutableStateFlow(RedditRemovalReasonUiState())
    private var pendingAccountStableId: String? = null
    private var pendingCommunityName: String? = null

    val uiState: StateFlow<RedditRemovalReasonUiState> = mutableUiState.asStateFlow()

    suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        force: Boolean = false,
    ) {
        val communityName = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrElse {
            mutableUiState.value = failure(
                accountStableId = accountStableId,
                communityName = rawCommunityName.trim().lowercase(),
                kind = RedditRemovalReasonFailureKind.PROTOCOL,
            )
            return
        }
        if (
            !force &&
            mutableUiState.value.loaded &&
            mutableUiState.value.matches(accountStableId, communityName)
        ) return
        if (activeAccountStableId()?.trim() != accountStableId.trim()) {
            mutableUiState.value = failure(
                accountStableId,
                communityName,
                RedditRemovalReasonFailureKind.ACCOUNT_CHANGED,
            )
            return
        }
        pendingAccountStableId = accountStableId.trim()
        pendingCommunityName = communityName
        executeLoad(accountStableId.trim(), communityName)
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val accountStableId = pendingAccountStableId ?: return AuthBrowserLaunchResult.NotStarted
        val communityName = pendingCommunityName ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            mutableUiState.value = failure(
                accountStableId,
                communityName,
                RedditRemovalReasonFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (activeAccountStableId()?.trim() != accountStableId) {
            mutableUiState.value = failure(
                accountStableId,
                communityName,
                RedditRemovalReasonFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        mutableUiState.value = mutableUiState.value.copy(
            phase = RedditRemovalReasonPhase.AUTHORIZING,
        )
        return try {
            val url = configuredCapability.beginAuthorization(accountStableId)
            mutableUiState.value = mutableUiState.value.copy(
                phase = RedditRemovalReasonPhase.AWAITING_CALLBACK,
            )
            AuthBrowserLaunchResult.Ready(url, configuredClientMode)
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                accountStableId,
                communityName,
                RedditRemovalReasonFailureKind.AUTHORIZATION,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val accountStableId = pendingAccountStableId ?: return
        val communityName = pendingCommunityName ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            accountStableId,
            communityName,
            RedditRemovalReasonFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    // LOW9_CANCEL_IS_NOT_A_FAILURE_V1 (OCB-444): closing the login window or
    // pressing Cancel goes back to "authorization required" for the same
    // community (Authorize is offered again), not to a callback failure.
    fun cancelPendingAuthorization() {
        val accountStableId = pendingAccountStableId ?: return
        val communityName = pendingCommunityName ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = RedditRemovalReasonUiState(
            phase = RedditRemovalReasonPhase.AUTHORIZATION_REQUIRED,
            accountStableId = accountStableId,
            communityName = communityName,
            failureKind = RedditRemovalReasonFailureKind.AUTHORIZATION,
            retryable = true,
            contentRevision = mutableUiState.value.contentRevision,
        )
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability ?: return false
        if (!configuredCapability.hasStoredCallback()) {
            // MED1_REJECTED_CALLBACK_ENDS_REMOVAL_REASON_WAIT_V1 (OCB-445): a
            // rejected or failed callback (no stored callback) reached this
            // owner; like the moderator action owner, drop the pending record
            // and show a retryable failure instead of waiting for good.
            configuredCapability.cancelPendingAuthorization()
            val account = pendingAccountStableId
            val community = pendingCommunityName
            if (account != null && community != null) {
                mutableUiState.value = failure(
                    account,
                    community,
                    RedditRemovalReasonFailureKind.CALLBACK,
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

    // CONC01_LATEST_REMOVAL_REASON_LOAD_WINS_V1 (OCB-447): loads run in their
    // own coroutines; an older load that finishes last no longer overwrites
    // the newer request's state.
    private var loadGeneration = 0L

    private suspend fun executeLoad(accountStableId: String, communityName: String) {
        val generation = ++loadGeneration
        val configuredLoader = loader
        if (configuredLoader == null) {
            mutableUiState.value = failure(
                accountStableId,
                communityName,
                RedditRemovalReasonFailureKind.CONFIGURATION,
            )
            return
        }
        mutableUiState.value = RedditRemovalReasonUiState(
            phase = RedditRemovalReasonPhase.LOADING,
            accountStableId = accountStableId,
            communityName = communityName,
            contentRevision = mutableUiState.value.contentRevision,
        )
        val result = configuredLoader.load(accountStableId, communityName)
        if (generation != loadGeneration) return
        when (result) {
            is AuthenticatedRedditRemovalReasonResult.Loaded -> {
                pendingAccountStableId = accountStableId
                pendingCommunityName = communityName
                mutableUiState.value = RedditRemovalReasonUiState(
                    phase = RedditRemovalReasonPhase.LOADED,
                    accountStableId = accountStableId,
                    communityName = communityName,
                    reasons = result.reasons,
                    contentRevision = mutableUiState.value.contentRevision + 1L,
                )
            }
            AuthenticatedRedditRemovalReasonResult.AuthorizationRequired -> {
                pendingAccountStableId = accountStableId
                pendingCommunityName = communityName
                mutableUiState.value = RedditRemovalReasonUiState(
                    phase = RedditRemovalReasonPhase.AUTHORIZATION_REQUIRED,
                    accountStableId = accountStableId,
                    communityName = communityName,
                    failureKind = RedditRemovalReasonFailureKind.AUTHORIZATION,
                    retryable = true,
                    contentRevision = mutableUiState.value.contentRevision,
                )
            }
            is AuthenticatedRedditRemovalReasonResult.Failed -> {
                mutableUiState.value = failure(
                    accountStableId = accountStableId,
                    communityName = communityName,
                    kind = result.kind,
                    retryable = result.retryable,
                    httpStatusCode = result.httpStatusCode,
                    failureDetails = result.failureDetails,
                )
            }
        }
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val accountStableId = pendingAccountStableId ?: return
        val communityName = pendingCommunityName ?: return
        mutableUiState.value = mutableUiState.value.copy(
            phase = RedditRemovalReasonPhase.AUTHORIZING,
        )
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (
                    result.accountStableId != accountStableId ||
                    activeAccountStableId()?.trim() != accountStableId
                ) {
                    mutableUiState.value = failure(
                        accountStableId,
                        communityName,
                        RedditRemovalReasonFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                executeLoad(accountStableId, communityName)
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = failure(
                    accountStableId,
                    communityName,
                    result.kind.toRemovalReasonFailureKind(),
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun failure(
        accountStableId: String,
        communityName: String,
        kind: RedditRemovalReasonFailureKind,
        retryable: Boolean = false,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = RedditRemovalReasonUiState(
        phase = RedditRemovalReasonPhase.FAILED,
        accountStableId = accountStableId,
        communityName = communityName,
        failureKind = kind,
        retryable = retryable,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        contentRevision = mutableUiState.value.contentRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toRemovalReasonFailureKind() = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT -> RedditRemovalReasonFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.CALLBACK -> RedditRemovalReasonFailureKind.CALLBACK
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> RedditRemovalReasonFailureKind.AUTHORIZATION
    AuthCommentWriteCapabilityFailureKind.STORAGE -> RedditRemovalReasonFailureKind.SESSION
}
