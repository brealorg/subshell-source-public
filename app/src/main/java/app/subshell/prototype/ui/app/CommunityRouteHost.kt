package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.ui.ExpandedContextDiscoveryMaxWidth
import app.subshell.prototype.ui.ExpandedContextReadingMaxWidth
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.ExpandedContextSurface
import app.subshell.prototype.ui.app.ExpandedListDetailSurface
import app.subshell.prototype.ui.screens.CommunitiesScreen
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.useExpandedSubscriptionsCommunityListDetail

/**
 * The Community route branch of SubshellApp's route dispatch: the community
 * screen, or the Communities two-pane workspace when entered from the
 * Communities destination in an expanded window. Moved verbatim; the
 * SubshellApp vars it writes arrive as MutableState.
 */
@Composable
internal fun CommunityRouteHost(
    customFeeds: CustomFeedsEntryPoints,
    redditFeed: FeedEntryPoints,
    redditCommunity: CommunityEntryPoints,
    redditModeration: ModerationQueueEntryPoints,
    state: SubshellState,
    route: SubshellRoute.Community,
    padding: PaddingValues,
    expandedWindowLayout: Boolean,
    screenStateHolder: SaveableStateHolder,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
    activeCustomFeedPathState: MutableState<String?>,
    customFeedCreateRequestedState: MutableState<Boolean>,
    customFeedFabVisibleState: MutableState<Boolean>,
) {
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    var activeCustomFeedPath by activeCustomFeedPathState
    var customFeedCreateRequested by customFeedCreateRequestedState
    var customFeedFabVisible by customFeedFabVisibleState
    val redditModerationState = redditModeration.redditModerationState
    val onLoadRedditModQueue = redditModeration.onLoadRedditModQueue
    val onAppendRedditModQueue = redditModeration.onAppendRedditModQueue
    val moderationActionState = redditModeration.moderationActionState
    val onModerateRedditQueueItem = redditModeration.onModerateRedditQueueItem
    val onRetryRedditModerationAction = redditModeration.onRetryRedditModerationAction
    val onAuthorizeRedditModerationAction = redditModeration.onAuthorizeRedditModerationAction
    val removalReasonState = redditModeration.removalReasonState
    val onLoadRedditRemovalReasons = redditModeration.onLoadRedditRemovalReasons
    val onAuthorizeRedditRemovalReasons = redditModeration.onAuthorizeRedditRemovalReasons
    val customFeedsState = customFeeds.customFeedsState
    val customFeedPostsState = customFeeds.customFeedPostsState
    val onRequestHiddenSubscriptionsUnlock = customFeeds.onRequestHiddenSubscriptionsUnlock
    val onLoadCustomFeeds = customFeeds.onLoadCustomFeeds
    val onLoadCustomFeedPosts = customFeeds.onLoadCustomFeedPosts
    val onAppendCustomFeedPosts = customFeeds.onAppendCustomFeedPosts
    val onCreateCustomFeed = customFeeds.onCreateCustomFeed
    val onUpdateCustomFeed = customFeeds.onUpdateCustomFeed
    val onDeleteCustomFeed = customFeeds.onDeleteCustomFeed
    val onResetCustomFeedCreate = customFeeds.onResetCustomFeedCreate
    val onResetCustomFeedManage = customFeeds.onResetCustomFeedManage
    val onRefreshRedditCommunityFeed = redditCommunity.onRefreshRedditCommunityFeed
    val onAppendRedditCommunityFeed = redditCommunity.onAppendRedditCommunityFeed
    val onViewQuarantinedCommunity = redditCommunity.onViewQuarantinedCommunity
    val redditCommunityWikiState = redditCommunity.redditCommunityWikiState
    val onLoadRedditCommunityWikiIndex = redditCommunity.onLoadRedditCommunityWikiIndex
    val onLoadRedditCommunityWikiPage = redditCommunity.onLoadRedditCommunityWikiPage
    val onAuthorizeRedditCommunityWiki = redditCommunity.onAuthorizeRedditCommunityWiki
    val redditCommunityInfoState = redditCommunity.redditCommunityInfoState
    val onLoadRedditCommunityInfo = redditCommunity.onLoadRedditCommunityInfo
    val communityMembershipState = redditCommunity.communityMembershipState
    val onChangeCommunityMembership = redditCommunity.onChangeCommunityMembership
    val redditCommunityDiscoveryState = redditCommunity.redditCommunityDiscoveryState
    val onLoadRedditCommunityDiscovery = redditCommunity.onLoadRedditCommunityDiscovery
    val redditFeedState = redditFeed.redditFeedState
    val onRefreshRedditFeed = redditFeed.onRefreshRedditFeed
    val onAppendRedditFeed = redditFeed.onAppendRedditFeed
    val redditSubscriptionsState = redditFeed.redditSubscriptionsState
    val onLoadRedditSubscriptions = redditFeed.onLoadRedditSubscriptions
    val subscriptionsCommunityListDetail =
        useExpandedSubscriptionsCommunityListDetail(
            expandedWindow = expandedWindowLayout,
            supportedOrigin =
                route.destination == MainDestination.SUBS,
            tabletLayoutMode = state.tabletLayoutMode,
        )

    screenStateHolder.SaveableStateProvider(
        if (subscriptionsCommunityListDetail) {
            "subscriptions-community-workspace"
        } else {
            "community-${route.communityName}"
        },
    ) {
        if (subscriptionsCommunityListDetail) {
            // NEXT01A5_SUBSCRIPTIONS_COMMUNITY_ROUTE_TWO_PANE_V1
            ExpandedListDetailSurface(
                scaffoldPadding = padding,
                listTitle = "Communities",
                listSubtitle = null,
                listContent = { listModifier ->
                    CommunitiesScreen(
                        modifier = listModifier,
                        state = state,
                        redditSubscriptionsState =
                            redditSubscriptionsState,
                        onLoadRedditSubscriptions =
                            onLoadRedditSubscriptions,
                        redditCommunityDiscoveryState =
                            redditCommunityDiscoveryState,
                        onLoadRedditCommunityDiscovery =
                            onLoadRedditCommunityDiscovery,
                        communityMembershipState =
                            communityMembershipState,
                        onChangeCommunityMembership =
                            onChangeCommunityMembership,
                        customFeedsState = customFeedsState,
                        onLoadCustomFeeds = onLoadCustomFeeds,
                        onRequestHiddenSubscriptionsUnlock =
                            onRequestHiddenSubscriptionsUnlock,
                        onOpenCustomFeedAtHome = { feedPath ->
                            pendingAuthenticatedLandingAccountId = null
                            activeCustomFeedPath = feedPath
                            state.navigate(MainDestination.HOME)
                        },
                        customFeedPostsState = customFeedPostsState,
                        onLoadCustomFeedPosts = onLoadCustomFeedPosts,
                        onAppendCustomFeedPosts = onAppendCustomFeedPosts,
                        customFeedCreateRequested = customFeedCreateRequested,
                        onCustomFeedCreateRequestConsumed = {
                            customFeedCreateRequested = false
                        },
                        onCustomFeedFabVisibilityChanged = { visible ->
                            customFeedFabVisible = visible
                            if (!visible) customFeedCreateRequested = false
                        },
                        onCreateCustomFeed = onCreateCustomFeed,
                        onUpdateCustomFeed = onUpdateCustomFeed,
                        onDeleteCustomFeed = onDeleteCustomFeed,
                        onResetCustomFeedCreate = onResetCustomFeedCreate,
                        onResetCustomFeedManage = onResetCustomFeedManage,
                        // NEXT01A5_SPLIT_SUBSCRIPTIONS_CONTENT_PADDING_V1
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 8.dp,
                        ),
                    )
                },
                detailContent = { detailModifier ->
                    val contextModifier = detailModifier
                    androidx.compose.runtime.key(
                        route.communityName,
                    ) {
                        CommunityScreen(
                        communityName = route.communityName,
                        state = state,
                        redditFeedState = redditFeedState,
                        onRefreshRedditFeed = onRefreshRedditCommunityFeed,
                        onAppendRedditFeed = onAppendRedditCommunityFeed,
                        onViewQuarantinedCommunity = onViewQuarantinedCommunity,
                        redditSubscriptionsState =
                            redditSubscriptionsState,
                        onLoadRedditSubscriptions =
                            onLoadRedditSubscriptions,
                        redditCommunityInfoState =
                            redditCommunityInfoState,
                        onLoadRedditCommunityInfo =
                            onLoadRedditCommunityInfo,
                        redditPinnedPostsState = redditCommunity.redditPinnedPostsState,
                        onLoadRedditPinnedPosts = redditCommunity.onLoadRedditPinnedPosts,
                        redditCommunityWikiState =
                            redditCommunityWikiState,
                        onLoadRedditCommunityWikiIndex =
                            onLoadRedditCommunityWikiIndex,
                        onLoadRedditCommunityWikiPage =
                            onLoadRedditCommunityWikiPage,
                        onAuthorizeRedditCommunityWiki =
                            onAuthorizeRedditCommunityWiki,
                        redditModerationState =
                            redditModerationState,
                        onLoadRedditModQueue =
                            onLoadRedditModQueue,
                        onAppendRedditModQueue =
                            onAppendRedditModQueue,
                        moderationActionState = moderationActionState,
                        onModerateRedditQueueItem = onModerateRedditQueueItem,
                        onRetryRedditModerationAction = onRetryRedditModerationAction,
                        onAuthorizeRedditModerationAction =
                            onAuthorizeRedditModerationAction,
                        removalReasonState = removalReasonState,
                        onLoadRedditRemovalReasons = onLoadRedditRemovalReasons,
                        onAuthorizeRedditRemovalReasons =
                            onAuthorizeRedditRemovalReasons,
                        communityMembershipState =
                            communityMembershipState,
                        onChangeCommunityMembership =
                            onChangeCommunityMembership,
                        onBack = state::closeSubreddit,
                        // NEXT01A5_SPLIT_COMMUNITY_NO_VISUAL_BACK_V1
                        showBackButton = false,
                        modifier = contextModifier,
                        )
                    }
                },
            )
        } else {
            ExpandedContextSurface(
                scaffoldPadding = padding,
                expanded = expandedWindowLayout,
                // M3224_READING_WIDTH_V1: a community is a feed, read at the reading width.
                maxWidth = ExpandedContextReadingMaxWidth,
            ) { contextModifier ->
                CommunityScreen(
                communityName = route.communityName,
                state = state,
                redditFeedState = redditFeedState,
                onRefreshRedditFeed = onRefreshRedditCommunityFeed,
                onAppendRedditFeed = onAppendRedditCommunityFeed,
                onViewQuarantinedCommunity = onViewQuarantinedCommunity,
                redditSubscriptionsState =
                    redditSubscriptionsState,
                onLoadRedditSubscriptions =
                    onLoadRedditSubscriptions,
                redditCommunityInfoState =
                    redditCommunityInfoState,
                onLoadRedditCommunityInfo =
                    onLoadRedditCommunityInfo,
                redditPinnedPostsState = redditCommunity.redditPinnedPostsState,
                onLoadRedditPinnedPosts = redditCommunity.onLoadRedditPinnedPosts,
                redditCommunityWikiState =
                    redditCommunityWikiState,
                onLoadRedditCommunityWikiIndex =
                    onLoadRedditCommunityWikiIndex,
                onLoadRedditCommunityWikiPage =
                    onLoadRedditCommunityWikiPage,
                onAuthorizeRedditCommunityWiki =
                    onAuthorizeRedditCommunityWiki,
                redditModerationState =
                    redditModerationState,
                onLoadRedditModQueue =
                    onLoadRedditModQueue,
                onAppendRedditModQueue =
                    onAppendRedditModQueue,
                moderationActionState = moderationActionState,
                onModerateRedditQueueItem = onModerateRedditQueueItem,
                onRetryRedditModerationAction = onRetryRedditModerationAction,
                onAuthorizeRedditModerationAction =
                    onAuthorizeRedditModerationAction,
                removalReasonState = removalReasonState,
                onLoadRedditRemovalReasons = onLoadRedditRemovalReasons,
                onAuthorizeRedditRemovalReasons =
                    onAuthorizeRedditRemovalReasons,
                communityMembershipState =
                    communityMembershipState,
                onChangeCommunityMembership =
                    onChangeCommunityMembership,
                onBack = state::closeSubreddit,
                modifier = contextModifier,
                )
            }
        }
    }
}
