package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditCommunityWikiUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationFailureKind
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.authorizationContextKeyFits
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationResult
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiTarget
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.communityWikiAuthorizationContextKey
import app.subshell.prototype.data.auth.communityWikiCommunityFromAuthorizationContextKey
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// SUBSHELL_COMMUNITYWIKI01_COORDINATOR_V1
/**
 * Community wiki reads (index and page) and the contextual read
 * authorization they may need: begin, browser-launch failure, and the
 * callback completion/resume the ViewModel's routing dispatches for
 * [AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ]. Moved verbatim
 * out of AuthConnectViewModel; the ViewModel keeps one-line forwarders.
 */
internal class CommunityWikiCoordinator(
    private val wikiLoader: AuthenticatedRedditCommunityWikiLoader?,
    private val wikiAuthorizer: AuthContextualCapabilityAuthorizer?,
    private val wikiClientMode: OAuthClientMode?,
    private val activeAuthenticatedAccountStableId: () -> String?,
    private val scope: CoroutineScope,
) {
    private val _redditCommunityWikiUiState =
        MutableStateFlow(RedditCommunityWikiUiState())
    private var redditCommunityWikiJob: Job? = null

    val redditCommunityWikiUiState:
        StateFlow<RedditCommunityWikiUiState> =
        _redditCommunityWikiUiState.asStateFlow()

    fun loadRedditCommunityWikiIndex(
        accountStableId: String,
        rawCommunityName: String,
        forceRefresh: Boolean = false,
    ) {
        val target = try {
            AuthenticatedRedditCommunityWikiTarget.index(rawCommunityName)
        } catch (_: IllegalArgumentException) {
            _redditCommunityWikiUiState.value = RedditCommunityWikiUiState(
                accountStableId = accountStableId.trim().takeIf(String::isNotEmpty),
                failureKind = RedditCommunityWikiUiFailureKind.PROTOCOL,
            )
            return
        }
        loadRedditCommunityWiki(
            accountStableId = accountStableId,
            target = target,
            forceRefresh = forceRefresh,
        )
    }
    fun loadRedditCommunityWikiPage(
        accountStableId: String,
        rawCommunityName: String,
        rawPagePath: String,
        forceRefresh: Boolean = false,
    ) {
        val target = try {
            AuthenticatedRedditCommunityWikiTarget.page(
                rawCommunityName = rawCommunityName,
                rawPagePath = rawPagePath,
            )
        } catch (_: IllegalArgumentException) {
            _redditCommunityWikiUiState.value =
                _redditCommunityWikiUiState.value.copy(
                    loading = false,
                    loadingRequestKey = null,
                    failureKind = RedditCommunityWikiUiFailureKind.PROTOCOL,
                    failureRetryable = false,
                )
            return
        }
        loadRedditCommunityWiki(
            accountStableId = accountStableId,
            target = target,
            forceRefresh = forceRefresh,
        )
    }
    // LOW9_WIKI_AUTHORIZATION_RELOADS_WHAT_WAS_ASKED_V1 (OCB-411): the last
    // index or page asked for; after authorization that target is reloaded,
    // not always the index (which dropped a page opened from a wiki link).
    private var lastRequestedWikiTarget: Pair<String, AuthenticatedRedditCommunityWikiTarget>? = null

    private fun loadRedditCommunityWiki(
        accountStableId: String,
        target: AuthenticatedRedditCommunityWikiTarget,
        forceRefresh: Boolean,
    ) {
        val account = accountStableId.trim()
        if (account.isNotEmpty()) lastRequestedWikiTarget = account to target
        if (account.isEmpty()) {
            _redditCommunityWikiUiState.value = RedditCommunityWikiUiState(
                failureKind = RedditCommunityWikiUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return
        }

        val current = _redditCommunityWikiUiState.value
        val matches = current.matches(account, target.communityName)
        val alreadyLoaded = if (target.pagePath == null) {
            current.index != null
        } else {
            current.page?.path == target.pagePath
        }

        if (matches && current.loadingRequestKey == target.requestKey) {
            return
        }
        if (matches && alreadyLoaded && !forceRefresh) {
            if (target.pagePath == null) {
                _redditCommunityWikiUiState.value = current.copy(
                    page = null,
                    loading = false,
                    loadingRequestKey = null,
                    failureKind = null,
                    failureRetryable = false,
                    failureDetails = null,
                    failureHttpStatusCode = null,
                )
            }
            return
        }

        val loader = wikiLoader
        if (loader == null) {
            _redditCommunityWikiUiState.value = RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = target.communityName,
                failureKind = RedditCommunityWikiUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return
        }

        redditCommunityWikiJob?.cancel()
        val previous = current.takeIf {
            it.matches(account, target.communityName)
        }
        _redditCommunityWikiUiState.value = RedditCommunityWikiUiState(
            accountStableId = account,
            communityName = target.communityName,
            index = previous?.index,
            page = previous?.page?.takeIf { page ->
                target.pagePath != null && page.path == target.pagePath
            },
            loading = true,
            loadingRequestKey = target.requestKey,
            authorizing = previous?.authorizing == true,
            authorizationRequired = false,
            contentRevision = previous?.contentRevision ?: 0L,
        )

        redditCommunityWikiJob = scope.launch {
            val result = try {
                loader.load(account, target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommunityWikiResult.Failed(
                    kind = AuthenticatedRedditCommunityWikiFailureKind.TRANSPORT,
                    retryable = true,
                )
            }

            val latest = _redditCommunityWikiUiState.value
            if (
                !latest.matches(account, target.communityName) ||
                latest.loadingRequestKey != target.requestKey
            ) {
                return@launch
            }

            _redditCommunityWikiUiState.value = when (result) {
                is AuthenticatedRedditCommunityWikiResult.IndexLoaded ->
                    latest.copy(
                        index = result.index,
                        page = null,
                        loading = false,
                        loadingRequestKey = null,
                        authorizing = false,
                        authorizationRequired = false,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        failureHttpStatusCode = null,
                        contentRevision = latest.contentRevision + 1L,
                    )

                is AuthenticatedRedditCommunityWikiResult.PageLoaded ->
                    latest.copy(
                        page = result.page,
                        loading = false,
                        loadingRequestKey = null,
                        authorizing = false,
                        authorizationRequired = false,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        failureHttpStatusCode = null,
                        contentRevision = latest.contentRevision + 1L,
                    )

                AuthenticatedRedditCommunityWikiResult.AuthorizationRequired ->
                    latest.copy(
                        loading = false,
                        loadingRequestKey = null,
                        authorizing = false,
                        authorizationRequired = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        failureHttpStatusCode = null,
                        contentRevision = latest.contentRevision + 1L,
                    )

                is AuthenticatedRedditCommunityWikiResult.Failed ->
                    latest.copy(
                        loading = false,
                        loadingRequestKey = null,
                        authorizing = false,
                        failureKind = result.kind.toCommunityWikiUiFailureKind(),
                        failureRetryable = result.retryable,
                        failureDetails = result.details,
                        failureHttpStatusCode =
                            result.details?.httpStatusCode ?: result.httpStatusCode,
                        contentRevision = latest.contentRevision + 1L,
                    )
            }
        }
    }
    suspend fun beginRedditCommunityWikiAuthorization(
        accountStableId: String,
        rawCommunityName: String,
    ): AuthBrowserLaunchResult {
        val account = accountStableId.trim()
        val target = runCatching {
            AuthenticatedRedditCommunityWikiTarget.index(rawCommunityName)
        }.getOrNull()
        val authorizer = wikiAuthorizer
        val clientMode = wikiClientMode

        if (
            account.isEmpty() ||
            target == null ||
            authorizer == null ||
            clientMode == null
        ) {
            _redditCommunityWikiUiState.value = RedditCommunityWikiUiState(
                accountStableId = account.takeIf(String::isNotEmpty),
                communityName = target?.communityName,
                authorizationRequired = true,
                failureKind = RedditCommunityWikiUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        if (activeAuthenticatedAccountStableId() != account) {
            _redditCommunityWikiUiState.value = RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = target.communityName,
                authorizationRequired = true,
                failureKind = RedditCommunityWikiUiFailureKind.SESSION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        val contextKey = communityWikiAuthorizationContextKey(target.communityName)
        if (!authorizationContextKeyFits(contextKey)) {
            // AUTHKEY01 (OCB-605): no authorization can be stored for this name.
            _redditCommunityWikiUiState.value = RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = target.communityName,
                authorizationRequired = true,
                failureKind = RedditCommunityWikiUiFailureKind.UNAVAILABLE,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        val current = _redditCommunityWikiUiState.value
            .takeIf { it.matches(account, target.communityName) }
            ?: RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = target.communityName,
            )

        _redditCommunityWikiUiState.value = current.copy(
            authorizing = true,
            authorizationRequired = true,
            failureKind = null,
            failureRetryable = false,
            failureDetails = null,
            failureHttpStatusCode = null,
        )

        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizer.beginAuthorization(
                    accountStableId = account,
                    owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                    contextKey = contextKey,
                ),
                clientMode = clientMode,
            )
        } catch (_: Exception) {
            runCatching {
                authorizer.cancelPendingAuthorization(
                    owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                    contextKey = contextKey,
                )
            }
            _redditCommunityWikiUiState.value =
                _redditCommunityWikiUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = true,
                    failureKind = RedditCommunityWikiUiFailureKind.SESSION,
                    failureRetryable = true,
                )
            AuthBrowserLaunchResult.NotStarted
        }
    }
    fun redditCommunityWikiAuthorizationBrowserLaunchFailed() {
        val current = _redditCommunityWikiUiState.value
        val community = current.communityName ?: return
        val contextKey = runCatching {
            communityWikiAuthorizationContextKey(community)
        }.getOrNull()

        if (contextKey != null) {
            wikiAuthorizer?.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            )
        }

        _redditCommunityWikiUiState.value = current.copy(
            authorizing = false,
            authorizationRequired = true,
            failureKind = RedditCommunityWikiUiFailureKind.SESSION,
            failureRetryable = true,
        )
    }
    suspend fun completeCommunityWikiAuthorization(
        resultToken: String,
        contextKey: String?,
    ) {
        val authorizer = wikiAuthorizer ?: return
        val community = communityWikiCommunityFromAuthorizationContextKey(contextKey)

        if (community == null) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            )
            return
        }

        val account = activeAuthenticatedAccountStableId()
        if (account == null) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            )
            return
        }

        val current = _redditCommunityWikiUiState.value
            .takeIf { it.matches(account, community) }
            ?: RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = community,
                authorizationRequired = true,
            )

        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            )
            _redditCommunityWikiUiState.value = current.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = null,
                failureRetryable = false,
            )
            return
        }

        if (
            !authorizer.hasStoredCallback(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            )
        ) {
            authorizer.cancelPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            )
            _redditCommunityWikiUiState.value = current.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = RedditCommunityWikiUiFailureKind.SESSION,
                failureRetryable = true,
            )
            return
        }

        _redditCommunityWikiUiState.value = current.copy(
            authorizing = true,
            authorizationRequired = true,
            failureKind = null,
            failureRetryable = false,
        )

        val result = try {
            authorizer.completePendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
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
                    result.owner != AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ ||
                    result.accountStableId != account ||
                    activeAuthenticatedAccountStableId() != account
                ) {
                    _redditCommunityWikiUiState.value =
                        _redditCommunityWikiUiState.value.copy(
                            authorizing = false,
                            authorizationRequired = true,
                            failureKind = RedditCommunityWikiUiFailureKind.SESSION,
                            failureRetryable = false,
                        )
                    return
                }

                _redditCommunityWikiUiState.value =
                    _redditCommunityWikiUiState.value.copy(
                        authorizing = false,
                        authorizationRequired = false,
                        failureKind = null,
                        failureRetryable = false,
                    )

                val asked = lastRequestedWikiTarget
                    ?.takeIf { (askedAccount, target) ->
                        askedAccount == account &&
                            target.communityName.equals(community, ignoreCase = true) &&
                            !target.isIndex
                    }
                    ?.second
                if (asked != null) {
                    loadRedditCommunityWiki(
                        accountStableId = account,
                        target = asked,
                        forceRefresh = true,
                    )
                } else {
                    loadRedditCommunityWikiIndex(
                        accountStableId = account,
                        rawCommunityName = community,
                        forceRefresh = true,
                    )
                }
            }

            is AuthCapabilityAuthorizationResult.Failed -> {
                _redditCommunityWikiUiState.value =
                    _redditCommunityWikiUiState.value.copy(
                        authorizing = false,
                        authorizationRequired = true,
                        failureKind = RedditCommunityWikiUiFailureKind.SESSION,
                        failureRetryable = result.retryable,
                    )
            }
        }
    }
    suspend fun resumeCommunityWikiAuthorization(
        contextKey: String?,
    ): Boolean {
        val authorizer = wikiAuthorizer ?: return false
        val community =
            communityWikiCommunityFromAuthorizationContextKey(contextKey)
                ?: return false
        val account = activeAuthenticatedAccountStableId() ?: return false

        return when {
            authorizer.hasStoredCallback(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            ) -> {
                completeCommunityWikiAuthorization(
                    resultToken = "stored",
                    contextKey = contextKey,
                )
                true
            }

            authorizer.hasPendingAuthorization(
                owner = AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                contextKey = contextKey,
            ) -> {
                val current = _redditCommunityWikiUiState.value
                    .takeIf { it.matches(account, community) }
                    ?: RedditCommunityWikiUiState(
                        accountStableId = account,
                        communityName = community,
                    )
                _redditCommunityWikiUiState.value = current.copy(
                    authorizing = true,
                    authorizationRequired = true,
                    failureKind = null,
                    failureRetryable = false,
                )
                true
            }

            else -> false
        }
    }
}

private fun AuthenticatedRedditCommunityWikiFailureKind.toCommunityWikiUiFailureKind():
    RedditCommunityWikiUiFailureKind = when (this) {
    AuthenticatedRedditCommunityWikiFailureKind.CLIENT_CONFIGURATION ->
        RedditCommunityWikiUiFailureKind.CLIENT_CONFIGURATION
    AuthenticatedRedditCommunityWikiFailureKind.SESSION ->
        RedditCommunityWikiUiFailureKind.SESSION
    AuthenticatedRedditCommunityWikiFailureKind.HTTP ->
        RedditCommunityWikiUiFailureKind.HTTP
    AuthenticatedRedditCommunityWikiFailureKind.TRANSPORT ->
        RedditCommunityWikiUiFailureKind.TRANSPORT
    AuthenticatedRedditCommunityWikiFailureKind.PROTOCOL ->
        RedditCommunityWikiUiFailureKind.PROTOCOL
    AuthenticatedRedditCommunityWikiFailureKind.UNAVAILABLE ->
        RedditCommunityWikiUiFailureKind.UNAVAILABLE
}
