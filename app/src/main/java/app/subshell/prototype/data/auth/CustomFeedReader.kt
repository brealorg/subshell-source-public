package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.CustomFeedFailureKind
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.customFeedApiPathSegments
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditReadListingDto
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Call
import app.subshell.prototype.data.account.CustomFeedPostsFailureKind
import app.subshell.prototype.data.account.customFeedPathSegments
import app.subshell.prototype.data.network.reddit.toNetworkPage
import app.subshell.prototype.model.Post

internal sealed interface CustomFeedReadResult {
    data class Loaded(
        val feeds: List<CustomFeedSummary>,
    ) : CustomFeedReadResult

    data class Failed(
        val kind: CustomFeedFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
    ) : CustomFeedReadResult
}

internal sealed interface CustomFeedPostReadResult {
    data class Loaded(
        val posts: List<Post>,
        val nextCursor: String?,
    ) : CustomFeedPostReadResult

    data class Failed(
        val kind: CustomFeedPostsFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        // DIAG04_JSON_PATH_REACHES_THE_REPORT_V1
        val details: FailureDetails? = null,
    ) : CustomFeedPostReadResult
}

// HARNESS03_CUSTOM_FEED_SEAM_V1: the reads the ViewModel drives, as an
// interface so JVM tests can inject a fake through the runtime seam.
internal interface CustomFeedReader {
    suspend fun load(
        accountStableId: String,
        preferredSession: StoredOAuthSession? = null,
    ): CustomFeedReadResult

    suspend fun loadPosts(
        accountStableId: String,
        feedPath: String,
        after: String? = null,
        preferredSession: StoredOAuthSession? = null,
    ): CustomFeedPostReadResult

    // REDDITURL04_MULTIREDDIT_LINK_V1: one feed by path, also one the
    // account does not own (a public multireddit from a link).
    suspend fun loadFeed(
        accountStableId: String,
        feedPath: String,
    ): CustomFeedSummary? = null
}

internal class ConfiguredCustomFeedReader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CustomFeedReader {
    override suspend fun load(
        accountStableId: String,
        preferredSession: StoredOAuthSession?,
    ): CustomFeedReadResult = withContext(ioDispatcher) {
        try {
            if (
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                configuration.scopes != RedditOAuth.coreReadScopes
            ) {
                return@withContext failed(CustomFeedFailureKind.CLIENT_CONFIGURATION)
            }

            val directory = accountCoordinator.snapshot()
            val active = directory.accounts.firstOrNull { account ->
                account.stableId == directory.activeAccountId
            }
            if (
                active == null ||
                active.source != AccountSessionSource.AUTHENTICATED ||
                active.stableId != accountStableId
            ) {
                return@withContext failed(CustomFeedFailureKind.CLIENT_CONFIGURATION)
            }

            val session = preferredCustomFeedReadSession(
                candidate = preferredSession,
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
                requiredScopes = RedditOAuth.coreReadScopes,
                nowEpochMs = SystemAuthClock.epochMillis(),
                refreshSkewMs = OAuthSessionManager.DEFAULT_REFRESH_SKEW_MS,
            ) ?: when (
                val tokenResult = accountCoordinator.ensureAccessTokenForAccount(
                    accountStableId = accountStableId,
                    requiredScopes = RedditOAuth.coreReadScopes,
                )
            ) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> return@withContext CustomFeedReadResult.Failed(
                    kind = CustomFeedFailureKind.SESSION,
                    retryable = when (val endpointResult = tokenResult.result) {
                        is OAuthEndpointResult.HttpFailure -> endpointResult.retryable
                        is OAuthEndpointResult.TransportFailure -> endpointResult.retryable
                        is OAuthEndpointResult.ProtocolFailure -> false
                        is OAuthEndpointResult.Success -> false
                    },
                )
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withContext failed(CustomFeedFailureKind.SESSION)
            }

            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint
            ) {
                return@withContext failed(CustomFeedFailureKind.CLIENT_CONFIGURATION)
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = client.getJson(
                    request = JsonGetRequest(
                        pathSegments = listOf("api", "multi", "mine"),
                        queryParameters = listOf(
                            "raw_json" to "1",
                            "expand_srs" to "false",
                        ),
                    ),
                    deserializer = ListSerializer(RedditCustomFeedEnvelope.serializer()),
                )
            ) {
                is NetworkResult.Success -> CustomFeedReadResult.Loaded(
                    feeds = result.value
                        .mapNotNull(RedditCustomFeedEnvelope::toSummaryOrNull)
                        .distinctBy { feed -> feed.path.lowercase() }
                        .sortedBy { feed -> feed.displayName.lowercase() },
                )
                is NetworkResult.HttpFailure -> CustomFeedReadResult.Failed(
                    kind = CustomFeedFailureKind.HTTP,
                    retryable = result.retryable,
                    statusCode = result.statusCode,
                )
                is NetworkResult.TransportFailure -> CustomFeedReadResult.Failed(
                    kind = CustomFeedFailureKind.TRANSPORT,
                    retryable = result.retryable,
                )
                is NetworkResult.ProtocolFailure -> failed(CustomFeedFailureKind.PROTOCOL)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed(CustomFeedFailureKind.PROTOCOL)
        }
    }

    override suspend fun loadPosts(
        accountStableId: String,
        feedPath: String,
        after: String?,
        preferredSession: StoredOAuthSession?,
    ): CustomFeedPostReadResult = withContext(ioDispatcher) {
        try {
            val account = accountStableId.trim()
            val pathSegments = customFeedPathSegments(feedPath)
                ?: return@withContext CustomFeedPostReadResult.Failed(CustomFeedPostsFailureKind.PROTOCOL)
            if (
                account.isEmpty() ||
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                !configuration.scopes.containsAll(RedditOAuth.coreReadScopes)
            ) {
                return@withContext CustomFeedPostReadResult.Failed(
                    CustomFeedPostsFailureKind.CLIENT_CONFIGURATION,
                )
            }
            if (accountCoordinator.activeAuthenticatedAccountId() != account) {
                return@withContext CustomFeedPostReadResult.Failed(CustomFeedPostsFailureKind.SESSION)
            }
            val session = preferredCustomFeedReadSession(
                candidate = preferredSession,
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
                requiredScopes = RedditOAuth.coreReadScopes,
                nowEpochMs = SystemAuthClock.epochMillis(),
                refreshSkewMs = OAuthSessionManager.DEFAULT_REFRESH_SKEW_MS,
            ) ?: when (
                val tokenResult = accountCoordinator.ensureAccessTokenForAccount(
                    accountStableId = account,
                    requiredScopes = RedditOAuth.coreReadScopes,
                )
            ) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> return@withContext CustomFeedPostReadResult.Failed(
                    kind = CustomFeedPostsFailureKind.SESSION,
                    retryable = when (val endpointResult = tokenResult.result) {
                        is OAuthEndpointResult.HttpFailure -> endpointResult.retryable
                        is OAuthEndpointResult.TransportFailure -> endpointResult.retryable
                        is OAuthEndpointResult.ProtocolFailure -> false
                        is OAuthEndpointResult.Success -> false
                    },
                )
                else -> return@withContext CustomFeedPostReadResult.Failed(CustomFeedPostsFailureKind.SESSION)
            }
            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = client.getJson(
                    request = JsonGetRequest(
                        pathSegments = pathSegments + "hot",
                        queryParameters = buildList {
                            add("raw_json" to "1")
                            add("limit" to "25")
                            after?.trim()?.takeIf(String::isNotEmpty)?.let { cursor ->
                                add("after" to cursor)
                            }
                        },
                    ),
                    deserializer = RedditReadListingDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> result.value
                    .toNetworkPage(SubshellNetworkJson.providerJson, System.currentTimeMillis() / 1_000L)
                    .let { page ->
                        CustomFeedPostReadResult.Loaded(
                            posts = page.items,
                            nextCursor = page.nextCursor,
                        )
                    }
                is NetworkResult.HttpFailure -> CustomFeedPostReadResult.Failed(
                    kind = CustomFeedPostsFailureKind.HTTP,
                    retryable = result.retryable,
                    statusCode = result.statusCode,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.TransportFailure -> CustomFeedPostReadResult.Failed(
                    kind = CustomFeedPostsFailureKind.TRANSPORT,
                    retryable = result.retryable,
                    details = result.failureDetailsOrNull(),
                )
                is NetworkResult.ProtocolFailure -> CustomFeedPostReadResult.Failed(
                    CustomFeedPostsFailureKind.PROTOCOL,
                    details = result.failureDetailsOrNull(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CustomFeedPostReadResult.Failed(
                CustomFeedPostsFailureKind.PROTOCOL,
                details = FailureDetails(retryable = false, localKind = LocalFailureKind.UNKNOWN)
                    .withExceptionClass(error),
            )
        }
    }

    override suspend fun loadFeed(
        accountStableId: String,
        feedPath: String,
    ): CustomFeedSummary? = withContext(ioDispatcher) {
        try {
            val account = accountStableId.trim()
            val pathSegments = customFeedApiPathSegments(feedPath) ?: return@withContext null
            if (
                account.isEmpty() ||
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                !configuration.scopes.containsAll(RedditOAuth.coreReadScopes) ||
                accountCoordinator.activeAuthenticatedAccountId() != account
            ) {
                return@withContext null
            }
            val session = when (
                val tokenResult = accountCoordinator.ensureAccessTokenForAccount(
                    accountStableId = account,
                    requiredScopes = RedditOAuth.coreReadScopes,
                )
            ) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                else -> return@withContext null
            }
            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = client.getJson(
                    request = JsonGetRequest(
                        pathSegments = pathSegments,
                        queryParameters = listOf(
                            "raw_json" to "1",
                            "expand_srs" to "false",
                        ),
                    ),
                    deserializer = RedditCustomFeedEnvelope.serializer(),
                )
            ) {
                is NetworkResult.Success -> result.value.toSummaryOrNull()
                else -> null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun failed(
        kind: CustomFeedFailureKind,
        retryable: Boolean = false,
    ): CustomFeedReadResult.Failed = CustomFeedReadResult.Failed(kind, retryable)
}

internal fun preferredCustomFeedReadSession(
    candidate: StoredOAuthSession?,
    clientMode: OAuthClientMode,
    clientIdFingerprint: String,
    requiredScopes: Set<String>,
    nowEpochMs: Long,
    refreshSkewMs: Long,
): StoredOAuthSession? = candidate?.takeIf { session ->
    session.identity != null &&
        session.clientMode == clientMode &&
        constantTimeEquals(session.clientIdFingerprint, clientIdFingerprint) &&
        session.tokens.grants(requiredScopes) &&
        session.tokens.isUsableAt(nowEpochMs, refreshSkewMs)
}

internal fun RedditCustomFeedEnvelope.toSummaryOrNull(): CustomFeedSummary? {
    val safePath = data.path?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeName = data.displayName?.trim()?.takeIf(String::isNotEmpty)
        ?: data.name?.trim()?.takeIf(String::isNotEmpty)
        ?: return null
    return CustomFeedSummary(
        path = safePath,
        displayName = safeName,
        description = data.descriptionMd.orEmpty().trim(),
        visibility = data.visibility.orEmpty().ifBlank { "private" },
        subredditNames = data.subreddits.orEmpty()
            .mapNotNull { subreddit -> subreddit.name?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy(String::lowercase),
        redditFavorite = data.isFavorited == true,
    )
}

@Serializable
internal data class RedditCustomFeedEnvelope(
    val data: RedditCustomFeedData = RedditCustomFeedData(),
)

@Serializable
internal data class RedditCustomFeedData(
    val path: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("description_md") val descriptionMd: String? = null,
    val visibility: String? = null,
    val subreddits: List<RedditCustomFeedSubreddit>? = null,
    @SerialName("is_favorited") val isFavorited: Boolean? = null,
)

@Serializable
internal data class RedditCustomFeedSubreddit(
    val name: String? = null,
)
