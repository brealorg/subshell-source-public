package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizer
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityWikiFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityWikiLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityWikiResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityWikiTarget
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.toFailureDetails
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditCommunityWikiNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditCommunityWikiSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

// WIKI01A1_AUTHENTICATED_COMMUNITY_WIKI_LOADER_V1
internal class RedditCommunityWikiLoader(
    private val capability: AuthCapabilityAuthorizer,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditCommunityWikiLoader {
    private val loadMutex = Mutex()

    override suspend fun load(
        accountStableId: String,
        target: AuthenticatedRedditCommunityWikiTarget,
    ): AuthenticatedRedditCommunityWikiResult = withContext(ioDispatcher) {
        loadMutex.withLock {
            if (
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                // WIKI01A2_PRESERVED_SCOPE_SUPERSET_RUNTIME_FIX_V1
                !configuration.scopes.containsAll(
                    RedditOAuth.liveCommunityWikiReadScopes,
                )
            ) {
                return@withLock configurationFailure()
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
                return@withLock configurationFailure()
            }

            val session = when (
                val authorization = capability.ensureAuthorized(accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                is AuthSessionResult.AuthorizationDenied -> {
                    if (authorization.error == MISSING_INCREMENTAL_SCOPE) {
                        return@withLock AuthenticatedRedditCommunityWikiResult.AuthorizationRequired
                    }
                    return@withLock failed(
                        AuthenticatedRedditCommunityWikiFailureKind.SESSION,
                    )
                }
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditCommunityWikiResult.Failed(
                        kind = AuthenticatedRedditCommunityWikiFailureKind.SESSION,
                        retryable = when (val endpointFailure = authorization.result) {
                            is OAuthEndpointResult.HttpFailure -> endpointFailure.retryable
                            is OAuthEndpointResult.TransportFailure -> endpointFailure.retryable
                            is OAuthEndpointResult.ProtocolFailure -> false
                            is OAuthEndpointResult.Success -> false
                        },
                        details = authorization.result.toFailureDetails(),
                    )
                }
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                -> return@withLock failed(
                    AuthenticatedRedditCommunityWikiFailureKind.SESSION,
                )
            }

            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.liveCommunityWikiReadScopes)
            ) {
                return@withLock AuthenticatedRedditCommunityWikiResult.AuthorizationRequired
            }

            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider =
                    accountCoordinator.sessionBoundBearerTokenProvider(
                        session.tokens.accessToken,
                    ),
            )

            loadRedditCommunityWiki(
                source = RedditCommunityWikiNetworkDataSource(httpClient),
                target = target,
            )
        }
    }

    private fun configurationFailure(): AuthenticatedRedditCommunityWikiResult.Failed =
        AuthenticatedRedditCommunityWikiResult.Failed(
            kind = AuthenticatedRedditCommunityWikiFailureKind.CLIENT_CONFIGURATION,
            retryable = false,
            details = FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.CONFIGURATION,
            ),
        )

    private fun failed(
        kind: AuthenticatedRedditCommunityWikiFailureKind,
    ): AuthenticatedRedditCommunityWikiResult.Failed =
        AuthenticatedRedditCommunityWikiResult.Failed(
            kind = kind,
            retryable = false,
        )
}

internal suspend fun loadRedditCommunityWiki(
    source: RedditCommunityWikiSource,
    target: AuthenticatedRedditCommunityWikiTarget,
): AuthenticatedRedditCommunityWikiResult =
    if (target.pagePath == null) {
        when (val result = source.index(target.communityName)) {
            is NetworkResult.Success ->
                AuthenticatedRedditCommunityWikiResult.IndexLoaded(result.value)
            else -> result.toCommunityWikiFailure()
        }
    } else {
        when (
            val result = source.page(
                communityName = target.communityName,
                pagePath = target.pagePath,
            )
        ) {
            is NetworkResult.Success ->
                AuthenticatedRedditCommunityWikiResult.PageLoaded(result.value)
            else -> result.toCommunityWikiFailure()
        }
    }

private fun NetworkResult<*>.toCommunityWikiFailure():
    AuthenticatedRedditCommunityWikiResult.Failed = when (this) {
    is NetworkResult.HttpFailure -> {
        val unavailable = statusCode == 403 || statusCode == 404
        AuthenticatedRedditCommunityWikiResult.Failed(
            kind = if (unavailable) {
                AuthenticatedRedditCommunityWikiFailureKind.UNAVAILABLE
            } else {
                AuthenticatedRedditCommunityWikiFailureKind.HTTP
            },
            retryable = retryable,
            details = failureDetailsOrNull(),
            httpStatusCode = statusCode,
        )
    }
    is NetworkResult.TransportFailure ->
        AuthenticatedRedditCommunityWikiResult.Failed(
            kind = AuthenticatedRedditCommunityWikiFailureKind.TRANSPORT,
            retryable = retryable,
            details = failureDetailsOrNull(),
        )
    is NetworkResult.ProtocolFailure ->
        AuthenticatedRedditCommunityWikiResult.Failed(
            kind = AuthenticatedRedditCommunityWikiFailureKind.PROTOCOL,
            retryable = false,
            details = failureDetailsOrNull(),
        )
    is NetworkResult.Success ->
        error("Successful wiki result cannot be mapped as failure")
}

private const val MISSING_INCREMENTAL_SCOPE = "missing_incremental_scope"
