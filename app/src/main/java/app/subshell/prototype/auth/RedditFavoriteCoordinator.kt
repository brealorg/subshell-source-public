package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditFavoriteUiFailure
import app.subshell.prototype.data.account.RedditFavoriteUiFailureKind
import app.subshell.prototype.data.account.RedditFavoriteUiState
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.RedditFavoriteCommunityWriter
import app.subshell.prototype.data.auth.reddit.RedditFavoriteFailureKind
import app.subshell.prototype.data.auth.reddit.RedditFavoriteRequest
import app.subshell.prototype.data.auth.reddit.RedditFavoriteWriteResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * REDDITFAV02_FAVORITE_WRITE_V1: sends one favorite change to Reddit and
 * reads the subscriptions back for Reddit's answer (api/favorite answers
 * `{}`). A lost answer is never sent again: the read back decides, and a
 * change Reddit's list does not show is reported as not confirmed.
 *
 * REDDITFAV03_FAVORITE_AUTHORIZATION_V1: a change refused for a missing
 * grant is kept; Authorize asks for `subscribe` under the REDDIT_FAVORITE
 * owner, and the callback sends that change (it was never applied).
 *
 * [readBack] forces a fresh read of the request's list (subscriptions, or
 * the custom feeds for a feed) and returns whether it shows the target as a
 * favorite, or null when that read failed or the account changed.
 */
internal class RedditFavoriteCoordinator(
    private val writer: RedditFavoriteCommunityWriter?,
    private val capability: AuthCustomFeedWriteCapability?,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val readBack: suspend (RedditFavoriteRequest) -> Boolean?,
    private val scope: CoroutineScope,
    private val readBackDelaysMillis: List<Long> = REDDIT_FAVORITE_READ_BACK_DELAYS_MILLIS,
) {
    private val mutableUiState = MutableStateFlow(RedditFavoriteUiState())
    val uiState: StateFlow<RedditFavoriteUiState> = mutableUiState.asStateFlow()

    // The change a missing grant refused, sent once authorization completes.
    private var authorizationRequest: RedditFavoriteRequest? = null

    fun update(
        accountStableId: String,
        communityName: String,
        makeFavorite: Boolean,
        customFeedPath: String? = null,
    ) {
        if (activeAccountStableId() != accountStableId) return
        val request = RedditFavoriteRequest(
            accountStableId = accountStableId,
            communityName = communityName,
            makeFavorite = makeFavorite,
            customFeedPath = customFeedPath,
        )
        val current = mutableUiState.value
            .takeIf { state -> state.matches(accountStableId) }
            ?: RedditFavoriteUiState(
                accountStableId = accountStableId,
                failureRevision = mutableUiState.value.failureRevision,
            )
        val pending = if (customFeedPath == null) current.pending else current.pendingCustomFeeds
        if (request.key in pending) return
        mutableUiState.value = current.withPending(request, pending + (request.key to makeFavorite))
        scope.launch {
            val outcome = try {
                send(request)
            } catch (cancelled: CancellationException) {
                settle(request, null)
                throw cancelled
            } catch (_: Exception) {
                RedditFavoriteUiFailureKind.FAILED
            }
            if (outcome == RedditFavoriteUiFailureKind.AUTHORIZATION) {
                authorizationRequest = request
            }
            settle(request, outcome)
        }
    }

    fun dismissFailure() {
        mutableUiState.update { current -> current.copy(failure = null) }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = authorizationRequest ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (
            configuredCapability == null ||
            configuredClientMode == null ||
            activeAccountStableId() != request.accountStableId
        ) {
            authorizationRequest = null
            fail(request, RedditFavoriteUiFailureKind.FAILED)
            return AuthBrowserLaunchResult.NotStarted
        }
        return try {
            val url = configuredCapability.beginAuthorization(request.accountStableId)
            mutableUiState.update { current -> current.copy(authorizing = true) }
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = url,
                clientMode = configuredClientMode,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            authorizationRequest = null
            fail(request, RedditFavoriteUiFailureKind.FAILED)
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        capability?.cancelPendingAuthorization()
        val request = authorizationRequest
        authorizationRequest = null
        mutableUiState.update { current -> current.copy(authorizing = false) }
        request?.let { fail(it, RedditFavoriteUiFailureKind.FAILED) }
    }

    fun cancelPendingAuthorization() {
        capability?.cancelPendingAuthorization()
        authorizationRequest = null
        mutableUiState.update { current -> current.copy(authorizing = false) }
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability
        if (configuredCapability == null || !configuredCapability.hasStoredCallback()) {
            configuredCapability?.cancelPendingAuthorization()
            val request = authorizationRequest
            authorizationRequest = null
            mutableUiState.update { current -> current.copy(authorizing = false) }
            request?.let { fail(it, RedditFavoriteUiFailureKind.FAILED) }
            return true
        }
        completeAuthorization(configuredCapability)
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        val configuredCapability = capability ?: return false
        return when {
            configuredCapability.hasStoredCallback() -> {
                completeAuthorization(configuredCapability)
                true
            }
            configuredCapability.hasPendingAuthorization() -> true
            else -> false
        }
    }

    private suspend fun completeAuthorization(configuredCapability: AuthCustomFeedWriteCapability) {
        val request = authorizationRequest
        authorizationRequest = null
        val result = configuredCapability.completePendingAuthorization()
        mutableUiState.update { current -> current.copy(authorizing = false) }
        if (request == null) return
        when (result) {
            is AuthCustomFeedWriteCapabilityResult.Upgraded ->
                if (
                    result.accountStableId == request.accountStableId &&
                    activeAccountStableId() == request.accountStableId
                ) {
                    update(
                        request.accountStableId,
                        request.communityName,
                        request.makeFavorite,
                        request.customFeedPath,
                    )
                } else {
                    fail(request, RedditFavoriteUiFailureKind.FAILED)
                }
            is AuthCustomFeedWriteCapabilityResult.Failed ->
                fail(request, RedditFavoriteUiFailureKind.FAILED)
        }
    }

    private suspend fun send(request: RedditFavoriteRequest): RedditFavoriteUiFailureKind? {
        val configured = writer ?: return RedditFavoriteUiFailureKind.FAILED
        return when (val result = configured.update(request)) {
            RedditFavoriteWriteResult.Updated -> confirm(request)
            RedditFavoriteWriteResult.AuthorizationRequired -> RedditFavoriteUiFailureKind.AUTHORIZATION
            is RedditFavoriteWriteResult.Failed ->
                if (result.kind == RedditFavoriteFailureKind.OUTCOME_UNKNOWN) {
                    confirm(request)
                } else {
                    RedditFavoriteUiFailureKind.FAILED
                }
        }
    }

    // Reads Reddit's list until it shows the change or the delays run out.
    private suspend fun confirm(request: RedditFavoriteRequest): RedditFavoriteUiFailureKind? {
        readBackDelaysMillis.forEach { waitMillis ->
            if (waitMillis > 0L) delay(waitMillis)
            val shown = readBack(request) ?: return@forEach
            if (shown == request.makeFavorite) return null
        }
        return RedditFavoriteUiFailureKind.NOT_CONFIRMED
    }

    private fun settle(request: RedditFavoriteRequest, failureKind: RedditFavoriteUiFailureKind?) {
        mutableUiState.update { current ->
            if (!current.matches(request.accountStableId)) return@update current
            val pending = if (request.customFeedPath == null) current.pending else current.pendingCustomFeeds
            current.withPending(request, pending - request.key)
        }
        failureKind?.let { kind -> fail(request, kind) }
    }

    private fun fail(request: RedditFavoriteRequest, kind: RedditFavoriteUiFailureKind) {
        mutableUiState.update { current ->
            if (!current.matches(request.accountStableId)) return@update current
            current.copy(
                failure = RedditFavoriteUiFailure(
                    communityName = request.communityName,
                    makeFavorite = request.makeFavorite,
                    kind = kind,
                    customFeed = request.customFeedPath != null,
                ),
                failureRevision = current.failureRevision + 1L,
            )
        }
    }
}

private fun RedditFavoriteUiState.withPending(
    request: RedditFavoriteRequest,
    pending: Map<String, Boolean>,
): RedditFavoriteUiState = if (request.customFeedPath == null) {
    copy(pending = pending)
} else {
    copy(pendingCustomFeeds = pending)
}

internal val REDDIT_FAVORITE_READ_BACK_DELAYS_MILLIS = listOf(0L, 1_500L, 5_000L)
