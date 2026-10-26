package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.auth.authDebugLog
import app.subshell.prototype.data.network.reddit.mergeContinuedThread
import app.subshell.prototype.model.isContinueThreadNode
import app.subshell.prototype.model.isMoreRepliesNode
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditFocusedCommentContinuityLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentSnapshotLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.COMMENTS02_MORE_BATCH_SIZE
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.live03CommentCacheKey
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditCommentNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditAccountCommentHistory
import app.subshell.prototype.data.network.reddit.readRedditAccountRecentComments
import app.subshell.prototype.data.network.reddit.readRedditCommentResolvedBody
import app.subshell.prototype.data.network.reddit.findComment
import app.subshell.prototype.data.network.reddit.mergeMoreChildrenPage
import app.subshell.prototype.data.network.reddit.normalizeRedditCommentParentFullname
import app.subshell.prototype.model.Comment
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import app.subshell.prototype.data.network.reddit.focusedCommentContextForTarget
import app.subshell.prototype.data.network.reddit.mergeFocusedCommentContext

internal fun redditHydrationMergeBase(
    currentComments: List<Comment>?,
    cachedComments: () -> List<Comment>,
): List<Comment> = currentComments ?: cachedComments()

internal class RedditCommentLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val cacheStore: RoomSubshellCacheStore,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditCommentLoader,
    AuthenticatedRedditCommentSnapshotLoader,
    AuthenticatedRedditFocusedCommentContinuityLoader {
    private val loadMutex = Mutex()

    override suspend fun cached(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): AuthenticatedRedditCommentResult = withContext(ioDispatcher) {
        if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
            return@withContext failed(AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION)
        }
        try {
            AuthenticatedRedditCommentResult.Loaded(
                comments = cacheStore.allComments(
                    postId = live03CommentCacheKey(accountStableId, postId, sort),
                ),
                fromCache = true,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed(AuthenticatedRedditCommentFailureKind.STORAGE, retryable = true)
        }
    }

    override fun observeStoredThread(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): Flow<List<Comment>> =
        cacheStore.observeComments(live03CommentCacheKey(accountStableId, postId, sort))
            .flowOn(ioDispatcher)

    override suspend fun updateStoredThread(
        accountStableId: String,
        postId: String,
        transform: (List<Comment>) -> List<Comment>?,
    ) = withContext(ioDispatcher) {
        loadMutex.withLock {
            cacheStore.updateStoredComments(
                postIds = RedditCommentSort.entries.map { sort -> live03CommentCacheKey(accountStableId, postId, sort) },
                transform = transform,
            )
        }
    }

    override suspend fun synchronize(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): AuthenticatedRedditCommentResult = withContext(ioDispatcher) {
        loadMutex.withLock {
            if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
                )
            }
            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditCommentResult.Failed(
                        kind = AuthenticatedRedditCommentFailureKind.SESSION,
                        retryable = tokenResult.result.retryable,
                        details = tokenResult.result.toFailureDetails(),
                    )
                }
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock failed(AuthenticatedRedditCommentFailureKind.SESSION)
            }
            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
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
                val result = RedditCommentNetworkDataSource(httpClient).comments(postId, sort)
            ) {
                is NetworkResult.Success -> try {
                    cacheStore.cacheComments(
                        postId = live03CommentCacheKey(accountStableId, postId, sort),
                        values = result.value.comments,
                    )
                    AuthenticatedRedditCommentResult.Loaded(
                        comments = result.value.comments,
                        fromCache = false,
                        suggestedSort = result.value.suggestedSort,
                        authorFullnames = result.value.authorFullnames,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed(AuthenticatedRedditCommentFailureKind.STORAGE, retryable = true)
                }
                is NetworkResult.HttpFailure -> AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure ->
                    failed(
                        kind = AuthenticatedRedditCommentFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
        }
    }

    override suspend fun synchronizeFocused(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
        context: Int,
    ): AuthenticatedRedditCommentResult = withContext(ioDispatcher) {
            val fullThreadComments = try {
                cacheStore.allComments(
                    postId = live03CommentCacheKey(accountStableId, postId, sort),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }

        loadMutex.withLock {
            if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
                )
            }
            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditCommentResult.Failed(
                        kind = AuthenticatedRedditCommentFailureKind.SESSION,
                        retryable = tokenResult.result.retryable,
                    )
                }
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock failed(AuthenticatedRedditCommentFailureKind.SESSION)
            }
            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
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
                val result = RedditCommentNetworkDataSource(httpClient).comments(
                    postId = postId,
                    sort = sort,
                    focusedCommentId = focusedCommentId,
                    context = context,
                )
            ) {
                is NetworkResult.Success -> try {
                    AuthenticatedRedditCommentResult.Loaded(
                        // CORRECT03_FOCUSED_LOAD_PREFERS_FRESH_FIELDS_V1 (OCB-492): the
                        // focused comment and its parents show what Reddit just
                        // returned, not their cached copy.
                        comments = mergeFocusedCommentContext(
                            current = fullThreadComments,
                            focused = result.value.comments,
                            preferFocusedFields = true,
                        ),
                        fromCache = false,
                        suggestedSort = result.value.suggestedSort,
                        authorFullnames = result.value.authorFullnames,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed(AuthenticatedRedditCommentFailureKind.STORAGE, retryable = true)
                }
                is NetworkResult.HttpFailure -> AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure ->
                    failed(
                        kind = AuthenticatedRedditCommentFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
        }
    }

    override suspend fun synchronizePreservingFocusedTarget(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
        currentComments: List<Comment>,
    ): AuthenticatedRedditCommentResult = withContext(ioDispatcher) {
        val targetId = focusedCommentId.trim()
        if (targetId.isEmpty()) {
            return@withContext failed(AuthenticatedRedditCommentFailureKind.PROTOCOL)
        }

        val currentFocusedContext = focusedCommentContextForTarget(
            comments = currentComments,
            focusedCommentId = targetId,
        )
        val cachedFocusedContext = if (currentFocusedContext.isEmpty()) {
            try {
                focusedCommentContextForTarget(
                    comments = cacheStore.allComments(
                        postId = live03CommentCacheKey(accountStableId, postId, sort),
                    ),
                    focusedCommentId = targetId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val preservedFocusedContext = if (currentFocusedContext.isNotEmpty()) {
            currentFocusedContext
        } else {
            cachedFocusedContext
        }

        val canonical = synchronizeCanonicalWithoutCache(
            accountStableId = accountStableId,
            postId = postId,
            sort = sort,
        )
        if (canonical !is AuthenticatedRedditCommentResult.Loaded) {
            return@withContext canonical
        }

        val canonicalTargetPresent = focusedCommentContextForTarget(
            comments = canonical.comments,
            focusedCommentId = targetId,
        ).isNotEmpty()
        var continuitySource = if (canonicalTargetPresent) {
            "canonical"
        } else if (currentFocusedContext.isNotEmpty()) {
            "ui_snapshot"
        } else if (cachedFocusedContext.isNotEmpty()) {
            "cache_snapshot"
        } else {
            "focused_network"
        }
        // CORRECT03_FULL_REFRESH_KEEPS_AUTHOR_FULLNAMES_V1 (OCB-493): the avatars
        // of authors seen only in the full discussion (and the repair load).
        var repairAuthorFullnames = emptyMap<String, String>()
        var mergedComments = if (canonicalTargetPresent) {
            canonical.comments
        } else {
            mergeFocusedCommentContext(
                current = canonical.comments,
                focused = preservedFocusedContext,
            )
        }

        if (
            focusedCommentContextForTarget(
                comments = mergedComments,
                focusedCommentId = targetId,
            ).isEmpty()
        ) {
            val focused = synchronizeFocused(
                accountStableId = accountStableId,
                postId = postId,
                sort = sort,
                focusedCommentId = targetId,
                context = 8,
            )
            when (focused) {
                is AuthenticatedRedditCommentResult.Loaded -> {
                    val networkFocusedContext = focusedCommentContextForTarget(
                        comments = focused.comments,
                        focusedCommentId = targetId,
                    )
                    mergedComments = mergeFocusedCommentContext(
                        current = canonical.comments,
                        focused = networkFocusedContext,
                    )
                    repairAuthorFullnames = focused.authorFullnames
                    continuitySource = "focused_network"
                }
                is AuthenticatedRedditCommentResult.Failed -> {
                    android.util.Log.e(
                        "SubshellCommentNav",
                        "phase=canonical_target_repair_failed post=$postId target=$targetId " +
                            "source=focused_network kind=${focused.kind.name.lowercase()}",
                    )
                    return@withContext focused
                }
            }
        }

        val mergedTargetPresent = focusedCommentContextForTarget(
            comments = mergedComments,
            focusedCommentId = targetId,
        ).isNotEmpty()
        if (!mergedTargetPresent) {
            android.util.Log.e(
                "SubshellCommentNav",
                "phase=canonical_target_repair_failed post=$postId target=$targetId " +
                    "source=$continuitySource kind=target_missing_after_merge",
            )
            return@withContext failed(AuthenticatedRedditCommentFailureKind.PROTOCOL)
        }

        try {
            cacheStore.cacheComments(
                postId = live03CommentCacheKey(accountStableId, postId, sort),
                values = mergedComments,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext failed(
                AuthenticatedRedditCommentFailureKind.STORAGE,
                retryable = true,
            )
        }

        android.util.Log.d(
            "SubshellCommentNav",
            "phase=canonical_target_continuity_owned post=$postId target=$targetId " +
                "source=$continuitySource uiSnapshot=${currentFocusedContext.isNotEmpty()} " +
                "cacheSnapshot=${cachedFocusedContext.isNotEmpty()} " +
                "canonical=$canonicalTargetPresent merged=true roots=${mergedComments.size}",
        )
        AuthenticatedRedditCommentResult.Loaded(
            comments = mergedComments,
            fromCache = false,
            suggestedSort = canonical.suggestedSort,
            authorFullnames = repairAuthorFullnames + canonical.authorFullnames,
        )
    }

    private suspend fun synchronizeCanonicalWithoutCache(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): AuthenticatedRedditCommentResult = loadMutex.withLock {
        if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
            return@withLock failed(
                AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
            )
        }
        val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
            is AuthSessionResult.Authenticated -> tokenResult.session
            is AuthSessionResult.EndpointFailure -> {
                return@withLock AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.SESSION,
                    retryable = tokenResult.result.retryable,
                )
            }
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            AuthSessionResult.ClientConfigurationMismatch,
            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> return@withLock failed(AuthenticatedRedditCommentFailureKind.SESSION)
        }
        if (
            session.clientMode != configuration.clientMode ||
            session.clientIdFingerprint != configuration.clientIdFingerprint ||
            !session.tokens.grants(RedditOAuth.coreReadScopes)
        ) {
            return@withLock failed(
                AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
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
            val result = RedditCommentNetworkDataSource(httpClient).comments(postId, sort)
        ) {
            is NetworkResult.Success -> AuthenticatedRedditCommentResult.Loaded(
                comments = result.value.comments,
                fromCache = false,
                suggestedSort = result.value.suggestedSort,
                authorFullnames = result.value.authorFullnames,
            )
            is NetworkResult.HttpFailure -> AuthenticatedRedditCommentResult.Failed(
                kind = AuthenticatedRedditCommentFailureKind.HTTP,
                retryable = result.retryable,
                details = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> AuthenticatedRedditCommentResult.Failed(
                kind = AuthenticatedRedditCommentFailureKind.TRANSPORT,
                retryable = result.retryable,
                details = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure ->
                failed(
                    kind = AuthenticatedRedditCommentFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
        }
    }

    override suspend fun loadMore(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
    ): AuthenticatedRedditCommentResult = loadMoreInternal(
        accountStableId = accountStableId,
        postId = postId,
        sort = sort,
        moreNodeId = moreNodeId,
        currentComments = null,
    )

    override suspend fun loadMoreFromSnapshot(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
        currentComments: List<Comment>,
    ): AuthenticatedRedditCommentResult = loadMoreInternal(
        accountStableId = accountStableId,
        postId = postId,
        sort = sort,
        moreNodeId = moreNodeId,
        currentComments = currentComments,
    )

    private suspend fun loadMoreInternal(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
        currentComments: List<Comment>?,
    ): AuthenticatedRedditCommentResult = withContext(ioDispatcher) {
        loadMutex.withLock {
            if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
                )
            }
            val cacheKey = live03CommentCacheKey(accountStableId, postId, sort)
            val mergeBaseComments = try {
                redditHydrationMergeBase(currentComments) {
                    cacheStore.allComments(
                        postId = cacheKey,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                )
            }
            // ISSUE210_A_LOADED_NODE_IS_NOT_A_FAILURE_V1 (#210): the screen
            // publishes loaded replies only once the reader stops scrolling,
            // so it can still show (and ask again for) a more node whose last
            // replies already arrived. That node is done, not broken: answer
            // with the thread as it is, so the screen catches up and the row
            // goes away. It was PROTOCOL, a "Couldn't load replies" whose
            // Retry asked for the same finished node forever.
            val moreNode = mergeBaseComments.findComment(moreNodeId)
                ?.takeIf { comment -> comment.isMoreRepliesNode }
                ?: run {
                    moreProtocolDebugLog(postId, moreNodeId, "node_already_loaded")
                    return@withLock AuthenticatedRedditCommentResult.Loaded(
                        comments = mergeBaseComments,
                        fromCache = false,
                    )
                }
            val childIds = moreNode.moreChildIds.take(COMMENTS02_MORE_BATCH_SIZE)
            moreProtocolDebugLog(
                postId,
                moreNodeId,
                "request childIds=${childIds.size} of ${moreNode.moreChildIds.size} count=${moreNode.moreCount} " +
                    "continue=${moreNode.isContinueThreadNode}",
            )

            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditCommentResult.Failed(
                        kind = AuthenticatedRedditCommentFailureKind.SESSION,
                        retryable = tokenResult.result.retryable,
                        details = tokenResult.result.toFailureDetails(),
                    )
                }
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock failed(AuthenticatedRedditCommentFailureKind.SESSION)
            }
            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
                )
            }
            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
            )
            if (moreNode.isContinueThreadNode) {
                return@withLock continueThread(
                    dataSource = RedditCommentNetworkDataSource(httpClient),
                    cacheKey = cacheKey,
                    postId = postId,
                    sort = sort,
                    continueNode = moreNode,
                    mergeBaseComments = mergeBaseComments,
                )
            }
            when (
                val result = RedditCommentNetworkDataSource(httpClient).moreChildren(
                    postId = postId,
                    childIds = childIds,
                    sort = sort,
                )
            ) {
                is NetworkResult.Success -> try {
                    val merged = mergeMoreChildrenPage(
                        current = mergeBaseComments,
                        moreNodeId = moreNodeId,
                        page = result.value,
                        requestedChildCount = childIds.size,
                    ) ?: run {
                        moreProtocolDebugLog(
                            postId,
                            moreNodeId,
                            "rejected_by_reddit comments=${result.value.comments.size}",
                        )
                        return@withLock failed(
                            AuthenticatedRedditCommentFailureKind.PROTOCOL,
                            retryable = true,
                        )
                    }
                    cacheStore.cacheComments(postId = cacheKey, values = merged)
                    AuthenticatedRedditCommentResult.Loaded(
                        comments = merged,
                        fromCache = false,
                        authorFullnames = result.value.authorFullnames,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (invalid: IllegalArgumentException) {
                    moreProtocolDebugLog(
                        postId,
                        moreNodeId,
                        "merge_invalid comments=${result.value.comments.size} message=${invalid.message}",
                    )
                    failed(AuthenticatedRedditCommentFailureKind.PROTOCOL)
                } catch (_: Exception) {
                    failed(AuthenticatedRedditCommentFailureKind.STORAGE, retryable = true)
                }
                is NetworkResult.HttpFailure -> AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> {
                    val details = result.failureDetailsOrNull()
                    moreProtocolDebugLog(
                        postId,
                        moreNodeId,
                        "network kind=${details?.protocolKind} http=${details?.httpStatusCode} " +
                            "path=${details?.jsonPath?.value}",
                    )
                    failed(
                        kind = AuthenticatedRedditCommentFailureKind.PROTOCOL,
                        details = details,
                    )
                }
            }
        }
    }

    // ISSUE210_MORE_CHILDREN_FAILURE_IS_NAMED_V1 (#210): debug builds name
    // which branch turned a replies load into PROTOCOL; release builds log
    // nothing (authDebugLog).
    private fun moreProtocolDebugLog(postId: String, moreNodeId: String, detail: String) {
        authDebugLog("subshell-comments", "more-protocol post=$postId node=$moreNodeId $detail")
    }

    // ISSUE3_CONTINUE_THREAD_NODE_V1: "Continue this thread" loads the thread
    // focused on the node's parent and grafts its replies in place.
    private suspend fun continueThread(
        dataSource: RedditCommentNetworkDataSource,
        cacheKey: String,
        postId: String,
        sort: RedditCommentSort,
        continueNode: Comment,
        mergeBaseComments: List<Comment>,
    ): AuthenticatedRedditCommentResult = when (
        val result = dataSource.comments(
            postId = postId,
            sort = sort,
            focusedCommentId = continueNode.parentId,
            context = 0,
        )
    ) {
        is NetworkResult.Success -> try {
            val merged = mergeContinuedThread(
                current = mergeBaseComments,
                continueNodeId = continueNode.id,
                focused = result.value.comments,
            ) ?: return failed(AuthenticatedRedditCommentFailureKind.PROTOCOL, retryable = true)
            cacheStore.cacheComments(postId = cacheKey, values = merged)
            AuthenticatedRedditCommentResult.Loaded(
                comments = merged,
                fromCache = false,
                authorFullnames = result.value.authorFullnames,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            failed(AuthenticatedRedditCommentFailureKind.PROTOCOL)
        } catch (_: Exception) {
            failed(AuthenticatedRedditCommentFailureKind.STORAGE, retryable = true)
        }
        is NetworkResult.HttpFailure -> AuthenticatedRedditCommentResult.Failed(
            kind = AuthenticatedRedditCommentFailureKind.HTTP,
            retryable = result.retryable,
            details = result.failureDetailsOrNull(),
        )
        is NetworkResult.TransportFailure -> AuthenticatedRedditCommentResult.Failed(
            kind = AuthenticatedRedditCommentFailureKind.TRANSPORT,
            retryable = result.retryable,
            details = result.failureDetailsOrNull(),
        )
        is NetworkResult.ProtocolFailure ->
            failed(
                kind = AuthenticatedRedditCommentFailureKind.PROTOCOL,
                details = result.failureDetailsOrNull(),
            )
    }

    override suspend fun cacheSubmittedComment(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        parentFullname: String,
        comment: app.subshell.prototype.model.Comment,
    ): AuthenticatedRedditCommentResult = cacheSubmittedCommentInternal(
        accountStableId = accountStableId,
        postId = postId,
        sort = sort,
        parentFullname = parentFullname,
        comment = comment,
        currentComments = null,
    )

    // COMMENTCACHE01_SUBMIT_MERGES_DISPLAYED_THREAD_V1 (#138): the cache can
    // lag the screen (edits and deletes change only the screen, a focused
    // load is not cached, the cache read stops at 100 roots), so a reply merged
    // into the cache put old text back, undeleted comments or lost the reply.
    // The reply goes into the thread the reader sees, and that is cached.
    override suspend fun cacheSubmittedCommentOnto(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        parentFullname: String,
        comment: app.subshell.prototype.model.Comment,
        currentComments: List<Comment>?,
    ): AuthenticatedRedditCommentResult = cacheSubmittedCommentInternal(
        accountStableId = accountStableId,
        postId = postId,
        sort = sort,
        parentFullname = parentFullname,
        comment = comment,
        currentComments = currentComments,
    )

    private suspend fun cacheSubmittedCommentInternal(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        parentFullname: String,
        comment: app.subshell.prototype.model.Comment,
        currentComments: List<Comment>?,
    ): AuthenticatedRedditCommentResult = withContext(ioDispatcher) {
        loadMutex.withLock {
            if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION,
                )
            }
            val cacheKey = live03CommentCacheKey(accountStableId, postId, sort)
            val cachedComments = try {
                redditHydrationMergeBase(currentComments) {
                    cacheStore.allComments(
                        postId = cacheKey,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock failed(
                    AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                )
            }
            val merged = mergeSubmittedCommentIntoThread(
                current = cachedComments,
                parentFullname = parentFullname,
                submitted = comment,
            ) ?: return@withLock failed(AuthenticatedRedditCommentFailureKind.PROTOCOL)
            try {
                cacheStore.cacheComments(postId = cacheKey, values = merged)
                AuthenticatedRedditCommentResult.Loaded(
                    comments = merged,
                    fromCache = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed(AuthenticatedRedditCommentFailureKind.STORAGE, retryable = true)
            }
        }
    }

    override suspend fun readCommentBody(
        accountStableId: String,
        commentFullname: String,
    ): String? = withContext(ioDispatcher) {
        if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
            return@withContext null
        }
        val session = (
            accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)
                as? AuthSessionResult.Authenticated
            )?.session ?: return@withContext null
        if (
            session.clientMode != configuration.clientMode ||
            session.clientIdFingerprint != configuration.clientIdFingerprint ||
            !session.tokens.grants(RedditOAuth.coreReadScopes)
        ) {
            return@withContext null
        }
        val httpClient = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        )
        readRedditCommentResolvedBody(httpClient, commentFullname)
    }

    override suspend fun readAccountRecentComments(
        accountStableId: String,
        username: String,
    ): RedditAccountCommentHistory? = withContext(ioDispatcher) {
        if (!hasExpectedConfiguration() || !isActiveAuthenticatedAccount(accountStableId)) {
            return@withContext null
        }
        val session = (
            accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)
                as? AuthSessionResult.Authenticated
            )?.session ?: return@withContext null
        if (
            session.clientMode != configuration.clientMode ||
            session.clientIdFingerprint != configuration.clientIdFingerprint ||
            !session.tokens.grants(RedditOAuth.coreReadScopes)
        ) {
            return@withContext null
        }
        val httpClient = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        )
        readRedditAccountRecentComments(httpClient, username)
    }

    private fun hasExpectedConfiguration(): Boolean =
        configuration.clientMode == OAuthClientMode.REDREADER_COMPATIBILITY &&
            configuration.scopes == RedditOAuth.coreReadScopes

    private fun isActiveAuthenticatedAccount(accountStableId: String): Boolean =
        accountCoordinator.snapshot().let { directory ->
            directory.accounts.any { account ->
                account.stableId == accountStableId &&
                    account.stableId == directory.activeAccountId &&
                    account.source == AccountSessionSource.AUTHENTICATED
            }
        }

    private fun failed(
        kind: AuthenticatedRedditCommentFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthenticatedRedditCommentResult.Failed = AuthenticatedRedditCommentResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details ?: when (kind) {
            AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION -> FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.CONFIGURATION,
            )
            AuthenticatedRedditCommentFailureKind.STORAGE -> FailureDetails(
                retryable = retryable,
                localKind = LocalFailureKind.STORAGE,
            )
            else -> null
        },
    )
}

internal fun mergeSubmittedCommentIntoThread(
    current: List<app.subshell.prototype.model.Comment>,
    parentFullname: String,
    submitted: app.subshell.prototype.model.Comment,
): List<app.subshell.prototype.model.Comment>? {
    val normalizedParent = runCatching {
        normalizeRedditCommentParentFullname(parentFullname)
    }.getOrNull() ?: return null
    val normalizedSubmittedId = normalizeCommentFullname(submitted.id) ?: return null
    if (normalizedParent == normalizedSubmittedId) return null

    val withoutDuplicate = current.removeCommentByFullname(normalizedSubmittedId)
    return if (normalizedParent.startsWith(REDDIT_POST_PARENT_PREFIX)) {
        listOf(
            submitted.copy(
                id = normalizedSubmittedId,
                depth = 0,
                parentId = normalizedParent,
            ),
        ) + withoutDuplicate
    } else {
        val inserted = withoutDuplicate.insertSubmittedReply(
            parentFullname = normalizedParent,
            submitted = submitted.copy(
                id = normalizedSubmittedId,
                parentId = normalizedParent,
            ),
        )
        inserted.takeIf { result -> result.second }?.first
    }
}

private fun List<app.subshell.prototype.model.Comment>.removeCommentByFullname(
    fullname: String,
): List<app.subshell.prototype.model.Comment> = mapNotNull { comment ->
    if (normalizeCommentFullname(comment.id) == fullname) {
        null
    } else {
        comment.copy(children = comment.children.removeCommentByFullname(fullname))
    }
}

private fun List<app.subshell.prototype.model.Comment>.insertSubmittedReply(
    parentFullname: String,
    submitted: app.subshell.prototype.model.Comment,
): Pair<List<app.subshell.prototype.model.Comment>, Boolean> {
    var inserted = false
    val updated = map { comment ->
        if (!inserted && normalizeCommentFullname(comment.id) == parentFullname) {
            inserted = true
            comment.copy(
                children = listOf(
                    submitted.copy(depth = (comment.depth + 1).coerceAtMost(REDDIT_SUBMITTED_MAX_DEPTH)),
                ) + comment.children.removeCommentByFullname(submitted.id),
            )
        } else {
            val childResult = comment.children.insertSubmittedReply(
                parentFullname = parentFullname,
                submitted = submitted,
            )
            if (childResult.second) inserted = true
            comment.copy(children = childResult.first)
        }
    }
    return updated to inserted
}

private fun normalizeCommentFullname(value: String): String? {
    val normalized = value.trim().lowercase()
    val candidate = when {
        normalized.startsWith(REDDIT_COMMENT_PARENT_PREFIX) -> normalized
        normalized.startsWith(REDDIT_POST_PARENT_PREFIX) -> return null
        else -> "$REDDIT_COMMENT_PARENT_PREFIX$normalized"
    }
    val identifier = candidate.removePrefix(REDDIT_COMMENT_PARENT_PREFIX)
    return candidate.takeIf {
        identifier.length in 1..REDDIT_SUBMITTED_ID_MAX_CHARS &&
            identifier.all(Char::isLetterOrDigit)
    }
}

private const val REDDIT_COMMENT_PARENT_PREFIX = "t1_"
private const val REDDIT_POST_PARENT_PREFIX = "t3_"
private const val REDDIT_SUBMITTED_ID_MAX_CHARS = 16
private const val REDDIT_SUBMITTED_MAX_DEPTH = 10

private val OAuthEndpointResult<Nothing>.retryable: Boolean
    get() = when (this) {
        is OAuthEndpointResult.HttpFailure -> retryable
        is OAuthEndpointResult.TransportFailure -> retryable
        is OAuthEndpointResult.ProtocolFailure -> false
        is OAuthEndpointResult.Success -> false
    }
