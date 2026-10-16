package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.RedditFavoriteCommunityStore
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.REDDIT_SUBSCRIPTIONS_MAX_PAGE_SIZE
import app.subshell.prototype.data.network.reddit.RedditSubscriptionNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditSubscribedCommunity
import app.subshell.prototype.data.network.reddit.RedditSubscriptionPageSource
import app.subshell.prototype.model.Community
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

internal class RedditSubscriptionsLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val cacheStore: RoomSubshellCacheStore,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val favoriteStore: RedditFavoriteCommunityStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditSubscriptionsLoader {
    private val loadMutex = Mutex()

    override suspend fun cached(
        accountStableId: String,
    ): AuthenticatedRedditSubscriptionsResult = withContext(ioDispatcher) {
        val snapshot = cacheStore.subscriptionSnapshot(accountStableId)
            ?: return@withContext AuthenticatedRedditSubscriptionsResult.NotCached
        AuthenticatedRedditSubscriptionsResult.Loaded(
            communities = snapshot.communities,
            fromCache = true,
            favoriteCommunityNames = favoriteStore?.read(accountStableId).orEmpty(),
        )
    }

    override suspend fun synchronize(
        accountStableId: String,
    ): AuthenticatedRedditSubscriptionsResult = withContext(ioDispatcher) {
        loadMutex.withLock {
            if (
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                configuration.scopes != RedditOAuth.coreReadScopes
            ) {
                return@withLock failed(
                    AuthenticatedRedditSubscriptionsFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val directory = accountCoordinator.snapshot()
            val active = directory.accounts.first { account ->
                account.stableId == directory.activeAccountId
            }
            if (
                active.source != AccountSessionSource.AUTHENTICATED ||
                active.stableId != accountStableId
            ) {
                return@withLock failed(
                    AuthenticatedRedditSubscriptionsFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditSubscriptionsResult.Failed(
                        kind = AuthenticatedRedditSubscriptionsFailureKind.SESSION,
                        retryable = when (val endpoint = tokenResult.result) {
                            is OAuthEndpointResult.HttpFailure -> endpoint.retryable
                            is OAuthEndpointResult.TransportFailure -> endpoint.retryable
                            is OAuthEndpointResult.ProtocolFailure -> false
                            is OAuthEndpointResult.Success -> false
                        },
                        details = tokenResult.result.toFailureDetails(),
                    )
                }
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock failed(
                    AuthenticatedRedditSubscriptionsFailureKind.SESSION,
                )
            }
            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock failed(
                    AuthenticatedRedditSubscriptionsFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = loadAllRedditSubscriptions(
                    RedditSubscriptionNetworkDataSource(httpClient),
                )
            ) {
                is NetworkResult.Success -> {
                    cacheStore.replaceSubscriptionSnapshot(
                        accountStableId = accountStableId,
                        communities = result.value.communities,
                    )
                    favoriteStore?.replace(
                        accountStableId = accountStableId,
                        communityNames = result.value.favoriteCommunityNames,
                    )
                    AuthenticatedRedditSubscriptionsResult.Loaded(
                        communities = result.value.communities,
                        fromCache = false,
                        favoriteCommunityNames = result.value.favoriteCommunityNames,
                    )
                }
                is NetworkResult.HttpFailure -> AuthenticatedRedditSubscriptionsResult.Failed(
                    kind = AuthenticatedRedditSubscriptionsFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditSubscriptionsResult.Failed(
                    kind = AuthenticatedRedditSubscriptionsFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> failed(
                    kind = AuthenticatedRedditSubscriptionsFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    private fun failed(
        kind: AuthenticatedRedditSubscriptionsFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditSubscriptionsResult.Failed = AuthenticatedRedditSubscriptionsResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details ?: when (kind) {
            AuthenticatedRedditSubscriptionsFailureKind.CLIENT_CONFIGURATION -> FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.CONFIGURATION,
            )
            AuthenticatedRedditSubscriptionsFailureKind.STORAGE -> FailureDetails(
                retryable = retryable,
                localKind = LocalFailureKind.STORAGE,
            )
            else -> null
        },
    )
}

internal data class RedditSubscriptionList(
    val communities: List<Community>,
    val favoriteCommunityNames: Set<String>,
)

internal suspend fun loadAllRedditSubscriptions(
    source: RedditSubscriptionPageSource,
): NetworkResult<RedditSubscriptionList> {
    val communitiesByName = linkedMapOf<String, RedditSubscribedCommunity>()
    val seenCursors = hashSetOf<String>()
    var cursor: String? = null
    repeat(MAX_REDDIT_SUBSCRIPTION_PAGES) {
        when (
            val result = source.page(
                cursor = cursor,
                limit = REDDIT_SUBSCRIPTIONS_MAX_PAGE_SIZE,
            )
        ) {
            is NetworkResult.Success -> {
                result.value.items.forEach { subscribed ->
                    communitiesByName.putIfAbsent(
                        subscribed.community.name.lowercase(),
                        subscribed,
                    )
                }
                val nextCursor = result.value.nextCursor
                if (nextCursor == null) {
                    val subscribed = communitiesByName.values.sortedBy { item ->
                        item.community.name.lowercase()
                    }
                    return NetworkResult.Success(
                        value = RedditSubscriptionList(
                            communities = subscribed.map(RedditSubscribedCommunity::community),
                            favoriteCommunityNames = subscribed
                                .filter(RedditSubscribedCommunity::favorite)
                                .map { item -> item.community.name }
                                .toSet(),
                        ),
                        metadata = result.metadata,
                    )
                }
                if (!seenCursors.add(nextCursor)) {
                    return NetworkResult.ProtocolFailure(
                        NetworkProtocolFailureKind.MALFORMED_JSON,
                    )
                }
                cursor = nextCursor
            }
            is NetworkResult.HttpFailure -> return result
            is NetworkResult.TransportFailure -> return result
            is NetworkResult.ProtocolFailure -> return result
        }
    }
    return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.BODY_TOO_LARGE)
}

private const val MAX_REDDIT_SUBSCRIPTION_PAGES = 100
