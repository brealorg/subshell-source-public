package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.communityFeedWithPinnedPosts
import app.subshell.prototype.ui.CommunityFeedWithPinned
import app.subshell.prototype.data.account.RedditCommunityPinnedPostsUiState
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.RowScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.subshell.prototype.update.appNotificationSettingsIntent
import app.subshell.prototype.communityposts.CommunityPostNotificationCenter
import app.subshell.prototype.communityposts.MAX_WATCHED_COMMUNITIES
import app.subshell.prototype.ui.noticeInfo
import app.subshell.prototype.ui.state.CommunityPostWatchChange
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.noticeFailure
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import app.subshell.prototype.ui.components.favoriteStarTint
import app.subshell.prototype.ui.components.FilteredPostsSummaryRow
import app.subshell.prototype.ui.components.LocalModeratorToolsNavigation
import app.subshell.prototype.ui.components.SavedResponseActions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.shortcuts.AndroidLauncherShortcutPublisher
import app.subshell.prototype.shortcuts.LauncherShortcutEntry
import app.subshell.prototype.shortcuts.rememberLauncherShortcutsEnabled
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.CommunitySort
import app.subshell.prototype.ui.isDeepForAutomaticRefresh
import app.subshell.prototype.ui.canAppendRedditFeed
import app.subshell.prototype.ui.captureFeedScrollAnchor
import app.subshell.prototype.ui.communityFeedScrollKey
import app.subshell.prototype.ui.communityFeedPostsInDisplayOrder
import app.subshell.prototype.ui.communityPostFeedQueryKey
import app.subshell.prototype.ui.pagingFooterKey
import app.subshell.prototype.ui.rememberFeedScrollState
import app.subshell.prototype.ui.PostLayout
import app.subshell.prototype.ui.feedItemSpacing
import app.subshell.prototype.ui.feedPostItemKeys
import app.subshell.prototype.ui.feedPostItems
import app.subshell.prototype.ui.rememberFeedGridColumns
import app.subshell.prototype.ui.components.PostLayoutMenuButton
import app.subshell.prototype.ui.ObserveFeedVideoViewport
import app.subshell.prototype.ui.GoogleFeedVideoViewport
import app.subshell.prototype.ui.ObserveLiveFeedAutoAppend
import app.subshell.prototype.ui.rememberTerminalVideoId
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.supportsTimePeriod
import app.subshell.prototype.ui.toRedditFeedSort
import app.subshell.prototype.ui.hiddenPostIdentities
import app.subshell.prototype.ui.isHiddenBy
import app.subshell.prototype.ui.components.CommunityAboutSheet
import app.subshell.prototype.ui.components.CommunityAvatar
import app.subshell.prototype.ui.components.CommunityMuteSheet
import app.subshell.prototype.ui.components.communityMuteStatusText
import app.subshell.prototype.ui.components.ModeratorToolsSheet
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.components.RepositoryPagingFooter
import app.subshell.prototype.ui.components.RefreshableLazyColumn
import app.subshell.prototype.ui.components.CommunityHighlightsRow
import app.subshell.prototype.ui.components.RemoteIdentityImage
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.communityMembershipErrorPresentation
import app.subshell.prototype.ui.error.ReportViewportError
import app.subshell.prototype.ui.error.redditSubscriptionsErrorPresentation
import app.subshell.prototype.ui.error.redditFeedAppendErrorPresentation
import app.subshell.prototype.ui.error.redditFeedErrorPresentation
import app.subshell.prototype.ui.error.communityForbiddenReason
import app.subshell.prototype.ui.error.communityForbiddenPresentation
import app.subshell.prototype.ui.error.CommunityForbiddenReason
import app.subshell.prototype.ui.uiDebugLog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.CircularProgressIndicator
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.account.blocksActionFor
import app.subshell.prototype.data.account.communityMembershipActionForJoined
import app.subshell.prototype.data.account.inProgressFor
import app.subshell.prototype.data.account.projectedJoined
import app.subshell.prototype.ui.components.MutedCommunityPostsHiddenCard
import app.subshell.prototype.ui.components.UserFlairSheet
import app.subshell.prototype.ui.components.InlineProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription

@Composable
fun CommunityScreen(
    communityName: String,
    state: SubshellState,
    redditFeedState: RedditFeedUiState = RedditFeedUiState(),
    onRefreshRedditFeed: (
        String,
        String,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    onAppendRedditFeed: (
        String,
        String,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    // ISSUE211_QUARANTINE_OPT_IN_V1
    onViewQuarantinedCommunity:
        (suspend (String, String) -> app.subshell.prototype.data.account.QuarantineOptInResult)? = null,
    redditSubscriptionsState: RedditSubscriptionsUiState =
        RedditSubscriptionsUiState(),
    onLoadRedditSubscriptions: (String, Boolean) -> Unit = { _, _ -> },
    redditCommunityInfoState: RedditCommunityInfoUiState =
        RedditCommunityInfoUiState(),
    onLoadRedditCommunityInfo: (String, String, Boolean) -> Unit =
        { _, _, _ -> },
    // ISSUE230_PINNED_POSTS_FIRST_V1
    redditPinnedPostsState: RedditCommunityPinnedPostsUiState = RedditCommunityPinnedPostsUiState(),
    onLoadRedditPinnedPosts: (String, String, Boolean) -> Unit = { _, _, _ -> },
    redditCommunityWikiState: RedditCommunityWikiUiState =
        RedditCommunityWikiUiState(),
    onLoadRedditCommunityWikiIndex: (String, String, Boolean) -> Unit =
        { _, _, _ -> },
    onLoadRedditCommunityWikiPage: (String, String, String, Boolean) -> Unit =
        { _, _, _, _ -> },
    onAuthorizeRedditCommunityWiki: (String, String) -> Unit = { _, _ -> },
    redditModerationState: RedditModerationUiState =
        RedditModerationUiState(),
    onLoadRedditModQueue: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onAppendRedditModQueue: (String, String) -> Unit = { _, _ -> },
    moderationActionState: RedditModeratorActionUiState =
        RedditModeratorActionUiState(),
    onModerateRedditQueueItem: (RedditModeratorActionRequest) -> Unit = {},
    onRetryRedditModerationAction: () -> Unit = {},
    onAuthorizeRedditModerationAction: () -> Unit = {},
    removalReasonState: RedditRemovalReasonUiState = RedditRemovalReasonUiState(),
    onLoadRedditRemovalReasons: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onAuthorizeRedditRemovalReasons: () -> Unit = {},
    communityMembershipState: CommunityMembershipUiState =
        CommunityMembershipUiState(),
    onChangeCommunityMembership: (
        String,
        String,
        CommunityMembershipAction,
    ) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    // NEXT01A1_COMMUNITY_LIST_DETAIL_SELECTION_V1
    onOpenPostSelection: ((Post) -> Unit)? = null,
    showContextHeader: Boolean = true,
    // NEXT01A5_SPLIT_AWARE_COMMUNITY_BACK_AFFORDANCE_V1
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val community = state.communityFor(communityName)
        ?: state.communityIdentityFor(communityName)
    val context = LocalContext.current
    val userNotices = LocalViewportErrorHostState.current
    // ISSUE239_NOTIFY_ABOUT_NEW_POSTS_FROM_THE_COMMUNITY_MENU_V1 (#239): the
    // community's ⋮ turns new-post notifications on and off; Android 13+ asks
    // for the notification permission first.
    fun watchCommunityPosts() {
        when (state.setWatchingCommunityPosts(community.name, watch = true)) {
            CommunityPostWatchChange.CHANGED -> {
                CommunityPostNotificationCenter.ensureChannel(context)
                userNotices.noticeInfo(
                    "Notifications on for ${state.subredditLabel(community.name)}",
                    "New posts are checked every 15 minutes",
                )
            }
            CommunityPostWatchChange.LIMIT_REACHED -> userNotices.noticeFailure(
                "You get notifications from $MAX_WATCHED_COMMUNITIES communities",
                "Turn one off in Settings › Notifications › New posts first",
            )
            CommunityPostWatchChange.UNCHANGED,
            CommunityPostWatchChange.UNAVAILABLE,
            -> Unit
        }
    }
    // ISSUE239_NOTIFICATIONS_ARE_ASKED_FOR_OR_OPENED_V1 (owner, phone check):
    // when Android answers without showing its question (the permission was
    // refused before) or notifications are off for subshell, the system's
    // notification settings open, and the community is turned on on return
    // once notifications are allowed. A "Don't allow" the reader just chose
    // is only said, not answered with the settings.
    var newPostPermissionDialogShown by remember { mutableStateOf(false) }
    var watchAfterNotificationSettings by remember(community.name) { mutableStateOf(false) }
    fun openNotificationSettingsThenWatch() {
        watchAfterNotificationSettings = true
        runCatching { context.startActivity(appNotificationSettingsIntent(context)) }
            .onFailure { watchAfterNotificationSettings = false }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { newPostPermissionDialogShown = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (watchAfterNotificationSettings) {
            watchAfterNotificationSettings = false
            if (CommunityPostNotificationCenter.canPost(context)) {
                watchCommunityPosts()
            } else {
                userNotices.noticeFailure(
                    "Notifications are not allowed",
                    "Allow notifications for subshell to get new posts",
                )
            }
        }
    }
    val newPostPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted && CommunityPostNotificationCenter.canPost(context) -> watchCommunityPosts()
            granted || !newPostPermissionDialogShown -> openNotificationSettingsThenWatch()
            else -> userNotices.noticeFailure(
                "Notifications are not allowed",
                "Allow notifications for subshell to get new posts",
            )
        }
    }
    fun toggleCommunityPostNotifications() {
        when {
            state.isWatchingCommunityPosts(community.name) -> {
                state.setWatchingCommunityPosts(community.name, watch = false)
                userNotices.noticeInfo(
                    "Notifications off for ${state.subredditLabel(community.name)}",
                    "No more notifications about its new posts",
                )
            }
            // The limit is said before anything is asked.
            state.watchedCommunityNames.size >= MAX_WATCHED_COMMUNITIES -> watchCommunityPosts()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> {
                newPostPermissionDialogShown = false
                newPostPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            !CommunityPostNotificationCenter.canPost(context) -> openNotificationSettingsThenWatch()
            else -> watchCommunityPosts()
        }
    }
    val aboutSheetVisible = rememberSaveable(community.name) {
        mutableStateOf(false)
    }
    // REDDITURL06_WIKI_PAGE_LINK_V1: a wiki link opens the about sheet on
    // that page.
    var linkedWikiPage by remember(community.name) { mutableStateOf<String?>(null) }
    LaunchedEffect(community.name, state.pendingCommunityWikiPage) {
        val page = state.consumeCommunityWikiPage(community.name) ?: return@LaunchedEffect
        linkedWikiPage = page
        aboutSheetVisible.value = true
    }
    val userFlairSheetVisible = rememberSaveable(community.name) {
        mutableStateOf(false)
    }
    val moderatorToolsNavigation = LocalModeratorToolsNavigation.current
    val communityMenuExpanded = rememberSaveable(community.name) {
        mutableStateOf(false)
    }
    val muteSheetVisible = rememberSaveable(community.name) {
        mutableStateOf(false)
    }
    val currentCommunityMute = state.communityMuteFor(community.name)
    val communityMuted = currentCommunityMute != null
    val launcherShortcutsEnabled by rememberLauncherShortcutsEnabled()
    val launcherShortcutPublisher = remember(context) { AndroidLauncherShortcutPublisher(context) }
    val launcherShortcutPinSupported = remember(launcherShortcutPublisher) {
        launcherShortcutPublisher.isPinSupported()
    }
    val mute01a3HidePosts =
        state.shouldHidePostsForMutedCommunity(community.name)
    LaunchedEffect(
        communityName,
        state.activeAccountIsAuthenticated,
        state.activeAccountId,
    ) {
        state.ensureCommunityIdentity(communityName)
        if (state.activeAccountIsAuthenticated) {
            onLoadRedditSubscriptions(
                state.activeAccountId,
                false,
            )
        }
    }
    val accent = Color(community.accent)
    val authenticatedRedditFeed = state.activeAccountIsAuthenticated
    val moderatorToolsAvailable =
        authenticatedRedditFeed &&
            redditModerationState.matchesAccount(state.activeAccountId) &&
            redditModerationState.directoryLoaded &&
            redditModerationState.moderates(community.name)
    val liveSubscriptions = redditSubscriptionsState.takeIf { subscriptions ->
        subscriptions.matches(state.activeAccountId)
    }
    val membershipTruthReady =
        !authenticatedRedditFeed ||
            liveSubscriptions?.let { subscriptions ->
                subscriptions.loaded &&
                    !subscriptions.fromCache &&
                    !subscriptions.loading
            } == true
    val authoritativeJoined = if (authenticatedRedditFeed) {
        liveSubscriptions
            ?.communities
            ?.any { candidate ->
                candidate.name.equals(
                    community.name,
                    ignoreCase = true,
                )
            }
            ?: false
    } else {
        community.id in state.joinedCommunityIds
    }
    val joined = if (authenticatedRedditFeed) {
        communityMembershipState.projectedJoined(
            accountStableId = state.activeAccountId,
            communityName = community.name,
            authoritativeJoined = authoritativeJoined,
        )
    } else {
        authoritativeJoined
    }
    val membershipAwaitingAuthorization =
        authenticatedRedditFeed &&
            communityMembershipState.matches(
                state.activeAccountId,
                community.name,
            ) &&
            communityMembershipState.phase ==
                CommunityMembershipPhase.AUTHORIZATION_REQUIRED
    val membershipTargetBusy =
        authenticatedRedditFeed &&
            (
                communityMembershipState.inProgressFor(
                    state.activeAccountId,
                    community.name,
                ) ||
                    membershipAwaitingAuthorization
            )
    val membershipFlowBusy =
        authenticatedRedditFeed &&
            (
                communityMembershipState.inProgress ||
                    communityMembershipState.phase ==
                    CommunityMembershipPhase.AUTHORIZATION_REQUIRED
            )
    // ISSUE97_MEMBERSHIP_SYNC_FAILURE_OFFERS_RETRY_V1: a failed subscriptions
    // sync left Join disabled on "Loading…" for good, with no word and no
    // retry; the failure is shown in the tray with Retry (a forced reload),
    // the button stops claiming to load, and pull-to-refresh reloads it too.
    val membershipSyncFailure = liveSubscriptions?.takeIf { subscriptions ->
        authenticatedRedditFeed &&
            !membershipTruthReady &&
            !subscriptions.loading &&
            subscriptions.failureKind != null
    }
    val membershipSyncFailurePresentation = membershipSyncFailure?.let { subscriptions ->
        redditSubscriptionsErrorPresentation(
            failureKind = requireNotNull(subscriptions.failureKind),
            details = subscriptions.failureDetails,
            retryable = subscriptions.failureRetryable,
            showingCachedContent = subscriptions.fromCache,
        ).copy(
            // The community itself still works; only Join/Leave waits.
            kind = ErrorPresentationKind.DEGRADED,
            title = "Couldn't check your communities",
        )
    }
    ReportViewportError(
        scopeKey = "community-membership:${state.activeAccountId}",
        presentation = membershipSyncFailurePresentation,
        failureDetails = membershipSyncFailure?.failureDetails,
        onRetry = if (membershipSyncFailurePresentation != null) {
            { onLoadRedditSubscriptions(state.activeAccountId, true) }
        } else {
            null
        },
    )
    val membershipFailurePresentation = if (
        authenticatedRedditFeed &&
        communityMembershipState.phase == CommunityMembershipPhase.FAILED &&
        communityMembershipState.matches(state.activeAccountId, community.name)
    ) {
        communityMembershipErrorPresentation(communityMembershipState)
    } else {
        null
    }
    val membershipActionEnabled = if (authenticatedRedditFeed) {
        membershipTruthReady &&
            !membershipFlowBusy &&
            !communityMembershipState.blocksActionFor(
                state.activeAccountId,
                community.name,
            )
    } else {
        state.localMutationsAllowed
    }
    val changeMembership = {
        if (authenticatedRedditFeed) {
            onChangeCommunityMembership(
                state.activeAccountId,
                community.name,
                communityMembershipActionForJoined(joined),
            )
        } else {
            state.toggleCommunity(community.id)
        }
    }
    val liveSort = state.communitySort.toRedditFeedSort()
    val timePeriodMenuExpanded = remember { mutableStateOf(false) }
    val pagingKey = remember(
        state.activeAccountSession,
        community.name,
        state.communitySort,
        state.listingTimePeriod,
    ) {
        communityPostFeedQueryKey(
            account = state.activeAccountSession,
            communityName = community.name,
            sort = state.communitySort,
            timePeriod = state.listingTimePeriod,
        )
    }
    val activeRedditFeedState = if (
        redditFeedState.matches(
            accountStableId = state.activeAccountId,
            queryKey = pagingKey,
        )
    ) {
        redditFeedState
    } else {
        RedditFeedUiState()
    }
    // FEEDS02_MUTED_COMMUNITY_LOADS_NOTHING_V1: a community muted in
    // hide-posts mode shows only its about card, so it fetches no pages.
    LaunchedEffect(pagingKey, state.postPagingInvalidationRevision, mute01a3HidePosts) {
        state.ensurePostPaging(pagingKey)
        if (authenticatedRedditFeed && !mute01a3HidePosts) {
            // ISSUE17_AUTHENTICATED_FEED_ENTRY_REVALIDATION_V1
            val decision = state.claimFeedEntryRefresh(
                pagingKey,
                deepInList = state.feedScrollAnchor(
                    communityFeedScrollKey(
                        community.name,
                        state.communitySort,
                        state.listingTimePeriod.takeIf { authenticatedRedditFeed }
                            ?: RedditListingTimePeriod.ALL,
                    ),
                ).isDeepForAutomaticRefresh(),
            )
            uiDebugLog(
                "subshell-issue17",
                "entry-refresh surface=community community=${community.name} sort=${state.communitySort.name.lowercase()}" +
                    " query=${pagingKey.value} " +
                    "decision=${decision.logToken} " +
                    "cached=${state.postPagingSnapshot(pagingKey).items.size}",
            )
            if (decision.execute) {
                onRefreshRedditFeed(
                    state.activeAccountId,
                    community.name,
                    liveSort,
                    state.listingTimePeriod,
                )
            }
        }
    }
    // ISSUE230_PINNED_POSTS_FIRST_V1: the pinned posts are read once per visit
    // and again on a pull to refresh.
    LaunchedEffect(state.activeAccountId, community.name, authenticatedRedditFeed, mute01a3HidePosts) {
        if (authenticatedRedditFeed && !mute01a3HidePosts) {
            onLoadRedditPinnedPosts(state.activeAccountId, community.name, false)
        }
    }
    val pagingSnapshot = state.postPagingSnapshot(pagingKey)
    val allPosts = pagingSnapshot.items
    // CONTENTFILTER04_FILTERED_SUMMARY_V1
    // CONTENTFILTER06_REVEAL_KEYED_ON_ACCOUNT_V1
    var filteredRevealed by rememberSaveable(state.activeAccountId, community.name) { mutableStateOf(false) }
    // FEEDS04_DERIVED_ID_SET_READS_V1: the id lists are read inside
    // derivedStateOf instead of being copied as remember keys on every
    // recomposition (readPostIds can hold thousands).
    val hiddenIdentities by remember(state) {
        derivedStateOf { hiddenPostIdentities(state.hiddenPostIds) }
    }
    val filteredCount by remember(
        allPosts,
        community.name,
        state.showNsfwPosts,
        state.contentFilters,
    ) {
        derivedStateOf {
            allPosts.count {
                communityNamesMatch(it.community, community.name) &&
                    !it.isHiddenBy(hiddenIdentities) &&
                    state.showsPost(it) &&
                    state.contentFilters.hidesOnCommunityPage(it, community.name)
            }
        }
    }
    val filteredSummaryVisible = state.filteredPostsSummary && filteredCount > 0
    val showFilteredPosts = filteredSummaryVisible && filteredRevealed
    val pinnedPosts = if (authenticatedRedditFeed) {
        redditPinnedPostsState.postsFor(state.activeAccountId, community.name)
    } else {
        emptyList()
    }
    val feedWithPinned by remember(
        allPosts,
        pinnedPosts,
        community.name,
        state.communitySort,
        state.showNsfwPosts,
        state.contentFilters,
        state.pinnedPostsPresentation,
        showFilteredPosts,
        mute01a3HidePosts,
    ) {
        derivedStateOf {
            if (mute01a3HidePosts) return@derivedStateOf CommunityFeedWithPinned(emptyList(), emptyList())
            // CONTENTFILTER02_EVERY_FEED_SURFACE_V1
            val shown = { post: Post ->
                communityNamesMatch(post.community, community.name) &&
                    !post.isHiddenBy(hiddenIdentities) &&
                    state.showsPost(post) &&
                    (showFilteredPosts || !state.contentFilters.hidesOnCommunityPage(post, community.name))
            }
            val ordered = communityFeedPostsInDisplayOrder(
                posts = allPosts.filter(shown),
                sort = state.communitySort,
                authenticatedRedditFeed = authenticatedRedditFeed,
            )
            if (authenticatedRedditFeed) {
                communityFeedWithPinnedPosts(ordered, pinnedPosts.filter(shown), state.pinnedPostsPresentation)
            } else {
                CommunityFeedWithPinned(emptyList(), ordered)
            }
        }
    }
    val visiblePosts = feedWithPinned.feed
    val pinnedHighlights = feedWithPinned.highlights
    val contentFiltersHideAllPosts =
        visiblePosts.isEmpty() &&
            allPosts.any { post ->
                communityNamesMatch(post.community, community.name) &&
                    !post.isHiddenBy(hiddenIdentities) &&
                    state.showsPost(post)
            }
    // LOWUX3_COMMUNITY_EMPTY_STATE_SAYS_WHY_V1 (OCB-190): a page the reader
    // hid entirely read "No cached Reddit posts / reconnect".
    val hiddenPostsHideAllPosts =
        visiblePosts.isEmpty() &&
            allPosts.any { post -> communityNamesMatch(post.community, community.name) } &&
            allPosts.filter { post -> communityNamesMatch(post.community, community.name) }
                .all { post -> post.isHiddenBy(hiddenIdentities) }
    val nsfwPreferenceHidesAllPosts =
        !state.showNsfwPosts &&
            visiblePosts.isEmpty() &&
            allPosts.any { post ->
                post.nsfw &&
                    !post.isHiddenBy(hiddenIdentities) &&
                    communityNamesMatch(post.community, community.name)
            }
    val communityReadFailure = activeRedditFeedState.failureKind?.takeUnless { failure ->
        failure == RedditFeedUiFailureKind.END_REACHED
    }
    // ISSUE211_FORBIDDEN_COMMUNITY_SAYS_WHY_V1: Reddit's reason for a 403.
    val forbiddenReason = communityForbiddenReason(activeRedditFeedState.failureDetails)
        .takeIf { communityReadFailure == RedditFeedUiFailureKind.HTTP }
    val quarantineScope = rememberCoroutineScope()
    val quarantineNoticeHost = app.subshell.prototype.ui.error.LocalViewportErrorHostState.current
    var quarantineOptInRunning by remember(community.name) { mutableStateOf(false) }
    // ISSUE211_QUARANTINE_OPT_IN_V1: sent once on the tap; a tap while it runs
    // does nothing. When it was sent (or its answer was lost) the reload is the
    // read-back; when it was not, the tray says why
    // (ISSUE211_QUARANTINE_OPT_IN_RESULT_IS_SHOWN_V1).
    fun viewQuarantinedCommunity() {
        val optIn = onViewQuarantinedCommunity ?: return
        if (quarantineOptInRunning) return
        val account = state.activeAccountId
        val name = community.name
        quarantineOptInRunning = true
        quarantineScope.launch {
            try {
                val result = optIn(account, name)
                if (state.activeAccountId != account) return@launch
                val notice = app.subshell.prototype.ui.error.quarantineOptInPresentation(result, "r/$name")
                val scope = "quarantine-opt-in:${name.lowercase()}"
                if (notice == null) {
                    quarantineNoticeHost?.clear(scope)
                    onRefreshRedditFeed(account, name, liveSort, state.listingTimePeriod)
                } else {
                    quarantineNoticeHost?.present(
                        scopeKey = scope,
                        presentation = notice,
                        failureDetails = (result as? app.subshell.prototype.data.account.QuarantineOptInResult.Refused)
                            ?.httpStatusCode
                            ?.let { code -> app.subshell.prototype.data.error.FailureDetails(httpStatusCode = code) },
                        onRetry = null,
                    )
                }
            } finally {
                quarantineOptInRunning = false
            }
        }
    }
    val degradedCommunityFeedPresentation = if (
        authenticatedRedditFeed &&
        communityReadFailure != null &&
        visiblePosts.isNotEmpty()
    ) {
        if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
            redditFeedAppendErrorPresentation(
                failureKind = communityReadFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
            )
        } else if (forbiddenReason != null) {
            communityForbiddenPresentation(
                reason = forbiddenReason,
                communityLabel = state.subredditLabel(community.name),
                showingCachedContent = true,
            )
        } else {
            redditFeedErrorPresentation(
                failureKind = communityReadFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
                showingCachedContent = true,
            ).copy(
                title = "Couldn't refresh ${state.subredditLabel(community.name)}",
            )
        }
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "community-feed:${state.activeAccountId}:${community.name}:${pagingKey.value}",
        presentation = degradedCommunityFeedPresentation,
        failureDetails = activeRedditFeedState.failureDetails,
        onRetry = if (degradedCommunityFeedPresentation != null) {
            if (forbiddenReason == CommunityForbiddenReason.QUARANTINED) {
                { viewQuarantinedCommunity() }
            } else if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
                {
                    onAppendRedditFeed(
                        state.activeAccountId,
                        community.name,
                        liveSort,
                        state.listingTimePeriod,
                    )
                }
            } else {
                {
                    onRefreshRedditFeed(
                        state.activeAccountId,
                        community.name,
                        liveSort,
                        state.listingTimePeriod,
                    )
                }
            }
        } else {
            null
        },
    )
    val liveAppendEnabled = canAppendRedditFeed(
        live = authenticatedRedditFeed && !mute01a3HidePosts,
        nextCursor = pagingSnapshot.nextCursor,
        state = activeRedditFeedState,
    )
    val showLivePagingStatus = authenticatedRedditFeed && !mute01a3HidePosts && (
        activeRedditFeedState.failureKind == RedditFeedUiFailureKind.END_REACHED ||
            (activeRedditFeedState.failureKind == null &&
                visiblePosts.isEmpty())
        )

    LaunchedEffect(
        community.name,
        state.communitySort,
        allPosts,
        visiblePosts,
        pagingSnapshot.nextCursor,
    ) {
        if (authenticatedRedditFeed) {
            uiDebugLog(
                FEED_ORDER_LOG_TAG,
                "display community=${community.name} sort=${state.communitySort.name.lowercase()} " +
                    "stored=${allPosts.size} visible=${visiblePosts.size} " +
                    "storedHead=${allPosts.take(FEED_ORDER_SAMPLE_SIZE).joinToString(",") { it.id }} " +
                    "visibleHead=${visiblePosts.take(FEED_ORDER_SAMPLE_SIZE).joinToString(",") { it.id }} " +
                    "hasNext=${pagingSnapshot.nextCursor != null}",
            )
        }
    }

    val pagingFooterKey = pagingSnapshot.pagingFooterKey
    // ISSUE234_FEED_POST_LAYOUTS_V1
    val postLayout = state.postLayout
    val gridColumns = rememberFeedGridColumns()
    val feedItemKeys = remember(
        visiblePosts,
        postLayout,
        gridColumns.count,
        pagingFooterKey,
        showLivePagingStatus,
        filteredSummaryVisible,
        mute01a3HidePosts,
    ) {
        buildList {
            if (showContextHeader) add("context-header")
            add("community-header")
            if (mute01a3HidePosts) {
                add("muted-community-about-only")
                return@buildList
            }
            add("sort")
            if (pinnedHighlights.isNotEmpty()) add("community-highlights")
            if (filteredSummaryVisible) add("filtered-summary")
            if (visiblePosts.isEmpty()) {
                add("empty")
            } else {
                addAll(feedPostItemKeys(visiblePosts, postLayout, gridColumns.count))
            }
            pagingFooterKey?.let(::add)
            if (showLivePagingStatus) add("reddit-paging")
        }
    }
    val listState = rememberFeedScrollState(
        state = state,
        scrollKey = communityFeedScrollKey(
            community.name,
            state.communitySort,
            state.listingTimePeriod.takeIf { authenticatedRedditFeed }
                ?: RedditListingTimePeriod.ALL,
        ),
        itemKeys = feedItemKeys,
    )
    val captureCurrentScroll = {
        captureFeedScrollAnchor(
            state = state,
            scrollKey = communityFeedScrollKey(
                community.name,
                state.communitySort,
                state.listingTimePeriod.takeIf { authenticatedRedditFeed }
                    ?: RedditListingTimePeriod.ALL,
            ),
            itemKeys = feedItemKeys,
            listState = listState,
        )
    }

    ObserveLiveFeedAutoAppend(
        listState = listState,
        sessionKey = "${pagingKey.value}|generation=${pagingSnapshot.generation}",
        appendEnabled = liveAppendEnabled,
        contentToken = pagingSnapshot.nextCursor,
        loadedItemCount = pagingSnapshot.items.size,
        visibleItemCount = visiblePosts.size,
        onAppend = {
            onAppendRedditFeed(
                state.activeAccountId,
                community.name,
                liveSort,
                state.listingTimePeriod,
            )
        },
    )

    val revealedMediaIds = state.revealedMediaIds.toSet()
    val videoPostIds = remember(visiblePosts, revealedMediaIds, postLayout) {
        // ISSUE234_FEED_POST_LAYOUTS_V1: only Large plays media in the feed.
        visiblePosts
            .filter { postLayout == PostLayout.LARGE }
            .filter { it.kind == PostKind.VIDEO && state.sensitiveMediaAutoplayAllowed(it) }
            .map(Post::id)
    }
    val terminalVideoId = rememberTerminalVideoId(videoPostIds, pagingSnapshot.endReached)
    LaunchedEffect(videoPostIds, terminalVideoId) {
        state.updateVideoFeedContext(videoPostIds, terminalVideoId)
    }

    ObserveFeedVideoViewport(listState = listState, state = state)

    val animatedPostIds = remember(visiblePosts, revealedMediaIds, postLayout) {
        visiblePosts
            .filter { postLayout == PostLayout.LARGE }
            .filter {
                it.kind in listOf(PostKind.GIF, PostKind.GALLERY) &&
                    state.sensitiveMediaAutoplayAllowed(it)
            }
            .mapTo(mutableSetOf()) { it.id }
    }
    LaunchedEffect(listState, animatedPostIds, state.videoAutoplayAllowed) {
        if (!state.videoAutoplayAllowed) {
            state.setFeedAutoplayAnimatedMedia(null)
        } else {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .filter { it.key in animatedPostIds }
                    .maxByOrNull { item ->
                        val start = item.offset.coerceAtLeast(0)
                        val end = (item.offset + item.size).coerceAtMost(listState.layoutInfo.viewportEndOffset)
                        (end - start).coerceAtLeast(0)
                    }
                    ?.key as? String
            }.distinctUntilChanged().collect(state::setFeedAutoplayAnimatedMedia)
        }
    }
    // PARITY01_FEED_SURFACE_CLEARS_ONLY_WHAT_IT_OWNS_V1
    DisposableEffect(Unit) {
        val owner = Any()
        state.claimFeedSurface(owner)
        onDispose { state.releaseFeedSurface(owner) }
    }

    // M3223_ONE_ROUTE_TOP_BAR_V1: the community's Back, title and actions stay
    // reachable while its posts scroll (they were the list's first row).
    // STEP3_NO_BAR_NO_BAR_SCROLL_V1: without its context header (the
    // community beside a post on a wide screen) there is no bar to hide, and a
    // bar never laid out would take every upward drag.
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(
        canScroll = {
            showContextHeader && (listState.canScrollForward || listState.canScrollBackward)
        },
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
    ) {
        if (showContextHeader) {
            RouteTopBar(
                title = state.subredditLabel(community.name),
                scrollBehavior = routeTopBarScroll,
                onBack = if (showBackButton) onBack else null,
                actions = {
                    IconButton(onClick = { state.openSearch() }) {
                        Icon(
                            Symbols.Search,
                            contentDescription =
                                "Search ${state.subredditLabel(community.name)}",
                        )
                    }
                    // MOD01A1_COMMUNITY_TOP_BAR_MOD_TOOLS_ENTRY_V1
                    if (moderatorToolsAvailable) {
                        IconButton(
                            onClick = {
                                moderatorToolsNavigation.openRoot(community.name)
                            },
                        ) {
                            Icon(
                                Symbols.AdminPanelSettings,
                                contentDescription =
                                    "Mod tools for ${state.subredditLabel(community.name)}",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    // COMMUNITYINFO01A31_TOP_BAR_ABOUT_ACTION_V1
                    IconButton(
                        onClick = { aboutSheetVisible.value = true },
                    ) {
                        Icon(
                            Symbols.Info,
                            contentDescription =
                                "About ${state.subredditLabel(community.name)}",
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { communityMenuExpanded.value = true },
                        ) {
                            Icon(
                                Symbols.MoreVert,
                                contentDescription =
                                    "Actions for ${state.subredditLabel(community.name)}",
                            )
                        }
                        DropdownMenu(
                            expanded = communityMenuExpanded.value,
                            onDismissRequest = { communityMenuExpanded.value = false },
                        ) {
                            // USERFLAIR01A1_COMMUNITY_OVERFLOW_ENTRY_V1
                            if (state.activeAccountIsAuthenticated) {
                                DropdownMenuItem(
                                    text = { Text("User flair") },
                                    leadingIcon = {
                                        Icon(Symbols.Person, contentDescription = null)
                                    },
                                    onClick = {
                                        communityMenuExpanded.value = false
                                        userFlairSheetVisible.value = true
                                    },
                                )
                            }

                            if (state.communityPostNotificationsAvailable) {
                                val watchingPosts = state.isWatchingCommunityPosts(community.name)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (watchingPosts) {
                                                "Stop new-post notifications"
                                            } else {
                                                "Notify about new posts"
                                            },
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (watchingPosts) Symbols.NotificationsOff else Symbols.Notifications,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        communityMenuExpanded.value = false
                                        toggleCommunityPostNotifications()
                                    },
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (communityMuted) {
                                            "Unmute community"
                                        } else {
                                            "Mute community…"
                                        },
                                    )
                                },
                                onClick = {
                                    communityMenuExpanded.value = false
                                    if (communityMuted) {
                                        state.unmuteCommunity(community.name)
                                    } else {
                                        muteSheetVisible.value = true
                                    }
                                },
                            )
                            // ISSUE37_LAUNCHER_SHORTCUTS_V1: never for a hidden community.
                            if (
                                launcherShortcutsEnabled &&
                                launcherShortcutPinSupported &&
                                !state.isCommunityHidden(community.name)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add to home screen") },
                                    leadingIcon = {
                                        Icon(Symbols.AddToHomeScreen, contentDescription = null)
                                    },
                                    onClick = {
                                        communityMenuExpanded.value = false
                                        val requested = launcherShortcutPublisher.requestPin(
                                            LauncherShortcutEntry.Community(community.name),
                                        )
                                        if (!requested) {
                                            userNotices.noticeFailure("Couldn't add the shortcut", "Your launcher did not accept it") // ISSUE222_NO_TOASTS_V1
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }
        GoogleFeedVideoViewport(
            state = state,
            sessionKey = pagingKey,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            RefreshableLazyColumn(
                modifier = Modifier.fillMaxSize().then(gridColumns.measureModifier),
                state = listState,
                // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1
                scrollPositionIndicator = state.scrollPositionIndicator,
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(feedItemSpacing(postLayout, 9.dp)),
                isRefreshing = if (authenticatedRedditFeed) {
                    activeRedditFeedState.refreshing
                } else {
                    pagingSnapshot.refreshing
                },
                onRefresh = if (mute01a3HidePosts) {
                    null
                } else if (authenticatedRedditFeed) {
                    {
                        if (!membershipTruthReady && liveSubscriptions?.loading != true) {
                            onLoadRedditSubscriptions(state.activeAccountId, true)
                        }
                        onLoadRedditPinnedPosts(state.activeAccountId, community.name, true)
                        onRefreshRedditFeed(
                            state.activeAccountId,
                            community.name,
                            liveSort,
                            state.listingTimePeriod,
                        )
                    }
                } else {
                    { state.refreshPostPaging(pagingKey) }
                },
                loadNextEnabled = if (authenticatedRedditFeed) {
                    liveAppendEnabled
                } else {
                    pagingSnapshot.canAppend && !mute01a3HidePosts
                },
                loadNextToken = if (authenticatedRedditFeed) {
                    pagingSnapshot.nextCursor
                } else {
                    pagingSnapshot.items.size
                },
                onLoadNext = {
                    if (authenticatedRedditFeed) {
                        onAppendRedditFeed(
                            state.activeAccountId,
                            community.name,
                            liveSort,
                            state.listingTimePeriod,
                        )
                    } else {
                        state.appendPostPaging(pagingKey)
                    }
                },
            ) {
                if (showContextHeader) {
                    // An empty anchor: feedItemKeys and the stored scroll anchors count it
                    // (M3223_ONE_ROUTE_TOP_BAR_V1 moved its contents to RouteTopBar).
                    item("context-header") {}
                }

                item("community-header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RemoteIdentityImage(
                            uri = community.bannerImageUri,
                            contentDescription = null, // decorative beside the visible name (ISSUE219)
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(124.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(accent.copy(alpha = 0.24f)),
                            )
                        }
                        // ISSUE220_COMMUNITY_HEADER_REFLOWS_AT_LARGE_TEXT_V1: from 130 % text the
                        // star and Join move to their own row (as the main header,
                        // A11Y01A1), so the name and member count get the width ("r/sub…",
                        // "member/s" at 200 %).
                        val headerLargeText = LocalDensity.current.fontScale >= 1.3f
                        val headerActions: @Composable RowScope.() -> Unit = {
                            val favorite = state.isCommunityFavorite(community.name)
                            IconButton(
                                onClick = { state.toggleFavoriteCommunity(community.name) },
                            ) {
                                Icon(
                                    imageVector = if (favorite) {
                                        Symbols.StarFilled
                                    } else {
                                        Symbols.Star
                                    },
                                    contentDescription = if (favorite) {
                                        "Remove ${state.subredditLabel(community.name)} from favorites"
                                    } else {
                                        "Add ${state.subredditLabel(community.name)} to favorites"
                                    },
                                    tint = favoriteStarTint(favorite),
                                )
                            }
                            if (joined) {
                                FilledTonalButton(
                                    onClick = changeMembership,
                                    enabled = membershipActionEnabled,
                                ) {
                                    if (membershipTargetBusy) {
                                        InlineProgressIndicator()
                                        Spacer(Modifier.width(5.dp))
                                        Text("Leaving…")
                                    } else {
                                        Icon(
                                            Symbols.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text("Joined")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = changeMembership,
                                    enabled = membershipActionEnabled,
                                ) {
                                    if (membershipTargetBusy) {
                                        InlineProgressIndicator()
                                        Spacer(Modifier.width(5.dp))
                                        Text("Joining…")
                                    } else if (
                                        authenticatedRedditFeed &&
                                        !membershipTruthReady &&
                                        membershipSyncFailure == null
                                    ) {
                                        Text("Loading…")
                                    } else {
                                        Text("Join")
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RemoteIdentityImage(
                                uri = community.iconImageUri,
                                contentDescription = null, // decorative beside the visible name (ISSUE219)
                                modifier = Modifier
                                    .offset(y = (-28).dp)
                                    .size(64.dp)
                                    .clip(CircleShape),
                            ) {
                                CommunityAvatar(
                                    name = community.name,
                                    accent = accent,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.subredditLabel(community.name),
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = if (headerLargeText) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${community.members} members",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!headerLargeText) headerActions()
                        }
                        if (headerLargeText) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 12.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                content = headerActions,
                            )
                        }
                        if (currentCommunityMute != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                onClick = { muteSheetVisible.value = true },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Symbols.VolumeOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 10.dp),
                                    ) {
                                        Text(
                                            text = communityMuteStatusText(
                                                context = context,
                                                mute = currentCommunityMute,
                                                nowEpochMillis = state.communityMuteClockEpochMillis,
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            text = "Hidden from Home, Popular and All",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Icon(
                                        imageVector = Symbols.KeyboardArrowRight,
                                        contentDescription = "Open mute settings",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            community.description,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        membershipFailurePresentation?.let { presentation ->
                            ErrorNotice(
                                presentation = presentation,
                                diagnosticScopeKey = "community_membership",
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 10.dp,
                                ),
                                onAction = { action ->
                                    if (action == ErrorActionKind.RETRY) {
                                        communityMembershipState.action?.let { membershipAction ->
                                            onChangeCommunityMembership(
                                                state.activeAccountId,
                                                community.name,
                                                membershipAction,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                if (mute01a3HidePosts) {
                    item("muted-community-about-only") {
                        MutedCommunityPostsHiddenCard(
                            communityLabel = state.subredditLabel(community.name),
                            onManageMute = {
                                muteSheetVisible.value = true
                            },
                        )
                    }
                } else {
                    item("sort") {
                        // M3225_COMMUNITY_SORT_IS_A_MENU_V1 (#225, owner): the seven sorts are
                        // a sort button with a checked menu, like the comment sort, not a
                        // row of filter chips (which read as checkboxes); Top and
                        // Controversial add a time button the same way.
                        val sortMenuExpanded = remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = ScreenMargin + 4.dp, end = ScreenMargin - 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Posts",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                TextButton(onClick = { sortMenuExpanded.value = true }) {
                                    Text(
                                        state.communitySort.label,
                                        modifier = Modifier.semantics {
                                            contentDescription = "Sort posts: ${state.communitySort.label}"
                                        },
                                    )
                                    Icon(Symbols.KeyboardArrowDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded.value,
                                    onDismissRequest = { sortMenuExpanded.value = false },
                                ) {
                                    CommunitySort.entries.forEach { sort ->
                                        DropdownMenuItem(
                                            text = { Text(sort.label) },
                                            modifier = Modifier.semantics { selected = sort == state.communitySort },
                                            trailingIcon = if (sort == state.communitySort) {
                                                { Icon(Symbols.Check, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                state.updateCommunitySort(sort)
                                                sortMenuExpanded.value = false
                                            },
                                        )
                                    }
                                }
                            }
                            if (authenticatedRedditFeed && state.communitySort.supportsTimePeriod) {
                                Box {
                                    TextButton(onClick = { timePeriodMenuExpanded.value = true }) {
                                        Text(
                                            state.listingTimePeriod.label,
                                            modifier = Modifier.semantics {
                                                contentDescription = "Time: ${state.listingTimePeriod.label}"
                                            },
                                        )
                                        Icon(
                                            Symbols.KeyboardArrowDown,
                                            contentDescription = "Choose listing time period",
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = timePeriodMenuExpanded.value,
                                        onDismissRequest = { timePeriodMenuExpanded.value = false },
                                    ) {
                                        RedditListingTimePeriod.entries.forEach { timePeriod ->
                                            DropdownMenuItem(
                                                text = { Text(timePeriod.label) },
                                                modifier = Modifier.semantics {
                                                    selected = timePeriod == state.listingTimePeriod
                                                },
                                                trailingIcon = if (timePeriod == state.listingTimePeriod) {
                                                    { Icon(Symbols.Check, contentDescription = null) }
                                                } else {
                                                    null
                                                },
                                                onClick = {
                                                    state.updateListingTimePeriod(timePeriod)
                                                    timePeriodMenuExpanded.value = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            // ISSUE234_LAYOUT_CHOICE_BESIDE_THE_FEED_V1
                            PostLayoutMenuButton(state)
                        }
                    }

                    // ISSUE230_PINNED_POSTS_FIRST_V1
                    if (pinnedHighlights.isNotEmpty()) {
                        item("community-highlights") {
                            CommunityHighlightsRow(
                                posts = pinnedHighlights,
                                onOpen = { post ->
                                    captureCurrentScroll()
                                    onOpenPostSelection?.invoke(post)
                                        ?: state.openFromCommunity(post, communityName)
                                },
                            )
                        }
                    }

                    if (filteredSummaryVisible) {
                        item("filtered-summary") {
                            FilteredPostsSummaryRow(
                                filteredCount = filteredCount,
                                revealed = filteredRevealed,
                                onToggle = { filteredRevealed = !filteredRevealed },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    if (visiblePosts.isEmpty()) {
                        item("empty") {
                            if (authenticatedRedditFeed && communityReadFailure != null) {
                                // OCB-190: a failed load-more (filters hid the first page)
                                // is retried as a load-more, as on Home (OCB-216).
                                val appendFailed =
                                    activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND
                                ErrorNotice(
                                    presentation = if (forbiddenReason != null && !appendFailed) {
                                        communityForbiddenPresentation(
                                            reason = forbiddenReason,
                                            communityLabel = state.subredditLabel(community.name),
                                            showingCachedContent = false,
                                        )
                                    } else if (appendFailed) {
                                        redditFeedAppendErrorPresentation(
                                            failureKind = communityReadFailure,
                                            details = activeRedditFeedState.failureDetails,
                                            retryable = activeRedditFeedState.failureRetryable,
                                        )
                                    } else {
                                        redditFeedErrorPresentation(
                                            failureKind = communityReadFailure,
                                            details = activeRedditFeedState.failureDetails,
                                            retryable = activeRedditFeedState.failureRetryable,
                                            showingCachedContent = false,
                                        )
                                    },
                                    diagnosticScopeKey = "community_feed",
                                    onAction = { action ->
                                        if (
                                            action == ErrorActionKind.RETRY &&
                                            forbiddenReason == CommunityForbiddenReason.QUARANTINED &&
                                            !appendFailed
                                        ) {
                                            viewQuarantinedCommunity()
                                        } else if (action == ErrorActionKind.RETRY && appendFailed) {
                                            onAppendRedditFeed(
                                                state.activeAccountId,
                                                community.name,
                                                liveSort,
                                                state.listingTimePeriod,
                                            )
                                        } else if (action == ErrorActionKind.RETRY) {
                                            onRefreshRedditFeed(
                                                state.activeAccountId,
                                                community.name,
                                                liveSort,
                                                state.listingTimePeriod,
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = ScreenMargin),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .padding(horizontal = ScreenMargin)
                                        .fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            when {
                                                hiddenPostsHideAllPosts -> "All posts are hidden"
                                                contentFiltersHideAllPosts -> "All posts are filtered"
                                                nsfwPreferenceHidesAllPosts -> "NSFW posts are hidden"
                                                authenticatedRedditFeed && activeRedditFeedState.inProgress ->
                                                    "Loading from Reddit…"
                                                authenticatedRedditFeed -> "No cached Reddit posts"
                                                else -> "No local posts yet"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            when {
                                                hiddenPostsHideAllPosts ->
                                                    "Posts you hide stay out of your feeds. Your profile's Hidden tab lists them."
                                                contentFiltersHideAllPosts ->
                                                    "Your content filters hide every post here. Change them in Settings."
                                                nsfwPreferenceHidesAllPosts ->
                                                    "Use the quick switch in the navigation menu to show them."
                                                authenticatedRedditFeed && activeRedditFeedState.inProgress ->
                                                    "The first page is on its way."
                                                authenticatedRedditFeed ->
                                                    "Pull to refresh, or reconnect the account if the session needs attention."
                                                else ->
                                                    "This community is ready for a fixture when its interaction matters."
                                            },
                                            modifier = Modifier.padding(top = 6.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        feedPostItems(
                            posts = visiblePosts,
                            layout = postLayout,
                            columns = gridColumns.count,
                            horizontalPadding = ScreenMargin,
                        ) { post, itemModifier ->
                            PostCard(
                                post = post,
                                state = state,
                                layout = postLayout,
                                onOpen = {
                                    // NEXT01A1_COMMUNITY_LIST_DETAIL_SELECTION_V1
                                    onOpenPostSelection?.invoke(post)
                                        ?: state.openFromCommunity(post, communityName)
                                },
                                beforeNavigation = captureCurrentScroll,
                                modifier = itemModifier,
                            )
                        }
                    }

                    if (pagingFooterKey != null) {
                        item(key = pagingFooterKey) {
                            RepositoryPagingFooter(
                                snapshot = pagingSnapshot,
                                onRetry = { state.retryPostPaging(pagingKey) },
                                modifier = Modifier.padding(horizontal = ScreenMargin),
                            )
                        }
                    }
                    if (showLivePagingStatus) {
                        item("reddit-paging") {
                            RedditPagingControl(
                                state = activeRedditFeedState,
                                hasPosts = visiblePosts.isNotEmpty(),
                                canAppend = pagingSnapshot.nextCursor != null,
                                onRefresh = {
                                    onRefreshRedditFeed(
                                        state.activeAccountId,
                                        community.name,
                                        liveSort,
                                        state.listingTimePeriod,
                                    )
                                },
                            )
                        }
                    }

                }
            }
            }
    }

    if (
        moderatorToolsNavigation.isVisible(community.name) &&
        moderatorToolsAvailable
    ) {
        LaunchedEffect(
            state.activeAccountId,
            community.name,
        ) {
            onLoadRedditModQueue(
                state.activeAccountId,
                community.name,
                false,
            )
        }
        ModeratorToolsSheet(
            communityLabel = state.subredditLabel(community.name),
            communityName = community.name,
            accountStableId = state.activeAccountId,
            uiState = redditModerationState,
            onDismiss = {
                moderatorToolsNavigation.dismiss(community.name)
            },
            onRefresh = {
                onLoadRedditModQueue(
                    state.activeAccountId,
                    community.name,
                    true,
                )
            },
            onAppend = {
                onAppendRedditModQueue(
                    state.activeAccountId,
                    community.name,
                )
            },
            actionUiState = moderationActionState,
            onModerate = onModerateRedditQueueItem,
            onRetryAction = onRetryRedditModerationAction,
            onAuthorizeAction = onAuthorizeRedditModerationAction,
            removalReasonState = removalReasonState,
            onLoadRemovalReasons = { force ->
                onLoadRedditRemovalReasons(
                    state.activeAccountId,
                    community.name,
                    force,
                )
            },
            onAuthorizeRemovalReasons = onAuthorizeRedditRemovalReasons,
            // MODRESPONSE01A3_SAVED_RESPONSES_PICKER_V1
            savedResponses = SavedResponseActions(
                responses = state.moderatorSavedResponses,
                onSave = state::saveModeratorResponse,
                onDelete = state::deleteModeratorResponse,
                initialType = state.moderatorRemovalMessageType(state.activeAccountId),
                onTypeUsed = { type ->
                    state.rememberModeratorRemovalMessageType(state.activeAccountId, type)
                },
            ),
        )
    }

    if (muteSheetVisible.value) {
        CommunityMuteSheet(
            communityLabel = state.subredditLabel(community.name),
            nowEpochMillis = System.currentTimeMillis(),
            onDismiss = { muteSheetVisible.value = false },
            onMute = { expiresAtEpochMillis ->
                state.muteCommunity(community.name, expiresAtEpochMillis)
            },
            currentMute = currentCommunityMute,
            onUnmute = if (currentCommunityMute != null) {
                { state.unmuteCommunity(community.name) }
            } else {
                null
            },
        )
    }

    if (
        aboutSheetVisible.value &&
        state.activeAccountIsAuthenticated
    ) {
        LaunchedEffect(
            aboutSheetVisible.value,
            state.activeAccountId,
            community.name,
        ) {
            onLoadRedditCommunityInfo(
                state.activeAccountId,
                community.name,
                false,
            )
        }
    }

    if (userFlairSheetVisible.value) {
        UserFlairSheet(
            state = state,
            communityName = community.name,
            onDismiss = { userFlairSheetVisible.value = false },
        )
    }

    if (aboutSheetVisible.value) {
        CommunityAboutSheet(
            communityLabel = state.subredditLabel(community.name),
            communityName = community.name,
            accountStableId = state.activeAccountId,
            authenticated = state.activeAccountIsAuthenticated,
            fallbackDescription = community.description,
            uiState = redditCommunityInfoState,
            wikiUiState = redditCommunityWikiState,
            onLoadWikiIndex = onLoadRedditCommunityWikiIndex,
            onLoadWikiPage = onLoadRedditCommunityWikiPage,
            onAuthorizeWiki = onAuthorizeRedditCommunityWiki,
            linkedWikiPage = linkedWikiPage,
            onLinkedWikiPageShown = { linkedWikiPage = null },
            onDismiss = { aboutSheetVisible.value = false },
            onRetry = {
                if (state.activeAccountIsAuthenticated) {
                    onLoadRedditCommunityInfo(
                        state.activeAccountId,
                        community.name,
                        true,
                    )
                }
            },
            moderatorIdentity = { username ->
                state.userFor(username)
            },
            onEnsureModeratorIdentity = { username ->
                state.ensureUserIdentity(username)
            },
            onOpenModerator = { username ->
                // COMMUNITYINFO01A31_MODERATOR_RETURN_TO_ABOUT_V1
                state.openUser(username)
            },
        )
    }
}

internal fun communityNamesMatch(postCommunity: String, selectedCommunity: String): Boolean {
    val normalizedPost = runCatching {
        normalizeRedditCommunityName(postCommunity)
    }.getOrNull() ?: return false
    val normalizedSelected = runCatching {
        normalizeRedditCommunityName(selectedCommunity)
    }.getOrNull() ?: return false
    return normalizedPost == normalizedSelected
}

private const val FEED_ORDER_LOG_TAG = "subshell-feed"
private const val FEED_ORDER_SAMPLE_SIZE = 5
