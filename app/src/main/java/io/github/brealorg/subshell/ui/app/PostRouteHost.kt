package io.github.brealorg.subshell.ui.app

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
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.RedditFeedSort
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditMainFeed
import io.github.brealorg.subshell.data.account.CommentDeleteRequest
import io.github.brealorg.subshell.data.account.CommentDeleteUiState
import io.github.brealorg.subshell.data.account.CommentEditRequest
import io.github.brealorg.subshell.data.account.CommentEditUiState
import io.github.brealorg.subshell.data.account.CommentWriteRequest
import io.github.brealorg.subshell.data.account.CommentWriteUiState
import io.github.brealorg.subshell.data.account.CommunityMembershipAction
import io.github.brealorg.subshell.data.account.CommunityMembershipUiState
import io.github.brealorg.subshell.data.account.PostDeleteRequest
import io.github.brealorg.subshell.data.account.PostDeleteUiState
import io.github.brealorg.subshell.data.account.PostEditRequest
import io.github.brealorg.subshell.data.account.PostEditUiState
import io.github.brealorg.subshell.data.account.PostManageMutationRequest
import io.github.brealorg.subshell.data.account.PostManageTarget
import io.github.brealorg.subshell.data.account.PostManageUiState
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.data.account.RedditCommunityDiscoveryUiState
import io.github.brealorg.subshell.data.account.RedditCommunityInfoUiState
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiState
import io.github.brealorg.subshell.data.account.RedditFeedUiState
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.data.account.RedditModerationUiState
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModmailListMode
import io.github.brealorg.subshell.data.account.RedditModmailTriageAction
import io.github.brealorg.subshell.data.account.RedditModmailUiState
import io.github.brealorg.subshell.data.account.RedditRemovalReasonUiState
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.inbox.ModmailNotificationTarget
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.model.CommunityDiscoverySort
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.ExpandedContextReadingMaxWidth
import io.github.brealorg.subshell.ui.FeedPostEntryTarget
import io.github.brealorg.subshell.ui.FeedPostOrigin
import io.github.brealorg.subshell.ui.FeedSource
import io.github.brealorg.subshell.ui.MainDestination
import io.github.brealorg.subshell.ui.SubshellRoute
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.app.ExpandedContextSurface
import io.github.brealorg.subshell.ui.app.ExpandedListDetailSurface
import io.github.brealorg.subshell.ui.screens.CommunityScreen
import io.github.brealorg.subshell.ui.screens.HomeScreen
import io.github.brealorg.subshell.ui.screens.InboxModSection
import io.github.brealorg.subshell.ui.screens.InboxPrimaryTab
import io.github.brealorg.subshell.ui.screens.InboxScreen
import io.github.brealorg.subshell.ui.screens.PostScreen
import io.github.brealorg.subshell.ui.screens.SearchScreen
import io.github.brealorg.subshell.ui.supportsExpandedPublicUserContentOrigin
import io.github.brealorg.subshell.ui.useExpandedCommunityPostListDetail
import io.github.brealorg.subshell.ui.useExpandedHomePostListDetail
import io.github.brealorg.subshell.ui.useExpandedInboxListDetail
import io.github.brealorg.subshell.ui.useExpandedPublicUserContentWorkspaceListDetail
import io.github.brealorg.subshell.ui.useExpandedSearchPostListDetail
import io.github.brealorg.subshell.ui.useExpandedYouBlockedWorkspaceListDetail
import io.github.brealorg.subshell.ui.useExpandedYouContentWorkspaceListDetail
import io.github.brealorg.subshell.ui.youContentWorkspaceLabel

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
    mainYouProfileContent: @Composable (String, Modifier, PaddingValues, ((io.github.brealorg.subshell.model.Post, String?) -> Unit)?, ((Int) -> Unit)?, ((String) -> Unit)?) -> Unit,
    publicUserProfileContent: @Composable (String, Modifier, PaddingValues, (() -> Unit)?, Boolean, ((io.github.brealorg.subshell.model.Post, String?) -> Unit)?, ((Int) -> Unit)?) -> Unit,
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
    val onSelectRedditModQueueList = redditModeration.onSelectRedditModQueueList
    val onResolveRedditModerationTarget = redditModeration.onResolveRedditModerationTarget
    val moderationActionState = redditModeration.moderationActionState
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
    // OPENPOSTSSOT01_SETTLED_OVERRIDES_GIVE_WAY_TO_THE_STORE_V1 (#244): a settled
    // comment vote or save gives way once the thread (which follows its store)
    // shows it.
    val settledOverrideTargets = state.settledOverrideTargetsKey()
    LaunchedEffect(redditCommentsState.comments, settledOverrideTargets) {
        state.releaseSettledCommentOverrides(redditCommentsState.comments)
    }
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
                                onSelectRedditModQueueList = onSelectRedditModQueueList,
                                moderationActionState =
                                    moderationActionState,
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
