package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthenticatedRedditBootstrapFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditBootstrapResult
import app.subshell.prototype.data.auth.AuthenticatedRedditBootstrapper
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.KeyedMutex
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.auth06cInitialFeedRequest
import app.subshell.prototype.data.auth.live02FeedPageRequest
import app.subshell.prototype.data.auth.authenticatedRedditFeedQueryKey
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.safeJsonPathText
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditListingNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditListingSort
import app.subshell.prototype.data.network.reddit.RedditPostFeedNetworkSource
import app.subshell.prototype.data.network.reddit.RedditSearchPostFeedNetworkSource
import app.subshell.prototype.data.sync.OfflineFirstPostFeedSynchronizer
import app.subshell.prototype.data.sync.PostFeedNetworkSource
import app.subshell.prototype.data.sync.PostFeedSyncResult
import okhttp3.Call
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.subshell.prototype.data.network.reddit.RedditUserSubmittedPostFeedNetworkSource

internal class RedditBootstrapper(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val cacheStore: RoomSubshellCacheStore,
) : AuthenticatedRedditBootstrapper {
    override suspend fun synchronize(
        session: StoredOAuthSession,
    ): AuthenticatedRedditBootstrapResult {
        if (
            configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
            configuration.scopes != RedditOAuth.coreReadScopes ||
            session.clientMode != configuration.clientMode ||
            session.clientIdFingerprint != configuration.clientIdFingerprint
        ) {
            return AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.CLIENT_CONFIGURATION,
                retryable = false,
            )
        }

        val httpClient = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            // The account coordinator runs this first sync itself, right after
            // sign-in; it has no session-bound provider to give (AUTH17).
            bearerTokenProvider = NetworkBearerTokenProvider { session.tokens.accessToken },
        )
        val synchronizer = OfflineFirstPostFeedSynchronizer(
            networkSource = RedditPostFeedNetworkSource(
                dataSource = RedditListingNetworkDataSource(httpClient),
                sort = RedditListingSort.BEST,
            ),
            cacheStore = cacheStore,
        )
        val identity = session.identity ?: return AuthenticatedRedditBootstrapResult.Failed(
            kind = AuthenticatedRedditBootstrapFailureKind.CLIENT_CONFIGURATION,
            retryable = false,
        )
        val accountStableId = authenticatedAccountId(
            provider = configuration.provider,
            clientMode = configuration.clientMode,
            providerAccountId = identity.providerAccountId,
        )
        return when (
            val result = synchronizer.synchronize(
                auth06cInitialFeedRequest(accountStableId),
            )
        ) {
            is PostFeedSyncResult.Applied ->
                AuthenticatedRedditBootstrapResult.Synchronized(
                    queryKey = result.queryKey,
                    networkItemCount = result.networkItemCount,
                    storedItemCount = result.storedItemCount,
                )
            is PostFeedSyncResult.HttpFailure -> AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.HTTP,
                retryable = result.retryable,
            )
            is PostFeedSyncResult.TransportFailure -> AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.TRANSPORT,
                retryable = result.retryable,
            )
            is PostFeedSyncResult.ProtocolFailure -> AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.PROTOCOL,
                retryable = false,
            )
            is PostFeedSyncResult.PreconditionFailure -> AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.PROTOCOL,
                retryable = false,
            )
            PostFeedSyncResult.StorageFailure -> AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.STORAGE,
                retryable = true,
            )
        }
    }
}

internal class RedditFeedUpdater(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val cacheStore: RoomSubshellCacheStore,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditFeedUpdater {
    // LOW3_LOCK_PER_KEY_V1 (OCB-610): one lock per account + feed, so a slow
    // Home refresh no longer holds up a community page.
    private val updateLocks = KeyedMutex()

    override suspend fun synchronize(
        accountStableId: String,
        operation: RepositoryLoadOperation,
        target: AuthenticatedRedditFeedTarget,
    ): AuthenticatedRedditFeedUpdateResult = withContext(ioDispatcher) {
        updateLocks.withLock(accountStableId to target) {
            if (
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                configuration.scopes != RedditOAuth.coreReadScopes ||
                operation !in app.subshell.prototype.data.auth.LIVE02_USER_INITIATED_OPERATIONS
            ) {
                return@withLock failed(
                    AuthenticatedRedditFeedUpdateFailureKind.CLIENT_CONFIGURATION,
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
                    AuthenticatedRedditFeedUpdateFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditFeedUpdateResult.Failed(
                        kind = AuthenticatedRedditFeedUpdateFailureKind.SESSION,
                        retryable = tokenResult.result.retryable,
                        details = tokenResult.result.toFailureDetails(),
                    )
                }
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock failed(
                    AuthenticatedRedditFeedUpdateFailureKind.SESSION,
                )
            }
            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock failed(
                    AuthenticatedRedditFeedUpdateFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val queryKey = authenticatedRedditFeedQueryKey(accountStableId, target)
            val metadata = cacheStore.queryMetadata(queryKey)
            if (
                operation == RepositoryLoadOperation.APPEND &&
                (metadata == null || metadata.terminal || metadata.nextCursor == null)
            ) {
                return@withLock failed(
                    AuthenticatedRedditFeedUpdateFailureKind.END_REACHED,
                )
            }
            val request = live02FeedPageRequest(
                accountStableId = accountStableId,
                operation = operation,
                remoteCursor = metadata?.nextCursor,
                target = target,
            )
            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
            )
            val synchronizer = OfflineFirstPostFeedSynchronizer(
                networkSource = target.networkSource(
                    dataSource = RedditListingNetworkDataSource(httpClient),
                ),
                cacheStore = cacheStore,
            )
            when (val result = synchronizer.synchronize(request)) {
                is PostFeedSyncResult.Applied -> AuthenticatedRedditFeedUpdateResult.Applied(
                    queryKey = result.queryKey,
                    operation = result.operation,
                    networkItemCount = result.networkItemCount,
                    storedItemCount = result.storedItemCount,
                    endReached = result.terminal,
                )
                is PostFeedSyncResult.HttpFailure -> AuthenticatedRedditFeedUpdateResult.Failed(
                    kind = AuthenticatedRedditFeedUpdateFailureKind.HTTP,
                    retryable = result.retryable,
                    details = FailureDetails(
                        httpStatusCode = result.statusCode,
                        retryAfterMs = result.retryAfterMs,
                        retryable = result.retryable,
                        providerCode = app.subshell.prototype.data.error.SafeFailureText.fromUntrusted(result.providerReason),
                    ),
                )
                is PostFeedSyncResult.TransportFailure -> AuthenticatedRedditFeedUpdateResult.Failed(
                    kind = AuthenticatedRedditFeedUpdateFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = FailureDetails(
                        transportKind = result.kind,
                        retryable = result.retryable,
                    ),
                )
                is PostFeedSyncResult.ProtocolFailure -> failed(
                    kind = AuthenticatedRedditFeedUpdateFailureKind.PROTOCOL,
                    details = FailureDetails(protocolKind = result.kind, jsonPath = safeJsonPathText(result.jsonPath)),
                )
                is PostFeedSyncResult.PreconditionFailure ->
                    failed(AuthenticatedRedditFeedUpdateFailureKind.PROTOCOL)
                PostFeedSyncResult.StorageFailure -> failed(
                    kind = AuthenticatedRedditFeedUpdateFailureKind.STORAGE,
                    retryable = true,
                    details = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.STORAGE,
                    ),
                )
            }
        }
    }

    private fun failed(
        kind: AuthenticatedRedditFeedUpdateFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditFeedUpdateResult.Failed = AuthenticatedRedditFeedUpdateResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details ?: when (kind) {
            AuthenticatedRedditFeedUpdateFailureKind.CLIENT_CONFIGURATION -> FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.CONFIGURATION,
            )
            AuthenticatedRedditFeedUpdateFailureKind.STORAGE -> FailureDetails(
                retryable = retryable,
                localKind = LocalFailureKind.STORAGE,
            )
            else -> null
        },
    )
}

internal fun AuthenticatedRedditFeedTarget.toRedditListingSort(): RedditListingSort = when (sort) {
    RedditFeedSort.BEST -> RedditListingSort.BEST
    RedditFeedSort.HOT -> RedditListingSort.HOT
    RedditFeedSort.NEW -> RedditListingSort.NEW
    RedditFeedSort.RISING -> RedditListingSort.RISING
    RedditFeedSort.TOP -> RedditListingSort.TOP
    RedditFeedSort.CONTROVERSIAL -> RedditListingSort.CONTROVERSIAL
}

private fun AuthenticatedRedditFeedTarget.networkSource(
    dataSource: RedditListingNetworkDataSource,
): PostFeedNetworkSource = normalizedUsername?.let { username ->
    RedditUserSubmittedPostFeedNetworkSource(
        dataSource = dataSource,
        username = username,
    )
} ?: normalizedSearchQuery?.let { query ->
    RedditSearchPostFeedNetworkSource(
        dataSource = dataSource,
        query = query,
        community = normalizedCommunityName,
        includeOver18 = searchIncludeOver18,
    )
} ?: RedditPostFeedNetworkSource(
    dataSource = dataSource,
    community = listingCommunityName,
    sort = toRedditListingSort(),
    timePeriod = effectiveTimePeriod,
)

private val OAuthEndpointResult<Nothing>.retryable: Boolean
    get() = when (this) {
        is OAuthEndpointResult.HttpFailure -> retryable
        is OAuthEndpointResult.TransportFailure -> retryable
        is OAuthEndpointResult.ProtocolFailure -> false
        is OAuthEndpointResult.Success -> false
    }
