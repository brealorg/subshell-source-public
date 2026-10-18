package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxSnapshot
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.isMissingIncrementalScopeAuthorization
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.REDDIT_INBOX_MAX_PAGE_SIZE
import app.subshell.prototype.data.network.reddit.RedditInboxNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditInboxTargetNetworkDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call

// MOD01A221_MISSING_INBOX_SCOPE_IS_FEATURE_AUTH_REQUIRED_V1
internal class RedditInboxLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val cacheStore: RoomSubshellCacheStore,
    private val capability: AuthInboxReadCapability,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditInboxLoader {
    private val loadMutex = Mutex()

    override suspend fun cached(
        accountStableId: String,
    ): AuthenticatedRedditInboxResult = withContext(ioDispatcher) {
        val snapshot = cacheStore.liveInboxSnapshot(accountStableId)
            ?: return@withContext AuthenticatedRedditInboxResult.NotCached
        AuthenticatedRedditInboxResult.Loaded(
            snapshot = AuthenticatedRedditInboxSnapshot(
                items = snapshot.items,
                nextCursor = snapshot.nextCursor,
                locallySeenIds = snapshot.locallySeenIds,
            ),
            fromCache = true,
        )
    }

    override suspend fun synchronize(
        accountStableId: String,
    ): AuthenticatedRedditInboxResult = withContext(ioDispatcher) {
        loadMutex.withInboxAccountLock(accountStableId) {
            val session = when (val result = capability.ensureAuthorized(accountStableId)) {
                is AuthSessionResult.Authenticated -> result.session
                AuthSessionResult.ReauthenticationRequired ->
                    return@withInboxAccountLock AuthenticatedRedditInboxResult.AuthorizationRequired
                is AuthSessionResult.EndpointFailure -> return@withInboxAccountLock AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.SESSION,
                    retryable = when (val endpointResult = result.result) {
                        is OAuthEndpointResult.HttpFailure -> endpointResult.retryable
                        is OAuthEndpointResult.TransportFailure -> endpointResult.retryable
                        is OAuthEndpointResult.ProtocolFailure -> false
                        is OAuthEndpointResult.Success -> false
                    },
                    details = result.result.toFailureDetails(),
                )
                AuthSessionResult.StorageFailure -> return@withInboxAccountLock failed(
                    AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                )
                is AuthSessionResult.AuthorizationDenied ->
                    if (result.isMissingIncrementalScopeAuthorization()) {
                        return@withInboxAccountLock AuthenticatedRedditInboxResult.AuthorizationRequired
                    } else {
                        return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.SESSION)
                    }
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                -> return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.SESSION)
            }

            val currentUsername = session.identity?.username
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.SESSION)
            when (val result = source(session.tokens.accessToken).page(
                cursor = null,
                limit = REDDIT_INBOX_MAX_PAGE_SIZE,
                currentUsername = currentUsername,
            )) {
                is NetworkResult.Success -> {
                    cacheStore.replaceLiveInboxSnapshot(
                        accountStableId = accountStableId,
                        items = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                    val stored = requireNotNull(cacheStore.liveInboxSnapshot(accountStableId))
                    AuthenticatedRedditInboxResult.Loaded(
                        snapshot = AuthenticatedRedditInboxSnapshot(
                            items = stored.items,
                            nextCursor = stored.nextCursor,
                            locallySeenIds = stored.locallySeenIds,
                        ),
                        fromCache = false,
                    )
                }
                is NetworkResult.HttpFailure -> AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> failed(
                    kind = AuthenticatedRedditInboxFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    override suspend fun append(
        accountStableId: String,
        after: String,
    ): AuthenticatedRedditInboxResult = withContext(ioDispatcher) {
        loadMutex.withInboxAccountLock(accountStableId) {
            val normalizedAfter = after.trim().takeIf(String::isNotEmpty)
                ?: return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.PROTOCOL)
            val cached = cacheStore.liveInboxSnapshot(accountStableId)
                ?: return@withInboxAccountLock AuthenticatedRedditInboxResult.NotCached
            if (cached.nextCursor != normalizedAfter) {
                return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.PROTOCOL)
            }
            val session = when (val result = capability.ensureAuthorized(accountStableId)) {
                is AuthSessionResult.Authenticated -> result.session
                AuthSessionResult.ReauthenticationRequired ->
                    return@withInboxAccountLock AuthenticatedRedditInboxResult.AuthorizationRequired
                is AuthSessionResult.EndpointFailure -> return@withInboxAccountLock AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.SESSION,
                    retryable = when (val endpointResult = result.result) {
                        is OAuthEndpointResult.HttpFailure -> endpointResult.retryable
                        is OAuthEndpointResult.TransportFailure -> endpointResult.retryable
                        is OAuthEndpointResult.ProtocolFailure -> false
                        is OAuthEndpointResult.Success -> false
                    },
                    details = result.result.toFailureDetails(),
                )
                AuthSessionResult.StorageFailure -> return@withInboxAccountLock failed(
                    AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                )
                is AuthSessionResult.AuthorizationDenied ->
                    if (result.isMissingIncrementalScopeAuthorization()) {
                        return@withInboxAccountLock AuthenticatedRedditInboxResult.AuthorizationRequired
                    } else {
                        return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.SESSION)
                    }
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                -> return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.SESSION)
            }
            val currentUsername = session.identity?.username
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.SESSION)
            when (val result = source(session.tokens.accessToken).page(
                cursor = normalizedAfter,
                limit = REDDIT_INBOX_MAX_PAGE_SIZE,
                currentUsername = currentUsername,
            )) {
                is NetworkResult.Success -> {
                    cacheStore.appendLiveInboxPage(
                        accountStableId = accountStableId,
                        expectedCursor = normalizedAfter,
                        items = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                    val stored = requireNotNull(cacheStore.liveInboxSnapshot(accountStableId))
                    AuthenticatedRedditInboxResult.Loaded(
                        snapshot = AuthenticatedRedditInboxSnapshot(
                            items = stored.items,
                            nextCursor = stored.nextCursor,
                            locallySeenIds = stored.locallySeenIds,
                        ),
                        fromCache = false,
                    )
                }
                is NetworkResult.HttpFailure -> AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditInboxResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> failed(
                    kind = AuthenticatedRedditInboxFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    // DEAD04_NO_LOCAL_SEEN_WITHOUT_INBOX_LOCK_V1 (OCB-502): the unused
    // markLocallySeen chain (VM → coordinator → loader) is gone; seen marks are
    // written by RedditInboxReadMutationWriter.

    override suspend fun resolveTargetPost(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditInboxTargetResult = withContext(ioDispatcher) {
        val normalizedPostId = postId.trim().removePrefix("t3_").takeIf(String::isNotEmpty)
            ?: return@withContext AuthenticatedRedditInboxTargetResult.NotFound
        // CACHE04_TARGET_POST_FROM_REDDIT_V1: the target post is always read
        // from Reddit. The former cache lookup used the id without t3_ while
        // cached posts are stored by fullname, so it never matched.
        val session = when (val result = capability.ensureAuthorized(accountStableId)) {
            is AuthSessionResult.Authenticated -> result.session
            AuthSessionResult.ReauthenticationRequired ->
                return@withContext AuthenticatedRedditInboxTargetResult.AuthorizationRequired
            is AuthSessionResult.EndpointFailure ->
                return@withContext AuthenticatedRedditInboxTargetResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.SESSION,
                    retryable = when (val endpointResult = result.result) {
                        is OAuthEndpointResult.HttpFailure -> endpointResult.retryable
                        is OAuthEndpointResult.TransportFailure -> endpointResult.retryable
                        is OAuthEndpointResult.ProtocolFailure -> false
                        is OAuthEndpointResult.Success -> false
                    },
                )
            AuthSessionResult.StorageFailure ->
                return@withContext AuthenticatedRedditInboxTargetResult.Failed(
                    kind = AuthenticatedRedditInboxFailureKind.STORAGE,
                    retryable = true,
                )
            is AuthSessionResult.AuthorizationDenied ->
                if (result.isMissingIncrementalScopeAuthorization()) {
                    return@withContext AuthenticatedRedditInboxTargetResult.AuthorizationRequired
                } else {
                    return@withContext AuthenticatedRedditInboxTargetResult.Failed(
                        kind = AuthenticatedRedditInboxFailureKind.SESSION,
                        retryable = false,
                    )
                }
            AuthSessionResult.NoPendingCallback,
            AuthSessionResult.ClientConfigurationMismatch,
            -> return@withContext AuthenticatedRedditInboxTargetResult.Failed(
                kind = AuthenticatedRedditInboxFailureKind.SESSION,
                retryable = false,
            )
        }
        when (val result = targetSource(session.tokens.accessToken).post(normalizedPostId)) {
            is NetworkResult.Success -> {
                val post = result.value ?: return@withContext AuthenticatedRedditInboxTargetResult.NotFound
                AuthenticatedRedditInboxTargetResult.Loaded(
                    post = post,
                    fromCache = false,
                )
            }
            is NetworkResult.HttpFailure -> AuthenticatedRedditInboxTargetResult.Failed(
                kind = AuthenticatedRedditInboxFailureKind.HTTP,
                retryable = result.retryable,
                details = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> AuthenticatedRedditInboxTargetResult.Failed(
                kind = AuthenticatedRedditInboxFailureKind.TRANSPORT,
                retryable = result.retryable,
                details = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure -> AuthenticatedRedditInboxTargetResult.Failed(
                kind = AuthenticatedRedditInboxFailureKind.PROTOCOL,
                retryable = false,
                details = result.failureDetailsOrNull(),
            )
        }
    }

    private fun source(accessToken: String): RedditInboxNetworkDataSource =
        RedditInboxNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(accessToken),
            ),
        )

    private fun targetSource(accessToken: String): RedditInboxTargetNetworkDataSource =
        RedditInboxTargetNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(accessToken),
            ),
        )

    private fun failed(
        kind: AuthenticatedRedditInboxFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditInboxResult.Failed = AuthenticatedRedditInboxResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details ?: when (kind) {
            AuthenticatedRedditInboxFailureKind.CLIENT_CONFIGURATION -> FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.CONFIGURATION,
            )
            AuthenticatedRedditInboxFailureKind.STORAGE -> FailureDetails(
                retryable = retryable,
                localKind = LocalFailureKind.STORAGE,
            )
            else -> null
        },
    )
}
