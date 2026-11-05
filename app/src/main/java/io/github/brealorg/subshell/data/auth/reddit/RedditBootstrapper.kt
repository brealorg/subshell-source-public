package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.error.SafeFailureText
import io.github.brealorg.subshell.data.RepositoryLoadOperation
import io.github.brealorg.subshell.data.RedditFeedSort
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBootstrapFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBootstrapResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBootstrapper
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFeedUpdateFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFeedUpdateResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFeedTarget
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFeedUpdater
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.KeyedMutex
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.toFailureDetails
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.auth.authenticatedAccountId
import io.github.brealorg.subshell.data.auth.auth06cInitialFeedRequest
import io.github.brealorg.subshell.data.auth.live02FeedPageRequest
import io.github.brealorg.subshell.data.auth.authenticatedRedditFeedQueryKey
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.safeJsonPathText
import io.github.brealorg.subshell.data.error.LocalFailureKind
import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditListingNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditListingSort
import io.github.brealorg.subshell.data.network.reddit.RedditPostFeedNetworkSource
import io.github.brealorg.subshell.data.network.reddit.RedditSearchPostFeedNetworkSource
import io.github.brealorg.subshell.data.sync.OfflineFirstPostFeedSynchronizer
import io.github.brealorg.subshell.data.sync.PostFeedNetworkSource
import io.github.brealorg.subshell.data.sync.PostFeedSyncResult
import okhttp3.Call
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.brealorg.subshell.data.network.reddit.RedditUserSubmittedPostFeedNetworkSource

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
                details = FailureDetails(
                    httpStatusCode = result.statusCode.takeIf { it in 100..599 },
                    retryAfterMs = result.retryAfterMs?.coerceAtLeast(0L),
                    retryable = result.retryable,
                    providerCode = SafeFailureText.fromUntrusted(result.providerReason),
                ),
            )
            is PostFeedSyncResult.TransportFailure -> AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.TRANSPORT,
                retryable = result.retryable,
                details = FailureDetails(transportKind = result.kind, retryable = result.retryable),
            )
            is PostFeedSyncResult.ProtocolFailure -> AuthenticatedRedditBootstrapResult.Failed(
                kind = AuthenticatedRedditBootstrapFailureKind.PROTOCOL,
                retryable = false,
                details = FailureDetails(
                    retryable = false,
                    protocolKind = result.kind,
                    jsonPath = safeJsonPathText(result.jsonPath),
                ),
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
                operation !in io.github.brealorg.subshell.data.auth.LIVE02_USER_INITIATED_OPERATIONS
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
                        providerCode = io.github.brealorg.subshell.data.error.SafeFailureText.fromUntrusted(result.providerReason),
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
        order = userPostsOrder,
    )
} ?: normalizedSearchQuery?.let { query ->
    RedditSearchPostFeedNetworkSource(
        dataSource = dataSource,
        query = query,
        community = normalizedCommunityName,
        includeOver18 = searchIncludeOver18,
        order = searchOrder,
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
