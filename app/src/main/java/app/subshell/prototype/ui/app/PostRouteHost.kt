package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.PostDeleteRequest
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
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
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ExpandedContextReadingMaxWidth
import app.subshell.prototype.ui.FeedPostEntryTarget
import app.subshell.prototype.ui.FeedPostOrigin
import app.subshell.prototype.ui.FeedSource
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.ExpandedContextSurface
import app.subshell.prototype.ui.app.ExpandedListDetailSurface
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.screens.HomeScreen
import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import app.subshell.prototype.ui.screens.InboxScreen
import app.subshell.prototype.ui.screens.PostScreen
import app.subshell.prototype.ui.screens.SearchScreen
import app.subshell.prototype.ui.supportsExpandedPublicUserContentOrigin
import app.subshell.prototype.ui.useExpandedCommunityPostListDetail
import app.subshell.prototype.ui.useExpandedHomePostListDetail
import app.subshell.prototype.ui.useExpandedInboxListDetail
import app.subshell.prototype.ui.useExpandedPublicUserContentWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedSearchPostListDetail
import app.subshell.prototype.ui.useExpandedYouBlockedWorkspaceListDetail
import app.subshell.prototype.ui.useExpandedYouContentWorkspaceListDetail
import app.subshell.prototype.ui.youContentWorkspaceLabel

/**
 * The Post route branch of SubshellApp's route dispatch: the post screen as
 * the detail pane of whichever two-pane workspace the post was opened from
 * (Home, community, search, You content, public user, inbox) or on a
 * compact context surface otherwise. Moved verbatim; the SubshellApp vars
 * it reads or writes arrive as MutableState.
 */
@Composable
internal fun PostRouteHost(
    redditFeed: FeedEntryPoints,
    redditCommunity: CommunityEntryPoints,
    redditComments: CommentLoadingEntryPoints,
    redditModeration: ModerationQueueEntryPoints,
    redditModmail: ModmailEntryPoints,
    commentMutations: CommentMutationEntryPoints,
    postMutations: PostMutationEntryPoints,
    redditInbox: InboxEntryPoints,
    onResolveRedditNavigationTarget: (String, String, (Post?) -> Unit) -> Unit,
    state: SubshellState,
    route: SubshellRoute.Post,
    padding: PaddingValues,
    expandedWindowLayout: Boolean,
    screenStateHolder: SaveableStateHolder,
    authenticatedLandingPending: Boolean,
    authenticatedHomeWithoutSubscriptions: Boolean,
    mainYouProfileContent: @Composable (String, Modifier, PaddingValues, ((app.subshell.prototype.model.Post, String?) -> Unit)?, ((Int) -> Unit)?, ((String) -> Unit)?) -> Unit,
    publicUserProfileContent: @Composable (String, Modifier, PaddingValues, (() -> Unit)?, Boolean, ((app.subshell.prototype.model.Post, String?) -> Unit)?, ((Int) -> Unit)?) -> Unit,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
    activeCustomFeedPathState: MutableState<String?>,
    inboxPrimaryTabState: MutableState<InboxPrimaryTab>,
    inboxModSectionState: MutableState<InboxModSection>,
) {
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    var activeCustomFeedPath by activeCustomFeedPathState
    var inboxPrimaryTab by inboxPrimaryTabState
    var inboxModSection by inboxModSectionState
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
    val post = state.selectedPost
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
    val onRefreshRedditCommunityFeed = redditCommunity.onRefreshRedditCommunityFeed
    val onAppendRedditCommunityFeed = redditCommunity.onAppendRedditCommunityFeed
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
    val onRefreshRedditSearch = redditFeed.onRefreshRedditSearch
    val onAppendRedditSearch = redditFeed.onAppendRedditSearch
    val redditSubscriptionsState = redditFeed.redditSubscriptionsState
    val onLoadRedditSubscriptions = redditFeed.onLoadRedditSubscriptions
    val redditCommentsState = redditComments.redditCommentsState
    val onLoadRedditComments = redditComments.onLoadRedditComments
    val onLoadCachedRedditComments = redditComments.onLoadCachedRedditComments
    val onRefreshRedditCommentsPreservingFocusedTarget = redditComments.onRefreshRedditCommentsPreservingFocusedTarget
    val onLoadFocusedRedditComment = redditComments.onLoadFocusedRedditComment
    val onLoadMoreRedditComments = redditComments.onLoadMoreRedditComments
    val commentWriteState = commentMutations.commentWriteState
    val onSubmitComment = commentMutations.onSubmitComment
    val onAuthorizeCommentWrite = commentMutations.onAuthorizeCommentWrite
    val onCancelCommentWriteAuthorization = commentMutations.onCancelCommentWriteAuthorization
    val onResetCommentWriteState = commentMutations.onResetCommentWriteState
    val commentEditState = commentMutations.commentEditState
    val onEditComment = commentMutations.onEditComment
    val onAuthorizeCommentEdit = commentMutations.onAuthorizeCommentEdit
    val onCancelCommentEditAuthorization = commentMutations.onCancelCommentEditAuthorization
    val onResetCommentEditState = commentMutations.onResetCommentEditState
    val onCheckCommentEdit = commentMutations.onCheckCommentEdit
    val commentDeleteState = commentMutations.commentDeleteState
    val onDeleteComment = commentMutations.onDeleteComment
    val onAuthorizeCommentDelete = commentMutations.onAuthorizeCommentDelete
    val onCancelCommentDeleteAuthorization = commentMutations.onCancelCommentDeleteAuthorization
    val onResetCommentDeleteState = commentMutations.onResetCommentDeleteState
    val postEditState = postMutations.postEditState
    val onEditPost = postMutations.onEditPost
    val onAuthorizePostEdit = postMutations.onAuthorizePostEdit
    val onCancelPostEditAuthorization = postMutations.onCancelPostEditAuthorization
    val onResetPostEditState = postMutations.onResetPostEditState
    val onCheckPostEdit = postMutations.onCheckPostEdit
    val postDeleteState = postMutations.postDeleteState
    val onDeletePost = postMutations.onDeletePost
    val onAuthorizePostDelete = postMutations.onAuthorizePostDelete
    val onCancelPostDeleteAuthorization = postMutations.onCancelPostDeleteAuthorization
    val onResetPostDeleteState = postMutations.onResetPostDeleteState
    val onCheckPostDelete = postMutations.onCheckPostDelete
    val postManageState = postMutations.postManageState
    val onLoadPostManagement = postMutations.onLoadPostManagement
    val onMutatePostManagement = postMutations.onMutatePostManagement
    val onAuthorizePostManagement = postMutations.onAuthorizePostManagement
    val onCancelPostManageAuthorization = postMutations.onCancelPostManageAuthorization
    val onResetPostManageState = postMutations.onResetPostManageState
    if (post == null) {
        LaunchedEffect(route.postRoute.postId) { state.closePost() }
    } else {
        // NEXT01A6_PUBLIC_USER_POST_ROUTE_WORKSPACE_V1
        val routeUserOrigin =
            route.postRoute.origin as? FeedPostOrigin.User
        val routeUserContentTab =
            routeUserOrigin?.let { origin ->
                state.userProfileTab(origin.username)
            } ?: 0
        // NEXT01A6_PRESERVE_YOU_BLOCKED_POST_ORIGIN_V1
        val routeUserBlockedWorkspace =
            routeUserOrigin?.let { origin ->
                useExpandedYouBlockedWorkspaceListDetail(
                    expandedWindow =
                        expandedWindowLayout,
                    supportedOrigin =
                        origin.destination ==
                            MainDestination.YOU,
                    selectedTab =
                        state.userProfileTab(
                            state.activeAccountUsername,
                        ),
                    tabletLayoutMode =
                        state.tabletLayoutMode,
                )
            } == true
        val publicUserContentListDetail =
            useExpandedPublicUserContentWorkspaceListDetail(
                expandedWindow = expandedWindowLayout,
                supportedOrigin =
                    routeUserOrigin?.let { origin ->
                        supportsExpandedPublicUserContentOrigin(
                            username = origin.username,
                            activeAccountUsername =
                                state.activeAccountUsername,
                            blockedWorkspace =
                                routeUserBlockedWorkspace,
                        )
                    } == true,
                selectedTab = routeUserContentTab,
                tabletLayoutMode = state.tabletLayoutMode,
            )

        screenStateHolder.SaveableStateProvider(
            if (
                publicUserContentListDetail &&
                routeUserOrigin != null
            ) {
                "public-user-workspace-${routeUserOrigin.username.lowercase()}"
            } else {
                "post-${post.id}-${route.postRoute.visitId}"
            },
        ) {
            val postDetail: @Composable (Modifier) -> Unit =
                { postModifier ->
                    PostScreen(
                        modifier = postModifier,
                        post = post,
                        state = state,
                        onBack = state::closePost,
                        routeFocusedCommentId =
                            route.postRoute.focusedCommentId,
                        redditCommentsState = redditCommentsState,
                        mentionSearchState = redditCommunity.redditMentionSearchState,
                        onSearchMentions = { query ->
                            redditCommunity.onSearchMentions(state.activeAccountId, query)
                        },
                        onRetryRedditComments = onLoadRedditComments,
                        onLoadCachedRedditComments =
                            onLoadCachedRedditComments,
                        onRefreshRedditCommentsPreservingFocusedTarget =
                            onRefreshRedditCommentsPreservingFocusedTarget,
                        onLoadFocusedRedditComment =
                            onLoadFocusedRedditComment,
                        onLoadMoreRedditComments = onLoadMoreRedditComments,
                        onFocusedCommentLandingVerified = { verifiedCommentId ->
                            state.consumeInboxReadReceipt(
                                postId = post.id,
                                commentId = verifiedCommentId,
                            )?.let { receipt ->
                                onMarkRedditInboxRead(
                                    receipt.accountStableId,
                                    receipt.inboxItemId,
                                )
                            }
                        },
                        commentWriteState = commentWriteState,
                        onSubmitComment = onSubmitComment,
                        commentMediaPolicies = commentMutations.commentMediaPolicies,
                        onLoadCommentMediaPolicy = commentMutations.onLoadCommentMediaPolicy,
                        onAuthorizeCommentWrite = onAuthorizeCommentWrite,
                        onCancelCommentWriteAuthorization =
                            onCancelCommentWriteAuthorization,
                        onResetCommentWriteState = onResetCommentWriteState,
                        commentEditState = commentEditState,
                        onEditComment = onEditComment,
                        onAuthorizeCommentEdit = onAuthorizeCommentEdit,
                        onCancelCommentEditAuthorization =
                            onCancelCommentEditAuthorization,
                        onResetCommentEditState = onResetCommentEditState,
                        onCheckCommentEdit = onCheckCommentEdit,
                        commentDeleteState = commentDeleteState,
                        onDeleteComment = onDeleteComment,
                        onAuthorizeCommentDelete = onAuthorizeCommentDelete,
                        onCancelCommentDeleteAuthorization =
                            onCancelCommentDeleteAuthorization,
                        onResetCommentDeleteState = onResetCommentDeleteState,
                        postEditState = postEditState,
                        onEditPost = onEditPost,
                        onAuthorizePostEdit = onAuthorizePostEdit,
                        onCancelPostEditAuthorization =
                            onCancelPostEditAuthorization,
                        onResetPostEditState = onResetPostEditState,
                        onCheckPostEdit = onCheckPostEdit,
                        postDeleteState = postDeleteState,
                        onDeletePost = onDeletePost,
                        onAuthorizePostDelete = onAuthorizePostDelete,
                        onCancelPostDeleteAuthorization =
                            onCancelPostDeleteAuthorization,
                        onResetPostDeleteState = onResetPostDeleteState,
                        onCheckPostDelete = onCheckPostDelete,
                        postManageState = postManageState,
                        onLoadPostManagement = onLoadPostManagement,
                        onMutatePostManagement = onMutatePostManagement,
                        onAuthorizePostManagement = onAuthorizePostManagement,
                        onCancelPostManageAuthorization =
                            onCancelPostManageAuthorization,
                        onResetPostManageState = onResetPostManageState,
                    )
                }

            val homeListDetail =
                useExpandedHomePostListDetail(
                    expandedWindow = expandedWindowLayout,
                    supportedOrigin =
                        state.currentPostSupportsExpandedHomeListDetail(),
                    customFeedActive = activeCustomFeedPath != null,
                    tabletLayoutMode = state.tabletLayoutMode,
                )
            val communityOrigin =
                route.postRoute.origin as? FeedPostOrigin.Community
            val communityListDetail =
                useExpandedCommunityPostListDetail(
                    expandedWindow = expandedWindowLayout,
                    supportedOrigin = communityOrigin != null,
                    tabletLayoutMode = state.tabletLayoutMode,
                )
            val searchOrigin =
                route.postRoute.origin as? FeedPostOrigin.Search
            val searchListDetail =
                useExpandedSearchPostListDetail(
                    expandedWindow = expandedWindowLayout,
                    supportedOrigin = searchOrigin != null,
                    tabletLayoutMode = state.tabletLayoutMode,
                )
            val inboxListDetail =
                useExpandedInboxListDetail(
                    expandedWindow = expandedWindowLayout,
                    supportedOrigin =
                        state.currentPostSupportsExpandedInboxListDetail(),
                    tabletLayoutMode = state.tabletLayoutMode,
                )
            // NEXT01A4A_YOU_POST_ROUTE_LIST_DETAIL_V1
            val userOrigin = routeUserOrigin
            val userContentTab = routeUserContentTab
            val youContentListDetail =
                useExpandedYouContentWorkspaceListDetail(
                    expandedWindow = expandedWindowLayout,
                    supportedOrigin =
                        userOrigin?.let { origin ->
                            origin.destination ==
                                MainDestination.YOU &&
                                origin.username.equals(
                                    state.activeAccountUsername,
                                    ignoreCase = true,
                                )
                        } == true,
                    selectedTab = userContentTab,
                    tabletLayoutMode = state.tabletLayoutMode,
                )

            when {
                homeListDetail -> ExpandedListDetailSurface(
                    scaffoldPadding = padding,
                    listTitle = "Home",
                    listSubtitle =
                        "${state.feedSource.label} · ${state.feedSort.label}",
                    listContent = { listModifier ->
                        HomeScreen(
                            modifier = listModifier,
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
                            onOpenPostSelection = {
                                    selectedPost,
                                    openComments,
                                ->
                                state.replaceCurrentPostSelection(
                                    selectedPost,
                                    if (openComments) {
                                        FeedPostEntryTarget.COMMENTS
                                    } else {
                                        FeedPostEntryTarget.TOP
                                    },
                                )
                            },
                            contentPadding = PaddingValues(
                                horizontal = 8.dp,
                                vertical = 8.dp,
                            ),
                        )
                    },
                    detailContent = postDetail,
                )

                communityListDetail &&
                    communityOrigin != null ->
                    // NEXT01A1_COMMUNITY_POST_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        scaffoldPadding = padding,
                        listTitle =
                            state.subredditLabel(
                                communityOrigin.communityName,
                            ),
                        listSubtitle = null,
                        listContent = { listModifier ->
                            CommunityScreen(
                                communityName =
                                    communityOrigin.communityName,
                                state = state,
                                redditFeedState =
                                    redditFeedState,
                                onRefreshRedditFeed =
                                    onRefreshRedditCommunityFeed,
                                onAppendRedditFeed =
                                    onAppendRedditCommunityFeed,
                                redditSubscriptionsState =
                                    redditSubscriptionsState,
                                onLoadRedditSubscriptions =
                                    onLoadRedditSubscriptions,
                                redditCommunityInfoState =
                                    redditCommunityInfoState,
                                onLoadRedditCommunityInfo =
                                    onLoadRedditCommunityInfo,
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
                                moderationActionState =
                                    moderationActionState,
                                onModerateRedditQueueItem =
                                    onModerateRedditQueueItem,
                                onRetryRedditModerationAction =
                                    onRetryRedditModerationAction,
                                onAuthorizeRedditModerationAction =
                                    onAuthorizeRedditModerationAction,
                                removalReasonState =
                                    removalReasonState,
                                onLoadRedditRemovalReasons =
                                    onLoadRedditRemovalReasons,
                                onAuthorizeRedditRemovalReasons =
                                    onAuthorizeRedditRemovalReasons,
                                communityMembershipState =
                                    communityMembershipState,
                                onChangeCommunityMembership =
                                    onChangeCommunityMembership,
                                onBack = state::closeSubreddit,
                                onOpenPostSelection = { selectedPost ->
                                    state.replaceCurrentPostSelection(
                                        selectedPost,
                                    )
                                },
                                showContextHeader = false,
                                modifier = listModifier,
                            )
                        },
                        detailContent = postDetail,
                    )

                searchListDetail &&
                    searchOrigin != null ->
                    // NEXT01A2_SEARCH_POST_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        scaffoldPadding = padding,
                        listTitle = "Search",
                        listSubtitle =
                            "“${searchOrigin.query}”",
                        listContent = { listModifier ->
                            SearchScreen(
                                modifier = listModifier,
                                state = state,
                                redditFeedState =
                                    redditFeedState,
                                redditCommunityDiscoveryState = redditCommunityDiscoveryState,
                                onLoadRedditCommunityDiscovery = onLoadRedditCommunityDiscovery,
                                onRefreshRedditFeed =
                                    onRefreshRedditFeed,
                                onRefreshRedditSearch =
                                    onRefreshRedditSearch,
                                onAppendRedditSearch =
                                    onAppendRedditSearch,
                                onResolveRedditNavigationTarget = onResolveRedditNavigationTarget,
                                onBack = state::closePost,
                                onOpenPostSelection = {
                                        selectedPost,
                                    ->
                                    state.replaceCurrentPostSelection(
                                        selectedPost,
                                    )
                                },
                                showWorkspaceControls = false,
                            )
                        },
                        detailContent = postDetail,
                    )

                youContentListDetail &&
                    userOrigin != null ->
                    // NEXT01A4A_YOU_CONTENT_DETAIL_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        scaffoldPadding = padding,
                        listTitle = "You",
                        listSubtitle =
                            youContentWorkspaceLabel(
                                userContentTab,
                            ),
                        listContent = { listModifier ->
                            mainYouProfileContent(
                                userOrigin.username,
                                listModifier,
                                PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                { selectedPost, focusedCommentId ->
                                    state.replaceCurrentUserContentSelection(
                                        post = selectedPost,
                                        username = userOrigin.username,
                                        commentId = focusedCommentId,
                                    )
                                },
                                { selectedTab ->
                                    if (selectedTab != userContentTab) {
                                        state.closePost()
                                    }
                                },
                                null,
                            )
                        },
                        detailContent = postDetail,
                    )

                publicUserContentListDetail &&
                    userOrigin != null ->
                    // NEXT01A6_PUBLIC_USER_POST_TWO_PANE_V1
                    ExpandedListDetailSurface(
                        scaffoldPadding = padding,
                        listTitle =
                            "u/${userOrigin.username}",
                        listSubtitle =
                            youContentWorkspaceLabel(
                                userContentTab,
                            ),
                        listContent = { listModifier ->
                            publicUserProfileContent(
                                userOrigin.username,
                                listModifier,
                                PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp,
                                ),
                                state::closePost,
                                false,
                                {
                                        selectedPost,
                                        focusedCommentId,
                                    ->
                                    state.replaceCurrentUserContentSelection(
                                        post = selectedPost,
                                        username =
                                            userOrigin.username,
                                        commentId =
                                            focusedCommentId,
                                    )
                                },
                                { selectedTab ->
                                    if (
                                        selectedTab !=
                                        userContentTab
                                    ) {
                                        state.closePost()
                                    }
                                },
                            )
                        },
                        detailContent = postDetail,
                        showListHeader = false,
                    )

                inboxListDetail -> ExpandedListDetailSurface(
                    scaffoldPadding = padding,
                    listTitle = "Inbox",
                    listSubtitle = "Activity, messages & moderation",
                    listContent = { listModifier ->
                        InboxScreen(
                            modmailThreadFillsTheScreen = false,
                            modifier = listModifier,
                            state = state,
                            selectedPrimaryTab = inboxPrimaryTab,
                            controlledModSection = inboxModSection,
                            redditInboxState = redditInboxState,
                            onLoadRedditInbox = onLoadRedditInbox,
                            onAppendRedditInbox = onAppendRedditInbox,
                            onMarkRedditInboxRead = onMarkRedditInboxRead,
                            onResolveRedditInboxTarget =
                                onResolveRedditInboxTarget,
                            onAuthorizeRedditInbox =
                                onAuthorizeRedditInbox,
                            redditModerationState = redditModerationState,
                            onLoadRedditModQueue = onLoadRedditModQueue,
                            onAppendRedditModQueue = onAppendRedditModQueue,
                            onResolveRedditModerationTarget =
                                onResolveRedditModerationTarget,
                            moderationActionState = moderationActionState,
                            onModerateRedditQueueItem = onModerateRedditQueueItem,
                            onRetryRedditModerationAction = onRetryRedditModerationAction,
                            onAuthorizeRedditModerationAction = onAuthorizeRedditModerationAction,
                            removalReasonState = removalReasonState,
                            onLoadRedditRemovalReasons = onLoadRedditRemovalReasons,
                            onAuthorizeRedditRemovalReasons = onAuthorizeRedditRemovalReasons,
                            redditModmailState = redditModmailState,
                            onLoadRedditModmail = onLoadRedditModmail,
                            onAppendRedditModmail = onAppendRedditModmail,
                            onSelectRedditModmailListMode = onSelectRedditModmailListMode,
                            onOpenRedditModmailConversation = onOpenRedditModmailConversation,
                            onSendRedditModmailMessage = onSendRedditModmailMessage,
                            onSetRedditModmailArchived = onSetRedditModmailArchived,
                            onSetRedditModmailUnread = onSetRedditModmailUnread,
                            onVerifyRedditModmailReadState =
                                onVerifyRedditModmailReadState,
                            onApplyRedditModmailTriage = onApplyRedditModmailTriage,
                            onCloseRedditModmailConversation = onCloseRedditModmailConversation,
                            onAuthorizeRedditModmail = onAuthorizeRedditModmail,
                            modmailNotificationTarget = modmailNotificationTarget,
                            onModmailNotificationConsumed =
                                onModmailNotificationConsumed,
                            onPrimaryTabChanged = { inboxPrimaryTab = it },
                            onModSectionChanged = { inboxModSection = it },
                            onOpenItemSelection = {
                                    item,
                                    resolvedPost,
                                    allowFallbackDetail,
                                ->
                                state.replaceCurrentInboxDetail(
                                    item = item,
                                    resolvedPost = resolvedPost,
                                    allowFallbackDetail =
                                        allowFallbackDetail,
                                )
                            },
                            onOpenModerationItemSelection = { item, resolvedPost ->
                                state.replaceCurrentModeratorQueueDetail(
                                    item = item,
                                    resolvedPost = resolvedPost,
                                )
                            },
                            contentPadding = PaddingValues(
                                horizontal = 8.dp,
                                vertical = 8.dp,
                            ),
                        )
                    },
                    detailContent = postDetail,
                )

                else -> ExpandedContextSurface(
                    scaffoldPadding = padding,
                    expanded = expandedWindowLayout,
                    maxWidth = ExpandedContextReadingMaxWidth,
                ) { contextModifier ->
                    postDetail(contextModifier)
                }
            }
        }
    }
}
