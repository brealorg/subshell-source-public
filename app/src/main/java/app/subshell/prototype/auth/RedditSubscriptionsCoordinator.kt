package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The account's subscribed communities: the cached read and the live
 * synchronisation. Moved verbatim out of AuthConnectViewModel (the loader
 * and scope are injected); the ViewModel keeps one-line forwarders.
 *
 * A live list is authoritative for a pending membership change, so a
 * Loaded result hands the joined names to [communityMembershipCoordinator].
 * The membership reconciliation that forces these refreshes stays in the
 * ViewModel and waits on [redditSubscriptionsJob].
 */
internal class RedditSubscriptionsCoordinator(
    private val redditSubscriptionsLoader: AuthenticatedRedditSubscriptionsLoader?,
    private val communityMembershipCoordinator: CommunityMembershipCoordinator,
    private val scope: CoroutineScope,
) {
    private val _redditSubscriptionsUiState =
        MutableStateFlow(RedditSubscriptionsUiState())
    var redditSubscriptionsJob: Job? = null
        private set
    private var redditSubscriptionsCacheJob: Job? = null
    private var runningLoadForced = false

    val redditSubscriptionsUiState: StateFlow<RedditSubscriptionsUiState> =
        _redditSubscriptionsUiState.asStateFlow()

    fun loadCachedRedditSubscriptions(accountStableId: String) {
        val current = _redditSubscriptionsUiState.value
        if (current.matches(accountStableId) &&
            (current.loading || current.loaded || current.communities.isNotEmpty())
        ) {
            return
        }

        val loader = redditSubscriptionsLoader ?: return
        redditSubscriptionsCacheJob?.cancel()
        redditSubscriptionsCacheJob = scope.launch {
            val cached = try {
                loader.cached(accountStableId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditSubscriptionsResult.Failed(
                    kind = AuthenticatedRedditSubscriptionsFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            val latest = _redditSubscriptionsUiState.value
            if (latest.matches(accountStableId) &&
                (latest.loading || latest.loaded && !latest.fromCache)
            ) {
                return@launch
            }
            _redditSubscriptionsUiState.value = when (cached) {
                is AuthenticatedRedditSubscriptionsResult.Loaded -> RedditSubscriptionsUiState(
                    accountStableId = accountStableId,
                    communities = cached.communities,
                    favoriteCommunityNames = cached.favoriteCommunityNames,
                    fromCache = true,
                    loaded = true,
                    contentRevision = latest
                        .takeIf { state -> state.matches(accountStableId) }
                        ?.contentRevision
                        ?.plus(1L)
                        ?: 1L,
                )
                AuthenticatedRedditSubscriptionsResult.NotCached -> RedditSubscriptionsUiState(
                    accountStableId = accountStableId,
                )
                is AuthenticatedRedditSubscriptionsResult.Failed -> RedditSubscriptionsUiState(
                    accountStableId = accountStableId,
                    failureKind = cached.kind.toUiFailureKind(),
                    failureRetryable = cached.retryable,
                    failureDetails = cached.details,
                )
            }
        }
    }

    fun loadRedditSubscriptions(
        accountStableId: String,
        forceRefresh: Boolean,
    ) {
        val current = _redditSubscriptionsUiState.value
        // LOW7_FORCED_SUBSCRIPTIONS_REFRESH_REPLACES_A_LOAD_V1 (OCB-442): a forced
        // refresh (membership reconciliation waits on its job) replaces a
        // running ordinary load, which may answer from the cache.
        if (
            current.matches(accountStableId) &&
            current.loading &&
            (runningLoadForced || !forceRefresh)
        ) {
            return
        }
        if (current.matches(accountStableId) &&
            current.loaded &&
            !current.fromCache &&
            current.failureKind == null &&
            !forceRefresh
        ) {
            return
        }

        val loader = redditSubscriptionsLoader
        if (loader == null) {
            _redditSubscriptionsUiState.value = RedditSubscriptionsUiState(
                accountStableId = accountStableId,
                failureKind =
                    RedditSubscriptionsUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return
        }

        redditSubscriptionsCacheJob?.cancel()
        redditSubscriptionsJob?.cancel()
        val previousForAccount = current.takeIf { state ->
            state.matches(accountStableId)
        }
        _redditSubscriptionsUiState.value = RedditSubscriptionsUiState(
            accountStableId = accountStableId,
            communities = previousForAccount?.communities.orEmpty(),
            favoriteCommunityNames = previousForAccount?.favoriteCommunityNames.orEmpty(),
            loading = true,
            fromCache = previousForAccount?.fromCache == true,
            loaded = previousForAccount?.loaded == true,
            contentRevision = previousForAccount?.contentRevision ?: 0L,
        )
        authDebugLog(SUBSCRIPTIONS_LOAD_LOG_TAG, "start account=$accountStableId")
        runningLoadForced = forceRefresh
        redditSubscriptionsJob = scope.launch {
            if (!forceRefresh) {
                val cached = try {
                    loader.cached(accountStableId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AuthenticatedRedditSubscriptionsResult.Failed(
                        kind = AuthenticatedRedditSubscriptionsFailureKind.STORAGE,
                        retryable = true,
                        details = storageFailureDetails(),
                    )
                }
                if (!_redditSubscriptionsUiState.value.matches(accountStableId)) {
                    return@launch
                }
                if (cached is AuthenticatedRedditSubscriptionsResult.Loaded) {
                    _redditSubscriptionsUiState.value =
                        _redditSubscriptionsUiState.value.copy(
                            communities = cached.communities,
                            favoriteCommunityNames = cached.favoriteCommunityNames,
                            fromCache = true,
                            loaded = true,
                            contentRevision =
                                _redditSubscriptionsUiState.value.contentRevision + 1L,
                        )
                    authDebugLog(
                        SUBSCRIPTIONS_LOAD_LOG_TAG,
                        "cache account=$accountStableId count=${cached.communities.size}",
                    )
                }
            }

            val refreshed = try {
                loader.synchronize(accountStableId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditSubscriptionsResult.Failed(
                    kind = AuthenticatedRedditSubscriptionsFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            if (!_redditSubscriptionsUiState.value.matches(accountStableId)) {
                return@launch
            }
            _redditSubscriptionsUiState.value = when (refreshed) {
                is AuthenticatedRedditSubscriptionsResult.Loaded -> {
                    authDebugLog(
                        SUBSCRIPTIONS_LOAD_LOG_TAG,
                        "applied account=$accountStableId count=${refreshed.communities.size}",
                    )
                    communityMembershipCoordinator
                        .authoritativeRefreshApplied(
                            accountStableId = accountStableId,
                            joinedCommunityNames =
                                refreshed.communities
                                    .map { community ->
                                        community.name
                                    }
                                    .toSet(),
                        )
                    _redditSubscriptionsUiState.value.copy(
                        communities = refreshed.communities,
                        favoriteCommunityNames = refreshed.favoriteCommunityNames,
                        loading = false,
                        fromCache = false,
                        loaded = true,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        contentRevision =
                            _redditSubscriptionsUiState.value.contentRevision + 1L,
                    )
                }
                AuthenticatedRedditSubscriptionsResult.NotCached ->
                    _redditSubscriptionsUiState.value.copy(
                        loading = false,
                        failureKind = RedditSubscriptionsUiFailureKind.STORAGE,
                        failureRetryable = true,
                    )
                is AuthenticatedRedditSubscriptionsResult.Failed -> {
                    authDebugLog(
                        SUBSCRIPTIONS_LOAD_LOG_TAG,
                        "failed account=$accountStableId " +
                            "kind=${refreshed.kind.name.lowercase()} " +
                            "retryable=${refreshed.retryable}",
                    )
                    _redditSubscriptionsUiState.value.copy(
                        loading = false,
                        failureKind = refreshed.kind.toUiFailureKind(),
                        failureRetryable = refreshed.retryable,
                        failureDetails = refreshed.details,
                    )
                }
            }
        }
    }

    fun cancelPending() {
        redditSubscriptionsJob?.cancel()
        redditSubscriptionsCacheJob?.cancel()
    }
}

private fun AuthenticatedRedditSubscriptionsFailureKind.toUiFailureKind():
    RedditSubscriptionsUiFailureKind = when (this) {
    AuthenticatedRedditSubscriptionsFailureKind.CLIENT_CONFIGURATION ->
        RedditSubscriptionsUiFailureKind.CLIENT_CONFIGURATION
    AuthenticatedRedditSubscriptionsFailureKind.SESSION ->
        RedditSubscriptionsUiFailureKind.SESSION
    AuthenticatedRedditSubscriptionsFailureKind.HTTP ->
        RedditSubscriptionsUiFailureKind.HTTP
    AuthenticatedRedditSubscriptionsFailureKind.TRANSPORT ->
        RedditSubscriptionsUiFailureKind.TRANSPORT
    AuthenticatedRedditSubscriptionsFailureKind.PROTOCOL ->
        RedditSubscriptionsUiFailureKind.PROTOCOL
    AuthenticatedRedditSubscriptionsFailureKind.STORAGE ->
        RedditSubscriptionsUiFailureKind.STORAGE
}

private const val SUBSCRIPTIONS_LOAD_LOG_TAG = "subshell-subs"
