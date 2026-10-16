package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistorySnapshot
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.cache.SavedHistoryAppendNeedsRefreshException
import app.subshell.prototype.data.cache.RedditSavedHistoryCacheStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditAccountHistoryNetworkDataSource
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call

internal class RedditSavedHistoryLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val cacheStore: RedditSavedHistoryCacheStore,
    private val capability: AuthProfileHistoryCapability,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditSavedHistoryLoader {
    private val loadMutex = Mutex()

    override suspend fun cached(
        accountStableId: String,
        username: String,
    ): AuthenticatedRedditSavedHistoryResult = withContext(ioDispatcher) {
        val identity = normalizedIdentity(accountStableId, username)
            ?: return@withContext failed(
                AuthenticatedRedditSavedHistoryFailureKind.PROTOCOL,
            )
        val queryKey = redditMixedSavedHistoryQueryKey(
            accountStableId = identity.first,
            username = identity.second,
        )
        val cached = try {
            cacheStore.read(queryKey)
        } catch (_: Exception) {
            return@withContext failed(
                kind = AuthenticatedRedditSavedHistoryFailureKind.STORAGE,
                retryable = true,
            )
        } ?: return@withContext AuthenticatedRedditSavedHistoryResult.NotCached

        AuthenticatedRedditSavedHistoryResult.Loaded(
            snapshot = AuthenticatedRedditSavedHistorySnapshot(
                items = cached.items,
                nextCursor = cached.nextCursor,
            ),
            fromCache = true,
        )
    }

    override suspend fun synchronize(
        accountStableId: String,
        username: String,
    ): AuthenticatedRedditSavedHistoryResult = withContext(ioDispatcher) {
        loadMutex.lock()
        try {
            load(
                accountStableId = accountStableId,
                username = username,
                after = null,
            )
        } finally {
            loadMutex.unlock()
        }
    }

    override suspend fun append(
        accountStableId: String,
        username: String,
        after: String,
    ): AuthenticatedRedditSavedHistoryResult = withContext(ioDispatcher) {
        loadMutex.lock()
        try {
            load(
                accountStableId = accountStableId,
                username = username,
                after = after.trim().takeIf(String::isNotEmpty)
                    ?: return@withContext failed(
                        AuthenticatedRedditSavedHistoryFailureKind.PROTOCOL,
                    ),
            )
        } finally {
            loadMutex.unlock()
        }
    }

    private suspend fun load(
        accountStableId: String,
        username: String,
        after: String?,
    ): AuthenticatedRedditSavedHistoryResult {
        val identity = normalizedIdentity(accountStableId, username)
            ?: return failed(
                AuthenticatedRedditSavedHistoryFailureKind.PROTOCOL,
            )
        val account = identity.first
        val normalizedUsername = identity.second
        val queryKey = redditMixedSavedHistoryQueryKey(
            accountStableId = account,
            username = normalizedUsername,
        )

        val session = when (val authResult = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> authResult.session
            AuthSessionResult.ReauthenticationRequired ->
                return AuthenticatedRedditSavedHistoryResult.AuthorizationRequired
            is AuthSessionResult.EndpointFailure ->
                return failed(
                    kind = AuthenticatedRedditSavedHistoryFailureKind.SESSION,
                    retryable = authResult.result.savedHistoryRetryable(),
                    details = authResult.result.toFailureDetails(),
                )
            AuthSessionResult.StorageFailure ->
                return failed(
                    kind = AuthenticatedRedditSavedHistoryFailureKind.STORAGE,
                    retryable = true,
                )
            AuthSessionResult.NoPendingCallback,
            AuthSessionResult.ClientConfigurationMismatch,
            is AuthSessionResult.AuthorizationDenied,
            -> return failed(
                AuthenticatedRedditSavedHistoryFailureKind.SESSION,
            )
        }

        val operation = if (after == null) {
            RepositoryLoadOperation.REFRESH
        } else {
            RepositoryLoadOperation.APPEND
        }
        val result = source(session.tokens.accessToken).saved(
            username = normalizedUsername,
            request = RepositoryPageRequest(
                cursor = after,
                limit = SAVED_HISTORY_PAGE_SIZE,
                queryKey = queryKey,
                operation = operation,
            ),
        )

        return when (result) {
            is NetworkResult.Success -> {
                val stored = try {
                    if (after == null) {
                        cacheStore.replace(
                            queryKey = queryKey,
                            items = result.value.items,
                            nextCursor = result.value.nextCursor,
                        )
                    } else {
                        cacheStore.append(
                            queryKey = queryKey,
                            expectedCursor = after,
                            items = result.value.items,
                            nextCursor = result.value.nextCursor,
                        )
                    }
                } catch (_: SavedHistoryAppendNeedsRefreshException) {
                    // OCB-618: nothing cached to continue; reload from the top.
                    return load(accountStableId, username, after = null)
                } catch (_: IllegalArgumentException) {
                    return failed(
                        AuthenticatedRedditSavedHistoryFailureKind.PROTOCOL,
                    )
                } catch (_: IllegalStateException) {
                    return failed(
                        AuthenticatedRedditSavedHistoryFailureKind.PROTOCOL,
                    )
                } catch (_: Exception) {
                    return failed(
                        kind = AuthenticatedRedditSavedHistoryFailureKind.STORAGE,
                        retryable = true,
                    )
                }
                AuthenticatedRedditSavedHistoryResult.Loaded(
                    snapshot = AuthenticatedRedditSavedHistorySnapshot(
                        items = stored.items,
                        nextCursor = stored.nextCursor,
                    ),
                    fromCache = false,
                )
            }
            is NetworkResult.HttpFailure ->
                failed(
                    kind = AuthenticatedRedditSavedHistoryFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
            is NetworkResult.TransportFailure ->
                failed(
                    kind = AuthenticatedRedditSavedHistoryFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
            is NetworkResult.ProtocolFailure ->
                failed(
                    kind = AuthenticatedRedditSavedHistoryFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
        }
    }

    private fun source(
        accessToken: String,
    ): RedditAccountHistoryNetworkDataSource =
        RedditAccountHistoryNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(accessToken),
            ),
        )

    private fun normalizedIdentity(
        accountStableId: String,
        username: String,
    ): Pair<String, String>? {
        val account = accountStableId.trim().takeIf(String::isNotEmpty)
            ?: return null
        val normalizedUsername = runCatching {
            normalizeRedditUsername(username)
        }.getOrNull() ?: return null
        return account to normalizedUsername
    }

    private fun failed(
        kind: AuthenticatedRedditSavedHistoryFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditSavedHistoryResult.Failed =
        AuthenticatedRedditSavedHistoryResult.Failed(
            kind = kind,
            retryable = retryable,
            details = details ?: if (
                kind == AuthenticatedRedditSavedHistoryFailureKind.STORAGE
            ) {
                FailureDetails(
                    retryable = retryable,
                    localKind = LocalFailureKind.STORAGE,
                )
            } else {
                null
            },
        )
}

private fun OAuthEndpointResult<*>.savedHistoryRetryable(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}

internal const val SAVED_HISTORY_PAGE_SIZE = 25
