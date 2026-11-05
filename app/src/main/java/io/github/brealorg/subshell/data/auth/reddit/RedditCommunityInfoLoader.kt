package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityInfoFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityInfoLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityInfoResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityInfoSection
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityInfoSectionFailure
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityInfoTarget
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
import io.github.brealorg.subshell.data.network.reddit.RedditCommunityInfoNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditCommunityInfoSource
import io.github.brealorg.subshell.model.CommunityInfoSnapshot
import io.github.brealorg.subshell.model.CommunityModerator
import io.github.brealorg.subshell.model.CommunityRule
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
