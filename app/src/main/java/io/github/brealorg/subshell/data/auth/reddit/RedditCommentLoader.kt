package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.auth.authDebugLog
import io.github.brealorg.subshell.data.network.reddit.mergeContinuedThread
import io.github.brealorg.subshell.model.isContinueThreadNode
import io.github.brealorg.subshell.model.isMoreRepliesNode
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFocusedCommentContinuityLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentSnapshotLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentFromEndLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.COMMENTS02_MORE_BATCH_SIZE
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.toFailureDetails
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.auth.live03CommentCacheKey
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.diagnostics.READ_EVENT_MORE_REPLIES_FAILED
import io.github.brealorg.subshell.data.diagnostics.RedditReadDiagnostics
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.error.REDDIT_BLOCK_PAGE_REASON
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditCommentNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditMoreChildrenPage
import io.github.brealorg.subshell.data.network.reddit.RedditAccountCommentHistory
import io.github.brealorg.subshell.data.network.reddit.readRedditAccountRecentComments
import io.github.brealorg.subshell.data.network.reddit.readRedditCommentResolvedBody
import io.github.brealorg.subshell.data.network.reddit.findComment
import io.github.brealorg.subshell.data.network.reddit.mergeMoreChildrenPage
import io.github.brealorg.subshell.data.network.reddit.mergeExpandedCommentsFromEnd
import io.github.brealorg.subshell.data.network.reddit.mergeExpandedComments
import io.github.brealorg.subshell.data.network.reddit.moreNodeWindowFromListedParents
import io.github.brealorg.subshell.data.network.reddit.normalizeRedditCommentParentFullname
import io.github.brealorg.subshell.model.Comment
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import io.github.brealorg.subshell.data.network.reddit.focusedCommentContextForTarget
import io.github.brealorg.subshell.data.network.reddit.mergeFocusedCommentContext

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
    AuthenticatedRedditCommentFromEndLoader,
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
                        // ISSUE32_REFUSED_READS_CARRY_THEIR_DETAILS_V1
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
                            focusedCommentId = focusedCommentId,
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
                focusedCommentId = targetId,
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
                        focusedCommentId = targetId,
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

    // ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1 (#298)
    override suspend fun loadMoreFromSnapshotFromEnd(
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
        fromEnd = true,
    )

    private suspend fun loadMoreInternal(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
        currentComments: List<Comment>?,
        fromEnd: Boolean = false,
    ): AuthenticatedRedditCommentResult = withContext(ioDispatcher) {
        val report = MoreRepliesFailureReport()
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
            // ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1: from the
            // end, ids already in the thread (the reader's own grafted comment
            // is often the last one listed) are not asked for again.
            // AUDIT298A_CONTINUE_NODES_LOAD_FROM_EITHER_SIDE_V1: a continue node
            // has no listed ids; it always reads its thread (it failed from below).
            val fromEndMode = fromEnd && !moreNode.isContinueThreadNode
            val threadIds = mutableSetOf<String>().also { ids -> mergeBaseComments.forEach { it.collectCommentIds(ids) } }
            fun inThread(id: String) = "t1_" + id.trim().removePrefix("t1_") in threadIds
            // AUDIT298A_NO_COMMENT_IS_ASKED_FOR_TWICE_V1 (audit 2026-11-12): from the
            // start too, ids already in the thread are walked past (they came with
            // an earlier subtree, or the focused path); asked for again they were
            // added a second time. The walked ids count as requested.
            var headWalked = 0
            val headIds = mutableListOf<String>()
            for (id in moreNode.moreChildIds) {
                if (headIds.size == COMMENTS02_MORE_BATCH_SIZE) break
                headWalked += 1
                if (!inThread(id)) headIds += id
            }
            val childIds: List<String> = if (fromEndMode) {
                moreNode.moreChildIds
                    .filterNot(::inThread)
                    .takeLast(COMMENTS02_MORE_BATCH_SIZE)
            } else {
                headIds
            }
            if (!moreNode.isContinueThreadNode && childIds.isEmpty()) {
                // Everything the node names (or the next batch of it) is already in the thread.
                val done = try {
                    if (fromEndMode) {
                        mergeExpandedCommentsFromEnd(
                            current = mergeBaseComments,
                            moreNodeId = moreNodeId,
                            expanded = emptyList(),
                            requestedIds = emptyList(),
                        )
                    } else {
                        mergeExpandedComments(
                            current = mergeBaseComments,
                            moreNodeId = moreNodeId,
                            expanded = emptyList(),
                            requestedChildCount = headWalked,
                        )
                    }.also { merged -> cacheStore.cacheComments(postId = cacheKey, values = merged) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withLock failed(AuthenticatedRedditCommentFailureKind.STORAGE, retryable = true)
                }
                return@withLock AuthenticatedRedditCommentResult.Loaded(comments = done, fromCache = false)
            }
            report.describe(moreNode, mergeBaseComments, requested = childIds.size)
            moreProtocolDebugLog(
                postId,
                moreNodeId,
                "request childIds=${childIds.size} of ${moreNode.moreChildIds.size} count=${moreNode.moreCount} " +
                    "continue=${moreNode.isContinueThreadNode} fromEnd=$fromEndMode",
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
            val dataSource = RedditCommentNetworkDataSource(httpClient)
            // ISSUE281_MORE_REPLIES_WITHOUT_MORECHILDREN_V1 (#281): when Reddit
            // refuses morechildren with a 403, the replies load through threads
            // focused on each child, a few at a time.
            // ISSUE35_FOCUSED_REPLIES_SKIP_WHAT_IS_LOADED_V1 (#35): a more node
            // also lists descendants of its children; one already in the
            // thread (it came with an earlier child's subtree) is skipped,
            // not read again and added a second time. The leading ids walked
            // past count as requested, so the node drops them.
            val loadedIds = mutableSetOf<String>().also { ids -> mergeBaseComments.forEach { it.collectCommentIds(ids) } }
            // From the start this walks the node's list from its first id (the
            // walked count is what the node drops); from the end, the window.
            val byFocusSource = if (fromEndMode) childIds else moreNode.moreChildIds
            suspend fun byFocus(): Pair<NetworkResult<RedditMoreChildrenPage>?, Int> {
                val focusedIds = mutableListOf<String>()
                var walked = 0
                for (childId in byFocusSource) {
                    if (focusedIds.size == MORE_REPLIES_BY_FOCUS_BATCH_SIZE) break
                    walked += 1
                    if ("t1_" + childId.trim().removePrefix("t1_") !in loadedIds) focusedIds += childId
                }
                if (focusedIds.isEmpty()) return null to walked
                return dataSource.moreChildrenByFocus(
                    postId = postId,
                    childIds = focusedIds,
                    sort = sort,
                    childDepth = moreNode.depth,
                ) to walked
            }
            // ISSUE35_MORECHILDREN_REFUSAL_IS_PER_ACCOUNT_AND_CLIENT_V1 (#35):
            // only Reddit's block page is remembered, and only for this account
            // and Client ID; another 403 (a private or quarantined thread)
            // reads this load by focus and asks morechildren again next time.
            val refusalKey = "$accountStableId|${configuration.clientIdFingerprint}"
            val (focusedOrNull, requestedChildCount) = if (refusalKey in moreChildrenRefusedFor) {
                byFocus()
            } else {
                val direct = dataSource.moreChildren(
                    postId = postId,
                    childIds = childIds,
                    sort = sort,
                )
                if (direct is NetworkResult.HttpFailure && direct.statusCode == 403) {
                    if (direct.providerReason == REDDIT_BLOCK_PAGE_REASON) moreChildrenRefusedFor += refusalKey
                    moreProtocolDebugLog(postId, moreNodeId, "morechildren_403 reading by focus")
                    byFocus()
                } else {
                    direct to (if (fromEndMode) childIds.size else headWalked)
                }
            }
            // Every id walked past was already in the thread: the node is done.
            val result = focusedOrNull ?: NetworkResult.Success(
                RedditMoreChildrenPage(comments = emptyList()),
                NetworkResponseMetadata(
                    statusCode = 200,
                    receivedAtEpochMs = System.currentTimeMillis(),
                    rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
                    retryAfterMs = null,
                ),
            )
            when (result) {
                is NetworkResult.Success -> try {
                    val merged = (
                        if (fromEndMode) {
                            if (result.value.rejectedByReddit) {
                                null
                            } else {
                                var window = byFocusSource.take(requestedChildCount)
                                var expanded = result.value.comments
                                var fromEndMerged = mergeExpandedCommentsFromEnd(
                                    current = mergeBaseComments,
                                    moreNodeId = moreNodeId,
                                    expanded = expanded,
                                    requestedIds = window,
                                )
                                // AUDIT298A_FROM_THE_END_ALWAYS_MOVES_ON_V1: when every comment
                                // that came back waits for a parent still listed (the window
                                // fell inside one subtree), the window moves back to that
                                // parent; the node never answers "nothing done" for ever.
                                var rounds = 0
                                while (
                                    rounds < FROM_END_PARENT_ROUNDS &&
                                    // Nothing taken off the list (kept comments may only have moved).
                                    fromEndMerged.findComment(moreNodeId)?.moreChildIds?.toSet() == moreNode.moreChildIds.toSet() &&
                                    refusalKey !in moreChildrenRefusedFor
                                ) {
                                    val parentWindow = moreNodeWindowFromListedParents(
                                        node = moreNode,
                                        expanded = expanded,
                                        isInThread = ::inThread,
                                        batchSize = COMMENTS02_MORE_BATCH_SIZE,
                                    ) ?: break
                                    if (parentWindow == window) break
                                    rounds += 1
                                    moreProtocolDebugLog(postId, moreNodeId, "from_end_parent_round=$rounds ids=${parentWindow.size}")
                                    val again = dataSource.moreChildren(postId = postId, childIds = parentWindow, sort = sort)
                                    if (again !is NetworkResult.Success || again.value.rejectedByReddit) break
                                    window = parentWindow
                                    expanded = again.value.comments
                                    fromEndMerged = mergeExpandedCommentsFromEnd(
                                        current = mergeBaseComments,
                                        moreNodeId = moreNodeId,
                                        expanded = expanded,
                                        requestedIds = window,
                                    )
                                }
                                if (fromEndMerged.findComment(moreNodeId)?.moreChildIds?.toSet() == moreNode.moreChildIds.toSet()) {
                                    // Still nothing taken: take what came, as a load from the start would.
                                    moreProtocolDebugLog(postId, moreNodeId, "from_end_no_progress placing_at_node")
                                    fromEndMerged = mergeExpandedCommentsFromEnd(
                                        current = mergeBaseComments,
                                        moreNodeId = moreNodeId,
                                        expanded = expanded,
                                        requestedIds = window,
                                        waitForListedParents = false,
                                    )
                                }
                                fromEndMerged
                            }
                        } else {
                            mergeMoreChildrenPage(
                                current = mergeBaseComments,
                                moreNodeId = moreNodeId,
                                page = result.value,
                                requestedChildCount = requestedChildCount,
                            )
                        }
                        ) ?: run {
                        moreProtocolDebugLog(
                            postId,
                            moreNodeId,
                            "rejected_by_reddit comments=${result.value.comments.size}",
                        )
                        report.branch = "rejected_by_reddit"
                        report.returned = result.value.comments.size
                        report.code = result.value.rejectionCode
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
                    report.branch = "merge_invalid"
                    report.returned = result.value.comments.size
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
        }.also(report::recordIfFailed)
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
        comment: io.github.brealorg.subshell.model.Comment,
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
        comment: io.github.brealorg.subshell.model.Comment,
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
        comment: io.github.brealorg.subshell.model.Comment,
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
        postId: String,
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
        readRedditCommentResolvedBody(httpClient, postId, commentFullname)
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
    current: List<io.github.brealorg.subshell.model.Comment>,
    parentFullname: String,
    submitted: io.github.brealorg.subshell.model.Comment,
): List<io.github.brealorg.subshell.model.Comment>? {
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

private fun List<io.github.brealorg.subshell.model.Comment>.removeCommentByFullname(
    fullname: String,
): List<io.github.brealorg.subshell.model.Comment> = mapNotNull { comment ->
    if (normalizeCommentFullname(comment.id) == fullname) {
        null
    } else {
        comment.copy(children = comment.children.removeCommentByFullname(fullname))
    }
}

private fun List<io.github.brealorg.subshell.model.Comment>.insertSubmittedReply(
    parentFullname: String,
    submitted: io.github.brealorg.subshell.model.Comment,
): Pair<List<io.github.brealorg.subshell.model.Comment>, Boolean> {
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

// ISSUE281_THREAD_READS_IN_THE_REPORT_V1 (#281: a mod comment's replies said
// "Couldn't load replies" and the report had nothing): a failed replies load
// is recorded with the branch that failed it and the shape of the node.
private class MoreRepliesFailureReport {
    var branch: String? = null
    var returned: Int? = null
    var code: String? = null
    private var node: Comment? = null
    private var requested: Int = 0
    private var parentModerator: Boolean? = null
    private var parentDepth: Int? = null

    fun describe(moreNode: Comment, mergeBase: List<Comment>, requested: Int) {
        node = moreNode
        this.requested = requested
        val parent = moreNode.parentId?.let { id ->
            mergeBase.findComment(id) ?: mergeBase.findComment(id.removePrefix("t1_"))
        }
        parentModerator = parent?.isModerator
        parentDepth = parent?.depth
    }

    fun recordIfFailed(result: AuthenticatedRedditCommentResult) {
        val failed = result as? AuthenticatedRedditCommentResult.Failed ?: return
        val more = node
        RedditReadDiagnostics.report(
            READ_EVENT_MORE_REPLIES_FAILED,
            "branch" to (branch ?: failed.kind.name.lowercase()),
            "http" to failed.details?.httpStatusCode,
            "code" to code,
            "requested" to requested,
            "of" to more?.moreChildIds?.size,
            "count" to more?.moreCount,
            "returned" to returned,
            "continue" to more?.isContinueThreadNode,
            "parent_mod" to parentModerator,
            "parent_depth" to parentDepth,
            "retryable" to failed.retryable,
        )
    }
}

// ISSUE281_MORE_REPLIES_WITHOUT_MORECHILDREN_V1: each child is one request.
internal const val MORE_REPLIES_BY_FOCUS_BATCH_SIZE = 5

/**
 * Account|Client ID pairs Reddit answered /api/morechildren with its block page
 * in this process (#281, #35); not persisted.
 */
private const val FROM_END_PARENT_ROUNDS = 5

internal val moreChildrenRefusedFor: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

private fun Comment.collectCommentIds(into: MutableSet<String>) {
    into += id
    children.forEach { it.collectCommentIds(into) }
}
