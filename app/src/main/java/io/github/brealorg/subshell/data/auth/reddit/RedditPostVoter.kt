package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.PostVoteFailureKind
import io.github.brealorg.subshell.data.account.PostVoteRequest
import io.github.brealorg.subshell.data.account.normalizedPostVoteRequest
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.auth.toFailureDetails
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.redditWriteHttpFailureDetails
import io.github.brealorg.subshell.data.error.redditWriteProtocolFailureDetails
import io.github.brealorg.subshell.data.error.redditWriteTransportFailureDetails
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditPostVoteNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditPostVoteNetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

internal sealed interface AuthenticatedPostVoteResult {
    data class Voted(
        val request: PostVoteRequest,
    ) : AuthenticatedPostVoteResult

    data object AuthorizationRequired : AuthenticatedPostVoteResult

    data class Failed(
        val kind: PostVoteFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedPostVoteResult
}

internal fun interface AuthenticatedPostVoteWriter {
    suspend fun vote(request: PostVoteRequest): AuthenticatedPostVoteResult
}

internal class ConfiguredRedditPostVoter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedPostVoteWriter {
    private val mutationMutex = Mutex()

    override suspend fun vote(
        request: PostVoteRequest,
    ): AuthenticatedPostVoteResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val normalized = try {
                request.normalizedPostVoteRequest()
            } catch (_: IllegalArgumentException) {
                return@withLock failed(PostVoteFailureKind.VALIDATION)
            }

            val session = when (
                val authorization =
                    capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock AuthenticatedPostVoteResult.AuthorizationRequired

                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock failed(PostVoteFailureKind.CONFIGURATION)

                AuthSessionResult.StorageFailure ->
                    return@withLock failed(
                        PostVoteFailureKind.SESSION,
                        retryable = true,
                    )

                AuthSessionResult.NoPendingCallback ->
                    return@withLock failed(PostVoteFailureKind.SESSION)

                is AuthSessionResult.EndpointFailure ->
                    return@withLock failed(
                        PostVoteFailureKind.SESSION,
                        retryable = authorization.result.isRetryableEndpointFailure(),
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
                val result = RedditPostVoteNetworkDataSource(httpClient).vote(
                    postFullname = normalized.postFullname,
                    direction = normalized.direction,
                )
            ) {
                is RedditPostVoteNetworkResult.Updated ->
                    AuthenticatedPostVoteResult.Voted(normalized)

                is RedditPostVoteNetworkResult.HttpFailure ->
                    // LOW1_403_IS_A_REFUSAL_NOT_A_MISSING_GRANT_V1 (OCB-517): only a 401 asks to authorize.
                    if (result.statusCode == 401) {
                        AuthenticatedPostVoteResult.AuthorizationRequired
                    } else {
                        failed(
                            PostVoteFailureKind.HTTP,
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

                is RedditPostVoteNetworkResult.TransportFailure ->
                    failed(
                        PostVoteFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            result.kind,
                            result.retryable,
                        ),
                    )

                is RedditPostVoteNetworkResult.ProtocolFailure ->
                    failed(
                        PostVoteFailureKind.PROTOCOL,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
            }
        }
    }

    private fun failed(
        kind: PostVoteFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): AuthenticatedPostVoteResult.Failed =
        AuthenticatedPostVoteResult.Failed(
            kind = kind,
            retryable = retryable,
            statusCode = statusCode,
            failureDetails = failureDetails,
        )
}

private fun OAuthEndpointResult<*>.isRetryableEndpointFailure(): Boolean =
    when (this) {
        is OAuthEndpointResult.HttpFailure -> retryable
        is OAuthEndpointResult.TransportFailure -> retryable
        is OAuthEndpointResult.ProtocolFailure -> false
        is OAuthEndpointResult.Success -> false
    }
