package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.normalizePostEditFullname
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.error.redditWriteRetryAfterMs
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.SystemNetworkClock
import app.subshell.prototype.data.network.reddit.RedditPostDeleteNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditPostDeleteNetworkResult
import app.subshell.prototype.data.network.reddit.RedditPostDeleteReadback
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import okhttp3.Call

internal data class AuthenticatedPostDeleteRequest(
    val accountStableId: String,
    val postFullname: String,
)

internal enum class AuthenticatedPostDeleteFailureKind {
    VALIDATION,
    ALREADY_DELETING,
    AUTHORIZATION_REQUIRED,
    SESSION,
    RATE_LIMIT,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    // H3_POST_DELETE_OUTCOME_UNKNOWN_V1: Reddit may have deleted the post; the
    // read-back could not tell. Check again reads, it never re-sends.
    OUTCOME_UNKNOWN,
    // The read-back still shows the post: deleting again is safe.
    NOT_DELETED,
}

internal sealed interface AuthenticatedPostDeleteResult {
    data class Deleted(
        val accountStableId: String,
        val postFullname: String,
        val metadata: NetworkResponseMetadata,
    ) : AuthenticatedPostDeleteResult

    data class Failed(
        val kind: AuthenticatedPostDeleteFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedPostDeleteResult
}

internal fun interface AuthenticatedPostDeleter {
    suspend fun delete(request: AuthenticatedPostDeleteRequest): AuthenticatedPostDeleteResult

    /** Reads whether Reddit still shows the post; never sends the delete. */
    suspend fun verify(request: AuthenticatedPostDeleteRequest): AuthenticatedPostDeleteResult =
        AuthenticatedPostDeleteResult.Failed(AuthenticatedPostDeleteFailureKind.OUTCOME_UNKNOWN)
}

internal class ConfiguredRedditPostDeleter(
    private val capability: AuthCommentWriteTokenSource,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val networkClock: NetworkClock = SystemNetworkClock,
    private val readBackDelaysMs: List<Long> = POST_DELETE_READBACK_DELAYS_MS,
) : AuthenticatedPostDeleter {
    private val deleteMutex = Mutex()

    override suspend fun delete(request: AuthenticatedPostDeleteRequest): AuthenticatedPostDeleteResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedPostDeleteFailureKind.VALIDATION)
        if (!deleteMutex.tryLock()) return failed(AuthenticatedPostDeleteFailureKind.ALREADY_DELETING)
        return try {
            val dataSource = when (val authorized = authorizedDataSource(normalized)) {
                is AuthorizedPostDelete.Refused -> return authorized.failure
                is AuthorizedPostDelete.Ready -> authorized.dataSource
            }
            when (val result = dataSource.delete(normalized.postFullname)) {
                is RedditPostDeleteNetworkResult.Deleted -> AuthenticatedPostDeleteResult.Deleted(
                    normalized.accountStableId,
                    normalized.postFullname,
                    result.metadata,
                )
                is RedditPostDeleteNetworkResult.HttpFailure -> {
                    val details = redditWriteHttpFailureDetails(
                        result.statusCode,
                        result.retryable,
                        result.metadata,
                        providerReason = result.providerReason,
                    )
                    when {
                        result.statusCode == 401 || result.statusCode == 403 -> failed(
                            AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED,
                            httpStatusCode = result.statusCode,
                            failureDetails = details.copy(retryable = false),
                        )
                        result.statusCode == 429 -> failed(
                            AuthenticatedPostDeleteFailureKind.RATE_LIMIT,
                            retryable = true,
                            retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = true),
                            httpStatusCode = result.statusCode,
                            failureDetails = details.copy(retryable = true),
                        )
                        isRedditWriteOutcomeUnknownStatus(result.statusCode) ->
                            confirmAfterUnknownOutcome(dataSource, normalized, details.copy(retryable = false))
                        else -> failed(
                            AuthenticatedPostDeleteFailureKind.HTTP,
                            retryable = false,
                            httpStatusCode = result.statusCode,
                            failureDetails = details.copy(retryable = false),
                        )
                    }
                }
                is RedditPostDeleteNetworkResult.TransportFailure -> {
                    val safeToRetry = result.kind in setOf(
                        NetworkTransportFailureKind.DNS,
                        NetworkTransportFailureKind.CONNECT,
                    )
                    if (safeToRetry || result.kind == NetworkTransportFailureKind.TLS) {
                        failed(
                            AuthenticatedPostDeleteFailureKind.TRANSPORT,
                            retryable = safeToRetry,
                            failureDetails = redditWriteTransportFailureDetails(
                                result.kind,
                                safeToRetry,
                            ),
                        )
                    } else {
                        // A timeout or a broken connection after sending: the post may be gone.
                        confirmAfterUnknownOutcome(
                            dataSource,
                            normalized,
                            redditWriteTransportFailureDetails(result.kind, false),
                        )
                    }
                }
                is RedditPostDeleteNetworkResult.ProtocolFailure -> confirmAfterUnknownOutcome(
                    dataSource,
                    normalized,
                    redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                )
            }
        } finally {
            deleteMutex.unlock()
        }
    }

    override suspend fun verify(request: AuthenticatedPostDeleteRequest): AuthenticatedPostDeleteResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedPostDeleteFailureKind.VALIDATION)
        val dataSource = when (val authorized = authorizedDataSource(normalized)) {
            is AuthorizedPostDelete.Refused -> return authorized.failure
            is AuthorizedPostDelete.Ready -> authorized.dataSource
        }
        return confirmAfterUnknownOutcome(dataSource, normalized, failureDetails = null, delaysMs = listOf(0L))
    }

    // H3_POST_DELETE_READBACK_V1: after an unknown outcome, read the post back
    // instead of re-sending. Gone or [deleted] means deleted; still there means
    // not deleted; no answer stays unknown.
    private suspend fun confirmAfterUnknownOutcome(
        dataSource: RedditPostDeleteNetworkDataSource,
        normalized: AuthenticatedPostDeleteRequest,
        failureDetails: FailureDetails?,
        delaysMs: List<Long> = readBackDelaysMs,
    ): AuthenticatedPostDeleteResult {
        var last: RedditPostDeleteReadback = RedditPostDeleteReadback.Unreadable
        for (delayMs in delaysMs) {
            if (delayMs > 0) delay(delayMs)
            last = dataSource.readBack(normalized.postFullname)
            val readBack = last
            if (readBack is RedditPostDeleteReadback.Deleted) {
                return AuthenticatedPostDeleteResult.Deleted(
                    normalized.accountStableId,
                    normalized.postFullname,
                    readBack.metadata,
                )
            }
        }
        return when (last) {
            RedditPostDeleteReadback.Present -> failed(
                AuthenticatedPostDeleteFailureKind.NOT_DELETED,
                retryable = true,
                httpStatusCode = failureDetails?.httpStatusCode,
                failureDetails = failureDetails?.copy(retryable = true),
            )
            else -> failed(
                AuthenticatedPostDeleteFailureKind.OUTCOME_UNKNOWN,
                httpStatusCode = failureDetails?.httpStatusCode,
                failureDetails = failureDetails?.copy(retryable = false),
            )
        }
    }

    private suspend fun authorizedDataSource(
        normalized: AuthenticatedPostDeleteRequest,
    ): AuthorizedPostDelete {
        val session = when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
            is AuthSessionResult.Authenticated -> authorization.session
            is AuthSessionResult.EndpointFailure -> return AuthorizedPostDelete.Refused(mapSessionFailure(authorization.result))
            // CORRECT02_STORAGE_FAILURE_IS_RETRYABLE_SESSION_V1 (OCB-514): a store
            // read that failed is a retryable session problem, not a new sign-in.
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> return AuthorizedPostDelete.Refused(failed(AuthenticatedPostDeleteFailureKind.SESSION, retryable = true))
            AuthSessionResult.ClientConfigurationMismatch,
            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> return AuthorizedPostDelete.Refused(failed(AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED))
        }
        val identity = session.identity
            ?: return AuthorizedPostDelete.Refused(failed(AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED))
        if (
            authenticatedAccountId(
                session.provider,
                session.clientMode,
                identity.providerAccountId,
            ) != normalized.accountStableId ||
            !session.tokens.grants(RedditOAuth.liveCommentEditScopes)
        ) return AuthorizedPostDelete.Refused(failed(AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED))

        return AuthorizedPostDelete.Ready(RedditPostDeleteNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = json,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
                clock = networkClock,
            ),
        ))
    }

    private fun normalizeRequest(
        request: AuthenticatedPostDeleteRequest,
    ): AuthenticatedPostDeleteRequest? = runCatching {
        AuthenticatedPostDeleteRequest(
            accountStableId = request.accountStableId.trim().also {
                require(it.isNotEmpty()) { "Account stable ID cannot be blank" }
            },
            postFullname = normalizePostEditFullname(request.postFullname),
        )
    }.getOrNull()

    private fun mapSessionFailure(
        result: OAuthEndpointResult<Nothing>,
    ): AuthenticatedPostDeleteResult.Failed = when (result) {
        is OAuthEndpointResult.HttpFailure -> failed(
            if (result.statusCode == 401 || result.statusCode == 403) {
                AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED
            } else {
                AuthenticatedPostDeleteFailureKind.SESSION
            },
            retryable = result.retryable,
            httpStatusCode = result.statusCode,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.TransportFailure -> failed(
            AuthenticatedPostDeleteFailureKind.SESSION,
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.ProtocolFailure -> failed(
            AuthenticatedPostDeleteFailureKind.SESSION,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.Success -> failed(AuthenticatedPostDeleteFailureKind.SESSION)
    }

    private fun failed(
        kind: AuthenticatedPostDeleteFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = AuthenticatedPostDeleteResult.Failed(kind, retryable, retryAfterMs, httpStatusCode, failureDetails)

}

private sealed interface AuthorizedPostDelete {
    data class Ready(val dataSource: RedditPostDeleteNetworkDataSource) : AuthorizedPostDelete
    data class Refused(val failure: AuthenticatedPostDeleteResult.Failed) : AuthorizedPostDelete
}

internal val POST_DELETE_READBACK_DELAYS_MS = listOf(0L, 1_000L, 2_500L)
