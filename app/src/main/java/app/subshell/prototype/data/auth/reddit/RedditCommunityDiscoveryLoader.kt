package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchResult
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.auth.communityDiscoveryCacheKey
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditCommunityDiscoveryNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditCommunityDiscoveryPageSource
import app.subshell.prototype.data.network.reddit.RedditPeopleSearchNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditPeopleSearchPageSource
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.RedditPeopleSearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

internal class RedditCommunityDiscoveryLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val cacheStore: RoomSubshellCacheStore,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditCommunityDiscoveryLoader {
    private val loadMutex = Mutex()
    private val peopleSearchMutex = Mutex()

    override suspend fun cached(
        accountStableId: String,
        target: AuthenticatedRedditCommunityDiscoveryTarget,
    ): AuthenticatedRedditCommunityDiscoveryResult = withContext(ioDispatcher) {
        val snapshot = cacheStore.communityDiscoverySnapshot(
            communityDiscoveryCacheKey(accountStableId, target),
        ) ?: return@withContext AuthenticatedRedditCommunityDiscoveryResult.NotCached
        AuthenticatedRedditCommunityDiscoveryResult.Loaded(
            items = snapshot.items,
            fromCache = true,
        )
    }

    override suspend fun synchronize(
        accountStableId: String,
        target: AuthenticatedRedditCommunityDiscoveryTarget,
    ): AuthenticatedRedditCommunityDiscoveryResult = withContext(ioDispatcher) {
        loadMutex.withLock {
            if (
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                configuration.scopes != RedditOAuth.coreReadScopes
            ) {
                return@withLock failed(
                    AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION,
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
                    AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditCommunityDiscoveryResult.Failed(
                        kind = AuthenticatedRedditCommunityDiscoveryFailureKind.SESSION,
                        retryable = when (val endpointFailure = tokenResult.result) {
                            is OAuthEndpointResult.HttpFailure -> endpointFailure.retryable
                            is OAuthEndpointResult.TransportFailure -> endpointFailure.retryable
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
                    AuthenticatedRedditCommunityDiscoveryFailureKind.SESSION,
                )
            }
            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock failed(
                    AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION,
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
                val result = loadRedditCommunityDiscovery(
                    source = RedditCommunityDiscoveryNetworkDataSource(httpClient),
                    target = target,
                )
            ) {
                is NetworkResult.Success -> {
                    // LOW5_DISCOVERY_RESULTS_SURVIVE_A_CACHE_FAULT_V1 (OCB-495): the
                    // results Reddit returned are shown even when storing them fails.
                    try {
                        cacheStore.replaceCommunityDiscoverySnapshot(
                            cacheKey = communityDiscoveryCacheKey(accountStableId, target),
                            items = result.value,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The next successful load writes the snapshot.
                    }
                    AuthenticatedRedditCommunityDiscoveryResult.Loaded(
                        items = result.value,
                        fromCache = false,
                    )
                }
                is NetworkResult.HttpFailure -> AuthenticatedRedditCommunityDiscoveryResult.Failed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditCommunityDiscoveryResult.Failed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> failed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    override suspend fun searchPeople(
        accountStableId: String,
        target: AuthenticatedRedditPeopleSearchTarget,
    ): AuthenticatedRedditPeopleSearchResult = withContext(ioDispatcher) {
        peopleSearchMutex.withLock {
            if (
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                configuration.scopes != RedditOAuth.coreReadScopes
            ) {
                return@withLock peopleFailed(
                    AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val directory = accountCoordinator.snapshot()
            val active = directory.accounts.firstOrNull { account ->
                account.stableId == directory.activeAccountId
            }
            if (
                active == null ||
                active.source != AccountSessionSource.AUTHENTICATED ||
                active.stableId != accountStableId
            ) {
                return@withLock peopleFailed(
                    AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditPeopleSearchResult.Failed(
                        kind = AuthenticatedRedditCommunityDiscoveryFailureKind.SESSION,
                        retryable = when (val endpointFailure = tokenResult.result) {
                            is OAuthEndpointResult.HttpFailure -> endpointFailure.retryable
                            is OAuthEndpointResult.TransportFailure -> endpointFailure.retryable
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
                -> return@withLock peopleFailed(
                    AuthenticatedRedditCommunityDiscoveryFailureKind.SESSION,
                )
            }
            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock peopleFailed(
                    AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider =
                    accountCoordinator.sessionBoundBearerTokenProvider(
                        session.tokens.accessToken,
                    ),
            )
            when (
                val result = loadRedditPeopleSearch(
                    source = RedditPeopleSearchNetworkDataSource(httpClient),
                    target = target,
                )
            ) {
                is NetworkResult.Success -> AuthenticatedRedditPeopleSearchResult.Loaded(
                    items = result.value,
                )
                is NetworkResult.HttpFailure -> AuthenticatedRedditPeopleSearchResult.Failed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure ->
                    AuthenticatedRedditPeopleSearchResult.Failed(
                        kind = AuthenticatedRedditCommunityDiscoveryFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure -> peopleFailed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    private fun peopleFailed(
        kind: AuthenticatedRedditCommunityDiscoveryFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditPeopleSearchResult.Failed =
        AuthenticatedRedditPeopleSearchResult.Failed(
            kind = kind,
            retryable = retryable,
            details = details ?: when (kind) {
                AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION ->
                    FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.CONFIGURATION,
                    )
                AuthenticatedRedditCommunityDiscoveryFailureKind.STORAGE ->
                    FailureDetails(
                        retryable = retryable,
                        localKind = LocalFailureKind.STORAGE,
                    )
                else -> null
            },
        )

    private fun failed(
        kind: AuthenticatedRedditCommunityDiscoveryFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditCommunityDiscoveryResult.Failed = AuthenticatedRedditCommunityDiscoveryResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details ?: when (kind) {
            AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION -> FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.CONFIGURATION,
            )
            AuthenticatedRedditCommunityDiscoveryFailureKind.STORAGE -> FailureDetails(
                retryable = retryable,
                localKind = LocalFailureKind.STORAGE,
            )
            else -> null
        },
    )
}

internal suspend fun loadRedditCommunityDiscovery(
    source: RedditCommunityDiscoveryPageSource,
    target: AuthenticatedRedditCommunityDiscoveryTarget,
): NetworkResult<List<CommunityDiscoveryItem>> = when (
    val result = source.page(
        searchQuery = target.searchQuery,
        searchSort = target.searchSort,
        autocompleteIncludeOver18 = target.autocompleteIncludeOver18,
        cursor = null,
        limit = target.pageSize,
        listingIncludeOver18 = target.listingIncludeOver18,
    )
) {
    is NetworkResult.Success -> NetworkResult.Success(
        value = result.value.items.distinctBy { item ->
            item.community.name.lowercase()
        },
        metadata = result.metadata,
    )
    is NetworkResult.HttpFailure -> result
    is NetworkResult.TransportFailure -> result
    is NetworkResult.ProtocolFailure -> result
}

internal suspend fun loadRedditPeopleSearch(
    source: RedditPeopleSearchPageSource,
    target: AuthenticatedRedditPeopleSearchTarget,
): NetworkResult<List<RedditPeopleSearchItem>> = when (
    val result = source.page(
        searchQuery = target.searchQuery,
        cursor = null,
        limit = target.pageSize,
    )
) {
    is NetworkResult.Success -> NetworkResult.Success(
        value = result.value.items.distinctBy { item ->
            item.username.lowercase()
        },
        metadata = result.metadata,
    )
    is NetworkResult.HttpFailure -> result
    is NetworkResult.TransportFailure -> result
    is NetworkResult.ProtocolFailure -> result
}
