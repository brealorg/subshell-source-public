package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthCommentWriteTokenSource
import io.github.brealorg.subshell.data.auth.AuthCustomFeedWriteCapability
import io.github.brealorg.subshell.data.auth.AuthInboxReadCapability
import io.github.brealorg.subshell.data.auth.AuthInboxReadMutationWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditAccountHistoryUpdater
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditUserFlairManager
import io.github.brealorg.subshell.data.auth.ConfiguredCustomFeedCreator
import io.github.brealorg.subshell.data.auth.ConfiguredCustomFeedReader
import io.github.brealorg.subshell.data.auth.CustomFeedCreator
import io.github.brealorg.subshell.data.auth.CustomFeedReader
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommunityMembershipWriter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedContentSafetyWriter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModerationWriter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditRemovalReasonLoader
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditCommunityMembershipWriter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditContentSafetyWriter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditFavoriteCommunityWriter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditModerationWriter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditRemovalReasonLoader
import io.github.brealorg.subshell.data.auth.reddit.RedditFavoriteCommunityWriter
import io.github.brealorg.subshell.data.auth.reddit.RedditInboxReadMutationWriter
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuthRuntimeSelection
import io.github.brealorg.subshell.data.auth.reddit.RedditUserFlairManager
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.SubshellNetworkTransport
import okhttp3.Call

/**
 * The account and community write part of the auth runtime: custom feed
 * reader and creator, the inbox read-mutation writer, the moderation
 * action writer and removal reasons, community membership, content safety
 * (with the shared hidden-history updater), online visibility and user
 * flair. Each is null unless its selection and capability exist.
 * Property names are the AuthApplicationRuntime fields they fill.
 */
internal class AccountWriteRuntimeParts(
    val customFeedReader: CustomFeedReader?,
    val customFeedCreator: CustomFeedCreator?,
    val inboxReadMutationWriter: AuthInboxReadMutationWriter?,
    val moderationActionWriter: AuthenticatedRedditModerationWriter?,
    val removalReasonLoader: AuthenticatedRedditRemovalReasonLoader?,
    val communityMembershipWriter: AuthenticatedCommunityMembershipWriter?,
    val redditFavoriteWriter: RedditFavoriteCommunityWriter?,
    val contentSafetyWriter: AuthenticatedContentSafetyWriter?,
    val onlineVisibilityService: io.github.brealorg.subshell.data.account.RedditOnlineVisibilityService?,
    val communityAccessService: io.github.brealorg.subshell.data.account.RedditCommunityAccessService?,
    val redditUserFlairManager: AuthenticatedRedditUserFlairManager?,
)

/**
 * Builds the account and community write parts. The constructor arguments
 * moved out of AuthApplicationRuntimeFactory.create()'s
 * AuthApplicationRuntime(...) call verbatim; parameters carry the names
 * those expressions read.
 */
internal fun accountWriteRuntimeParts(
    selected: RedditOAuthRuntimeSelection,
    configuration: OAuthProviderConfiguration,
    composerFlairSelection: RedditOAuthRuntimeSelection?,
    postModerationSelection: RedditOAuthRuntimeSelection?,
    removalReasonSelection: RedditOAuthRuntimeSelection?,
    contentSafetySelection: RedditOAuthRuntimeSelection?,
    customFeedWriteSelection: RedditOAuthRuntimeSelection?,
    inboxReadSelection: RedditOAuthRuntimeSelection?,
    callFactory: Call.Factory,
    liveCacheStore: RoomSubshellCacheStore,
    coordinator: AuthAccountSessionCoordinator,
    inboxReadCapability: AuthInboxReadCapability?,
    contentSafetyCapability: AuthCommentWriteCapability?,
    composerFlairCapability: AuthCommentWriteTokenSource?,
    moderationActionCapability: AuthCommentWriteCapability?,
    removalReasonCapability: AuthCommentWriteCapability?,
    communityMembershipCapability: AuthCustomFeedWriteCapability?,
    redditFavoriteCapability: AuthCustomFeedWriteCapability?,
    accountHistoryUpdater: AuthenticatedRedditAccountHistoryUpdater?,
): AccountWriteRuntimeParts = AccountWriteRuntimeParts(
        customFeedReader = ConfiguredCustomFeedReader(
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
            configuration = configuration,
            accountCoordinator = coordinator,
            // ISSUE32_CUSTOM_FEED_POSTS_ARE_STORED_V1
            postStore = liveCacheStore,
        ),
        customFeedCreator = customFeedWriteSelection?.let { writeSelection ->
            ConfiguredCustomFeedCreator(
                callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = writeSelection.userAgent,
                accountCoordinator = coordinator,
                requiredScopes = writeSelection.configuration.scopes,
            )
        },
        inboxReadMutationWriter = if (
            inboxReadSelection != null &&
            inboxReadSelection.configuration.clientMode == configuration.clientMode
        ) {
            RedditInboxReadMutationWriter(
                callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = inboxReadSelection.userAgent,
                cacheStore = liveCacheStore,
                capability = requireNotNull(inboxReadCapability),
            )
        } else {
            null
        },
        moderationActionWriter =
            postModerationSelection?.let { moderationSelection ->
                moderationActionCapability?.let { capability ->
                    ConfiguredRedditModerationWriter(
                        capability = capability,
                        callFactory =
                            SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = moderationSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        removalReasonLoader = removalReasonSelection?.let { reasonSelection ->
            removalReasonCapability?.let { capability ->
                ConfiguredRedditRemovalReasonLoader(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.sharedCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = reasonSelection.userAgent,
                )
            }
        },
        communityMembershipWriter =
            customFeedWriteSelection?.let { writeSelection ->
                communityMembershipCapability?.let { capability ->
                    ConfiguredRedditCommunityMembershipWriter(
                        capability = capability,
                        callFactory =
                            SubshellNetworkTransport
                                .nonRetryingWriteCallFactory,
                        endpoint =
                            NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = writeSelection.userAgent,
                    )
                }
            },
        // REDDITFAV02_FAVORITE_WRITE_V1: same scope (subscribe) as membership,
        // under its own owner (REDDITFAV03), on the non-retrying write client.
        redditFavoriteWriter =
            customFeedWriteSelection?.let { writeSelection ->
                redditFavoriteCapability?.let { capability ->
                    ConfiguredRedditFavoriteCommunityWriter(
                        capability = capability,
                        callFactory =
                            SubshellNetworkTransport
                                .nonRetryingWriteCallFactory,
                        endpoint =
                            NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = writeSelection.userAgent,
                    )
                }
            },
        contentSafetyWriter =
            contentSafetySelection?.let { safetySelection ->
                contentSafetyCapability?.let { capability ->
                    ConfiguredRedditContentSafetyWriter(
                        capability = capability,
                        callFactory =
                            SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = safetySelection.userAgent,
                        hiddenHistoryUpdater = accountHistoryUpdater,
                    )
                }
            },
        onlineVisibilityService = io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditOnlineVisibilityService(
            configuration = configuration,
            activeAccountId = coordinator::activeAuthenticatedAccountId,
            ensureSession = coordinator::ensureAccessTokenForAccount,
            storedSessionGrants = coordinator::storedSessionGrants,
            networkForSession = { session ->
                io.github.brealorg.subshell.data.network.reddit.RedditOnlineVisibilityNetworkDataSource(
                    client = io.github.brealorg.subshell.data.network.SubshellHttpClient(
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        json = SubshellNetworkJson.providerJson,
                        userAgent = selected.userAgent,
                        bearerTokenProvider = coordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
                        maxJsonBodyBytes = 64L * 1024L,
                    ),
                )
            },
        ),
        // ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1
        communityAccessService = io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditCommunityAccessService(
            configuration = configuration,
            activeAccountId = coordinator::activeAuthenticatedAccountId,
            ensureSession = coordinator::ensureAccessTokenForAccount,
            storedSessionGrants = coordinator::storedSessionGrants,
            networkForSession = { session ->
                io.github.brealorg.subshell.data.network.reddit.RedditNsfwPreferencesNetworkDataSource(
                    client = io.github.brealorg.subshell.data.network.SubshellHttpClient(
                        // A read: the retrying transport (ISSUE211_ACCOUNT_PREFERENCE_READ_RETRIES_V1).
                        callFactory = SubshellNetworkTransport.sharedCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        json = SubshellNetworkJson.providerJson,
                        userAgent = selected.userAgent,
                        bearerTokenProvider = coordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
                        maxJsonBodyBytes = 64L * 1024L,
                    ),
                )
            },
            writeClientForSession = { session ->
                io.github.brealorg.subshell.data.network.SubshellHttpClient(
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    json = SubshellNetworkJson.providerJson,
                    userAgent = selected.userAgent,
                    bearerTokenProvider = coordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
                    maxJsonBodyBytes = 64L * 1024L,
                )
            },
            // REDDITCHECK01_CHECK_REDDIT_ACCESS_V1: no retries, so each answer is Reddit's first.
            probeClientForSession = { session ->
                io.github.brealorg.subshell.data.network.SubshellHttpClient(
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    json = SubshellNetworkJson.providerJson,
                    userAgent = selected.userAgent,
                    bearerTokenProvider = coordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
                )
            },
        ),
        redditUserFlairManager =
            composerFlairSelection?.let { flairSelection ->
                composerFlairCapability?.let { capability ->
                    RedditUserFlairManager(
                        capability = capability,
                        readCallFactory = SubshellNetworkTransport.sharedCallFactory,
                        writeCallFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = flairSelection.userAgent,
                        accountCoordinator = coordinator,
                        activeAccountStableId = {
                            coordinator.activeAuthenticatedAccountId()
                        },
                    )
                }
            },
)
