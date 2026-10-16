package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoSection
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoSectionFailure
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoTarget
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditCommunityInfoNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditCommunityInfoSource
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.CommunityRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

// COMMUNITYINFO01A2_AUTHENTICATED_COMMUNITY_INFO_LOADER_V1
internal class RedditCommunityInfoLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditCommunityInfoLoader {
    private val loadMutex = Mutex()

    override suspend fun load(
        accountStableId: String,
        target: AuthenticatedRedditCommunityInfoTarget,
    ): AuthenticatedRedditCommunityInfoResult = withContext(ioDispatcher) {
        loadMutex.withLock {
            if (
                configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
                configuration.scopes != RedditOAuth.coreReadScopes
            ) {
                return@withLock failed(
                    AuthenticatedRedditCommunityInfoFailureKind.CLIENT_CONFIGURATION,
                )
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
                return@withLock failed(
                    AuthenticatedRedditCommunityInfoFailureKind.CLIENT_CONFIGURATION,
                )
            }

            val session = when (val tokenResult = accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)) {
                is AuthSessionResult.Authenticated -> tokenResult.session
                is AuthSessionResult.EndpointFailure -> {
                    return@withLock AuthenticatedRedditCommunityInfoResult.Failed(
                        kind = AuthenticatedRedditCommunityInfoFailureKind.SESSION,
                        retryable = when (val endpointFailure = tokenResult.result) {
                            is OAuthEndpointResult.HttpFailure -> endpointFailure.retryable
                            is OAuthEndpointResult.TransportFailure -> endpointFailure.retryable
                            is OAuthEndpointResult.ProtocolFailure -> false
                            is OAuthEndpointResult.Success -> false
                        },
                        details = tokenResult.result.toFailureDetails(),
                    )
                }
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock failed(
                    AuthenticatedRedditCommunityInfoFailureKind.SESSION,
                )
            }

            if (
                session.clientMode != configuration.clientMode ||
                session.clientIdFingerprint != configuration.clientIdFingerprint ||
                !session.tokens.grants(RedditOAuth.coreReadScopes)
            ) {
                return@withLock failed(
                    AuthenticatedRedditCommunityInfoFailureKind.CLIENT_CONFIGURATION,
                )
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
            loadRedditCommunityInfo(
                source = RedditCommunityInfoNetworkDataSource(httpClient),
                target = target,
            )
        }
    }

    private fun failed(
        kind: AuthenticatedRedditCommunityInfoFailureKind,
    ): AuthenticatedRedditCommunityInfoResult.Failed =
        AuthenticatedRedditCommunityInfoResult.Failed(
            kind = kind,
            retryable = false,
            details = if (
                kind == AuthenticatedRedditCommunityInfoFailureKind.CLIENT_CONFIGURATION
            ) {
                FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.CONFIGURATION,
                )
            } else {
                null
            },
        )
}

internal suspend fun loadRedditCommunityInfo(
    source: RedditCommunityInfoSource,
    target: AuthenticatedRedditCommunityInfoTarget,
): AuthenticatedRedditCommunityInfoResult {
    var successfulSections = 0
    var sidebarMarkdown: String? = null
    var freeFormReports: Boolean? = null
    var rules: List<CommunityRule> = emptyList()
    var moderators: List<CommunityModerator> = emptyList()
    val failures = mutableListOf<AuthenticatedRedditCommunityInfoSectionFailure>()

    when (val result = source.sidebarInfo(target.communityName)) {
        is NetworkResult.Success -> {
            successfulSections += 1
            sidebarMarkdown = result.value.markdown
            freeFormReports = result.value.freeFormReports
        }
        else -> failures += result.toSectionFailure(
            AuthenticatedRedditCommunityInfoSection.SIDEBAR,
        )
    }
    when (val result = source.rules(target.communityName)) {
        is NetworkResult.Success -> {
            successfulSections += 1
            rules = result.value
        }
        else -> failures += result.toSectionFailure(
            AuthenticatedRedditCommunityInfoSection.RULES,
        )
    }
    when (val result = source.moderators(target.communityName)) {
        is NetworkResult.Success -> {
            successfulSections += 1
            moderators = result.value
        }
        else -> failures += result.toSectionFailure(
            AuthenticatedRedditCommunityInfoSection.MODERATORS,
        )
    }

    if (successfulSections == 0) {
        val primary = failures.first()
        return AuthenticatedRedditCommunityInfoResult.Failed(
            kind = primary.kind,
            retryable = failures.any { failure -> failure.retryable },
            details = primary.details,
            httpStatusCode = primary.httpStatusCode,
        )
    }

    return AuthenticatedRedditCommunityInfoResult.Loaded(
        snapshot = CommunityInfoSnapshot(
            communityName = target.communityName,
            sidebarMarkdown = sidebarMarkdown,
            freeFormReports = freeFormReports,
            rules = rules,
            moderators = moderators,
        ),
        sectionFailures = failures,
    )
}

private fun NetworkResult<*>.toSectionFailure(
    section: AuthenticatedRedditCommunityInfoSection,
): AuthenticatedRedditCommunityInfoSectionFailure = when (this) {
    is NetworkResult.HttpFailure -> AuthenticatedRedditCommunityInfoSectionFailure(
        section = section,
        kind = AuthenticatedRedditCommunityInfoFailureKind.HTTP,
        retryable = retryable,
        details = failureDetailsOrNull(),
        httpStatusCode = statusCode,
    )
    is NetworkResult.TransportFailure -> AuthenticatedRedditCommunityInfoSectionFailure(
        section = section,
        kind = AuthenticatedRedditCommunityInfoFailureKind.TRANSPORT,
        retryable = retryable,
        details = failureDetailsOrNull(),
    )
    is NetworkResult.ProtocolFailure -> AuthenticatedRedditCommunityInfoSectionFailure(
        section = section,
        kind = AuthenticatedRedditCommunityInfoFailureKind.PROTOCOL,
        retryable = false,
        details = failureDetailsOrNull(),
    )
    is NetworkResult.Success -> error("Successful community info result cannot be a failure")
}
