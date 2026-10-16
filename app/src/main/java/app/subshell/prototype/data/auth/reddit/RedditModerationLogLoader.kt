package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditModerationLogNetworkDataSource
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

// MODERATION02A4_OWNER_BOUND_MOD_LOG_LOADER_V1
internal class RedditModerationLogLoader(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModerationLogLoader {
    private val requestMutex = Mutex()

    override suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String?,
    ): AuthenticatedRedditModerationLogResult = withContext(ioDispatcher) {
        val communityName = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrElse {
            return@withContext AuthenticatedRedditModerationLogResult.Failed(
                communityName = rawCommunityName.trim().lowercase(),
                kind = AuthenticatedRedditModerationFailureKind.PROTOCOL,
            )
        }

        requestMutex.withLock {
            val client = when (val authorization = authorizedClient(accountStableId)) {
                is AuthorizedModerationLogClient.Ready -> authorization.client
                AuthorizedModerationLogClient.AuthorizationRequired ->
                    return@withLock AuthenticatedRedditModerationLogResult.AuthorizationRequired
                is AuthorizedModerationLogClient.Failed ->
                    return@withLock AuthenticatedRedditModerationLogResult.Failed(
                        communityName = communityName,
                        kind = authorization.kind,
                        retryable = authorization.retryable,
                        details = authorization.details,
                    )
            }

            when (
                val result = RedditModerationLogNetworkDataSource(client).page(
                    communityName = communityName,
                    after = after,
                )
            ) {
                is NetworkResult.Success ->
                    AuthenticatedRedditModerationLogResult.Loaded(
                        communityName = communityName,
                        entries = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                is NetworkResult.HttpFailure ->
                    if (result.statusCode == 401) {
                        AuthenticatedRedditModerationLogResult.AuthorizationRequired
                    } else {
                        AuthenticatedRedditModerationLogResult.Failed(
                            communityName = communityName,
                            kind = AuthenticatedRedditModerationFailureKind.HTTP,
                            retryable = result.retryable,
                            details = result.failureDetailsOrNull(),
                            httpStatusCode = result.statusCode,
                        )
                    }
                is NetworkResult.TransportFailure ->
                    AuthenticatedRedditModerationLogResult.Failed(
                        communityName = communityName,
                        kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                is NetworkResult.ProtocolFailure ->
                    AuthenticatedRedditModerationLogResult.Failed(
                        communityName = communityName,
                        kind = AuthenticatedRedditModerationFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
            }
        }
    }

    private suspend fun authorizedClient(
        accountStableId: String,
    ): AuthorizedModerationLogClient =
        when (val authorization = capability.ensureAuthorized(accountStableId)) {
            is AuthSessionResult.Authenticated ->
                AuthorizedModerationLogClient.Ready(
                    SubshellHttpClient(
                        callFactory = callFactory,
                        endpoint = endpoint,
                        json = SubshellNetworkJson.providerJson,
                        userAgent = userAgent,
                        bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                    ),
                )
            // LOW1 (OCB-508): an expired grant asks to authorize like a denied one.
            is AuthSessionResult.AuthorizationDenied,
            AuthSessionResult.ReauthenticationRequired,
            -> AuthorizedModerationLogClient.AuthorizationRequired
            AuthSessionResult.ClientConfigurationMismatch ->
                AuthorizedModerationLogClient.Failed(
                    AuthenticatedRedditModerationFailureKind.CLIENT_CONFIGURATION,
                )
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthorizedModerationLogClient.Failed(
                kind = AuthenticatedRedditModerationFailureKind.SESSION,
                retryable = true,
            )
            is AuthSessionResult.EndpointFailure ->
                AuthorizedModerationLogClient.Failed(
                    kind = AuthenticatedRedditModerationFailureKind.SESSION,
                    retryable = authorization.result.retryable(),
                    details = authorization.result.toFailureDetails(),
                )
        }
}

private sealed interface AuthorizedModerationLogClient {
    data class Ready(val client: SubshellHttpClient) : AuthorizedModerationLogClient
    data object AuthorizationRequired : AuthorizedModerationLogClient
    data class Failed(
        val kind: AuthenticatedRedditModerationFailureKind,
        val retryable: Boolean = false,
        val details: app.subshell.prototype.data.error.FailureDetails? = null,
    ) : AuthorizedModerationLogClient
}

private fun OAuthEndpointResult<*>.retryable(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}
