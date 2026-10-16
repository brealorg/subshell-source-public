package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationFailureKind
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationResult
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.auth.authorizationContextKeyFits
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.profileHistoryAuthorizationContextKey
import app.subshell.prototype.data.auth.profileHistoryUsernameFromAuthorizationContextKey
import app.subshell.prototype.data.normalizeRedditUsername
import kotlin.coroutines.cancellation.CancellationException
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// SUBSHELL_PROFILECOMMENTS01_COORDINATOR_V1
/**
 * The profile-history read authorization shared by the account-history and
 * saved-history coordinators: begin, browser-launch failure, and the
 * callback completion/resume the ViewModel's routing dispatches for
 * [AuthCapabilityAuthorizationOwner.PROFILE_HISTORY]. Moved verbatim out of
 * AuthConnectViewModel; the ViewModel keeps one-line forwarders.
 * Characterised by AuthConnectViewModelProfileCommentsTest.
 */
internal class ProfileCommentsCoordinator(
    private val profileHistoryAuthorizer: AuthContextualCapabilityAuthorizer?,
    private val profileHistoryClientMode: OAuthClientMode?,
    private val redditAccountHistoryCoordinator: RedditAccountHistoryRequestCoordinator,
    private val redditSavedHistoryCoordinator: RedditSavedHistoryRequestCoordinator,
    private val activeAuthenticatedAccountStableId: () -> String?,
) {
    // DEAD02_PROFILE_COMMENTS_FLOW_REMOVED_V1 (OCB-437/520): the profile
    // comments load/append path and its UI state were dead (the Comments tab
    // reads account history). The state only remained as the fallback holder of
    // which account/username a profile-history authorization was for; that is
    // this field now.
    private var pendingAuthorizationIdentity: Pair<String, String>? = null

    suspend fun beginProfileHistoryAuthorization(
        accountStableId: String,
        username: String,
    ): AuthBrowserLaunchResult {
        val account = accountStableId.trim()
        val normalizedUsername = runCatching {
            normalizeRedditUsername(username)
        }.getOrNull()
        val authorizer = profileHistoryAuthorizer
        val clientMode = profileHistoryClientMode

        if (
            account.isEmpty() ||
            normalizedUsername == null ||
            authorizer == null ||
            clientMode == null
        ) {
            if (account.isNotEmpty() && normalizedUsername != null) {
                updateProfileHistoryAuthorizationState(
                    accountStableId = account,
                    username = normalizedUsername,
                    authorizing = false,
                    authorizationRequired = true,
                    accountHistoryFailureKind =
                        RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
                    failureRetryable = false,
                )
            }
            return AuthBrowserLaunchResult.NotStarted
        }

        if (activeAuthenticatedAccountStableId() != account) {
            updateProfileHistoryAuthorizationState(
                accountStableId = account,
                username = normalizedUsername,
                authorizing = false,
                authorizationRequired = true,
                accountHistoryFailureKind = RedditAccountHistoryUiFailureKind.SESSION,
                failureRetryable = false,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        val contextKey = profileHistoryAuthorizationContextKey(normalizedUsername)
        if (!authorizationContextKeyFits(contextKey)) {
            // AUTHKEY01 (OCB-605): no authorization can be stored for this name.
            updateProfileHistoryAuthorizationState(
                accountStableId = account,
                username = normalizedUsername,
                authorizing = false,
                authorizationRequired = true,
                accountHistoryFailureKind = RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION,
                failureRetryable = false,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        updateProfileHistoryAuthorizationState(
            accountStableId = account,
            username = normalizedUsername,
            authorizing = true,
            authorizationRequired = true,
            accountHistoryFailureKind = null,
            failureRetryable = false,
        )

        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizer.beginAuthorization(
                    accountStableId = account,
                    owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                    contextKey = contextKey,
                ),
                clientMode = clientMode,
            )
        } catch (_: Exception) {
            runCatching {
                authorizer.cancelPendingAuthorization(
                    owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                    contextKey = contextKey,
                )
            }
            updateProfileHistoryAuthorizationState(
                accountStableId = account,
                username = normalizedUsername,
                authorizing = false,
                authorizationRequired = true,
                accountHistoryFailureKind = RedditAccountHistoryUiFailureKind.SESSION,
                failureRetryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun profileHistoryAuthorizationBrowserLaunchFailed() {
        val identity = currentProfileHistoryAuthorizationIdentity() ?: return
        val account = identity.first
        val username = identity.second
        val contextKey = runCatching {
            profileHistoryAuthorizationContextKey(username)
        }.getOrNull()

        if (contextKey != null) {
            profileHistoryAuthorizer?.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            )
        }

        updateProfileHistoryAuthorizationState(
            accountStableId = account,
            username = username,
            authorizing = false,
            authorizationRequired = true,
            accountHistoryFailureKind = RedditAccountHistoryUiFailureKind.SESSION,
            failureRetryable = true,
        )
    }

    suspend fun completeProfileHistoryAuthorization(
        resultToken: String,
        contextKey: String?,
    ) {
        val authorizer = profileHistoryAuthorizer ?: return
        val username = profileHistoryUsernameFromAuthorizationContextKey(contextKey)

        if (username == null) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            )
            return
        }

        val account = activeAuthenticatedAccountStableId()
        if (account == null) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            )
            return
        }

        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            )
            updateProfileHistoryAuthorizationState(
                accountStableId = account,
                username = username,
                authorizing = false,
                authorizationRequired = true,
                accountHistoryFailureKind = null,
                failureRetryable = false,
            )
            return
        }

        if (
            !authorizer.hasStoredCallback(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            )
        ) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            )
            updateProfileHistoryAuthorizationState(
                accountStableId = account,
                username = username,
                authorizing = false,
                authorizationRequired = true,
                accountHistoryFailureKind = RedditAccountHistoryUiFailureKind.SESSION,
                failureRetryable = true,
            )
            return
        }

        updateProfileHistoryAuthorizationState(
            accountStableId = account,
            username = username,
            authorizing = true,
            authorizationRequired = true,
            accountHistoryFailureKind = null,
            failureRetryable = false,
        )

        val result = try {
            authorizer.completePendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthCapabilityAuthorizationResult.Failed(
                kind = AuthCapabilityAuthorizationFailureKind.CALLBACK,
                retryable = true,
            )
        }

        when (result) {
            is AuthCapabilityAuthorizationResult.Upgraded -> {
                if (
                    result.owner != AuthCapabilityAuthorizationOwner.PROFILE_HISTORY ||
                    result.accountStableId != account ||
                    activeAuthenticatedAccountStableId() != account
                ) {
                    updateProfileHistoryAuthorizationState(
                        accountStableId = account,
                        username = username,
                        authorizing = false,
                        authorizationRequired = true,
                        accountHistoryFailureKind = RedditAccountHistoryUiFailureKind.SESSION,
                        failureRetryable = false,
                    )
                    return
                }

                val accountHistoryMatch =
                    redditAccountHistoryCoordinator.uiState.value.matchesUser(
                        account,
                        username,
                    )
                val savedHistoryMatch =
                    redditSavedHistoryCoordinator.uiState.value.matches(
                        account,
                        username,
                    )

                updateProfileHistoryAuthorizationState(
                    accountStableId = account,
                    username = username,
                    authorizing = false,
                    authorizationRequired = false,
                    accountHistoryFailureKind = null,
                    failureRetryable = false,
                )

                if (accountHistoryMatch) {
                    redditAccountHistoryCoordinator.refreshAfterAuthorization(
                        accountStableId = account,
                        username = username,
                    )
                }
                if (savedHistoryMatch) {
                    redditSavedHistoryCoordinator.refreshAfterAuthorization(
                        accountStableId = account,
                        username = username,
                    )
                }
            }

            is AuthCapabilityAuthorizationResult.Failed -> {
                val accountHistoryFailureKind =
                    if (result.kind == AuthCapabilityAuthorizationFailureKind.STORAGE) {
                        RedditAccountHistoryUiFailureKind.STORAGE
                    } else {
                        RedditAccountHistoryUiFailureKind.SESSION
                    }
                updateProfileHistoryAuthorizationState(
                    accountStableId = account,
                    username = username,
                    authorizing = false,
                    authorizationRequired = true,
                    accountHistoryFailureKind = accountHistoryFailureKind,
                    failureRetryable = result.retryable,
                )
            }
        }
    }

    suspend fun resumeProfileHistoryAuthorization(
        contextKey: String?,
    ): Boolean {
        val authorizer = profileHistoryAuthorizer ?: return false
        val username =
            profileHistoryUsernameFromAuthorizationContextKey(contextKey)
                ?: return false
        val account = activeAuthenticatedAccountStableId() ?: return false

        return when {
            authorizer.hasStoredCallback(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            ) -> {
                completeProfileHistoryAuthorization(
                    resultToken = "stored",
                    contextKey = contextKey,
                )
                true
            }

            authorizer.hasPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
                contextKey = contextKey,
            ) -> {
                updateProfileHistoryAuthorizationState(
                    accountStableId = account,
                    username = username,
                    authorizing = true,
                    authorizationRequired = true,
                    accountHistoryFailureKind = null,
                    failureRetryable = false,
                )
                true
            }

            else -> false
        }
    }

    private fun updateProfileHistoryAuthorizationState(
        accountStableId: String,
        username: String,
        authorizing: Boolean,
        authorizationRequired: Boolean,
        accountHistoryFailureKind: RedditAccountHistoryUiFailureKind?,
        failureRetryable: Boolean,
    ) {
        val historyMatches =
            redditAccountHistoryCoordinator.uiState.value.matchesUser(
                accountStableId,
                username,
            )
        if (historyMatches) {
            redditAccountHistoryCoordinator.updateAuthorizationState(
                accountStableId = accountStableId,
                username = username,
                authorizing = authorizing,
                authorizationRequired = authorizationRequired,
                failureKind = accountHistoryFailureKind,
                failureRetryable = failureRetryable,
            )
        }

        val savedHistoryMatches =
            redditSavedHistoryCoordinator.uiState.value.matches(
                accountStableId,
                username,
            )
        if (savedHistoryMatches) {
            redditSavedHistoryCoordinator.updateAuthorizationState(
                accountStableId = accountStableId,
                username = username,
                authorizing = authorizing,
                authorizationRequired = authorizationRequired,
                failureKind = accountHistoryFailureKind,
                failureRetryable = failureRetryable,
            )
        }

        if (
            pendingAuthorizationIdentity == (accountStableId to username) ||
            (!historyMatches && !savedHistoryMatches)
        ) {
            pendingAuthorizationIdentity =
                if (authorizing) accountStableId to username else null
        }
    }

    private fun currentProfileHistoryAuthorizationIdentity(): Pair<String, String>? {
        val history = redditAccountHistoryCoordinator.uiState.value
        if (
            history.authorizing &&
            history.accountStableId != null &&
            history.username != null
        ) {
            return history.accountStableId to history.username
        }
        val savedHistory = redditSavedHistoryCoordinator.uiState.value
        if (
            savedHistory.authorizing &&
            savedHistory.accountStableId != null &&
            savedHistory.username != null
        ) {
            return savedHistory.accountStableId to savedHistory.username
        }
        return pendingAuthorizationIdentity
    }

}
