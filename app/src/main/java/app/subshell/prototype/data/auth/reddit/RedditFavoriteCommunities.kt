package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.normalizeCommunityMembershipName
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

/**
 * REDDITFAV02_FAVORITE_WRITE_V1: `POST api/favorite` (sr_name, make_favorite)
 * under the `subscribe` scope; Reddit answers 200 with `{}` (device-verified
 * 2026-10-05), so the answer confirms nothing about the list and the caller
 * reads the subscriptions back.
 */
internal fun redditFavoriteFormRequest(
    communityName: String,
    makeFavorite: Boolean,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "favorite"),
    fields = listOf(
        "make_favorite" to makeFavorite.toString(),
        "sr_name" to normalizeCommunityMembershipName(communityName),
        "api_type" to "json",
    ),
)

/**
 * REDDITFAV04_CUSTOM_FEED_FAVORITES_V1: `POST api/multi/favorite`
 * (multipath, make_favorite); Reddit answers 200 with an empty body
 * (device-verified 2026-10-05), so the feed list is read back.
 */
internal fun redditCustomFeedFavoriteFormRequest(
    customFeedPath: String,
    makeFavorite: Boolean,
): FormPostRequest {
    val path = customFeedPath.trim()
    require(path.startsWith("/") && path.length <= 256 && path.none(Char::isWhitespace)) {
        "Custom feed path is not a Reddit multireddit path"
    }
    return FormPostRequest(
        pathSegments = listOf("api", "multi", "favorite"),
        fields = listOf(
            "multipath" to path,
            "make_favorite" to makeFavorite.toString(),
            "api_type" to "json",
        ),
    )
}

/**
 * One favorite change. [communityName] names the target for messages: the
 * community, or a custom feed's display name when [customFeedPath] is set.
 */
internal data class RedditFavoriteRequest(
    val accountStableId: String,
    val communityName: String,
    val makeFavorite: Boolean,
    val customFeedPath: String? = null,
) {
    /** The pending key: a lowercased community name or custom feed path. */
    val key: String
        get() = (customFeedPath ?: communityName).lowercase()
}

internal enum class RedditFavoriteFailureKind {
    VALIDATION,
    CONFIGURATION,
    SESSION,
    HTTP,
    PROTOCOL,

    // A lost or 5xx answer: the change may have been applied. Never sent
    // again; the subscriptions read decides.
    OUTCOME_UNKNOWN,
}

internal sealed interface RedditFavoriteWriteResult {
    data object Updated : RedditFavoriteWriteResult

    data object AuthorizationRequired : RedditFavoriteWriteResult

    data class Failed(
        val kind: RedditFavoriteFailureKind,
        val retryable: Boolean = false,
        val failureDetails: FailureDetails? = null,
    ) : RedditFavoriteWriteResult
}

internal fun interface RedditFavoriteCommunityWriter {
    suspend fun update(request: RedditFavoriteRequest): RedditFavoriteWriteResult
}

internal class ConfiguredRedditFavoriteCommunityWriter(
    private val capability: AuthCustomFeedWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RedditFavoriteCommunityWriter {
    private val mutationMutex = Mutex()

    override suspend fun update(
        request: RedditFavoriteRequest,
    ): RedditFavoriteWriteResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val formRequest = try {
                request.customFeedPath
                    ?.let { path -> redditCustomFeedFavoriteFormRequest(path, request.makeFavorite) }
                    ?: redditFavoriteFormRequest(request.communityName, request.makeFavorite)
            } catch (_: IllegalArgumentException) {
                return@withLock RedditFavoriteWriteResult.Failed(RedditFavoriteFailureKind.VALIDATION)
            }

            val session = when (
                val authorization = capability.ensureAuthorized(request.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock RedditFavoriteWriteResult.AuthorizationRequired
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock RedditFavoriteWriteResult.Failed(RedditFavoriteFailureKind.CONFIGURATION)
                AuthSessionResult.StorageFailure ->
                    return@withLock RedditFavoriteWriteResult.Failed(
                        RedditFavoriteFailureKind.SESSION,
                        retryable = true,
                    )
                AuthSessionResult.NoPendingCallback ->
                    return@withLock RedditFavoriteWriteResult.Failed(RedditFavoriteFailureKind.SESSION)
                is AuthSessionResult.EndpointFailure ->
                    return@withLock RedditFavoriteWriteResult.Failed(
                        RedditFavoriteFailureKind.SESSION,
                        retryable = true,
                    )
            }

            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            when (val result = httpClient.postFormStatus(formRequest)) {
                is NetworkResult.Success -> RedditFavoriteWriteResult.Updated
                is NetworkResult.HttpFailure -> when {
                    result.statusCode == 401 ||
                        result.statusCode == 403 &&
                        !session.tokens.grants(RedditOAuth.liveCustomFeedWriteScopes) ->
                        RedditFavoriteWriteResult.AuthorizationRequired
                    isRedditWriteOutcomeUnknownStatus(result.statusCode) ->
                        RedditFavoriteWriteResult.Failed(
                            kind = RedditFavoriteFailureKind.OUTCOME_UNKNOWN,
                            failureDetails = redditWriteHttpFailureDetails(
                                result.statusCode,
                                false,
                                result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )
                    else -> RedditFavoriteWriteResult.Failed(
                        kind = RedditFavoriteFailureKind.HTTP,
                        retryable = result.retryable,
                        failureDetails = redditWriteHttpFailureDetails(
                            result.statusCode,
                            result.retryable,
                            result.metadata,
                            providerReason = result.providerReason,
                        ),
                    )
                }
                is NetworkResult.TransportFailure -> RedditFavoriteWriteResult.Failed(
                    kind = RedditFavoriteFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = redditWriteTransportFailureDetails(result.kind, result.retryable),
                )
                is NetworkResult.ProtocolFailure -> RedditFavoriteWriteResult.Failed(
                    kind = RedditFavoriteFailureKind.PROTOCOL,
                    failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                )
            }
        }
    }
}
