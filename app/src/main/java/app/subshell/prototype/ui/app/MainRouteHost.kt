package app.subshell.prototype.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.inbox.ModmailNotificationTarget
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.FeedSource
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.YOU_BLOCKED_WORKSPACE_PLACEHOLDER
import app.subshell.prototype.ui.YOU_DRAFTS_WORKSPACE_PLACEHOLDER
import app.subshell.prototype.ui.app.ExpandedListDetailSurface
import app.subshell.prototype.ui.app.mainContentPadding
import app.subshell.prototype.ui.screens.CommunitiesScreen
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.screens.HomeScreen
import app.subshell.prototype.ui.screens.InboxContextualSelection
import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import app.subshell.prototype.ui.screens.InboxScreen
import app.subshell.prototype.ui.useExpandedInboxActivityWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedInboxMessagesWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedInboxModQueueWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedSubscriptionsCommunityListDetail
import app.subshell.prototype.ui.useExpandedYouBlockedWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedYouContentWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedYouDraftsWorkspaceListDetail
import app.subshell.prototype.ui.visibleChromePadding
import app.subshell.prototype.ui.youContentWorkspaceLabel
import app.subshell.prototype.ui.youContentWorkspacePlaceholder

/**
 * The Main route branch of SubshellApp's route dispatch: the workspace of
 * the current main destination (Home feed, Communities, Inbox with its
 * two-pane variants, You content), bounded and padded for the chrome that
 * SubshellApp's scaffold owns. Moved verbatim; the chrome facts arrive as
 * values and the SubshellApp vars it writes as MutableState.
 */
@Composable
internal fun MainRouteHost(
    customFeeds: CustomFeedsEntryPoints,
    redditFeed: FeedEntryPoints,
    redditCommunity: CommunityEntryPoints,
    redditModeration: ModerationQueueEntryPoints,
    redditModmail: ModmailEntryPoints,
    redditInbox: InboxEntryPoints,
    state: SubshellState,
    route: SubshellRoute.Main,
    padding: PaddingValues,
    screenStateHolder: SaveableStateHolder,
    density: Density,
    layoutDirection: LayoutDirection,
    navInteractionSources: Map<MainDestination, MutableInteractionSource>,
    navigationBarInsetHeight: Dp,
    expandedWindowLayout: Boolean,
    modmailWorkspaceListDetail: Boolean,
    expandedMainLayout: Boolean,
    chromeScrollConnection: NestedScrollConnection,
    topBarHiddenFraction: Float,
    bottomBarHiddenFraction: Float,
    stableMainContentBottomPadding: Dp,
    mainYouProfileContent: @Composable (String, Modifier, PaddingValues, ((app.subshell.prototype.model.Post, String?) -> Unit)?, ((Int) -> Unit)?, ((String) -> Unit)?) -> Unit,
    inboxUnreadCount: Int,
    activeCustomFeed: CustomFeedSummary?,
    authenticatedLandingPending: Boolean,
    authenticatedHomeWithoutSubscriptions: Boolean,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
    inboxContextualSelectionState: MutableState<InboxContextualSelection?>,
    inboxPrimaryTabState: MutableState<InboxPrimaryTab>,
    inboxModSectionState: MutableState<InboxModSection>,
    suppressYouClickState: MutableState<Boolean>,
    accountSwitcherVisibleState: MutableState<Boolean>,
    customFeedFabVisibleState: MutableState<Boolean>,
    customFeedCreateRequestedState: MutableState<Boolean>,
    activeCustomFeedPathState: MutableState<String?>,
) {
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    var inboxContextualSelection by inboxContextualSelectionState
    var inboxPrimaryTab by inboxPrimaryTabState
    var inboxModSection by inboxModSectionState
    var suppressYouClick by suppressYouClickState
    var accountSwitcherVisible by accountSwitcherVisibleState
    var customFeedFabVisible by customFeedFabVisibleState
    var customFeedCreateRequested by customFeedCreateRequestedState
    var activeCustomFeedPath by activeCustomFeedPathState
    val redditModmailState = redditModmail.redditModmailState
    val onLoadRedditModmail = redditModmail.onLoadRedditModmail
    val onAppendRedditModmail = redditModmail.onAppendRedditModmail
    val onSelectRedditModmailListMode = redditModmail.onSelectRedditModmailListMode
    val onOpenRedditModmailConversation = redditModmail.onOpenRedditModmailConversation
    val onSendRedditModmailMessage = redditModmail.onSendRedditModmailMessage
    val onSetRedditModmailArchived = redditModmail.onSetRedditModmailArchived
    val onSetRedditModmailUnread = redditModmail.onSetRedditModmailUnread
    val onVerifyRedditModmailReadState = redditModmail.onVerifyRedditModmailReadState
    val onApplyRedditModmailTriage = redditModmail.onApplyRedditModmailTriage
    val onCloseRedditModmailConversation = redditModmail.onCloseRedditModmailConversation
    val onAuthorizeRedditModmail = redditModmail.onAuthorizeRedditModmail
    val modmailNotificationTarget = redditModmail.modmailNotificationTarget
    val onModmailNotificationConsumed = redditModmail.onModmailNotificationConsumed
    val redditModerationState = redditModeration.redditModerationState
    val onLoadRedditModQueue = redditModeration.onLoadRedditModQueue
    val onAppendRedditModQueue = redditModeration.onAppendRedditModQueue
    val onResolveRedditModerationTarget = redditModeration.onResolveRedditModerationTarget
    val moderationActionState = redditModeration.moderationActionState
    val onModerateRedditQueueItem = redditModeration.onModerateRedditQueueItem
    val onRetryRedditModerationAction = redditModeration.onRetryRedditModerationAction
    val onAuthorizeRedditModerationAction = redditModeration.onAuthorizeRedditModerationAction
    val removalReasonState = redditModeration.removalReasonState
    val onLoadRedditRemovalReasons = redditModeration.onLoadRedditRemovalReasons
    val onAuthorizeRedditRemovalReasons = redditModeration.onAuthorizeRedditRemovalReasons
    val redditInboxState = redditInbox.redditInboxState
    val onLoadRedditInbox = redditInbox.onLoadRedditInbox
    val onAppendRedditInbox = redditInbox.onAppendRedditInbox
    val onMarkRedditInboxRead = redditInbox.onMarkRedditInboxRead
    val onResolveRedditInboxTarget = redditInbox.onResolveRedditInboxTarget
    val onAuthorizeRedditInbox = redditInbox.onAuthorizeRedditInbox
    val onCancelRedditInboxAuthorization = redditInbox.onCancelRedditInboxAuthorization
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
    val communityMembershipState = redditCommunity.communityMembershipState
    val onChangeCommunityMembership = redditCommunity.onChangeCommunityMembership
    val redditCommunityDiscoveryState = redditCommunity.redditCommunityDiscoveryState
    val onLoadRedditCommunityDiscovery = redditCommunity.onLoadRedditCommunityDiscovery
    val redditFeedState = redditFeed.redditFeedState
    val onRefreshRedditFeed = redditFeed.onRefreshRedditFeed
    val onAppendRedditFeed = redditFeed.onAppendRedditFeed
    val redditSubscriptionsState = redditFeed.redditSubscriptionsState
    val onLoadRedditSubscriptions = redditFeed.onLoadRedditSubscriptions
Box(
    modifier = Modifier
        .fillMaxSize()
        .nestedScroll(chromeScrollConnection),
) {
    // A11Y01A2_EXPANDED_MAIN_NAVIGATION_RAIL_V1: the rail moved beside the whole
    // app (SubshellApp, M3224_NAVIGATION_RAIL_FROM_MEDIUM_WIDTH_V1).

    AnimatedContent(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxHeight()
            // M3224_ONE_LIST_DETAIL_GEOMETRY_V1: splits take the full width; a single
            // pane centres itself at the reading width (readingColumn).
            .fillMaxWidth(),
        targetState = route.destination,
    transitionSpec = {
        if (state.reduceMotion) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            fadeIn() togetherWith fadeOut()
        }
    },
    label = "main navigation",
) { destination ->
    screenStateHolder.SaveableStateProvider("main-${destination.name}") {
        when (destination) {
            MainDestination.HOME -> {
                val homeContentPadding = mainContentPadding(
                    scaffoldPadding = padding,
                    layoutDirection = layoutDirection,
                    horizontal = ScreenMargin,
                    topExtra = 8.dp,
                    bottomExtra = stableMainContentBottomPadding,
                )
                if (activeCustomFeed != null) {
                    CustomFeedBrowser(
                        modifier = Modifier.readingColumn(),
                        state = state,
                        accountStableId = state.activeAccountId,
                        feed = activeCustomFeed,
                        postsState = customFeedPostsState,
                        contentPadding = homeContentPadding,
                        showContextHeader = false,
                        onBack = { activeCustomFeedPath = null },
                        onRefresh = {
                            onLoadCustomFeedPosts(
                                state.activeAccountId,
                                activeCustomFeed.path,
                                true,
                            )
                        },
                        onLoadInitial = {
                            onLoadCustomFeedPosts(
                                state.activeAccountId,
                                activeCustomFeed.path,
                                false,
                            )
                        },
                        onAppend = {
                            onAppendCustomFeedPosts(
                                state.activeAccountId,
                                activeCustomFeed.path,
                            )
                        },
                    )
                } else {
                    HomeScreen(
                        // M3224_READING_WIDTH_V1
                        modifier = Modifier.readingColumn(),
                        state = state,
                        redditFeedState = redditFeedState,
                        onRefreshRedditFeed = onRefreshRedditFeed,
                        onAppendRedditFeed = onAppendRedditFeed,
                        authenticatedLandingPending =
                            authenticatedLandingPending,
                        authenticatedHomeWithoutSubscriptions =
                            authenticatedHomeWithoutSubscriptions,
                        onBrowsePopular = {
                            pendingAuthenticatedLandingAccountId = null
                            state.selectFeedSource(
                                FeedSource.POPULAR,
                            )
                        },
                        onFindCommunities = {
                            pendingAuthenticatedLandingAccountId = null
                            state.openCommunityExplore()
                        },
                        contentPadding = homeContentPadding,
                        videoViewportPadding = PaddingValues(
                            top = visibleChromePadding(
                                totalPadding = padding.calculateTopPadding(),
                                hiddenFraction = topBarHiddenFraction,
                                minimumPadding = with(density) {
                                    WindowInsets.statusBars.getTop(this).toDp()
                                },
                            ),
                            bottom = visibleChromePadding(
                                totalPadding = padding.calculateBottomPadding(),
                                hiddenFraction = bottomBarHiddenFraction,
                                minimumPadding = navigationBarInsetHeight,
                            ),
                        ),
                    )
                }
            }
            MainDestination.SUBS -> {
                val subscriptionsCommunityListDetail =
                    useExpandedSubscriptionsCommunityListDetail(
                        expandedWindow = expandedWindowLayout,
                        supportedOrigin = true,
                        tabletLayoutMode = state.tabletLayoutMode,
                    )

                if (subscriptionsCommunityListDetail) {
                    // NEXT01A5_SUBSCRIPTIONS_COMMUNITY_MAIN_TWO_PANE_V1
                    screenStateHolder.SaveableStateProvider(
                        "subscriptions-community-workspace",
                    ) {
                        ExpandedListDetailSurface(
                            // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                            showListHeader = false,
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
                                Box(
                                    modifier = detailModifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Select a community",
                                        style = MaterialTheme.typography.titleMedium,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                } else {
                    CommunitiesScreen(
                        modifier = Modifier.readingColumn(),
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
                        // SUBS01B6_COMPACT_EXPLORE_LAUNCHER_SPACING_V1
                        contentPadding = mainContentPadding(
                            scaffoldPadding = padding,
                            layoutDirection = layoutDirection,
                            horizontal = ScreenMargin,
                            topExtra = 4.dp,
                            bottomExtra = stableMainContentBottomPadding,
                        ),
                    )
                }
            }
            MainDestination.INBOX -> {
                val inboxActivityWorkspaceListDetail =
                    useExpandedInboxActivityWorkspaceListDetail(
                        expandedWindow = expandedWindowLayout,
                        selectedTab = inboxPrimaryTab,
                        tabletLayoutMode = state.tabletLayoutMode,
                    )

                // NEXT01A3A_SHARED_MAIN_INBOX_HOST_V1
                val mainInboxContent:
                    @Composable (Modifier, PaddingValues) -> Unit =
                    { inboxModifier, inboxContentPadding ->
                        InboxScreen(
                            modifier = inboxModifier,
                            state = state,
                            selectedPrimaryTab = inboxPrimaryTab,
                            controlledModSection = inboxModSection,
                            showSelectedModmailThread =
                                !modmailWorkspaceListDetail,
                            redditInboxState = redditInboxState,
                            onLoadRedditInbox = onLoadRedditInbox,
                            onAppendRedditInbox = onAppendRedditInbox,
                            onMarkRedditInboxRead = onMarkRedditInboxRead,
                            onResolveRedditInboxTarget = onResolveRedditInboxTarget,
                            onAuthorizeRedditInbox = onAuthorizeRedditInbox,
                            onCancelRedditInboxAuthorization = onCancelRedditInboxAuthorization,
                            redditModerationState = redditModerationState,
                            onLoadRedditModQueue = onLoadRedditModQueue,
                            onAppendRedditModQueue = onAppendRedditModQueue,
                            onResolveRedditModerationTarget =
                                onResolveRedditModerationTarget,
                            moderationActionState = moderationActionState,
                            onModerateRedditQueueItem = onModerateRedditQueueItem,
                            onRetryRedditModerationAction =
                                onRetryRedditModerationAction,
                            onAuthorizeRedditModerationAction =
                                onAuthorizeRedditModerationAction,
                            removalReasonState = removalReasonState,
                            onLoadRedditRemovalReasons =
                                onLoadRedditRemovalReasons,
                            onAuthorizeRedditRemovalReasons =
                                onAuthorizeRedditRemovalReasons,
                            redditModmailState = redditModmailState,
                            onLoadRedditModmail = onLoadRedditModmail,
                            onAppendRedditModmail = onAppendRedditModmail,
                            onSelectRedditModmailListMode = onSelectRedditModmailListMode,
                            onOpenRedditModmailConversation =
                                onOpenRedditModmailConversation,
                            onSendRedditModmailMessage =
                                onSendRedditModmailMessage,
                            onSetRedditModmailArchived = onSetRedditModmailArchived,
                            onSetRedditModmailUnread = onSetRedditModmailUnread,
                            onVerifyRedditModmailReadState =
                                onVerifyRedditModmailReadState,
                            onApplyRedditModmailTriage = onApplyRedditModmailTriage,
                            onCloseRedditModmailConversation =
                                onCloseRedditModmailConversation,
                            onAuthorizeRedditModmail = onAuthorizeRedditModmail,
                            modmailNotificationTarget = modmailNotificationTarget,
                            onModmailNotificationConsumed =
                                onModmailNotificationConsumed,
                            onPrimaryTabChanged = { inboxPrimaryTab = it },
                            onModSectionChanged = { inboxModSection = it },
                            onContextualSelectionChanged = { selection ->
                                inboxContextualSelection = selection
                            },
                            contentPadding = inboxContentPadding,
                        )
                    }

                if (inboxActivityWorkspaceListDetail) {
                    // NEXT01A3A_INBOX_ACTIVITY_WORKSPACE_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                        showListHeader = false,
scaffoldPadding = padding,
                        listTitle = "Inbox",
                        listSubtitle = "Activity",
                        listContent = { listModifier ->
                            mainInboxContent(
                                listModifier,
                                PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp,
                                ),
                            )
                        },
                        detailContent = { detailModifier ->
                            Box(
                                modifier =
                                    detailModifier.fillMaxSize(),
                                contentAlignment =
                                    Alignment.Center,
                            ) {
                                Text(
                                    text = "Select activity",
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                } else if (
                    useExpandedInboxMessagesWorkspaceListDetail(
                        expandedWindow = expandedWindowLayout,
                        selectedTab = inboxPrimaryTab,
                        tabletLayoutMode = state.tabletLayoutMode,
                    )
                ) {
                    // NEXT01A3B_INBOX_MESSAGES_WORKSPACE_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                        showListHeader = false,
scaffoldPadding = padding,
                        listTitle = "Inbox",
                        listSubtitle = "Messages",
                        listContent = { listModifier ->
                            mainInboxContent(
                                listModifier,
                                PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp,
                                ),
                            )
                        },
                        detailContent = { detailModifier ->
                            Box(
                                modifier =
                                    detailModifier.fillMaxSize(),
                                contentAlignment =
                                    Alignment.Center,
                            ) {
                                Text(
                                    text = "Select a conversation",
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                } else if (
                    useExpandedInboxModQueueWorkspaceListDetail(
                        expandedWindow = expandedWindowLayout,
                        selectedTab = inboxPrimaryTab,
                        selectedModSection = inboxModSection,
                        tabletLayoutMode = state.tabletLayoutMode,
                    )
                ) {
                    // NEXT01A3C_INBOX_MOD_QUEUE_WORKSPACE_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                        showListHeader = false,
scaffoldPadding = padding,
                        listTitle = "Inbox",
                        listSubtitle = "Mod · Queue",
                        listContent = { listModifier ->
                            mainInboxContent(
                                listModifier,
                                PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp,
                                ),
                            )
                        },
                        detailContent = { detailModifier ->
                            Box(
                                modifier =
                                    detailModifier.fillMaxSize(),
                                contentAlignment =
                                    Alignment.Center,
                            ) {
                                Text(
                                    text = "Select a queue item",
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                } else if (modmailWorkspaceListDetail) {
                    // NEXT01A3D_INBOX_MODMAIL_WORKSPACE_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                        showListHeader = false,
scaffoldPadding = padding,
                        listTitle = "Inbox",
                        listSubtitle = "Mod · Modmail",
                        listContent = { listModifier ->
                            mainInboxContent(
                                listModifier,
                                PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp,
                                ),
                            )
                        },
                        detailContent = { detailModifier ->
                            val selectedModmailThread =
                                redditModmailState.thread?.takeIf { thread ->
                                    redditModmailState.matches(
                                        state.activeAccountId,
                                    ) &&
                                        redditModmailState
                                            .selectedConversationId ==
                                        thread.conversation.id
                                }

                            when {
                                selectedModmailThread != null -> {
                                    val detailListState =
                                        androidx.compose.foundation.lazy
                                            .rememberLazyListState()

                                    app.subshell.prototype.ui.screens
                                        .InboxModmailThreadSurface(
                                            modifier = detailModifier,
                                            thread = selectedModmailThread,
                                            state = state,
                                            redditModmailState =
                                                redditModmailState,
                                            accountStableId =
                                                state.activeAccountId,
                                            authenticated =
                                                state.activeAccountIsAuthenticated,
                                            listState = detailListState,
                                            applySystemBars = false,
                                            showBackButton = false,
                                            onOpenRedditModmailConversation = {
                                                    account,
                                                    conversationId,
                                                ->
                                                onOpenRedditModmailConversation(
                                                    account,
                                                    conversationId,
                                                )
                                            },
                                            onSendRedditModmailMessage = {
                                                    account,
                                                    conversationId,
                                                    body,
                                                    internal,
                                                    authorHidden,
                                                ->
                                                onSendRedditModmailMessage(
                                                    account,
                                                    conversationId,
                                                    body,
                                                    internal,
                                                    authorHidden,
                                                )
                                            },
                                            onSetRedditModmailArchived = {
                                                    account,
                                                    conversationId,
                                                    archived,
                                                ->
                                                onSetRedditModmailArchived(
                                                    account,
                                                    conversationId,
                                                    archived,
                                                )
                                            },
                                            onSetRedditModmailUnread = {
                                                    account,
                                                    conversationId,
                                                    unread,
                                                ->
                                                onSetRedditModmailUnread(
                                                    account,
                                                    conversationId,
                                                    unread,
                                                )
                                            },
                                            onVerifyRedditModmailReadState = {
                                                    account,
                                                    conversationId,
                                                ->
                                                onVerifyRedditModmailReadState(
                                                    account,
                                                    conversationId,
                                                )
                                            },
                                            onApplyRedditModmailTriage = {
                                                    account,
                                                    conversationId,
                                                    action,
                                                ->
                                                onApplyRedditModmailTriage(
                                                    account,
                                                    conversationId,
                                                    action,
                                                )
                                            },
                                            onCloseRedditModmailConversation = {
                                                onCloseRedditModmailConversation()
                                            },
                                        )
                                }

                                redditModmailState
                                    .selectedConversationId != null &&
                                    redditModmailState.threadLoading -> {
                                    Box(
                                        modifier =
                                            detailModifier.fillMaxSize(),
                                        contentAlignment =
                                            Alignment.Center,
                                    ) {
                                        Text(
                                            text =
                                                "Loading conversation…",
                                            style =
                                                MaterialTheme.typography
                                                    .titleMedium,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant,
                                        )
                                    }
                                }

                                else -> {
                                    Box(
                                        modifier =
                                            detailModifier.fillMaxSize(),
                                        contentAlignment =
                                            Alignment.Center,
                                    ) {
                                        Text(
                                            text =
                                                "Select a Modmail conversation",
                                            style =
                                                MaterialTheme.typography
                                                    .titleMedium,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        },
                    )
                } else {
                    mainInboxContent(
                        Modifier.readingColumn(),
                        mainContentPadding(
                            scaffoldPadding = padding,
                            layoutDirection = layoutDirection,
                            horizontal = ScreenMargin,
                            topExtra = 8.dp,
                            bottomExtra =
                                stableMainContentBottomPadding,
                        ),
                    )
                }
            }
            MainDestination.YOU -> {
                // NEXT01A4A_YOU_CONTENT_WORKSPACE_MAIN_V1
                val selectedYouContentTab =
                    state.userProfileTab(
                        state.activeAccountUsername,
                    )
                val youContentWorkspace =
                    useExpandedYouContentWorkspaceListDetail(
                        expandedWindow =
                            expandedWindowLayout,
                        supportedOrigin = true,
                        selectedTab =
                            selectedYouContentTab,
                        tabletLayoutMode =
                            state.tabletLayoutMode,
                    )

                val youBlockedWorkspace =
                    useExpandedYouBlockedWorkspaceListDetail(
                        expandedWindow =
                            expandedWindowLayout,
                        supportedOrigin = true,
                        selectedTab =
                            selectedYouContentTab,
                        tabletLayoutMode =
                            state.tabletLayoutMode,
                    )

                val youDraftsWorkspace =
                    useExpandedYouDraftsWorkspaceListDetail(
                        expandedWindow =
                            expandedWindowLayout,
                        supportedOrigin = true,
                        selectedTab =
                            selectedYouContentTab,
                        tabletLayoutMode =
                            state.tabletLayoutMode,
                    )


                if (youContentWorkspace) {
                    ExpandedListDetailSurface(
                        // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                        showListHeader = false,
scaffoldPadding = padding,
                        listTitle = "You",
                        listSubtitle =
                            youContentWorkspaceLabel(
                                selectedYouContentTab,
                            ),
                        listContent = { listModifier ->
                            mainYouProfileContent(
                                state.activeAccountUsername,
                                listModifier,
                                PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                null,
                                null,
                                null,
                            )
                        },
                        detailContent = { detailModifier ->
                            Box(
                                modifier =
                                    detailModifier.fillMaxSize(),
                                contentAlignment =
                                    Alignment.Center,
                            ) {
                                Text(
                                    text =
                                        youContentWorkspacePlaceholder(
                                            selectedYouContentTab,
                                        ),
                                    style =
                                        MaterialTheme.typography
                                            .titleMedium,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                )
                            }
                        },
                    )
                } else if (youBlockedWorkspace) {
                    ExpandedListDetailSurface(
                        // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                        showListHeader = false,
scaffoldPadding = padding,
                        listTitle = "You",
                        listSubtitle = "Blocked",
                        listContent = { listModifier ->
                            mainYouProfileContent(
                                state.activeAccountUsername,
                                listModifier,
                                PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp,
                                ),
                                null,
                                null,
                                { selectedUsername ->
                                    state.openUser(
                                        selectedUsername,
                                    )
                                },
                            )
                        },
                        detailContent = { detailModifier ->
                            Box(
                                modifier =
                                    detailModifier.fillMaxSize(),
                                contentAlignment =
                                    Alignment.Center,
                            ) {
                                Text(
                                    text =
                                        YOU_BLOCKED_WORKSPACE_PLACEHOLDER,
                                    style =
                                        MaterialTheme.typography
                                            .titleMedium,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                )
                            }
                        },
                    )

                } else if (youDraftsWorkspace) {
                    // NEXT01A4C_DRAFTS_SPLIT_FROM_ENTRY_V1
                    ExpandedListDetailSurface(
                        // M3224_ONE_TITLE_PER_PANE_V1 (#224): the top bar already names the destination.
                        showListHeader = false,
scaffoldPadding = padding,
                        listTitle = "You",
                        listSubtitle = "Drafts",
                        listContent = { listModifier ->
                            mainYouProfileContent(
                                state.activeAccountUsername,
                                listModifier,
                                PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp,
                                ),
                                null,
                                null,
                                null,
                            )
                        },
                        detailContent = { detailModifier ->
                            Box(
                                modifier =
                                    detailModifier.fillMaxSize(),
                                contentAlignment =
                                    Alignment.Center,
                            ) {
                                Text(
                                    text =
                                        YOU_DRAFTS_WORKSPACE_PLACEHOLDER,
                                    style =
                                        MaterialTheme.typography
                                            .titleMedium,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                )
                            }
                        },
                    )

                } else {
                    mainYouProfileContent(
                        state.activeAccountUsername,
                        Modifier.readingColumn(),
                        mainContentPadding(
                            scaffoldPadding = padding,
                            layoutDirection = layoutDirection,
                            horizontal = ScreenMargin,
                            topExtra = 4.dp,
                            bottomExtra =
                                stableMainContentBottomPadding,
                        ),
                        null,
                        null,
                        null,
                    )
                }
                // NEXT01A4A_YOU_CONTENT_WORKSPACE_MAIN_END_V1
            }
        }
    }
}
}
}
