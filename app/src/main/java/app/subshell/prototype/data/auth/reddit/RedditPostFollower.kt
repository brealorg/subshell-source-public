package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.PostFollowFailureKind
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.normalizedPostFollowRequest
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditPostFollowNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditPostFollowNetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

// FOLLOWPOST01A1A_OWNER_BOUND_NONRETRYING_WRITER_V1
internal sealed interface AuthenticatedPostFollowResult {
    data class Updated(
        val request: PostFollowRequest,
    ) : AuthenticatedPostFollowResult

    data object AuthorizationRequired : AuthenticatedPostFollowResult

    data class Failed(
        val kind: PostFollowFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedPostFollowResult
}

internal fun interface AuthenticatedPostFollowWriter {
    suspend fun setFollowed(
        request: PostFollowRequest,
    ): AuthenticatedPostFollowResult
}

internal class ConfiguredRedditPostFollower(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedPostFollowWriter {
    private val mutationMutex = Mutex()

    override suspend fun setFollowed(
        request: PostFollowRequest,
    ): AuthenticatedPostFollowResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val normalized = try {
                request.normalizedPostFollowRequest()
            } catch (_: IllegalArgumentException) {
                return@withLock failed(PostFollowFailureKind.VALIDATION)
            }

            val session = when (
                val authorization =
                    capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session

                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock AuthenticatedPostFollowResult.AuthorizationRequired

                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock failed(PostFollowFailureKind.CONFIGURATION)

                AuthSessionResult.StorageFailure ->
                    return@withLock failed(
                        PostFollowFailureKind.SESSION,
                        retryable = true,
                    )

                AuthSessionResult.NoPendingCallback ->
                    return@withLock failed(PostFollowFailureKind.SESSION)

                is AuthSessionResult.EndpointFailure ->
                    return@withLock failed(
                        PostFollowFailureKind.SESSION,
                        retryable =
                            authorization.result.postFollowEndpointRetryable(),
                        failureDetails = authorization.result.toFailureDetails(),
                    )
            }

            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )

            when (
                val result = RedditPostFollowNetworkDataSource(httpClient)
                    .setFollowed(
                        postFullname = normalized.postFullname,
                        followed = normalized.followed,
                    )
            ) {
                is RedditPostFollowNetworkResult.Updated ->
                    AuthenticatedPostFollowResult.Updated(normalized)

                is RedditPostFollowNetworkResult.HttpFailure ->
                    // LOW1_403_IS_A_REFUSAL_NOT_A_MISSING_GRANT_V1 (OCB-517): only a 401 asks to authorize.
                    if (result.statusCode == 401) {
                        AuthenticatedPostFollowResult.AuthorizationRequired
                    } else {
                        failed(
                            PostFollowFailureKind.HTTP,
                            retryable = result.retryable,
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                result.statusCode,
                                result.retryable,
                                result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )
                    }

                is RedditPostFollowNetworkResult.TransportFailure ->
                    failed(
                        PostFollowFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            result.kind,
                            result.retryable,
                        ),
                    )

                is RedditPostFollowNetworkResult.ProtocolFailure ->
                    failed(
                        PostFollowFailureKind.PROTOCOL,
                        failureDetails =
                            redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
            }
        }
    }

    private fun failed(
        kind: PostFollowFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): AuthenticatedPostFollowResult.Failed =
        AuthenticatedPostFollowResult.Failed(
            kind = kind,
            retryable = retryable,
            statusCode = statusCode,
            failureDetails = failureDetails,
        )
}

private fun OAuthEndpointResult<*>.postFollowEndpointRetryable(): Boolean =
    when (this) {
        is OAuthEndpointResult.HttpFailure -> retryable
        is OAuthEndpointResult.TransportFailure -> retryable
        is OAuthEndpointResult.ProtocolFailure -> false
        is OAuthEndpointResult.Success -> false
    }
