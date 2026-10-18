package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.normalized
import app.subshell.prototype.data.account.isReportControl
import app.subshell.prototype.data.account.isContentControl
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditModerationActionNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditModerationActionNetworkResult
import app.subshell.prototype.data.account.RedditRemovalStepOutcome
import app.subshell.prototype.data.network.reddit.RedditRemovalMessageNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditRemovalMessageNetworkResult
import app.subshell.prototype.data.network.reddit.RedditRemovalReasonNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditRemovalReasonWriteNetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call

internal sealed interface AuthenticatedRedditModerationActionResult {
    data class Updated(
        val removalReasonWarning: Boolean = false,
        // MODRESPONSE01A1_REMOVAL_MESSAGE_MODEL_V1
        val removalMessageOutcome: RedditRemovalStepOutcome = RedditRemovalStepOutcome.NOT_REQUESTED,
    ) : AuthenticatedRedditModerationActionResult
    data object AuthorizationRequired : AuthenticatedRedditModerationActionResult

    data class Failed(
        val kind: RedditModeratorActionFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedRedditModerationActionResult
}

internal interface AuthenticatedRedditModerationWriter {
    suspend fun mutate(
        request: RedditModeratorActionRequest,
    ): AuthenticatedRedditModerationActionResult
}

// MOD01A2_OWNER_BOUND_NONRETRYING_MODERATION_WRITER_V1
internal class ConfiguredRedditModerationWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val activeAccountStableId: () -> String? = { null },
) : AuthenticatedRedditModerationWriter {
    private val mutationMutex = Mutex()

    override suspend fun mutate(
        request: RedditModeratorActionRequest,
    ): AuthenticatedRedditModerationActionResult = withContext(ioDispatcher) {
        if (!mutationMutex.tryLock()) {
            return@withContext failed(RedditModeratorActionFailureKind.ALREADY_MUTATING)
        }
        try {
            val normalized = try {
                request.normalized()
            } catch (_: IllegalArgumentException) {
                return@withContext failed(RedditModeratorActionFailureKind.VALIDATION)
            }
            val session = when (
                val authorization = capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withContext AuthenticatedRedditModerationActionResult.AuthorizationRequired
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withContext failed(RedditModeratorActionFailureKind.CONFIGURATION)
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withContext failed(
                    kind = RedditModeratorActionFailureKind.SESSION,
                    retryable = true,
                )
                is AuthSessionResult.EndpointFailure ->
                    return@withContext failed(
                        kind = RedditModeratorActionFailureKind.SESSION,
                        retryable = authorization.result.isRetryableModerationEndpointFailure(),
                        failureDetails = authorization.result.toFailureDetails(),
                    )
            }

            // MODERATION02A2_REPORT_WRITER_RECHECK_AFTER_AUTHORIZATION_V1
            if (normalized.action.isReportControl() && activeAccountStableId() != normalized.accountStableId) {
                return@withContext failed(RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
            }
            // MODERATION02A3_CONTENT_CONTROL_WRITER_RECHECK_AFTER_AUTHORIZATION_V1
            if (normalized.action.isContentControl() && activeAccountStableId() != normalized.accountStableId) {
                return@withContext failed(RedditModeratorActionFailureKind.ACCOUNT_CHANGED)
            }
            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = object : NetworkBearerTokenProvider {
                    private val sessionBound = capability.bearerTokenProvider(session.tokens.accessToken)

                    override fun bearerToken(): String? {
                        if (normalized.action.isReportControl()) {
                            check(activeAccountStableId() == normalized.accountStableId) {
                                "Moderator report account changed before dispatch"
                            }
                        }
                        if (normalized.action.isContentControl()) {
                            check(activeAccountStableId() == normalized.accountStableId) {
                                "Moderator content-control account changed before dispatch"
                            }
                        }
                        return sessionBound.bearerToken()
                    }

                    override fun onUnauthorized() = sessionBound.onUnauthorized()
                },
            )
            when (
                val result = RedditModerationActionNetworkDataSource(httpClient)
                    .mutate(normalized)
            ) {
                is RedditModerationActionNetworkResult.Updated -> {
                    // The removal has succeeded here. The reason and the response
                    // are separate, optional writes; each reports its own outcome
                    // and none of them is retried (the removal must not repeat).
                    val needsRemovalReason =
                        normalized.action == app.subshell.prototype.data.account.RedditModeratorAction.REMOVE &&
                            (normalized.removalReasonId != null || normalized.modNote != null)
                    val removalReasonWarning = if (!needsRemovalReason) {
                        false
                    } else {
                        val reasonResult = RedditRemovalReasonNetworkDataSource(httpClient)
                            .attachRemovalReason(
                                thingFullname = normalized.thingFullname,
                                reasonId = normalized.removalReasonId,
                                modNote = normalized.modNote,
                            )
                        reasonResult !is RedditRemovalReasonWriteNetworkResult.Updated
                    }
                    // MODRESPONSE01A1_REMOVAL_MESSAGE_AFTER_REMOVAL_V1
                    val removalMessage = normalized.removalMessage
                    val removalMessageOutcome = if (removalMessage == null) {
                        RedditRemovalStepOutcome.NOT_REQUESTED
                    } else {
                        val messageResult = RedditRemovalMessageNetworkDataSource(httpClient)
                            .send(
                                thingFullname = normalized.thingFullname,
                                message = removalMessage,
                            )
                        if (messageResult is RedditRemovalMessageNetworkResult.Sent) {
                            RedditRemovalStepOutcome.SENT
                        } else {
                            RedditRemovalStepOutcome.FAILED
                        }
                    }
                    AuthenticatedRedditModerationActionResult.Updated(
                        removalReasonWarning = removalReasonWarning,
                        removalMessageOutcome = removalMessageOutcome,
                    )
                }

                is RedditModerationActionNetworkResult.HttpFailure -> when {
                    result.statusCode == 401 ->
                        AuthenticatedRedditModerationActionResult.AuthorizationRequired
                    result.statusCode == 403 -> failed(
                        kind = RedditModeratorActionFailureKind.AUTHORIZATION,
                        httpStatusCode = result.statusCode,
                        failureDetails = redditWriteHttpFailureDetails(
                            statusCode = result.statusCode,
                            retryable = false,
                            metadata = result.metadata,
                            providerReason = result.providerReason,
                        ),
                    )
                    result.statusCode == 429 -> failed(
                        kind = RedditModeratorActionFailureKind.HTTP,
                        retryable = true,
                        httpStatusCode = result.statusCode,
                        failureDetails = redditWriteHttpFailureDetails(
                            statusCode = result.statusCode,
                            retryable = true,
                            metadata = result.metadata,
                            providerReason = result.providerReason,
                        ),
                    )
                    result.statusCode == 408 ||
                        result.statusCode == 421 ||
                        result.statusCode == 425 ||
                        result.statusCode in 500..599 -> failed(
                            kind = RedditModeratorActionFailureKind.OUTCOME_UNKNOWN,
                            httpStatusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                statusCode = result.statusCode,
                                retryable = false,
                                metadata = result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )
                    else -> failed(
                        kind = RedditModeratorActionFailureKind.HTTP,
                        retryable = result.retryable,
                        httpStatusCode = result.statusCode,
                        failureDetails = redditWriteHttpFailureDetails(
                            statusCode = result.statusCode,
                            retryable = result.retryable,
                            metadata = result.metadata,
                            providerReason = result.providerReason,
                        ),
                    )
                }

                is RedditModerationActionNetworkResult.TransportFailure -> when (result.kind) {
                    NetworkTransportFailureKind.DNS,
                    NetworkTransportFailureKind.CONNECT,
                    -> failed(
                        kind = RedditModeratorActionFailureKind.TRANSPORT,
                        retryable = true,
                        failureDetails = redditWriteTransportFailureDetails(
                            kind = result.kind,
                            retryable = true,
                        ),
                    )
                    NetworkTransportFailureKind.TLS -> failed(
                        kind = RedditModeratorActionFailureKind.TRANSPORT,
                        failureDetails = redditWriteTransportFailureDetails(
                            kind = result.kind,
                            retryable = false,
                        ),
                    )
                    NetworkTransportFailureKind.TIMEOUT,
                    NetworkTransportFailureKind.IO,
                    -> failed(
                        kind = RedditModeratorActionFailureKind.OUTCOME_UNKNOWN,
                        failureDetails = redditWriteTransportFailureDetails(
                            kind = result.kind,
                            retryable = false,
                        ),
                    )
                }

                is RedditModerationActionNetworkResult.Rejected -> failed(
                    kind = RedditModeratorActionFailureKind.HTTP,
                    httpStatusCode = result.metadata.statusCode,
                    failureDetails = redditWriteHttpFailureDetails(
                        statusCode = result.metadata.statusCode,
                        retryable = false,
                        metadata = result.metadata,
                    ).withProviderDetails(code = result.code, message = result.message),
                )
                is RedditModerationActionNetworkResult.ProtocolFailure -> failed(
                    kind = RedditModeratorActionFailureKind.PROTOCOL,
                    failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    private fun failed(
        kind: RedditModeratorActionFailureKind,
        retryable: Boolean = false,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = AuthenticatedRedditModerationActionResult.Failed(
        kind = kind,
        retryable = retryable,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
    )
}

private fun OAuthEndpointResult<*>.isRetryableModerationEndpointFailure(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}
