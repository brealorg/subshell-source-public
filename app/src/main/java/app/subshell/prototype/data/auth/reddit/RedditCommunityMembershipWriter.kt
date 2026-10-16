package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipRequest
import app.subshell.prototype.data.account.normalizedCommunityMembershipRequest
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditCommunityMembershipNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditCommunityMembershipNetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

internal sealed interface AuthenticatedCommunityMembershipResult {
    data class Updated(
        val request: CommunityMembershipRequest,
    ) : AuthenticatedCommunityMembershipResult

    data object AuthorizationRequired : AuthenticatedCommunityMembershipResult

    data class Failed(
        val kind: CommunityMembershipFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedCommunityMembershipResult
}

internal fun interface AuthenticatedCommunityMembershipWriter {
    suspend fun update(
        request: CommunityMembershipRequest,
    ): AuthenticatedCommunityMembershipResult
}

internal class ConfiguredRedditCommunityMembershipWriter(
    private val capability: AuthCustomFeedWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedCommunityMembershipWriter {
    private val mutationMutex = Mutex()

    override suspend fun update(
        request: CommunityMembershipRequest,
    ): AuthenticatedCommunityMembershipResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val normalized = try {
                request.normalizedCommunityMembershipRequest()
            } catch (_: IllegalArgumentException) {
                return@withLock failed(
                    CommunityMembershipFailureKind.VALIDATION,
                )
            }

            val session = when (
                val authorization =
                    capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated ->
                    authorization.session

                AuthSessionResult.ReauthenticationRequired ->
                    return@withLock AuthenticatedCommunityMembershipResult.AuthorizationRequired

                is AuthSessionResult.AuthorizationDenied ->
                    return@withLock AuthenticatedCommunityMembershipResult.AuthorizationRequired

                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock failed(
                        CommunityMembershipFailureKind.CONFIGURATION,
                    )

                AuthSessionResult.StorageFailure ->
                    return@withLock failed(
                        CommunityMembershipFailureKind.SESSION,
                        retryable = true,
                    )

                AuthSessionResult.NoPendingCallback ->
                    return@withLock failed(
                        CommunityMembershipFailureKind.SESSION,
                    )

                is AuthSessionResult.EndpointFailure ->
                    return@withLock failed(
                        CommunityMembershipFailureKind.SESSION,
                        retryable =
                            authorization.result.isRetryableEndpointFailure(),
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
                val result =
                    RedditCommunityMembershipNetworkDataSource(httpClient)
                        .update(
                            communityName = normalized.communityName,
                            action = normalized.action,
                        )
            ) {
                is RedditCommunityMembershipNetworkResult.Updated ->
                    AuthenticatedCommunityMembershipResult.Updated(normalized)

                is RedditCommunityMembershipNetworkResult.HttpFailure ->
                    // CORRECT02_MEMBERSHIP_403_WITH_SCOPE_IS_A_REFUSAL_V1 (OCB-614): a
                    // 403 asks to authorize only when the token lacks "subscribe";
                    // with it, the community refused (private, quarantined, banned)
                    // and authorizing again would send the same join in a loop.
                    if (
                        result.statusCode == 401 ||
                        (
                            result.statusCode == 403 &&
                                !session.tokens.grants(RedditOAuth.liveCustomFeedWriteScopes)
                            )
                    ) {
                        AuthenticatedCommunityMembershipResult.AuthorizationRequired
                    } else if (isRedditWriteOutcomeUnknownStatus(result.statusCode)) {
                        // DATALAYER09: the join/leave may have been applied; the
                        // #43 reconciliation confirms it against Reddit's list.
                        failed(
                            CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                            retryable = false,
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                result.statusCode,
                                false,
                                result.metadata,
                            ),
                        )
                    } else {
                        failed(
                            CommunityMembershipFailureKind.HTTP,
                            retryable = result.retryable,
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                result.statusCode,
                                result.retryable,
                                result.metadata,
                            ),
                        )
                    }

                is RedditCommunityMembershipNetworkResult.TransportFailure ->
                    failed(
                        CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                        retryable = false,
                        failureDetails = redditWriteTransportFailureDetails(
                            result.kind,
                            result.retryable,
                        ),
                    )

                is RedditCommunityMembershipNetworkResult.ProtocolFailure ->
                    failed(
                        CommunityMembershipFailureKind.PROTOCOL,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
            }
        }
    }

    private fun failed(
        kind: CommunityMembershipFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): AuthenticatedCommunityMembershipResult.Failed =
        AuthenticatedCommunityMembershipResult.Failed(
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
