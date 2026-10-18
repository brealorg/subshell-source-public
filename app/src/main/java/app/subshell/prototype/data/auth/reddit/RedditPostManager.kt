package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageSnapshot
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteProviderFailureDetails
import app.subshell.prototype.data.error.redditWriteRetryAfterMs
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.SystemNetworkClock
import app.subshell.prototype.data.network.reddit.RedditPostManageLoadNetworkResult
import app.subshell.prototype.data.network.reddit.RedditPostManageMutationNetworkResult
import app.subshell.prototype.data.network.reddit.RedditPostManageNetworkDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import okhttp3.Call

internal enum class AuthenticatedPostManageFailureKind {
    VALIDATION,
    ALREADY_MUTATING,
    AUTHORIZATION_REQUIRED,
    SESSION,
    RATE_LIMIT,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

internal sealed interface AuthenticatedPostManageLoadResult {
    data class Loaded(
        val snapshot: PostManageSnapshot,
    ) : AuthenticatedPostManageLoadResult

    data class Failed(
        val kind: AuthenticatedPostManageFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedPostManageLoadResult
}

internal sealed interface AuthenticatedPostManageMutationResult {
    data object Updated : AuthenticatedPostManageMutationResult

    data class Failed(
        val kind: AuthenticatedPostManageFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedPostManageMutationResult
}

internal interface AuthenticatedPostManager {
    suspend fun load(target: PostManageTarget): AuthenticatedPostManageLoadResult

    suspend fun mutate(
        request: PostManageMutationRequest,
    ): AuthenticatedPostManageMutationResult
}

internal class ConfiguredRedditPostManager(
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val networkClock: NetworkClock = SystemNetworkClock,
) : AuthenticatedPostManager {
    private val mutationMutex = Mutex()

    override suspend fun load(
        target: PostManageTarget,
    ): AuthenticatedPostManageLoadResult {
        val session = when (
            val result = accountCoordinator.ensureAccessTokenForAccount(
                accountStableId = target.accountStableId,
                requiredScopes = setOf("read"),
            )
        ) {
            is AuthSessionResult.Authenticated -> result.session
            is AuthSessionResult.EndpointFailure -> return failedLoad(
                kind = AuthenticatedPostManageFailureKind.SESSION,
                retryable = result.result.toFailureDetails().retryable == true,
                failureDetails = result.result.toFailureDetails(),
            )
            AuthSessionResult.ClientConfigurationMismatch,
            AuthSessionResult.NoPendingCallback,
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            is AuthSessionResult.AuthorizationDenied,
            -> return failedLoad(AuthenticatedPostManageFailureKind.SESSION)
        }
        val result = dataSource(session.tokens.accessToken).load(
            postFullname = target.postFullname,
            flairScopeGranted = session.tokens.grants(setOf("flair")),
        )
        return when (result) {
            is RedditPostManageLoadNetworkResult.Loaded ->
                AuthenticatedPostManageLoadResult.Loaded(result.snapshot)
            is RedditPostManageLoadNetworkResult.HttpFailure ->
                loadHttpFailure(
                    statusCode = result.statusCode,
                    retryable = result.retryable,
                    retryAfterMs =
                        redditWriteRetryAfterMs(result.metadata, rateLimited = result.statusCode == 429),
                )
            is RedditPostManageLoadNetworkResult.TransportFailure ->
                failedLoad(
                    kind = AuthenticatedPostManageFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    failureDetails = redditWriteTransportFailureDetails(
                        result.kind,
                        result.retryable,
                    ),
                )
            is RedditPostManageLoadNetworkResult.ProtocolFailure ->
                failedLoad(
                    AuthenticatedPostManageFailureKind.PROTOCOL,
                    failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                )
        }
    }

    override suspend fun mutate(
        request: PostManageMutationRequest,
    ): AuthenticatedPostManageMutationResult {
        if (!mutationMutex.tryLock()) {
            return failedMutation(
                AuthenticatedPostManageFailureKind.ALREADY_MUTATING,
            )
        }
        return try {
            val requiredScopes = when (request.operation) {
                PostManageOperation.FLAIR_ACCESS,
                PostManageOperation.SET_FLAIR,
                -> setOf("flair")
                PostManageOperation.SET_NSFW,
                PostManageOperation.SET_SPOILER,
                -> setOf("modposts")
                PostManageOperation.SET_SEND_REPLIES -> setOf("edit")
            }
            val session = when (
                val result = accountCoordinator.ensureAccessTokenForAccount(
                    accountStableId = request.target.accountStableId,
                    requiredScopes = requiredScopes,
                )
            ) {
                is AuthSessionResult.Authenticated -> result.session
                // CORRECT02_TRANSIENT_SESSION_FAILURE_IS_NOT_SIGN_IN_V1 (OCB-516):
                // as load() does, a failed refresh or store read is a session
                // problem (retryable when the refresh was), not a new authorization.
                is AuthSessionResult.EndpointFailure -> return failedMutation(
                    kind = AuthenticatedPostManageFailureKind.SESSION,
                    retryable = result.result.toFailureDetails().retryable == true,
                    failureDetails = result.result.toFailureDetails(),
                )
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return failedMutation(
                    AuthenticatedPostManageFailureKind.SESSION,
                    retryable = true,
                )
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return failedMutation(
                    AuthenticatedPostManageFailureKind.AUTHORIZATION_REQUIRED,
                )
            }
            if (request.operation == PostManageOperation.FLAIR_ACCESS) {
                return AuthenticatedPostManageMutationResult.Updated
            }
            when (val result = dataSource(session.tokens.accessToken).mutate(request)) {
                is RedditPostManageMutationNetworkResult.Updated ->
                    AuthenticatedPostManageMutationResult.Updated
                is RedditPostManageMutationNetworkResult.Rejected ->
                    failedMutation(
                        kind = AuthenticatedPostManageFailureKind.HTTP,
                        failureDetails = redditWriteProviderFailureDetails(
                            code = result.errors.first().code,
                            message = result.errors.first().message,
                            retryable = false,
                        ),
                    )
                is RedditPostManageMutationNetworkResult.HttpFailure ->
                    mutationHttpFailure(
                        statusCode = result.statusCode,
                        retryable = result.retryable,
                        retryAfterMs =
                            redditWriteRetryAfterMs(result.metadata, rateLimited = result.statusCode == 429),
                    )
                is RedditPostManageMutationNetworkResult.TransportFailure ->
                    failedMutation(
                        kind = AuthenticatedPostManageFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            result.kind,
                            result.retryable,
                        ),
                    )
                is RedditPostManageMutationNetworkResult.ProtocolFailure ->
                    failedMutation(
                        AuthenticatedPostManageFailureKind.PROTOCOL,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    private fun dataSource(accessToken: String) =
        RedditPostManageNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = json,
                userAgent = userAgent,
                bearerTokenProvider = { accessToken },
                clock = networkClock,
            ),
        )

    private fun loadHttpFailure(
        statusCode: Int,
        retryable: Boolean,
        retryAfterMs: Long?,
    ): AuthenticatedPostManageLoadResult.Failed {
        val details = FailureDetails(
            httpStatusCode = statusCode,
            retryAfterMs = retryAfterMs,
            retryable = retryable,
        )
        return when {
            statusCode == 401 || statusCode == 403 ->
                failedLoad(
                    kind = AuthenticatedPostManageFailureKind.AUTHORIZATION_REQUIRED,
                    httpStatusCode = statusCode,
                    failureDetails = details,
                )
            statusCode == 429 ->
                failedLoad(
                    kind = AuthenticatedPostManageFailureKind.RATE_LIMIT,
                    retryable = true,
                    retryAfterMs = retryAfterMs,
                    httpStatusCode = statusCode,
                    failureDetails = details.copy(retryable = true),
                )
            else ->
                failedLoad(
                    kind = AuthenticatedPostManageFailureKind.HTTP,
                    retryable = retryable,
                    httpStatusCode = statusCode,
                    failureDetails = details,
                )
        }
    }

    private fun mutationHttpFailure(
        statusCode: Int,
        retryable: Boolean,
        retryAfterMs: Long?,
    ): AuthenticatedPostManageMutationResult.Failed {
        val details = FailureDetails(
            httpStatusCode = statusCode,
            retryAfterMs = retryAfterMs,
            retryable = retryable,
        )
        return when {
            statusCode == 401 || statusCode == 403 ->
                failedMutation(
                    kind = AuthenticatedPostManageFailureKind.AUTHORIZATION_REQUIRED,
                    httpStatusCode = statusCode,
                    failureDetails = details,
                )
            statusCode == 429 ->
                failedMutation(
                    kind = AuthenticatedPostManageFailureKind.RATE_LIMIT,
                    retryable = true,
                    retryAfterMs = retryAfterMs,
                    httpStatusCode = statusCode,
                    failureDetails = details.copy(retryable = true),
                )
            else ->
                failedMutation(
                    kind = AuthenticatedPostManageFailureKind.HTTP,
                    retryable = retryable,
                    httpStatusCode = statusCode,
                    failureDetails = details,
                )
        }
    }

    private fun failedLoad(
        kind: AuthenticatedPostManageFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = AuthenticatedPostManageLoadResult.Failed(
        kind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
    )

    private fun failedMutation(
        kind: AuthenticatedPostManageFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = AuthenticatedPostManageMutationResult.Failed(
        kind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
    )
}
