package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsSnapshot
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
import app.subshell.prototype.data.network.reddit.REDDIT_PROFILE_COMMENTS_PAGE_SIZE
import app.subshell.prototype.data.network.reddit.RedditAccountHistoryNetworkDataSource
import app.subshell.prototype.data.network.reddit.normalizeRedditProfileCommentsUsername
import app.subshell.prototype.model.ProfileCommentFixture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentTargetResult
import app.subshell.prototype.data.network.reddit.RedditInboxTargetNetworkDataSource

internal class RedditProfileCommentsLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val cacheStore: RoomSubshellCacheStore,
    private val capability: AuthProfileHistoryCapability,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditProfileCommentsLoader, AuthenticatedRedditAccountHistoryCommentLoader {
    private val loadMutex = Mutex()

    // DEAD02_PROFILE_COMMENTS_FLOW_REMOVED_V1: the two-argument cached/
    // synchronize/append overloads (the removed profile comments flow, always
    // COMMENTS) are gone; callers pass the account-history kind.

    override suspend fun cached(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): AuthenticatedRedditProfileCommentsResult =
        withContext(ioDispatcher) {
            if (kind.isPostHistory) {
                return@withContext failed(
                    AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                )
            }
            val queryKey =
                try {
                    redditAccountHistoryQueryKey(
                        accountStableId = accountStableId,
                        username = username,
                        kind = kind,
                    )
                } catch (_: IllegalArgumentException) {
                    return@withContext failed(
                        AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                    )
                }

            val comments =
                try {
                    var stored = cachedComments(queryKey.value)
                    if (
                        stored.isEmpty() &&
                        kind == RedditAccountHistoryKind.COMMENTS
                    ) {
                        val legacyCacheKey =
                            redditProfileCommentsCacheKey(
                                accountStableId = accountStableId,
                                username = username,
                            )
                        val legacy = cachedComments(legacyCacheKey)
                        if (legacy.isNotEmpty()) {
                            cacheStore.cacheProfileComments(
                                username = queryKey.value,
                                values = legacy,
                            )
                            stored = cachedComments(queryKey.value)
                        }
                    }
                    stored
                } catch (_: Exception) {
                    return@withContext failed(
                        kind =
                            AuthenticatedRedditProfileCommentsFailureKind
                                .STORAGE,
                        retryable = true,
                    )
                }

            if (comments.isEmpty()) {
                AuthenticatedRedditProfileCommentsResult.NotCached
            } else {
                AuthenticatedRedditProfileCommentsResult.Loaded(
                    snapshot =
                        AuthenticatedRedditProfileCommentsSnapshot(
                            comments = comments,
                            nextCursor = null,
                        ),
                    fromCache = true,
                )
            }
        }

    override suspend fun synchronize(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): AuthenticatedRedditProfileCommentsResult =
        withContext(ioDispatcher) {
            loadMutex.lock()

            try {
                synchronizeLocked(
                    accountStableId = accountStableId,
                    username = username,
                    kind = kind,
                )
            } finally {
                loadMutex.unlock()
            }
        }

    override suspend fun append(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        after: String,
    ): AuthenticatedRedditProfileCommentsResult =
        withContext(ioDispatcher) {
            loadMutex.lock()

            try {
                appendLocked(
                    accountStableId = accountStableId,
                    username = username,
                    kind = kind,
                    after = after,
                )
            } finally {
                loadMutex.unlock()
            }
        }

    private suspend fun synchronizeLocked(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): AuthenticatedRedditProfileCommentsResult {
        val account =
            normalizedProfileCommentsAccount(accountStableId)
                ?: return failed(
                    AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                )

        val normalizedUsername =
            try {
                normalizeRedditProfileCommentsUsername(username)
            } catch (_: IllegalArgumentException) {
                return failed(
                    AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                )
            }
        if (kind.isPostHistory) {
            return failed(
                AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
            )
        }
        val queryKey = redditAccountHistoryQueryKey(
            accountStableId = account,
            username = normalizedUsername,
            kind = kind,
        )

        val authResult =
            capability.ensureAuthorized(account)

        val session =
            when (authResult) {
                is AuthSessionResult.Authenticated ->
                    authResult.session

                AuthSessionResult.ReauthenticationRequired ->
                    return AuthenticatedRedditProfileCommentsResult
                        .AuthorizationRequired

                is AuthSessionResult.EndpointFailure ->
                    return failed(
                        kind =
                            AuthenticatedRedditProfileCommentsFailureKind
                                .SESSION,
                        retryable =
                            authResult.result.profileHistoryRetryable(),
                        details = authResult.result.toFailureDetails(),
                    )

                AuthSessionResult.StorageFailure ->
                    return failed(
                        kind =
                            AuthenticatedRedditProfileCommentsFailureKind
                                .STORAGE,
                        retryable = true,
                    )

                AuthSessionResult.NoPendingCallback ->
                    return failed(
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                    )

                AuthSessionResult.ClientConfigurationMismatch ->
                    return failed(
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                    )

                is AuthSessionResult.AuthorizationDenied ->
                    return failed(
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                    )
            }

        val networkResult =
            historySource(session.tokens.accessToken).comments(
                username = normalizedUsername,
                kind = kind,
                request = RepositoryPageRequest(
                    limit = REDDIT_PROFILE_COMMENTS_PAGE_SIZE,
                    queryKey = queryKey,
                    operation = RepositoryLoadOperation.REFRESH,
                ),
            )

        return when (networkResult) {
            is NetworkResult.Success -> {
                val cacheKey = queryKey.value

                val stored =
                    try {
                        cacheStore.cacheProfileComments(
                            username = cacheKey,
                            values = networkResult.value.items,
                        )

                        cachedComments(cacheKey)
                    } catch (_: Exception) {
                        return failed(
                            kind =
                                AuthenticatedRedditProfileCommentsFailureKind
                                    .STORAGE,
                            retryable = true,
                        )
                    }

                AuthenticatedRedditProfileCommentsResult.Loaded(
                    snapshot =
                        AuthenticatedRedditProfileCommentsSnapshot(
                            comments = stored,
                            nextCursor =
                                networkResult.value.nextCursor,
                        ),
                    fromCache = false,
                )
            }

            is NetworkResult.HttpFailure ->
                failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.HTTP,
                    retryable = networkResult.retryable,
                    details = networkResult.failureDetailsOrNull(),
                )

            is NetworkResult.TransportFailure ->
                failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.TRANSPORT,
                    retryable = networkResult.retryable,
                    details = networkResult.failureDetailsOrNull(),
                )

            is NetworkResult.ProtocolFailure ->
                failed(
                    kind = AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                    details = networkResult.failureDetailsOrNull(),
                )
        }
    }

    private suspend fun appendLocked(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
        after: String,
    ): AuthenticatedRedditProfileCommentsResult {
        val account =
            normalizedProfileCommentsAccount(accountStableId)
                ?: return failed(
                    AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                )

        val normalizedUsername =
            try {
                normalizeRedditProfileCommentsUsername(username)
            } catch (_: IllegalArgumentException) {
                return failed(
                    AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                )
            }
        if (kind.isPostHistory) {
            return failed(
                AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
            )
        }
        val queryKey = redditAccountHistoryQueryKey(
            accountStableId = account,
            username = normalizedUsername,
            kind = kind,
        )

        val cursor =
            after
                .trim()
                .takeIf(String::isNotEmpty)
                ?: return failed(
                    AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                )

        val authResult =
            capability.ensureAuthorized(account)

        val session =
            when (authResult) {
                is AuthSessionResult.Authenticated ->
                    authResult.session

                AuthSessionResult.ReauthenticationRequired ->
                    return AuthenticatedRedditProfileCommentsResult
                        .AuthorizationRequired

                is AuthSessionResult.EndpointFailure ->
                    return failed(
                        kind =
                            AuthenticatedRedditProfileCommentsFailureKind
                                .SESSION,
                        retryable =
                            authResult.result.profileHistoryRetryable(),
                        details = authResult.result.toFailureDetails(),
                    )

                AuthSessionResult.StorageFailure ->
                    return failed(
                        kind =
                            AuthenticatedRedditProfileCommentsFailureKind
                                .STORAGE,
                        retryable = true,
                    )

                AuthSessionResult.NoPendingCallback ->
                    return failed(
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                    )

                AuthSessionResult.ClientConfigurationMismatch ->
                    return failed(
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                    )

                is AuthSessionResult.AuthorizationDenied ->
                    return failed(
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                    )
            }

        val networkResult =
            historySource(session.tokens.accessToken).comments(
                username = normalizedUsername,
                kind = kind,
                request = RepositoryPageRequest(
                    cursor = cursor,
                    limit = REDDIT_PROFILE_COMMENTS_PAGE_SIZE,
                    queryKey = queryKey,
                    operation = RepositoryLoadOperation.APPEND,
                ),
            )

        return when (networkResult) {
            is NetworkResult.Success -> {
                val cacheKey = queryKey.value

                val stored =
                    try {
                        val existing =
                            cachedComments(cacheKey)

                        if (existing.isEmpty()) {
                            // Nothing readable stored: write the page as the list.
                            cacheStore.cacheProfileComments(
                                username = cacheKey,
                                values = networkResult.value.items
                                    .distinctBy(ProfileCommentFixture::id),
                            )
                            cachedComments(cacheKey)
                        } else {
                            // LOW3_PROFILE_COMMENT_APPEND_WRITES_ONLY_NEW_ROWS_V1 (OCB-519)
                            (
                                existing +
                                    cacheStore.appendProfileComments(
                                        username = cacheKey,
                                        values = networkResult.value.items,
                                    )
                            ).take(RepositoryPageRequest.MAX_LIMIT)
                        }
                    } catch (_: Exception) {
                        return failed(
                            kind =
                                AuthenticatedRedditProfileCommentsFailureKind
                                    .STORAGE,
                            retryable = true,
                        )
                    }

                AuthenticatedRedditProfileCommentsResult.Loaded(
                    snapshot =
                        AuthenticatedRedditProfileCommentsSnapshot(
                            comments = stored,
                            nextCursor =
                                networkResult.value.nextCursor,
                        ),
                    fromCache = false,
                )
            }

            is NetworkResult.HttpFailure ->
                failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.HTTP,
                    retryable = networkResult.retryable,
                    details = networkResult.failureDetailsOrNull(),
                )

            is NetworkResult.TransportFailure ->
                failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.TRANSPORT,
                    retryable = networkResult.retryable,
                    details = networkResult.failureDetailsOrNull(),
                )

            is NetworkResult.ProtocolFailure ->
                failed(
                    kind = AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                    details = networkResult.failureDetailsOrNull(),
                )
        }
    }

    override fun observeStoredHistory(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): Flow<List<ProfileCommentFixture>> {
        if (kind.isPostHistory) return emptyFlow()
        val queryKey = runCatching {
            redditAccountHistoryQueryKey(accountStableId = accountStableId, username = username, kind = kind)
        }.getOrNull() ?: return emptyFlow()
        return cacheStore.observeProfileComments(queryKey.value).flowOn(ioDispatcher)
    }

    override suspend fun updateStoredHistoryComment(
        commentFullname: String,
        transform: (ProfileCommentFixture) -> ProfileCommentFixture?,
    ) = withContext(ioDispatcher) {
        val bare = commentFullname.trim().removePrefix("t1_").takeIf(String::isNotEmpty) ?: return@withContext
        cacheStore.updateProfileComment(commentIds = listOf("t1_$bare", bare), transform = transform)
    }

    private fun cachedComments(
        cacheKey: String,
    ): List<ProfileCommentFixture> =
        cacheStore.profileComments(
            username = cacheKey,
            request =
                RepositoryPageRequest(
                    limit = RepositoryPageRequest.MAX_LIMIT,
                ),
        )

    override suspend fun resolveTargetPost(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditProfileCommentTargetResult =
        withContext(ioDispatcher) {
            resolveTargetPostOnIo(
                accountStableId = accountStableId,
                postId = postId,
            )
        }

    private suspend fun resolveTargetPostOnIo(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditProfileCommentTargetResult {
        val account = accountStableId
            .trim()
            .takeIf(String::isNotEmpty)
            ?: return AuthenticatedRedditProfileCommentTargetResult.Failed(
                kind = AuthenticatedRedditProfileCommentsFailureKind.SESSION,
            )

        val normalizedPostId = postId
            .trim()
            .removePrefix("t3_")
            .takeIf(String::isNotEmpty)
            ?: return AuthenticatedRedditProfileCommentTargetResult.NotFound

        // CACHE04_TARGET_POST_FROM_REDDIT_V1: see RedditInboxLoader.

        val authorization =
            capability.ensureAuthorized(account)

        val session = when (authorization) {
            is AuthSessionResult.Authenticated ->
                authorization.session

            AuthSessionResult.ReauthenticationRequired ->
                return AuthenticatedRedditProfileCommentTargetResult
                    .AuthorizationRequired

            is AuthSessionResult.EndpointFailure ->
                return AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                    retryable =
                        authorization.result.profileHistoryTargetRetryable(),
                )

            AuthSessionResult.StorageFailure ->
                return AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.STORAGE,
                    retryable = true,
                )

            AuthSessionResult.NoPendingCallback ->
                return AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                )

            AuthSessionResult.ClientConfigurationMismatch ->
                return AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                )

            is AuthSessionResult.AuthorizationDenied ->
                return AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.SESSION,
                )
        }

        val networkResult =
            targetSource(session.tokens.accessToken)
                .post(normalizedPostId)

        return when (networkResult) {
            is NetworkResult.Success -> {
                val post = networkResult.value
                    ?: return AuthenticatedRedditProfileCommentTargetResult
                        .NotFound

                AuthenticatedRedditProfileCommentTargetResult.Loaded(
                    post = post,
                    fromCache = false,
                )
            }

            is NetworkResult.HttpFailure ->
                AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.HTTP,
                    retryable = networkResult.retryable,
                    details = networkResult.failureDetailsOrNull(),
                )

            is NetworkResult.TransportFailure ->
                AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.TRANSPORT,
                    retryable = networkResult.retryable,
                    details = networkResult.failureDetailsOrNull(),
                )

            is NetworkResult.ProtocolFailure ->
                AuthenticatedRedditProfileCommentTargetResult.Failed(
                    kind =
                        AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL,
                    details = networkResult.failureDetailsOrNull(),
                )
        }
    }

    private fun targetSource(
        accessToken: String,
    ): RedditInboxTargetNetworkDataSource =
        RedditInboxTargetNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider =
                    capability.bearerTokenProvider(accessToken),
            ),
        )

    private fun OAuthEndpointResult<*>.profileHistoryTargetRetryable():
        Boolean =
        when (this) {
            is OAuthEndpointResult.HttpFailure -> retryable
            is OAuthEndpointResult.TransportFailure -> retryable
            is OAuthEndpointResult.ProtocolFailure -> false
            is OAuthEndpointResult.Success -> false
        }

    private fun historySource(
        accessToken: String,
    ): RedditAccountHistoryNetworkDataSource =
        RedditAccountHistoryNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider =
                    capability.bearerTokenProvider(accessToken),
            ),
        )

    private fun failed(
        kind: AuthenticatedRedditProfileCommentsFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditProfileCommentsResult.Failed =
        AuthenticatedRedditProfileCommentsResult.Failed(
            kind = kind,
            retryable = retryable,
            details = details ?: when (kind) {
                AuthenticatedRedditProfileCommentsFailureKind.STORAGE -> FailureDetails(
                    retryable = retryable,
                    localKind = LocalFailureKind.STORAGE,
                )
                else -> null
            },
        )
}

internal fun redditProfileCommentsCacheKey(
    accountStableId: String,
    username: String,
): String {
    val account =
        requireNotNull(
            normalizedProfileCommentsAccount(accountStableId),
        )

    val normalizedUsername =
        normalizeRedditProfileCommentsUsername(username)
            .lowercase()

    return buildString {
        append("account|")
        append(account)
        append("|profile-comments|")
        append(normalizedUsername)
    }
}

private fun normalizedProfileCommentsAccount(
    value: String,
): String? =
    value
        .trim()
        .takeIf(String::isNotEmpty)

private fun OAuthEndpointResult<*>.profileHistoryRetryable(): Boolean =
    when (this) {
        is OAuthEndpointResult.HttpFailure ->
            retryable

        is OAuthEndpointResult.TransportFailure ->
            retryable

        is OAuthEndpointResult.ProtocolFailure ->
            false

        is OAuthEndpointResult.Success ->
            false
    }
