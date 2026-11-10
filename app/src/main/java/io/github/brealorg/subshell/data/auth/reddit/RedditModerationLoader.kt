package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.RedditModeratorQueueList
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditReportTargetResult
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModQueueResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationTargetResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationLoader
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditModerationNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditInboxTargetNetworkDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

// MOD01A1_OWNER_BOUND_MODERATION_READ_LOADER_V1
internal class RedditModerationLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModerationLoader {
    private val requestMutex = Mutex()

    override suspend fun loadModeratorCommunities(
        accountStableId: String,
    ): AuthenticatedRedditModeratedCommunitiesResult = withContext(ioDispatcher) {
        requestMutex.withLock {
            val client = authenticatedClient(accountStableId)
                ?: return@withLock AuthenticatedRedditModeratedCommunitiesResult.Failed(
                    kind = AuthenticatedRedditModerationFailureKind.SESSION,
                    retryable = lastSessionFailureRetryable,
                )
            val source = RedditModerationNetworkDataSource(client)
            val communities = linkedSetOf<String>()
            var after: String? = null
            repeat(MAX_MODERATOR_DIRECTORY_PAGES) {
                when (val result = source.moderatedCommunities(after)) {
                    is NetworkResult.Success -> {
                        communities += result.value.items
                        val nextCursor = result.value.nextCursor
                        if (nextCursor == null) {
                            return@withLock AuthenticatedRedditModeratedCommunitiesResult.Loaded(
                                communityNames = communities,
                            )
                        }
                        after = nextCursor
                    }
                    is NetworkResult.HttpFailure ->
                        return@withLock AuthenticatedRedditModeratedCommunitiesResult.Failed(
                            kind = AuthenticatedRedditModerationFailureKind.HTTP,
                            retryable = result.retryable,
                            details = result.failureDetailsOrNull(),
                            httpStatusCode = result.statusCode,
                        )
                    is NetworkResult.TransportFailure ->
                        return@withLock AuthenticatedRedditModeratedCommunitiesResult.Failed(
                            kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                            retryable = result.retryable,
                            details = result.failureDetailsOrNull(),
                        )
                    is NetworkResult.ProtocolFailure ->
                        return@withLock AuthenticatedRedditModeratedCommunitiesResult.Failed(
                            kind = AuthenticatedRedditModerationFailureKind.PROTOCOL,
                            details = result.failureDetailsOrNull(),
                        )
                }
            }
            AuthenticatedRedditModeratedCommunitiesResult.Loaded(
                communityNames = communities,
            )
        }
    }

    override suspend fun loadModQueue(
        accountStableId: String,
        rawCommunityName: String,
        after: String?,
    ): AuthenticatedRedditModQueueResult =
        loadModList(accountStableId, rawCommunityName, RedditModeratorQueueList.QUEUE, after)

    override suspend fun loadModList(
        accountStableId: String,
        rawCommunityName: String,
        list: RedditModeratorQueueList,
        after: String?,
    ): AuthenticatedRedditModQueueResult = withContext(ioDispatcher) {
        val communityName = runCatching {
            io.github.brealorg.subshell.data.normalizeRedditCommunityName(rawCommunityName)
        }.getOrElse {
            return@withContext AuthenticatedRedditModQueueResult.Failed(
                communityName = rawCommunityName.trim().lowercase(),
                kind = AuthenticatedRedditModerationFailureKind.PROTOCOL,
            )
        }
        requestMutex.withLock {
            val client = authenticatedClient(accountStableId)
                ?: return@withLock AuthenticatedRedditModQueueResult.Failed(
                    communityName = communityName,
                    kind = AuthenticatedRedditModerationFailureKind.SESSION,
                    retryable = lastSessionFailureRetryable,
                )
            when (
                val result = RedditModerationNetworkDataSource(client).modList(
                    communityName = communityName,
                    list = list,
                    after = after,
                )
            ) {
                is NetworkResult.Success -> AuthenticatedRedditModQueueResult.Loaded(
                    communityName = communityName,
                    items = result.value.items,
                    nextCursor = result.value.nextCursor,
                )
                is NetworkResult.HttpFailure -> AuthenticatedRedditModQueueResult.Failed(
                    communityName = communityName,
                    kind = AuthenticatedRedditModerationFailureKind.HTTP,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                    httpStatusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> AuthenticatedRedditModQueueResult.Failed(
                    communityName = communityName,
                    kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> AuthenticatedRedditModQueueResult.Failed(
                    communityName = communityName,
                    kind = AuthenticatedRedditModerationFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    // MOD01A22_MODERATION_OWNED_EXACT_TARGET_RESOLUTION_V1
    override suspend fun resolveTargetPost(
        accountStableId: String,
        postId: String,
    ): AuthenticatedRedditModerationTargetResult = withContext(ioDispatcher) {
        val normalizedPostId = postId
            .trim()
            .removePrefix("t3_")
            .lowercase()
            .takeIf { candidate -> candidate.matches(POST_ID_REGEX) }
            ?: return@withContext AuthenticatedRedditModerationTargetResult.NotFound
        requestMutex.withLock {
            val client = authenticatedClient(accountStableId)
                ?: return@withLock AuthenticatedRedditModerationTargetResult.Failed(
                    kind = AuthenticatedRedditModerationFailureKind.SESSION,
                    retryable = lastSessionFailureRetryable,
                )
            when (
                val result = RedditInboxTargetNetworkDataSource(client)
                    .post(normalizedPostId)
            ) {
                is NetworkResult.Success -> {
                    val post = result.value
                        ?: return@withLock AuthenticatedRedditModerationTargetResult.NotFound
                    AuthenticatedRedditModerationTargetResult.Loaded(post)
                }
                is NetworkResult.HttpFailure ->
                    AuthenticatedRedditModerationTargetResult.Failed(
                        kind = AuthenticatedRedditModerationFailureKind.HTTP,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                        httpStatusCode = result.statusCode,
                    )
                is NetworkResult.TransportFailure ->
                    AuthenticatedRedditModerationTargetResult.Failed(
                        kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    AuthenticatedRedditModerationTargetResult.Failed(
                        kind = AuthenticatedRedditModerationFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
        }
    }

    // MODERATION02A2_OWNER_BOUND_REPORT_TARGET_READ_V1
    override suspend fun loadReportTarget(
        accountStableId: String,
        target: ModeratorReportTarget,
    ): AuthenticatedRedditReportTargetResult = withContext(ioDispatcher) {
        requestMutex.withLock {
            // ISSUE281_READS_AVOID_API_INFO_V1: a bare comment id has no post
            // to read it from.
            if (target.postFullname == null) return@withLock AuthenticatedRedditReportTargetResult.NotFound
            val client = authenticatedClient(accountStableId)
                ?: return@withLock AuthenticatedRedditReportTargetResult.Failed(
                    AuthenticatedRedditModerationFailureKind.SESSION,
                )
            reportTargetResultOf(RedditModerationNetworkDataSource(client).reportTarget(target))
        }
    }

    // Set by authenticatedClient when it returns null; read under the same
    // mutex by the caller that reports the failure.
    private var lastSessionFailureRetryable = false

    private suspend fun authenticatedClient(
        accountStableId: String,
    ): SubshellHttpClient? {
        lastSessionFailureRetryable = false
        val directory = accountCoordinator.snapshot()
        val active = directory.accounts.firstOrNull { account ->
            account.stableId == directory.activeAccountId
        }
        if (
            active == null ||
            active.source != AccountSessionSource.AUTHENTICATED ||
            active.stableId != accountStableId
        ) return null

        val session = when (
            val result = accountCoordinator.ensureAccessTokenForAccount(
                accountStableId = accountStableId,
                requiredScopes = MODERATION_READ_SCOPES,
            )
        ) {
            is AuthSessionResult.Authenticated -> result.session
            // LOW1_MODERATION_SESSION_FAILURE_KEEPS_RETRYABLE_V1 (OCB-507): a
            // refresh or store fault can pass; the failure says so.
            is AuthSessionResult.EndpointFailure -> {
                lastSessionFailureRetryable = when (val endpoint = result.result) {
                    is OAuthEndpointResult.HttpFailure -> endpoint.retryable
                    is OAuthEndpointResult.TransportFailure -> endpoint.retryable
                    else -> false
                }
                return null
            }
            AuthSessionResult.StorageFailure -> {
                lastSessionFailureRetryable = true
                return null
            }
            AuthSessionResult.NoPendingCallback,
            AuthSessionResult.ClientConfigurationMismatch,
            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> {
                lastSessionFailureRetryable = false
                return null
            }
        }
        if (!session.tokens.grants(MODERATION_READ_SCOPES)) return null
        return SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(
                session.tokens.accessToken,
            ),
        )
    }

    private companion object {
        val MODERATION_READ_SCOPES = setOf("mysubreddits", "read")
        const val MAX_MODERATOR_DIRECTORY_PAGES = 10
        val POST_ID_REGEX = Regex("[a-z0-9]+")
    }
}

internal fun reportTargetResultOf(
    result: NetworkResult<RedditModeratorQueueItem?>,
): AuthenticatedRedditReportTargetResult = when (result) {
    is NetworkResult.Success -> result.value?.let {
        AuthenticatedRedditReportTargetResult.Loaded(it)
    } ?: AuthenticatedRedditReportTargetResult.NotFound
    // ISSUE36_A_404_THREAD_IS_NOT_PROOF_OF_DELETION_V1: the target is
    // read from its thread (#281); a thread Reddit does not know is
    // "not found", as an empty /api/info answer was.
    is NetworkResult.HttpFailure -> if (result.statusCode == 404) {
        AuthenticatedRedditReportTargetResult.NotFound
    } else {
        // DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1
        AuthenticatedRedditReportTargetResult.Failed(
            AuthenticatedRedditModerationFailureKind.HTTP, result.statusCode, result.failureDetailsOrNull(),
        )
    }
    is NetworkResult.TransportFailure -> AuthenticatedRedditReportTargetResult.Failed(
        AuthenticatedRedditModerationFailureKind.TRANSPORT,
        details = result.failureDetailsOrNull(),
    )
    is NetworkResult.ProtocolFailure -> AuthenticatedRedditReportTargetResult.Failed(
        AuthenticatedRedditModerationFailureKind.PROTOCOL,
        details = result.failureDetailsOrNull(),
    )
}
