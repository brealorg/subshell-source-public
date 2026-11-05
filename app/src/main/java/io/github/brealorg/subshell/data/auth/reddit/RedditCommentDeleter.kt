package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.normalizeCommentEditFullname
import io.github.brealorg.subshell.data.auth.normalizeRedditPostFullname
import io.github.brealorg.subshell.data.auth.AuthCommentWriteTokenSource
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.auth.authenticatedAccountId
import io.github.brealorg.subshell.data.auth.toFailureDetails
import io.github.brealorg.subshell.data.error.isRedditWriteOutcomeUnknownStatus
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.redditWriteHttpFailureDetails
import io.github.brealorg.subshell.data.error.redditWriteProtocolFailureDetails
import io.github.brealorg.subshell.data.error.redditWriteRetryAfterMs
import io.github.brealorg.subshell.data.error.redditWriteTransportFailureDetails
import io.github.brealorg.subshell.data.network.NetworkClock
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.SystemNetworkClock
import io.github.brealorg.subshell.data.network.reddit.RedditCommentDeleteNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditCommentDeleteNetworkResult
import io.github.brealorg.subshell.data.network.reddit.RedditCommentDeleteReadback
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import okhttp3.Call

internal data class AuthenticatedCommentDeleteRequest(val accountStableId: String, val postId: String, val commentFullname: String)
internal enum class AuthenticatedCommentDeleteFailureKind { VALIDATION, ALREADY_DELETING, AUTHORIZATION_REQUIRED, SESSION, RATE_LIMIT, HTTP, TRANSPORT, PROTOCOL, OUTCOME_NOT_CONFIRMED }
internal sealed interface AuthenticatedCommentDeleteResult {
    data class Deleted(val accountStableId: String, val commentFullname: String, val metadata: NetworkResponseMetadata) : AuthenticatedCommentDeleteResult
    data class Failed(val kind: AuthenticatedCommentDeleteFailureKind, val retryable: Boolean = false, val retryAfterMs: Long? = null, val httpStatusCode: Int? = null, val failureDetails: FailureDetails? = null) : AuthenticatedCommentDeleteResult
}
internal fun interface AuthenticatedCommentDeleter { suspend fun delete(request: AuthenticatedCommentDeleteRequest): AuthenticatedCommentDeleteResult }

internal class ConfiguredRedditCommentDeleter(
    private val capability: AuthCommentWriteTokenSource,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val networkClock: NetworkClock = SystemNetworkClock,
) : AuthenticatedCommentDeleter {
    private val deleteMutex = Mutex()

    override suspend fun delete(request: AuthenticatedCommentDeleteRequest): AuthenticatedCommentDeleteResult {
        val normalized = normalizeRequest(request) ?: return failed(AuthenticatedCommentDeleteFailureKind.VALIDATION)
        if (!deleteMutex.tryLock()) return failed(AuthenticatedCommentDeleteFailureKind.ALREADY_DELETING)
        return try {
            val session = when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
                is AuthSessionResult.Authenticated -> authorization.session
                is AuthSessionResult.EndpointFailure -> return mapSessionFailure(authorization.result)
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ReauthenticationRequired,
                AuthSessionResult.StorageFailure,
                is AuthSessionResult.AuthorizationDenied,
                -> return failed(AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED)
            }
            val identity = session.identity ?: return failed(AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED)
            if (
                authenticatedAccountId(session.provider, session.clientMode, identity.providerAccountId) != normalized.accountStableId ||
                !session.tokens.grants(RedditOAuth.liveCommentEditScopes)
            ) return failed(AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED)

            val dataSource = RedditCommentDeleteNetworkDataSource(
                SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = json,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
                    clock = networkClock,
                ),
                postId = normalized.postId,
            )
            when (val result = dataSource.delete(normalized.commentFullname)) {
                is RedditCommentDeleteNetworkResult.Deleted -> AuthenticatedCommentDeleteResult.Deleted(normalized.accountStableId, normalized.commentFullname, result.metadata)
                is RedditCommentDeleteNetworkResult.HttpFailure -> {
                    val details = redditWriteHttpFailureDetails(
                        result.statusCode,
                        result.retryable,
                        result.metadata,
                        providerReason = result.providerReason,
                    )
                    when {
                        result.statusCode == 401 || result.statusCode == 403 -> failed(
                            AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED,
                            httpStatusCode = result.statusCode,
                            failureDetails = details.copy(retryable = false),
                        )
                        result.statusCode == 429 -> failed(
                            AuthenticatedCommentDeleteFailureKind.RATE_LIMIT,
                            retryable = true,
                            retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = true),
                            httpStatusCode = result.statusCode,
                            failureDetails = details.copy(retryable = true),
                        )
                        // H3: the shared rule also covers 421.
                        isRedditWriteOutcomeUnknownStatus(result.statusCode) -> afterLostAnswer(
                            dataSource,
                            normalized,
                            notDeleted = failed(
                                AuthenticatedCommentDeleteFailureKind.HTTP,
                                retryable = true,
                                httpStatusCode = result.statusCode,
                                failureDetails = details.copy(retryable = true),
                            ),
                            unknown = failed(
                                AuthenticatedCommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
                                retryable = false,
                                httpStatusCode = result.statusCode,
                                failureDetails = details.copy(retryable = false),
                            ),
                        )
                        else -> failed(
                            AuthenticatedCommentDeleteFailureKind.HTTP,
                            retryable = false,
                            httpStatusCode = result.statusCode,
                            failureDetails = details.copy(retryable = false),
                        )
                    }
                }
                is RedditCommentDeleteNetworkResult.TransportFailure -> {
                    val safeToRetry = result.kind in setOf(
                        io.github.brealorg.subshell.data.network.NetworkTransportFailureKind.DNS,
                        io.github.brealorg.subshell.data.network.NetworkTransportFailureKind.CONNECT,
                    )
                    if (safeToRetry) {
                        failed(
                            AuthenticatedCommentDeleteFailureKind.TRANSPORT,
                            retryable = true,
                            failureDetails = redditWriteTransportFailureDetails(result.kind, true),
                        )
                    } else {
                        afterLostAnswer(
                            dataSource,
                            normalized,
                            notDeleted = failed(
                                AuthenticatedCommentDeleteFailureKind.TRANSPORT,
                                retryable = true,
                                failureDetails = redditWriteTransportFailureDetails(result.kind, true),
                            ),
                            unknown = failed(
                                AuthenticatedCommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
                                retryable = false,
                                failureDetails = redditWriteTransportFailureDetails(result.kind, false),
                            ),
                        )
                    }
                }
                is RedditCommentDeleteNetworkResult.ProtocolFailure -> afterLostAnswer(
                    dataSource,
                    normalized,
                    notDeleted = failed(
                        AuthenticatedCommentDeleteFailureKind.PROTOCOL,
                        retryable = true,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    ),
                    unknown = failed(
                        AuthenticatedCommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
                        retryable = false,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    ),
                )
                is RedditCommentDeleteNetworkResult.Rejected -> failed(
                    AuthenticatedCommentDeleteFailureKind.HTTP,
                    retryable = false,
                    httpStatusCode = result.metadata.statusCode,
                    failureDetails = redditWriteHttpFailureDetails(result.metadata.statusCode, false, result.metadata)
                        .withProviderDetails(code = result.code),
                )
                is RedditCommentDeleteNetworkResult.OutcomeNotConfirmed -> failed(
                    AuthenticatedCommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
                    retryable = false,
                    failureDetails = FailureDetails(retryable = false),
                )
            }
        } finally { deleteMutex.unlock() }
    }

    private suspend fun afterLostAnswer(
        dataSource: RedditCommentDeleteNetworkDataSource,
        request: AuthenticatedCommentDeleteRequest,
        notDeleted: AuthenticatedCommentDeleteResult.Failed,
        unknown: AuthenticatedCommentDeleteResult.Failed,
    ): AuthenticatedCommentDeleteResult = when (val readback = dataSource.readBack(request.commentFullname)) {
        is RedditCommentDeleteReadback.Deleted ->
            AuthenticatedCommentDeleteResult.Deleted(request.accountStableId, request.commentFullname, readback.metadata)
        RedditCommentDeleteReadback.StillPresent -> notDeleted
        RedditCommentDeleteReadback.Unknown -> unknown
    }

    private fun normalizeRequest(request: AuthenticatedCommentDeleteRequest): AuthenticatedCommentDeleteRequest? = runCatching {
        AuthenticatedCommentDeleteRequest(
            accountStableId = request.accountStableId.trim().also { require(it.isNotEmpty()) { "Account stable ID cannot be blank" } },
            postId = normalizeRedditPostFullname(request.postId),
            commentFullname = normalizeCommentEditFullname(request.commentFullname),
        )
    }.getOrNull()

    private fun mapSessionFailure(result: OAuthEndpointResult<Nothing>): AuthenticatedCommentDeleteResult.Failed = when (result) {
        is OAuthEndpointResult.HttpFailure -> failed(
            if (result.statusCode == 401 || result.statusCode == 403) AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED else AuthenticatedCommentDeleteFailureKind.SESSION,
            result.retryable,
            httpStatusCode = result.statusCode,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.TransportFailure -> failed(AuthenticatedCommentDeleteFailureKind.SESSION, result.retryable, failureDetails = result.toFailureDetails())
        is OAuthEndpointResult.ProtocolFailure -> failed(AuthenticatedCommentDeleteFailureKind.SESSION, failureDetails = result.toFailureDetails())
        is OAuthEndpointResult.Success -> failed(AuthenticatedCommentDeleteFailureKind.SESSION)
    }

    private fun failed(kind: AuthenticatedCommentDeleteFailureKind, retryable: Boolean = false, retryAfterMs: Long? = null, httpStatusCode: Int? = null, failureDetails: FailureDetails? = null) =
        AuthenticatedCommentDeleteResult.Failed(kind, retryable, retryAfterMs, httpStatusCode, failureDetails)

}
