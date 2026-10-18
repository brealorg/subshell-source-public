package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.PostSaveFailureKind
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.normalizedPostSaveRequest
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
import app.subshell.prototype.data.network.reddit.RedditPostSaveNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditPostSaveNetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

internal sealed interface AuthenticatedPostSaveResult {
    data class Updated(
        val request: PostSaveRequest,
    ) : AuthenticatedPostSaveResult

    data object AuthorizationRequired : AuthenticatedPostSaveResult

    data class Failed(
        val kind: PostSaveFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedPostSaveResult
}

internal fun interface AuthenticatedPostSaveWriter {
    suspend fun setSaved(request: PostSaveRequest): AuthenticatedPostSaveResult
}

internal class ConfiguredRedditPostSaver(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedPostSaveWriter {
    private val mutationMutex = Mutex()

    override suspend fun setSaved(
        request: PostSaveRequest,
    ): AuthenticatedPostSaveResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val normalized = try {
                request.normalizedPostSaveRequest()
            } catch (_: IllegalArgumentException) {
                return@withLock failed(PostSaveFailureKind.VALIDATION)
            }

            val session = when (
                val authorization = capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock AuthenticatedPostSaveResult.AuthorizationRequired

                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock failed(PostSaveFailureKind.CONFIGURATION)

                AuthSessionResult.StorageFailure ->
                    return@withLock failed(
                        PostSaveFailureKind.SESSION,
                        retryable = true,
                    )

                AuthSessionResult.NoPendingCallback ->
                    return@withLock failed(PostSaveFailureKind.SESSION)

                is AuthSessionResult.EndpointFailure ->
                    return@withLock failed(
                        PostSaveFailureKind.SESSION,
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
                val result = RedditPostSaveNetworkDataSource(httpClient).setSaved(
                    postFullname = normalized.postFullname,
                    saved = normalized.saved,
                )
            ) {
                is RedditPostSaveNetworkResult.Updated ->
                    AuthenticatedPostSaveResult.Updated(normalized)

                is RedditPostSaveNetworkResult.HttpFailure ->
                    // LOW1_403_IS_A_REFUSAL_NOT_A_MISSING_GRANT_V1 (OCB-517): only a 401 asks to authorize.
                    if (result.statusCode == 401) {
                        AuthenticatedPostSaveResult.AuthorizationRequired
                    } else {
                        failed(
                            PostSaveFailureKind.HTTP,
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

                is RedditPostSaveNetworkResult.TransportFailure ->
                    failed(
                        PostSaveFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            result.kind,
                            result.retryable,
                        ),
                    )

                is RedditPostSaveNetworkResult.ProtocolFailure ->
                    failed(
                        PostSaveFailureKind.PROTOCOL,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
            }
        }
    }

    private fun failed(
        kind: PostSaveFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): AuthenticatedPostSaveResult.Failed =
        AuthenticatedPostSaveResult.Failed(
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
