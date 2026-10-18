package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.SafeFailureText
import app.subshell.prototype.data.error.isHttpStatusCodeInRange
import app.subshell.prototype.data.error.safeJsonPathText
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.KeyedMutex
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditAccountHistoryNetworkDataSource
import app.subshell.prototype.data.sync.OfflineFirstPostFeedSynchronizer
import app.subshell.prototype.data.sync.PostFeedSyncResult
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.redditAccountHistoryPostQueryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call

internal class RedditAccountHistoryUpdater(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val cacheStore: RoomSubshellCacheStore,
    private val capability: AuthProfileHistoryCapability,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditAccountHistoryUpdater {
    // LOW3_LOCK_PER_KEY_V1 (OCB-608): one lock per account, user and history
    // kind, so a slow Upvoted load no longer holds up another account's Saved.
    private val updateLocks = KeyedMutex()

    override suspend fun synchronize(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        operation: RepositoryLoadOperation,
    ): AuthenticatedRedditAccountHistoryUpdateResult = withContext(ioDispatcher) {
        updateLocks.withLock(Triple(accountStableId.trim(), username.trim().lowercase(), kind)) {
            val account = accountStableId.trim()
            val normalizedUsername = runCatching {
                normalizeRedditUsername(username)
            }.getOrNull()
            if (
                account.isEmpty() ||
                normalizedUsername == null ||
                !kind.isPostHistory ||
                operation !in setOf(
                    RepositoryLoadOperation.REFRESH,
                    RepositoryLoadOperation.APPEND,
                )
            ) {
                return@withLock failed(
                    AuthenticatedRedditAccountHistoryFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val queryKey = redditAccountHistoryPostQueryKey(
                accountStableId = account,
                username = normalizedUsername,
                kind = kind,
            )
            val metadata = try {
                cacheStore.queryMetadata(queryKey)
            } catch (_: Exception) {
                return@withLock failed(
                    kind = AuthenticatedRedditAccountHistoryFailureKind.STORAGE,
                    retryable = true,
                )
            }
            if (
                operation == RepositoryLoadOperation.APPEND &&
                (metadata == null || metadata.terminal || metadata.nextCursor == null)
            ) {
                return@withLock failed(
                    AuthenticatedRedditAccountHistoryFailureKind.END_REACHED,
                )
            }

            val session = when (val authResult = capability.ensureAuthorized(account)) {
                is AuthSessionResult.Authenticated -> authResult.session
                AuthSessionResult.ReauthenticationRequired ->
                    return@withLock AuthenticatedRedditAccountHistoryUpdateResult.AuthorizationRequired
                is AuthSessionResult.EndpointFailure ->
                    return@withLock failed(
                        kind = AuthenticatedRedditAccountHistoryFailureKind.SESSION,
                        retryable = authResult.result.accountHistoryRetryable(),
                    )
                AuthSessionResult.StorageFailure ->
                    return@withLock failed(
                        kind = AuthenticatedRedditAccountHistoryFailureKind.STORAGE,
                        retryable = true,
                    )
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock failed(
                    AuthenticatedRedditAccountHistoryFailureKind.SESSION,
                )
            }

            val request = RepositoryPageRequest(
                cursor = if (operation == RepositoryLoadOperation.APPEND) {
                    metadata?.nextCursor
                } else {
                    null
                },
                limit = REDDIT_ACCOUNT_HISTORY_PAGE_SIZE,
                queryKey = queryKey,
                operation = operation,
            )
            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            val source = RedditAccountHistoryNetworkDataSource(httpClient)
            val synchronizer = OfflineFirstPostFeedSynchronizer(
                networkSource = { pageRequest ->
                    source.posts(
                        username = normalizedUsername,
                        kind = kind,
                        request = pageRequest,
                    )
                },
                cacheStore = cacheStore,
            )

            when (val result = synchronizer.synchronize(request)) {
                is PostFeedSyncResult.Applied ->
                    AuthenticatedRedditAccountHistoryUpdateResult.Applied(
                        queryKey = result.queryKey,
                        operation = result.operation,
                        networkItemCount = result.networkItemCount,
                        storedItemCount = result.storedItemCount,
                        endReached = result.terminal,
                    )
                // DIAG04_JSON_PATH_REACHES_THE_REPORT_V1: the history keeps the
                // feed sync's details (status, reason, the field a decode failed on).
                is PostFeedSyncResult.HttpFailure ->
                    failed(
                        kind = AuthenticatedRedditAccountHistoryFailureKind.HTTP,
                        retryable = result.retryable,
                        details = FailureDetails(
                            httpStatusCode = result.statusCode.takeIf(::isHttpStatusCodeInRange),
                            retryAfterMs = result.retryAfterMs?.coerceAtLeast(0L),
                            retryable = result.retryable,
                            providerCode = SafeFailureText.fromUntrusted(result.providerReason),
                        ),
                    )
                is PostFeedSyncResult.TransportFailure ->
                    failed(
                        kind = AuthenticatedRedditAccountHistoryFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = FailureDetails(transportKind = result.kind, retryable = result.retryable),
                    )
                is PostFeedSyncResult.ProtocolFailure ->
                    failed(
                        kind = AuthenticatedRedditAccountHistoryFailureKind.PROTOCOL,
                        details = FailureDetails(
                            retryable = false,
                            protocolKind = result.kind,
                            jsonPath = safeJsonPathText(result.jsonPath),
                        ),
                    )
                is PostFeedSyncResult.PreconditionFailure ->
                    failed(AuthenticatedRedditAccountHistoryFailureKind.PROTOCOL)
                PostFeedSyncResult.StorageFailure ->
                    failed(
                        kind = AuthenticatedRedditAccountHistoryFailureKind.STORAGE,
                        retryable = true,
                    )
            }
        }
    }

    private fun failed(
        kind: AuthenticatedRedditAccountHistoryFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditAccountHistoryUpdateResult.Failed =
        AuthenticatedRedditAccountHistoryUpdateResult.Failed(
            kind = kind,
            retryable = retryable,
            details = details,
        )
}

private fun OAuthEndpointResult<Nothing>.accountHistoryRetryable(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}

internal const val REDDIT_ACCOUNT_HISTORY_PAGE_SIZE = 25
