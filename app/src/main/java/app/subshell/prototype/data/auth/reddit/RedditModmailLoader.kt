package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailThreadResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadCountsResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.RedditModmailNetworkDataSource
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

// MODMAIL01A1_OWNER_BOUND_READ_LOADER_V1
// MODMAIL06_AUTHORITATIVE_UNREAD_LOADER_V1
internal class RedditModmailLoader(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModmailLoader {
    private val requestMutex = Mutex()

    override suspend fun load(
        accountStableId: String,
        after: String?,
    ): AuthenticatedRedditModmailResult =
        loadByMode(
            accountStableId = accountStableId,
            after = after,
            listMode = RedditModmailListMode.ACTIVE,
        )

    override suspend fun loadByMode(
        accountStableId: String,
        after: String?,
        listMode: RedditModmailListMode,
    ): AuthenticatedRedditModmailResult = withContext(ioDispatcher) {
        requestMutex.withLock {
            val client = when (val session = authorizedClient(accountStableId)) {
                is AuthorizedModmailClient.Ready -> session.client
                AuthorizedModmailClient.AuthorizationRequired ->
                    return@withLock AuthenticatedRedditModmailResult.AuthorizationRequired
                is AuthorizedModmailClient.Failed ->
                    return@withLock session.toListFailure()
            }
            val source = RedditModmailNetworkDataSource(client)
            val communities = when (val result = source.mailCommunities()) {
                is NetworkResult.Success -> result.value
                is NetworkResult.HttpFailure -> {
                    if (result.statusCode == 401) {
                        return@withLock AuthenticatedRedditModmailResult.AuthorizationRequired
                    }
                    return@withLock AuthenticatedRedditModmailResult.Failed(
                        kind = AuthenticatedRedditModmailFailureKind.HTTP,
                        retryable = result.retryable,
                        httpStatusCode = result.statusCode,
                        details = result.failureDetailsOrNull(),
                    )
                }
                is NetworkResult.TransportFailure ->
                    return@withLock AuthenticatedRedditModmailResult.Failed(
                        kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    return@withLock AuthenticatedRedditModmailResult.Failed(
                        kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
            if (communities.isEmpty()) {
                return@withLock AuthenticatedRedditModmailResult.Loaded(
                    mailCommunities = emptySet(),
                    conversations = emptyList(),
                    nextCursor = null,
                )
            }

            val unreadCounts = when (val result = source.unreadCounts()) {
                is NetworkResult.Success -> result.value
                is NetworkResult.HttpFailure -> {
                    if (result.statusCode == 401) {
                        return@withLock AuthenticatedRedditModmailResult.AuthorizationRequired
                    }
                    null
                }
                is NetworkResult.TransportFailure,
                is NetworkResult.ProtocolFailure,
                -> null
            }

            val apiState = when (listMode) {
                RedditModmailListMode.ACTIVE -> "all"
                RedditModmailListMode.NEW -> "new"
                RedditModmailListMode.IN_PROGRESS -> "inprogress"
                RedditModmailListMode.FILTERED -> "filtered"
                RedditModmailListMode.HIGHLIGHTED -> "highlighted"
                RedditModmailListMode.ARCHIVED -> "archived"
                RedditModmailListMode.APPEALS -> "appeals"
                RedditModmailListMode.JOIN_REQUESTS -> "join_requests"
                RedditModmailListMode.MOD_DISCUSSIONS -> "mod"
                RedditModmailListMode.NOTIFICATIONS -> "notifications"
            }
            when (
                val result = source.conversationsByState(
                    after = after,
                    state = apiState,
                )
            ) {
                is NetworkResult.Success -> AuthenticatedRedditModmailResult.Loaded(
                    mailCommunities = communities,
                    conversations = result.value.items,
                    nextCursor = result.value.nextCursor,
                    unreadCounts = unreadCounts,
                )
                is NetworkResult.HttpFailure -> {
                    if (result.statusCode == 401) {
                        AuthenticatedRedditModmailResult.AuthorizationRequired
                    } else {
                        AuthenticatedRedditModmailResult.Failed(
                            kind = AuthenticatedRedditModmailFailureKind.HTTP,
                            retryable = result.retryable,
                            httpStatusCode = result.statusCode,
                            details = result.failureDetailsOrNull(),
                        )
                    }
                }
                is NetworkResult.TransportFailure -> AuthenticatedRedditModmailResult.Failed(
                    kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> AuthenticatedRedditModmailResult.Failed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    override suspend fun loadUnread(
        accountStableId: String,
    ): AuthenticatedRedditModmailUnreadResult = withContext(ioDispatcher) {
        requestMutex.withLock {
            val client = when (val session = authorizedClient(accountStableId)) {
                is AuthorizedModmailClient.Ready -> session.client
                AuthorizedModmailClient.AuthorizationRequired ->
                    return@withLock AuthenticatedRedditModmailUnreadResult.AuthorizationRequired
                is AuthorizedModmailClient.Failed ->
                    return@withLock session.toUnreadFailure()
            }
            when (val result = RedditModmailNetworkDataSource(client).unreadConversations()) {
                is NetworkResult.Success -> AuthenticatedRedditModmailUnreadResult.Loaded(
                    conversations = result.value.items.filter { conversation ->
                        conversation.unread
                    },
                )
                is NetworkResult.HttpFailure -> {
                    if (result.statusCode == 401) {
                        AuthenticatedRedditModmailUnreadResult.AuthorizationRequired
                    } else {
                        AuthenticatedRedditModmailUnreadResult.Failed(
                            kind = AuthenticatedRedditModmailFailureKind.HTTP,
                            retryable = result.retryable,
                            httpStatusCode = result.statusCode,
                            details = result.failureDetailsOrNull(),
                        )
                    }
                }
                is NetworkResult.TransportFailure -> AuthenticatedRedditModmailUnreadResult.Failed(
                    kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> AuthenticatedRedditModmailUnreadResult.Failed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    override suspend fun loadUnreadCounts(
        accountStableId: String,
    ): AuthenticatedRedditModmailUnreadCountsResult = withContext(ioDispatcher) {
        requestMutex.withLock {
            val client = when (val session = authorizedClient(accountStableId)) {
                is AuthorizedModmailClient.Ready -> session.client
                AuthorizedModmailClient.AuthorizationRequired ->
                    return@withLock AuthenticatedRedditModmailUnreadCountsResult.AuthorizationRequired
                is AuthorizedModmailClient.Failed ->
                    return@withLock session.toUnreadCountsFailure()
            }
            when (val result = RedditModmailNetworkDataSource(client).unreadCounts()) {
                is NetworkResult.Success -> AuthenticatedRedditModmailUnreadCountsResult.Loaded(
                    counts = result.value,
                )
                is NetworkResult.HttpFailure -> {
                    if (result.statusCode == 401) {
                        AuthenticatedRedditModmailUnreadCountsResult.AuthorizationRequired
                    } else {
                        AuthenticatedRedditModmailUnreadCountsResult.Failed(
                            kind = AuthenticatedRedditModmailFailureKind.HTTP,
                            retryable = result.retryable,
                            httpStatusCode = result.statusCode,
                            details = result.failureDetailsOrNull(),
                        )
                    }
                }
                is NetworkResult.TransportFailure ->
                    AuthenticatedRedditModmailUnreadCountsResult.Failed(
                        kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    AuthenticatedRedditModmailUnreadCountsResult.Failed(
                        kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
        }
    }

    override suspend fun loadThread(
        accountStableId: String,
        conversationId: String,
    ): AuthenticatedRedditModmailThreadResult = withContext(ioDispatcher) {
        requestMutex.withLock {
            val client = when (val session = authorizedClient(accountStableId)) {
                is AuthorizedModmailClient.Ready -> session.client
                AuthorizedModmailClient.AuthorizationRequired ->
                    return@withLock AuthenticatedRedditModmailThreadResult.AuthorizationRequired
                is AuthorizedModmailClient.Failed ->
                    return@withLock session.toThreadFailure()
            }
            when (
                val result = RedditModmailNetworkDataSource(client)
                    .conversation(conversationId)
            ) {
                is NetworkResult.Success -> AuthenticatedRedditModmailThreadResult.Loaded(
                    thread = result.value,
                )
                is NetworkResult.HttpFailure -> {
                    if (result.statusCode == 401) {
                        AuthenticatedRedditModmailThreadResult.AuthorizationRequired
                    } else {
                        AuthenticatedRedditModmailThreadResult.Failed(
                            kind = AuthenticatedRedditModmailFailureKind.HTTP,
                            retryable = result.retryable,
                            httpStatusCode = result.statusCode,
                            details = result.failureDetailsOrNull(),
                        )
                    }
                }
                is NetworkResult.TransportFailure -> AuthenticatedRedditModmailThreadResult.Failed(
                    kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> AuthenticatedRedditModmailThreadResult.Failed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        }
    }

    private suspend fun authorizedClient(accountStableId: String): AuthorizedModmailClient {
        return when (val authorization = capability.ensureAuthorized(accountStableId)) {
            is AuthSessionResult.Authenticated -> AuthorizedModmailClient.Ready(
                SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                ),
            )
            is AuthSessionResult.AuthorizationDenied ->
                AuthorizedModmailClient.AuthorizationRequired
            AuthSessionResult.ReauthenticationRequired -> AuthorizedModmailClient.Failed(
                kind = AuthenticatedRedditModmailFailureKind.SESSION,
            )
            AuthSessionResult.ClientConfigurationMismatch -> AuthorizedModmailClient.Failed(
                kind = AuthenticatedRedditModmailFailureKind.CONFIGURATION,
            )
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthorizedModmailClient.Failed(
                kind = AuthenticatedRedditModmailFailureKind.SESSION,
                retryable = true,
            )
            is AuthSessionResult.EndpointFailure -> AuthorizedModmailClient.Failed(
                kind = AuthenticatedRedditModmailFailureKind.SESSION,
                retryable = authorization.result.retryable(),
                details = authorization.result.toFailureDetails(),
            )
        }
    }
}

private sealed interface AuthorizedModmailClient {
    data class Ready(val client: SubshellHttpClient) : AuthorizedModmailClient
    data object AuthorizationRequired : AuthorizedModmailClient
    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val retryable: Boolean = false,
        val details: app.subshell.prototype.data.error.FailureDetails? = null,
    ) : AuthorizedModmailClient
}

private fun AuthorizedModmailClient.Failed.toListFailure() =
    AuthenticatedRedditModmailResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details,
    )

private fun AuthorizedModmailClient.Failed.toThreadFailure() =
    AuthenticatedRedditModmailThreadResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details,
    )

private fun AuthorizedModmailClient.Failed.toUnreadFailure() =
    AuthenticatedRedditModmailUnreadResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details,
    )

private fun AuthorizedModmailClient.Failed.toUnreadCountsFailure() =
    AuthenticatedRedditModmailUnreadCountsResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details,
    )

private fun OAuthEndpointResult<*>.retryable(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}
