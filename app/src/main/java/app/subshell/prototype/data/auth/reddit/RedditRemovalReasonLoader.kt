package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditRemovalReason
import app.subshell.prototype.data.account.RedditRemovalReasonFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditRemovalReasonNetworkDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call

internal sealed interface AuthenticatedRedditRemovalReasonResult {
    data class Loaded(
        val reasons: List<RedditRemovalReason>,
    ) : AuthenticatedRedditRemovalReasonResult

    data object AuthorizationRequired : AuthenticatedRedditRemovalReasonResult

    data class Failed(
        val kind: RedditRemovalReasonFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedRedditRemovalReasonResult
}

internal interface AuthenticatedRedditRemovalReasonLoader {
    suspend fun load(
        accountStableId: String,
        communityName: String,
    ): AuthenticatedRedditRemovalReasonResult
}

// MOD01A21_OWNER_BOUND_REMOVAL_REASON_LOADER_V1
internal class ConfiguredRedditRemovalReasonLoader(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditRemovalReasonLoader {
    override suspend fun load(
        accountStableId: String,
        communityName: String,
    ): AuthenticatedRedditRemovalReasonResult = withContext(ioDispatcher) {
        val session = when (val authorization = capability.ensureAuthorized(accountStableId)) {
            is AuthSessionResult.Authenticated -> authorization.session
            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> return@withContext AuthenticatedRedditRemovalReasonResult.AuthorizationRequired
            AuthSessionResult.ClientConfigurationMismatch ->
                return@withContext failed(RedditRemovalReasonFailureKind.CONFIGURATION)
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> return@withContext failed(
                RedditRemovalReasonFailureKind.SESSION,
                retryable = true,
            )
            is AuthSessionResult.EndpointFailure ->
                return@withContext failed(
                    RedditRemovalReasonFailureKind.SESSION,
                    retryable = authorization.result.isRetryableRemovalReasonEndpointFailure(),
                    failureDetails = authorization.result.toFailureDetails(),
                )
        }

        val client = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
        )
        when (val result = RedditRemovalReasonNetworkDataSource(client).reasons(communityName)) {
            is NetworkResult.Success -> AuthenticatedRedditRemovalReasonResult.Loaded(result.value)
            is NetworkResult.HttpFailure -> when (result.statusCode) {
                401 -> AuthenticatedRedditRemovalReasonResult.AuthorizationRequired
                403 -> failed(
                    kind = RedditRemovalReasonFailureKind.AUTHORIZATION,
                    retryable = false,
                    httpStatusCode = result.statusCode,
                    failureDetails = result.failureDetailsOrNull(),
                )
                else -> failed(
                    kind = RedditRemovalReasonFailureKind.HTTP,
                    retryable = result.retryable,
                    httpStatusCode = result.statusCode,
                    failureDetails = result.failureDetailsOrNull(),
                )
            }
            is NetworkResult.TransportFailure -> failed(
                kind = RedditRemovalReasonFailureKind.TRANSPORT,
                retryable = result.retryable,
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure -> failed(
                kind = RedditRemovalReasonFailureKind.PROTOCOL,
                failureDetails = result.failureDetailsOrNull(),
            )
        }
    }

    private fun failed(
        kind: RedditRemovalReasonFailureKind,
        retryable: Boolean = false,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = AuthenticatedRedditRemovalReasonResult.Failed(
        kind = kind,
        retryable = retryable,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
    )
}

private fun OAuthEndpointResult<*>.isRetryableRemovalReasonEndpointFailure(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}
