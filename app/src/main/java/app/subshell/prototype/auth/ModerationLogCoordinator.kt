package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorLogUiState
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MODERATION02A4_ACCOUNT_PINNED_MOD_LOG_COORDINATOR_V1
internal class ModerationLogCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val loader: AuthenticatedRedditModerationLogLoader?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
) {
    private val mutableUiState = MutableStateFlow(ModeratorLogUiState())
    val uiState: StateFlow<ModeratorLogUiState> = mutableUiState.asStateFlow()

    fun open(accountStableId: String, rawCommunityName: String) {
        val account = accountStableId.trim()
        val community = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull()
        if (account.isEmpty() || community == null) {
            mutableUiState.value = ModeratorLogUiState(
                visible = true,
                accountStableId = account.takeIf(String::isNotEmpty),
                communityName = community,
                failureKind = RedditModerationUiFailureKind.PROTOCOL,
            )
            return
        }
        if (activeAccountStableId()?.trim() != account) {
            mutableUiState.value = ModeratorLogUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                failureKind = RedditModerationUiFailureKind.SESSION,
            )
            return
        }
        val current = mutableUiState.value
        mutableUiState.value =
            if (current.matches(account, community)) {
                current.copy(visible = true)
            } else {
                ModeratorLogUiState(
                    visible = true,
                    accountStableId = account,
                    communityName = community,
                )
            }
    }

    suspend fun load(accountStableId: String, forceRefresh: Boolean = false) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        if (
            !current.visible ||
            !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account
        ) {
            return
        }
        if (current.loading || current.authorizing) return
        if (current.loaded && !forceRefresh && !current.authorizationRequired) return

        val configuredLoader = loader
        if (configuredLoader == null) {
            mutableUiState.value = current.copy(
                loading = false,
                failureKind = RedditModerationUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }

        mutableUiState.value = current.copy(
            loading = true,
            authorizationRequired = false,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
        )

        val result = try {
            configuredLoader.load(account, community, after = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthenticatedRedditModerationLogResult.Failed(
                communityName = community,
                kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                retryable = true,
            )
        }
        applyResult(account, community, result, append = false)
    }

    suspend fun append(accountStableId: String) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        val after = current.nextCursor ?: return
        if (
            !current.visible ||
            !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account ||
            current.loading ||
            current.authorizing ||
            current.authorizationRequired
        ) {
            return
        }
        val configuredLoader = loader ?: return

        mutableUiState.value = current.copy(
            loading = true,
            failureKind = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
        )

        val result = try {
            configuredLoader.load(account, community, after)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthenticatedRedditModerationLogResult.Failed(
                communityName = community,
                kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                retryable = true,
            )
        }
        applyResult(account, community, result, append = true)
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val current = mutableUiState.value
        val account = current.accountStableId?.trim().orEmpty()
        if (
            !current.visible ||
            current.communityName == null ||
            account.isEmpty() ||
            activeAccountStableId()?.trim() != account ||
            capability == null ||
            clientMode == null
        ) {
            mutableUiState.value = current.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = RedditModerationUiFailureKind.CLIENT_CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = current.copy(
            authorizing = true,
            authorizationRequired = true,
            failureKind = null,
            failureRetryable = false,
        )
        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = capability.beginAuthorization(account),
                clientMode = clientMode,
                freshAccountSession = true,
            )
        } catch (_: Exception) {
            capability.cancelPendingAuthorization()
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = RedditModerationUiFailureKind.SESSION,
                failureRetryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        capability?.cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
            failureKind = RedditModerationUiFailureKind.SESSION,
            failureRetryable = true,
        )
    }

    fun cancelPendingAuthorization() {
        capability?.cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
        )
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val configuredCapability = capability ?: return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
            )
            return true
        }
        if (!configuredCapability.hasStoredCallback()) return false
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

    fun accountChanged(accountStableId: String) {
        val current = mutableUiState.value
        if (
            current.accountStableId != null &&
            current.accountStableId != accountStableId.trim()
        ) {
            // LOW8_ACCOUNT_SWITCH_CANCELS_A_PENDING_AUTHORIZATION_V1 (OCB-424): the
            // old account's authorization would otherwise stay pending with no
            // log to complete it.
            if (current.authorizing) capability?.cancelPendingAuthorization()
            mutableUiState.value = ModeratorLogUiState()
        }
    }

    fun dismiss() {
        // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: closing the log while it
        // waits for the browser cancels the authorization, so the flag does not
        // survive into the next open (where Authorize was hidden for good).
        if (mutableUiState.value.authorizing) cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(visible = false)
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val current = mutableUiState.value
        // LOW8: a callback with no log to complete is dropped, not left stored.
        val account = current.accountStableId
            ?: return configuredCapability.cancelPendingAuthorization()
        val community = current.communityName
            ?: return configuredCapability.cancelPendingAuthorization()
        mutableUiState.value = current.copy(authorizing = true)

        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (
                    result.accountStableId != account ||
                    activeAccountStableId()?.trim() != account ||
                    !mutableUiState.value.matches(account, community)
                ) {
                    mutableUiState.value = mutableUiState.value.copy(
                        authorizing = false,
                        authorizationRequired = true,
                        failureKind = RedditModerationUiFailureKind.SESSION,
                    )
                    return
                }
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = false,
                    failureKind = null,
                    failureRetryable = false,
                )
                load(account, forceRefresh = true)
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = true,
                    failureKind = RedditModerationUiFailureKind.SESSION,
                    failureRetryable = result.retryable,
                )
            }
        }
    }

    private fun applyResult(
        account: String,
        community: String,
        result: AuthenticatedRedditModerationLogResult,
        append: Boolean,
    ) {
        val latest = mutableUiState.value
        if (
            !latest.matches(account, community) ||
            activeAccountStableId()?.trim() != account
        ) {
            return
        }

        when (result) {
            is AuthenticatedRedditModerationLogResult.Loaded -> {
                if (result.communityName != community) return
                mutableUiState.value = latest.copy(
                    entries = if (append) {
                        (latest.entries + result.entries)
                            .distinctBy { entry -> entry.id }
                    } else {
                        result.entries
                    },
                    loading = false,
                    loaded = true,
                    nextCursor = result.nextCursor,
                    authorizationRequired = false,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                    contentRevision = latest.contentRevision + 1L,
                )
            }
            AuthenticatedRedditModerationLogResult.AuthorizationRequired -> {
                mutableUiState.value = latest.copy(
                    loading = false,
                    authorizing = false,
                    authorizationRequired = true,
                    failureKind = null,
                    failureRetryable = false,
                    failureHttpStatusCode = null,
                )
            }
            is AuthenticatedRedditModerationLogResult.Failed -> {
                mutableUiState.value = latest.copy(
                    loading = false,
                    failureKind = result.kind.toUiFailureKind(),
                    failureRetryable = result.retryable,
                    failureHttpStatusCode = result.httpStatusCode,
                )
            }
        }
    }

    private fun AuthenticatedRedditModerationFailureKind.toUiFailureKind():
        RedditModerationUiFailureKind = when (this) {
        AuthenticatedRedditModerationFailureKind.CLIENT_CONFIGURATION ->
            RedditModerationUiFailureKind.CLIENT_CONFIGURATION
        AuthenticatedRedditModerationFailureKind.SESSION ->
            RedditModerationUiFailureKind.SESSION
        AuthenticatedRedditModerationFailureKind.HTTP ->
            RedditModerationUiFailureKind.HTTP
        AuthenticatedRedditModerationFailureKind.TRANSPORT ->
            RedditModerationUiFailureKind.TRANSPORT
        AuthenticatedRedditModerationFailureKind.PROTOCOL ->
            RedditModerationUiFailureKind.PROTOCOL
    }
}
