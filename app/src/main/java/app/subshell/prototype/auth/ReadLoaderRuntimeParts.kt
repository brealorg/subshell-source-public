package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.reddit.RedditCommunityNewPostsLoader
import app.subshell.prototype.data.auth.reddit.RedditCommunityPinnedPostsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityNewPostsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityPinnedPostsLoader
import android.content.Context
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.account.SharedPreferencesRedditFavoriteCommunityStore
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsLoader
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.reddit.RedditCommentLoader
import app.subshell.prototype.data.auth.reddit.RedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.reddit.RedditCommunityInfoLoader
import app.subshell.prototype.data.auth.reddit.RedditCommunityWikiLoader
import app.subshell.prototype.data.auth.reddit.RedditFeedUpdater
import app.subshell.prototype.data.auth.reddit.RedditInboxLoader
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthRuntimeSelection
import app.subshell.prototype.data.auth.reddit.RedditSavedHistoryLoader
import app.subshell.prototype.data.auth.reddit.RedditSubscriptionsLoader
import app.subshell.prototype.data.cache.RedditSavedHistoryCacheStore
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.network.NetworkEndpoint
import okhttp3.Call

/**
 * The read-loader part of the auth runtime: the feed updater, inbox,
 * saved history, comment, subscriptions, community discovery, wiki and
 * community info loaders. Cache-backed loaders exist only for the
 * RedReader-compatibility client (the compatibility cache store).
 * Property names are the AuthApplicationRuntime fields they fill.
 */
internal class ReadLoaderRuntimeParts(
    val redditFeedUpdater: AuthenticatedRedditFeedUpdater?,
    val redditInboxLoader: AuthenticatedRedditInboxLoader?,
    val redditSavedHistoryLoader: AuthenticatedRedditSavedHistoryLoader?,
    val redditCommentLoader: AuthenticatedRedditCommentLoader?,
    val redditSubscriptionsLoader: AuthenticatedRedditSubscriptionsLoader?,
    val redditCommunityDiscoveryLoader: AuthenticatedRedditCommunityDiscoveryLoader?,
    val redditCommunityWikiLoader: AuthenticatedRedditCommunityWikiLoader?,
    val redditCommunityInfoLoader: AuthenticatedRedditCommunityInfoLoader?,
    // ISSUE230_PINNED_POSTS_FIRST_V1
    val redditCommunityPinnedPostsLoader: AuthenticatedRedditCommunityPinnedPostsLoader?,
    val redditCommunityNewPostsLoader: AuthenticatedRedditCommunityNewPostsLoader?,
)

/**
 * Builds the read loaders. The constructor arguments moved out of
 * AuthApplicationRuntimeFactory.create()'s AuthApplicationRuntime(...) call
 * verbatim; parameters carry the names those expressions read.
 */
internal fun readLoaderRuntimeParts(
    appContext: Context,
    selected: RedditOAuthRuntimeSelection,
    configuration: OAuthProviderConfiguration,
    inboxReadSelection: RedditOAuthRuntimeSelection?,
    profileHistorySelection: RedditOAuthRuntimeSelection?,
    communityWikiReadSelection: RedditOAuthRuntimeSelection?,
    callFactory: Call.Factory,
    liveCacheStore: RoomSubshellCacheStore,
    compatibilityCacheStore: RoomSubshellCacheStore?,
    coordinator: AuthAccountSessionCoordinator,
    profileHistoryCapability: AuthProfileHistoryCapability?,
    communityWikiReadAuthorizer: AuthContextualCapabilityAuthorizer?,
    inboxReadCapability: AuthInboxReadCapability?,
): ReadLoaderRuntimeParts = ReadLoaderRuntimeParts(
        redditFeedUpdater = compatibilityCacheStore?.let { cacheStore ->
            RedditFeedUpdater(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = selected.userAgent,
                configuration = configuration,
                cacheStore = cacheStore,
                accountCoordinator = coordinator,
            )
        },
        redditInboxLoader = if (
            inboxReadSelection != null &&
            inboxReadSelection.configuration.clientMode == configuration.clientMode
        ) {
            val capability = requireNotNull(inboxReadCapability)
            RedditInboxLoader(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = inboxReadSelection.userAgent,
                cacheStore = liveCacheStore,
                capability = capability,
            )
        } else {
            null
        },
        redditSavedHistoryLoader =
            if (
                profileHistorySelection != null &&
                profileHistorySelection.configuration.clientMode ==
                    configuration.clientMode
            ) {
                RedditSavedHistoryLoader(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = profileHistorySelection.userAgent,
                    cacheStore = RedditSavedHistoryCacheStore(
                        directory = java.io.File(
                            appContext.noBackupFilesDir,
                            "reddit-saved-history-v1",
                        ),
                    ),
                    capability = requireNotNull(profileHistoryCapability),
                )
            } else {
                null
            },
        redditCommentLoader = compatibilityCacheStore?.let { cacheStore ->
            RedditCommentLoader(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = selected.userAgent,
                configuration = configuration,
                cacheStore = cacheStore,
                accountCoordinator = coordinator,
            )
        },
        redditSubscriptionsLoader = compatibilityCacheStore?.let { cacheStore ->
            RedditSubscriptionsLoader(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = selected.userAgent,
                configuration = configuration,
                cacheStore = cacheStore,
                accountCoordinator = coordinator,
                favoriteStore = SharedPreferencesRedditFavoriteCommunityStore(appContext),
            )
        },
        redditCommunityDiscoveryLoader = compatibilityCacheStore?.let { cacheStore ->
            RedditCommunityDiscoveryLoader(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = selected.userAgent,
                configuration = configuration,
                cacheStore = cacheStore,
                accountCoordinator = coordinator,
            )
        },
        redditCommunityWikiLoader =
            communityWikiReadSelection?.let { wikiSelection ->
                communityWikiReadAuthorizer?.let { authorizer ->
                    RedditCommunityWikiLoader(
                        capability = authorizer,
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = wikiSelection.userAgent,
                        configuration = wikiSelection.configuration,
                        accountCoordinator = coordinator,
                    )
                }
            },
        redditCommunityInfoLoader = RedditCommunityInfoLoader(
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
            configuration = configuration,
            accountCoordinator = coordinator,
        ),
        redditCommunityPinnedPostsLoader = RedditCommunityPinnedPostsLoader(
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
            configuration = configuration,
            accountCoordinator = coordinator,
        ),
        // ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1
        redditCommunityNewPostsLoader = RedditCommunityNewPostsLoader(
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
            configuration = configuration,
            accountCoordinator = coordinator,
        ),
)
