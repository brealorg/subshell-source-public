package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.favoriteStarTint
import app.subshell.prototype.ui.components.FilteredPostsSummaryRow
import app.subshell.prototype.ui.components.rememberLabelsFitSlots
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.model.COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH
import app.subshell.prototype.model.COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH
import app.subshell.prototype.model.COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.ui.CommunityDiscoveryLayout
import app.subshell.prototype.ui.ObserveLiveFeedAutoAppend
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.PostLayout
import app.subshell.prototype.ui.ObserveFeedVideoViewport
import app.subshell.prototype.ui.GoogleFeedVideoViewport
import app.subshell.prototype.ui.rememberTerminalVideoId
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import app.subshell.prototype.ui.feedItemSpacing
import app.subshell.prototype.ui.feedPostItems
import app.subshell.prototype.ui.rememberFeedGridColumns
import app.subshell.prototype.ui.RECENT_COMMUNITY_HISTORY_LIMIT
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.CommunityAvatar
import app.subshell.prototype.ui.components.RefreshableLazyColumn
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.copyCustomFeedLink
import app.subshell.prototype.ui.customFeedLinkShareable
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.communityMembershipErrorPresentation
import app.subshell.prototype.ui.error.customFeedCreateErrorPresentation
import app.subshell.prototype.ui.error.customFeedManageErrorPresentation
import app.subshell.prototype.ui.error.ReportViewportError
import app.subshell.prototype.ui.error.customFeedPostsErrorPresentation
import app.subshell.prototype.ui.error.redditCommunityDiscoveryErrorPresentation
import app.subshell.prototype.ui.error.redditCommunityDiscoverySuggestionErrorPresentation
import app.subshell.prototype.ui.error.redditSubscriptionsErrorPresentation
import app.subshell.prototype.ui.noticeFailure
import app.subshell.prototype.ui.shareCustomFeedLink
import app.subshell.prototype.ui.hiddenPostIdentities
import app.subshell.prototype.ui.isHiddenBy
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.delay
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.subshell.prototype.data.account.CustomFeedCreateFailureKind
import app.subshell.prototype.data.account.CustomFeedManageFailureKind
import app.subshell.prototype.data.account.CustomFeedCreatePhase
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.account.CustomFeedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import app.subshell.prototype.ui.components.RemoteIdentityImage
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import app.subshell.prototype.data.account.CustomFeedFailureKind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedManagePhase
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.customFeedCountLabel
import app.subshell.prototype.ui.components.PostCard
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.shortcuts.AndroidLauncherShortcutPublisher
import app.subshell.prototype.shortcuts.LauncherShortcutEntry
import app.subshell.prototype.shortcuts.customFeedShortcutLabel
import app.subshell.prototype.shortcuts.rememberLauncherShortcutsEnabled
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.blocksActionFor
import app.subshell.prototype.data.account.communityMembershipActionForJoined
import app.subshell.prototype.data.account.inProgressFor
import app.subshell.prototype.data.account.projectedJoined
import app.subshell.prototype.ui.components.SubshellSearchField
import app.subshell.prototype.ui.components.SearchEntryBar
import app.subshell.prototype.ui.components.SegmentedChoiceRow
import app.subshell.prototype.ui.components.InlineProgressIndicator
import app.subshell.prototype.ui.components.ListCard
import app.subshell.prototype.ui.components.AdaptiveFullScreenDialog
import app.subshell.prototype.ui.app.PredictiveBackPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
    modifier: Modifier = Modifier,
    state: SubshellState,
    redditSubscriptionsState: RedditSubscriptionsUiState =
        RedditSubscriptionsUiState(),
    onLoadRedditSubscriptions: (String, Boolean) -> Unit = { _, _ -> },
    redditCommunityDiscoveryState: RedditCommunityDiscoveryUiState =
        RedditCommunityDiscoveryUiState(),
    onLoadRedditCommunityDiscovery: (
        String,
        String?,
        CommunityDiscoverySort?,
        Boolean?,
        Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    communityMembershipState: CommunityMembershipUiState =
        CommunityMembershipUiState(),
    onChangeCommunityMembership: (
        String,
        String,
        CommunityMembershipAction,
    ) -> Unit = { _, _, _ -> },
    customFeedsState: CustomFeedsUiState = CustomFeedsUiState(),
    customFeedPostsState: CustomFeedPostsUiState = CustomFeedPostsUiState(),
    onLoadCustomFeeds: (String, Boolean) -> Unit = { _, _ -> },
    onRequestHiddenSubscriptionsUnlock: ((Boolean) -> Unit) -> Unit =
        { onResult -> onResult(false) },
    onOpenCustomFeedAtHome: (String) -> Unit = {},
    onLoadCustomFeedPosts: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onAppendCustomFeedPosts: (String, String) -> Unit = { _, _ -> },
    onCreateCustomFeed: (CustomFeedCreateRequest) -> Unit = {},
    onUpdateCustomFeed: (CustomFeedUpdateRequest) -> Unit = {},
    onDeleteCustomFeed: (CustomFeedDeleteRequest) -> Unit = {},
    onResetCustomFeedCreate: () -> Unit = {},
    onResetCustomFeedManage: () -> Unit = {},
    customFeedCreateRequested: Boolean = false,
    onCustomFeedCreateRequestConsumed: () -> Unit = {},
    onCustomFeedFabVisibilityChanged: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(
        start = 14.dp,
        top = 8.dp,
        end = 14.dp,
        bottom = 96.dp,
    ),
) {
    val favoritesListState = rememberLazyListState()
    val joinedListState = rememberLazyListState()
    val recentListState = rememberLazyListState()
    val authenticated = state.activeAccountIsAuthenticated
    val accountStableId = state.activeAccountId
    val liveState = redditSubscriptionsState.takeIf { subscriptions ->
        subscriptions.matches(accountStableId)
    } ?: RedditSubscriptionsUiState(accountStableId = accountStableId)
    val inlineDiscoveryState = redditCommunityDiscoveryState.takeIf { discovery ->
        discovery.accountStableId == accountStableId
    } ?: RedditCommunityDiscoveryUiState(accountStableId = accountStableId)
    val degradedSubscriptionsPresentation = if (
        authenticated &&
        liveState.failureKind != null &&
        liveState.communities.isNotEmpty()
    ) {
        redditSubscriptionsErrorPresentation(
            failureKind = requireNotNull(liveState.failureKind),
            details = liveState.failureDetails,
            retryable = liveState.failureRetryable,
            showingCachedContent = true,
        )
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "subscriptions:$accountStableId",
        presentation = degradedSubscriptionsPresentation,
        failureDetails = liveState.failureDetails,
        onRetry = if (degradedSubscriptionsPresentation != null) {
            { onLoadRedditSubscriptions(accountStableId, true) }
        } else {
            null
        },
    )
    val joined = state.communities.filter { it.id in state.joinedCommunityIds }
    val localDiscover = state.communities.filterNot { it.id in state.joinedCommunityIds }
    var selectedSection by rememberSaveable(accountStableId) {
        mutableStateOf(if (state.effectiveFavoriteCommunityNames.isNotEmpty()) 0 else 1)
    }
    // LOWUI8_FAVORITES_DEFAULT_APPLIED_ONCE_V1 (OCB-182): the switch to
    // Favorites for an account with only favorite feeds happens once; it ran
    // on every entry and overrode the reader's Joined choice.
    var favoritesSectionDefaultSettled by rememberSaveable(accountStableId) { mutableStateOf(false) }
    LaunchedEffect(accountStableId, state.favoriteCustomFeedPaths.size) {
        if (
            !favoritesSectionDefaultSettled &&
            state.effectiveFavoriteCommunityNames.isEmpty() &&
            state.favoriteCustomFeedPaths.isNotEmpty() &&
            selectedSection == 1
        ) {
            selectedSection = 0
            favoritesSectionDefaultSettled = true
        }
    }
    val bodyPadding = PaddingValues(
        start = 14.dp,
        top = 8.dp,
        end = 14.dp,
        bottom = contentPadding.calculateBottomPadding(),
    )

    LaunchedEffect(authenticated, accountStableId) {
        if (authenticated) {
            onLoadRedditSubscriptions(accountStableId, false)
        }
    }
    LaunchedEffect(
        authenticated,
        accountStableId,
        selectedSection,
        inlineDiscoveryState.searchQuery,
        inlineDiscoveryState.loaded,
        inlineDiscoveryState.loading,
        inlineDiscoveryState.failureKind,
    ) {
        val shouldLoadPopular = authenticated &&
            selectedSection == 1 &&
            !inlineDiscoveryState.loading &&
            (
                inlineDiscoveryState.searchQuery != null ||
                    (
                        !inlineDiscoveryState.loaded &&
                            inlineDiscoveryState.failureKind == null
                    )
            )
        if (shouldLoadPopular) {
            onLoadRedditCommunityDiscovery(
                accountStableId,
                null,
                null,
                null,
                false,
            )
        }
    }
    LaunchedEffect(authenticated, selectedSection) {
        onCustomFeedFabVisibilityChanged(authenticated && selectedSection == 3)
    }
    DisposableEffect(Unit) {
        onDispose { onCustomFeedFabVisibilityChanged(false) }
    }

    LaunchedEffect(authenticated, accountStableId, selectedSection) {
        if (authenticated && selectedSection in setOf(0, 3)) {
            onLoadCustomFeeds(accountStableId, false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        // ISSUE220_TABS_SCROLL_INSTEAD_OF_SHRINKING_V1 (owner decision): the labels
        // keep the reader's text size; when the four no longer fit equal slots
        // the row scrolls (M3 scrollable tabs) instead of shrinking every label
        // (ISSUE32 shrank them toward 9 sp and "Favorites" still clipped).
        val sectionTitles = listOf("Favorites", "Joined", "Recent", "Feeds")
        val tabLabelStyle = MaterialTheme.typography.labelLarge
        var tabRowWidth by remember { mutableStateOf(0.dp) }
        val rowDensity = LocalDensity.current
        val tabsFit = rememberLabelsFitSlots(
            labels = sectionTitles,
            slotWidth = tabRowWidth / sectionTitles.size - 32.dp,
            style = tabLabelStyle,
        )
        val sectionTabs: @Composable () -> Unit = {
            sectionTitles.forEachIndexed { index, title ->
                val selected = selectedSection == index
                Tab(
                    selected = selected,
                    onClick = {
                        selectedSection = index
                        favoritesSectionDefaultSettled = true
                    },
                    modifier = Modifier.heightIn(min = 52.dp),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            text = title,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    tabRowWidth = with(rowDensity) { size.width.toDp() }
                },
        ) {
            if (tabsFit) {
                PrimaryTabRow(selectedTabIndex = selectedSection, containerColor = Color.Transparent) { sectionTabs() }
            } else {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedSection,
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    tabs = sectionTabs,
                )
            }
        }

        when (selectedSection) {
            0 -> FavoriteCommunitiesContent(
                modifier = Modifier.weight(1f),
                state = state,
                listState = favoritesListState,
                candidates = if (authenticated) liveState.communities else joined + localDiscover,
                joinedCommunities = if (authenticated) liveState.communities else joined,
                customFeedsState = customFeedsState,
                onOpenCustomFeed = onOpenCustomFeedAtHome,
                contentPadding = bodyPadding,
                showMembershipAction = !authenticated,
                isRefreshing = authenticated && liveState.loading,
                onRefresh = if (authenticated) {
                    { onLoadRedditSubscriptions(accountStableId, true) }
                } else {
                    {}
                },
            )

            1 -> if (!authenticated) {
                LocalCommunitiesContent(
                    modifier = Modifier.weight(1f),
                    state = state,
                    listState = joinedListState,
                    joined = joined,
                    contentPadding = bodyPadding,
                )
            } else {
                JoinedCommunitiesContent(
                    modifier = Modifier.weight(1f),
                    state = state,
                    listState = joinedListState,
                    liveState = liveState,
                    discoveryState = inlineDiscoveryState,
                    communityMembershipState = communityMembershipState,
                    accountStableId = accountStableId,
                    contentPadding = bodyPadding,
                    onLoadRedditSubscriptions = onLoadRedditSubscriptions,
                    onChangeCommunityMembership = onChangeCommunityMembership,
                    onRequestHiddenSubscriptionsUnlock =
                        onRequestHiddenSubscriptionsUnlock,
                )
            }

            2 -> RecentCommunitiesContent(
                modifier = Modifier.weight(1f),
                state = state,
                listState = recentListState,
                candidates = if (authenticated) liveState.communities else joined + localDiscover,
                joinedCommunities = if (authenticated) liveState.communities else joined,
                contentPadding = bodyPadding,
                showMembershipAction = !authenticated,
            )

            else -> CustomFeedsContent(
                modifier = Modifier.weight(1f),
                state = state,
                authenticated = authenticated,
                accountStableId = accountStableId,
                communities = if (authenticated) liveState.communities else emptyList(),
                customFeedsState = customFeedsState,
                customFeedPostsState = customFeedPostsState,
                contentPadding = bodyPadding,
                onRefresh = { onLoadCustomFeeds(accountStableId, true) },
                onLoadCustomFeedPosts = onLoadCustomFeedPosts,
                onAppendCustomFeedPosts = onAppendCustomFeedPosts,
                onCreateCustomFeed = onCreateCustomFeed,
                onUpdateCustomFeed = onUpdateCustomFeed,
                onDeleteCustomFeed = onDeleteCustomFeed,
                onResetCustomFeedCreate = onResetCustomFeedCreate,
                onResetCustomFeedManage = onResetCustomFeedManage,
                onCustomFeedFabVisibilityChanged = onCustomFeedFabVisibilityChanged,
                createRequested = customFeedCreateRequested,
                onCreateRequestConsumed = onCustomFeedCreateRequestConsumed,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreCommunitiesScreen(
    modifier: Modifier = Modifier,
    state: SubshellState,
    redditSubscriptionsState: RedditSubscriptionsUiState =
        RedditSubscriptionsUiState(),
    onLoadRedditSubscriptions: (String, Boolean) -> Unit = { _, _ -> },
    redditCommunityDiscoveryState: RedditCommunityDiscoveryUiState =
        RedditCommunityDiscoveryUiState(),
    onLoadRedditCommunityDiscovery: (
        String,
        String?,
        CommunityDiscoverySort?,
        Boolean?,
        Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    communityMembershipState: CommunityMembershipUiState =
        CommunityMembershipUiState(),
    onChangeCommunityMembership: (
        String,
        String,
        CommunityMembershipAction,
    ) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
) {
    val exploreListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val authenticated = state.activeAccountIsAuthenticated
    val accountStableId = state.activeAccountId
    val liveState = redditSubscriptionsState.takeIf { subscriptions ->
        subscriptions.matches(accountStableId)
    } ?: RedditSubscriptionsUiState(accountStableId = accountStableId)
    val discoveryState = redditCommunityDiscoveryState.takeIf { discovery ->
        discovery.accountStableId == accountStableId
    } ?: RedditCommunityDiscoveryUiState(accountStableId = accountStableId)
    var discoveryDraft by rememberSaveable(accountStableId) { mutableStateOf("") }
    var suppressedTypeaheadQuery by rememberSaveable(accountStableId) {
        mutableStateOf<String?>(null)
    }
    val normalizedDraft = normalizeDiscoveryDraft(discoveryDraft)

    LaunchedEffect(
        state.communityExploreSearchFocusRequest,
        state.openKeyboardOnSearch,
    ) {
        val request = state.communityExploreSearchFocusRequest
        if (request > 0) {
            state.consumeCommunityExploreSearchFocusRequest(request)
            if (state.openKeyboardOnSearch) {
                searchFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    val joinedCommunityNames = liveState.communities
        .map { community -> community.name.lowercase() }
        .toSet()
    val discoveryMatchesDraft = when {
        normalizedDraft.length < COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH ->
            discoveryState.searchQuery == null
        else -> discoveryState.searchQuery.equals(normalizedDraft, ignoreCase = true)
    }
    val typeaheadDraftActive = normalizedDraft.length in
        COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH..COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH &&
        !normalizedDraft.equals(suppressedTypeaheadQuery, ignoreCase = true)
    val localTypeaheadItems = if (typeaheadDraftActive) {
        liveState.communities
            .asSequence()
            .filter { community ->
                community.name.contains(normalizedDraft, ignoreCase = true) ||
                    community.description.contains(normalizedDraft, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<Community> { community ->
                    community.name.startsWith(normalizedDraft, ignoreCase = true)
                }.thenBy { community -> community.name.lowercase() },
            )
            .take(4)
            .map { community -> CommunityDiscoveryItem(community = community, nsfw = false) }
            .toList()
    } else {
        emptyList()
    }
    val stateDiscoveryItems = discoveryState.items.takeIf { discoveryMatchesDraft }.orEmpty()
    val candidateDiscoveryItems = if (typeaheadDraftActive) {
        (stateDiscoveryItems + localTypeaheadItems)
            .distinctBy { item -> item.community.name.lowercase() }
    } else {
        stateDiscoveryItems
    }
    val searchMayShowJoined = normalizedDraft.isNotEmpty() &&
        (typeaheadDraftActive ||
            discoveryState.isAutocomplete ||
            discoveryState.searchSort == CommunityDiscoverySort.RELEVANCE ||
            discoveryState.itemsAreAutocompleteFallback)
    val visibleDiscoveryItems = candidateDiscoveryItems.filter { item ->
        (state.showNsfwPosts || !item.nsfw) &&
            (searchMayShowJoined || item.community.name.lowercase() !in joinedCommunityNames)
    }

    fun loadPopular(forceRefresh: Boolean) {
        onLoadRedditCommunityDiscovery(
            accountStableId,
            null,
            null,
            null,
            forceRefresh,
        )
    }

    fun clearDiscoverySearch() {
        discoveryDraft = ""
        suppressedTypeaheadQuery = null
        focusManager.clearFocus()
        loadPopular(forceRefresh = false)
    }

    fun submitDiscovery(
        rawQuery: String,
        searchSort: CommunityDiscoverySort = CommunityDiscoverySort.RELEVANCE,
        forceRefresh: Boolean = true,
    ) {
        val normalized = normalizeDiscoveryDraft(rawQuery)
        if (normalized.isEmpty()) {
            clearDiscoverySearch()
        } else if (
            normalized.length in
                COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH..COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH
        ) {
            discoveryDraft = normalized
            suppressedTypeaheadQuery = normalized
            focusManager.clearFocus()
            onLoadRedditCommunityDiscovery(
                accountStableId,
                normalized,
                searchSort,
                null,
                forceRefresh,
            )
        }
    }

    LaunchedEffect(authenticated, accountStableId) {
        if (authenticated) {
            onLoadRedditSubscriptions(accountStableId, false)
        } else {
            onBack()
        }
    }
    // LOWUI8_EXPLORE_SEARCH_IS_RUN_AGAIN_ON_RETURN_V1 (OCB-183): the saved
    // draft of a submitted search outlives Explore, but another screen may
    // have replaced the discovery results (Joined reloads popular); on entry
    // the search is run again instead of showing the word over nothing.
    LaunchedEffect(authenticated, accountStableId) {
        if (
            authenticated &&
            normalizedDraft.length >= COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH &&
            normalizedDraft.equals(suppressedTypeaheadQuery, ignoreCase = true) &&
            !discoveryMatchesDraft
        ) {
            submitDiscovery(normalizedDraft, forceRefresh = false)
        }
    }
    LaunchedEffect(
        authenticated,
        accountStableId,
        discoveryState.loaded,
        discoveryState.loading,
    ) {
        if (
            authenticated &&
            discoveryState.searchQuery == null &&
            !discoveryState.loaded &&
            !discoveryState.loading &&
            discoveryState.failureKind == null
        ) {
            loadPopular(forceRefresh = false)
        }
    }
    LaunchedEffect(
        authenticated,
        accountStableId,
        normalizedDraft,
        suppressedTypeaheadQuery,
        state.showNsfwPosts,
    ) {
        if (!authenticated) return@LaunchedEffect
        if (
            normalizedDraft.length !in
                COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH..COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH
        ) {
            if (
                normalizedDraft.length < COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH &&
                discoveryState.searchQuery != null
            ) {
                loadPopular(forceRefresh = false)
            }
            return@LaunchedEffect
        }
        if (normalizedDraft.equals(suppressedTypeaheadQuery, ignoreCase = true)) {
            return@LaunchedEffect
        }
        delay(COMMUNITY_TYPEAHEAD_DEBOUNCE_MS)
        onLoadRedditCommunityDiscovery(
            accountStableId,
            normalizedDraft,
            null,
            state.showNsfwPosts,
            false,
        )
    }

    // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1: Back clears a search
    // first; with none, it is left to the route stack, whose predictive Back
    // closes this route (an always-on handler here hid that preview).
    BackHandler(enabled = normalizedDraft.isNotEmpty() || discoveryState.searchQuery != null) {
        clearDiscoverySearch()
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
    ) {
        RouteTopBar(
            title = { Text("Discover communities") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Symbols.ArrowBack,
                        contentDescription = "Back to joined communities",
                    )
                }
            },
            scrollBehavior = routeTopBarScroll,
            // SUBS01B7_CONTEXTUAL_TOP_APP_BAR_INSET_OWNERSHIP_V1: the app owns the
            // status bar inset (M3223_ONE_ROUTE_TOP_BAR_V1, routeTopBarWindowInsets).
        )
        ExploreCommunitiesContent(
            modifier = Modifier.weight(1f),
            state = state,
            listState = exploreListState,
            liveState = liveState,
            discoveryState = discoveryState,
            visibleDiscoveryItems = visibleDiscoveryItems,
            joinedCommunityNames = joinedCommunityNames,
            discoveryDraft = discoveryDraft,
            normalizedDraft = normalizedDraft,
            discoveryMatchesDraft = discoveryMatchesDraft,
            searchFocusRequester = searchFocusRequester,
            onDiscoveryDraftChanged = { value ->
                discoveryDraft = value
                suppressedTypeaheadQuery = null
            },
            onClearDiscovery = ::clearDiscoverySearch,
            accountStableId = accountStableId,
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 8.dp,
                end = 14.dp,
                bottom = 24.dp,
            ),
            onLoadRedditSubscriptions = onLoadRedditSubscriptions,
            onLoadRedditCommunityDiscovery = onLoadRedditCommunityDiscovery,
            communityMembershipState = communityMembershipState,
            onChangeCommunityMembership = onChangeCommunityMembership,
            onSubmitDiscovery = ::submitDiscovery,
        )
    }
}

@Composable
private fun FavoriteCommunitiesContent(
    modifier: Modifier,
    state: SubshellState,
    listState: LazyListState,
    candidates: List<Community>,
    joinedCommunities: List<Community>,
    customFeedsState: CustomFeedsUiState,
    onOpenCustomFeed: (String) -> Unit,
    contentPadding: PaddingValues,
    showMembershipAction: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val favoriteCommunities = state.favoriteCommunities(candidates)
        .filterNot { community -> state.isCommunityHidden(community.name) }
    val favoriteFeeds = state.favoriteCustomFeeds(customFeedsState.feeds)
    val favoriteCount = favoriteCommunities.size + favoriteFeeds.size
    val joinedNames = joinedCommunities
        .map { community -> community.name.lowercase() }
        .toSet()

    RefreshableLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        if (favoriteCount > 0) {
            item("favorites-title") {
                SectionTitle(
                    title = "Favorites",
                    meta = "$favoriteCount pinned",
                )
            }
            if (favoriteFeeds.isNotEmpty()) {
                item("favorite-feeds-title") {
                    SectionTitle(
                        title = "Feeds",
                        meta = "${favoriteFeeds.size} pinned",
                    )
                }
                items(
                    items = favoriteFeeds,
                    key = { feed -> "favorite-feed-${feed.path.lowercase()}" },
                ) { feed ->
                    FavoriteCustomFeedRow(
                        feed = feed,
                        onOpen = { onOpenCustomFeed(feed.path) },
                        onToggleFavorite = {
                            state.toggleFavoriteCustomFeed(feed.path)
                        },
                    )
                }
            }
            if (favoriteCommunities.isNotEmpty()) {
                item("favorite-communities-title") {
                    SectionTitle(
                        title = "Communities",
                        meta = "${favoriteCommunities.size} pinned",
                    )
                }
                items(
                    items = favoriteCommunities,
                    key = { community -> "favorite-${community.name.lowercase()}" },
                ) { community ->
                    CommunityRow(
                        community = community,
                        state = state,
                        joined = community.name.lowercase() in joinedNames,
                        showMembershipAction = showMembershipAction,
                        showFavoriteAction = true,
                    ) { state.toggleCommunity(community.id) }
                }
            }
        } else {
            item("favorites-empty") {
                EmptyCommunitySurface(
                    title = "No favorites yet",
                    description = "Star a community or custom feed to keep it in your quick-access list.",
                )
            }
        }
    }
}

@Composable
private fun JoinedCommunitiesContent(
    modifier: Modifier,
    state: SubshellState,
    listState: LazyListState,
    liveState: RedditSubscriptionsUiState,
    discoveryState: RedditCommunityDiscoveryUiState,
    communityMembershipState: CommunityMembershipUiState,
    accountStableId: String,
    contentPadding: PaddingValues,
    onLoadRedditSubscriptions: (String, Boolean) -> Unit,
    onChangeCommunityMembership: (
        String,
        String,
        CommunityMembershipAction,
    ) -> Unit,
    onRequestHiddenSubscriptionsUnlock: ((Boolean) -> Unit) -> Unit,
) {
    val allJoinedCommunities = liveState.communities.sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER) { community -> community.name },
    )
    val visibleJoinedCommunities = allJoinedCommunities.filterNot { community ->
        state.isCommunityHidden(community.name)
    }
    val hiddenJoinedCommunities = allJoinedCommunities.filter { community ->
        state.isCommunityHidden(community.name)
    }
    val joinedCommunityNames = allJoinedCommunities
        .map { community -> community.name.lowercase() }
        .toSet()
    val inlineDiscoveryItems = inlineCommunityDiscoveryItems(
        items = if (discoveryState.searchQuery == null) discoveryState.items else emptyList(),
        joinedCommunityNames = joinedCommunityNames,
        showNsfw = state.showNsfwPosts,
        limit = when (state.communityDiscoveryLayout) {
            CommunityDiscoveryLayout.CAROUSEL -> INLINE_COMMUNITY_DISCOVERY_CAROUSEL_LIMIT
            CommunityDiscoveryLayout.LIST -> INLINE_COMMUNITY_DISCOVERY_LIST_LIMIT
        },
    )
    // LOWUI3_HIDDEN_LIST_NEEDS_A_NEW_UNLOCK_PER_ENTRY_V1 (OCB-184): with the
    // device-unlock protection on, the open hidden list is not saved, so
    // coming back to Communities (tab switch, Back from a community) asks again.
    val hiddenSubscriptionsOpenSaved = rememberSaveable(accountStableId) { mutableStateOf(false) }
    val hiddenSubscriptionsOpenUnsaved = remember(accountStableId) { mutableStateOf(false) }
    var hiddenSubscriptionsOpen by if (state.requireDeviceUnlockForHiddenSubscriptions) {
        hiddenSubscriptionsOpenUnsaved
    } else {
        hiddenSubscriptionsOpenSaved
    }
    val hiddenListState = rememberLazyListState()
    val context = LocalContext.current
    val activity = remember(context) { context.findHiddenSubscriptionsActivity() }
    val lifecycleOwner = activity as? LifecycleOwner
    val protectedHiddenSubscriptionsOpen =
        hiddenSubscriptionsOpen && state.requireDeviceUnlockForHiddenSubscriptions

    DisposableEffect(
        activity,
        lifecycleOwner,
        protectedHiddenSubscriptionsOpen,
    ) {
        val foreground =
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(
                Lifecycle.State.STARTED,
            ) == true
        if (protectedHiddenSubscriptionsOpen) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (foreground) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val observer = LifecycleEventObserver { _, event ->
            when {
                event == Lifecycle.Event.ON_STOP &&
                    protectedHiddenSubscriptionsOpen -> {
                    hiddenSubscriptionsOpen = false
                }
                event == Lifecycle.Event.ON_START &&
                    !protectedHiddenSubscriptionsOpen -> {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            val disposingInForeground =
                lifecycleOwner?.lifecycle?.currentState?.isAtLeast(
                    Lifecycle.State.STARTED,
                ) == true
            if (protectedHiddenSubscriptionsOpen && disposingInForeground) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    if (hiddenSubscriptionsOpen) {
        // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1
        PredictiveBackPage(onBack = { hiddenSubscriptionsOpen = false }, modifier = modifier) {
            HiddenSubscriptionsContent(
                modifier = Modifier,
                state = state,
                listState = hiddenListState,
                liveState = liveState,
                accountStableId = accountStableId,
                contentPadding = contentPadding,
                onLoadRedditSubscriptions = onLoadRedditSubscriptions,
            )
        }
        return
    }

    RefreshableLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        isRefreshing = liveState.loading,
        onRefresh = { onLoadRedditSubscriptions(accountStableId, true) },
    ) {
        item("inline-community-discovery") {
            InlineCommunityDiscoverySurface(
                state = state,
                items = inlineDiscoveryItems,
                joinedCommunityNames = joinedCommunityNames,
                liveState = liveState,
                discoveryState = discoveryState,
                communityMembershipState = communityMembershipState,
                accountStableId = accountStableId,
                onSearch = { state.openCommunityExplore(focusSearch = true) },
                onSeeAll = state::openCommunityExplore,
                onChangeCommunityMembership = onChangeCommunityMembership,
            )
        }
        item("live-your-title") {
            SectionTitle(
                title = "Joined communities",
                meta = if (hiddenJoinedCommunities.isEmpty()) {
                    "${visibleJoinedCommunities.size} joined"
                } else {
                    "${visibleJoinedCommunities.size} shown"
                },
            )
        }
        item("hidden-subscriptions-entry") {
            HiddenSubscriptionsEntry(
                count = hiddenJoinedCommunities.size,
                onOpen = {
                    if (state.requireDeviceUnlockForHiddenSubscriptions) {
                        onRequestHiddenSubscriptionsUnlock { unlocked ->
                            if (unlocked) {
                                hiddenSubscriptionsOpen = true
                            }
                        }
                    } else {
                        hiddenSubscriptionsOpen = true
                    }
                },
            )
        }
        liveState.failureKind?.let { failureKind ->
            if (liveState.communities.isEmpty()) {
                item("live-subscriptions-status") {
                    LiveSubscriptionsStatus(
                        failureKind = failureKind,
                        failureDetails = liveState.failureDetails,
                        showingCachedList = false,
                        retryable = liveState.failureRetryable,
                        onRetry = { onLoadRedditSubscriptions(accountStableId, true) },
                    )
                }
            }
        }
        when {
            allJoinedCommunities.isNotEmpty() -> {
                items(
                    items = visibleJoinedCommunities,
                    key = { community -> "live-${community.id}" },
                ) { community ->
                    CommunityRow(
                        community = community,
                        state = state,
                        joined = true,
                        showMembershipAction = false,
                        showFavoriteAction = true,
                        showHiddenAction = true,
                        hidden = false,
                        onToggle = {},
                    )
                }
            }

            liveState.loading -> item("live-subscriptions-loading") {
                LoadingCommunities()
            }

            liveState.loaded && liveState.communities.isEmpty() -> item("live-subscriptions-empty") {
                LiveSubscriptionsEmpty()
            }
        }
    }
}

// SUBSHELL_SUBS05A1_DISCOVERY_LAYOUT_PREFERENCE_V2
@Composable
private fun InlineCommunityDiscoverySurface(
    state: SubshellState,
    items: List<CommunityDiscoveryItem>,
    joinedCommunityNames: Set<String>,
    liveState: RedditSubscriptionsUiState,
    discoveryState: RedditCommunityDiscoveryUiState,
    communityMembershipState: CommunityMembershipUiState,
    accountStableId: String,
    onSearch: () -> Unit,
    onSeeAll: () -> Unit,
    onChangeCommunityMembership: (
        String,
        String,
        CommunityMembershipAction,
    ) -> Unit,
) {
    val membershipTruthReady =
        liveState.loaded &&
            !liveState.fromCache &&
            !liveState.loading
    val layout = state.communityDiscoveryLayout

    val renderItem: @Composable (CommunityDiscoveryItem) -> Unit = { item ->
        val authoritativeJoined =
            item.community.name.lowercase() in joinedCommunityNames
        val joined = communityMembershipState.projectedJoined(
            accountStableId = accountStableId,
            communityName = item.community.name,
            authoritativeJoined = authoritativeJoined,
        )
        val awaitingAuthorization = communityMembershipState.matches(
            accountStableId,
            item.community.name,
        ) &&
            communityMembershipState.phase ==
            CommunityMembershipPhase.AUTHORIZATION_REQUIRED
        val targetBusy = communityMembershipState.inProgressFor(
            accountStableId,
            item.community.name,
        ) || awaitingAuthorization
        val flowBusy = communityMembershipState.inProgress ||
            communityMembershipState.phase ==
            CommunityMembershipPhase.AUTHORIZATION_REQUIRED
        val errorPresentation = if (
            communityMembershipState.phase == CommunityMembershipPhase.FAILED &&
            communityMembershipState.matches(
                accountStableId,
                item.community.name,
            )
        ) {
            communityMembershipErrorPresentation(communityMembershipState)
        } else {
            null
        }
        val membershipEnabled = membershipTruthReady &&
            !flowBusy &&
            !communityMembershipState.blocksActionFor(
                accountStableId,
                item.community.name,
            )
        val toggleMembership = {
            onChangeCommunityMembership(
                accountStableId,
                item.community.name,
                communityMembershipActionForJoined(joined),
            )
        }

        when (layout) {
            CommunityDiscoveryLayout.CAROUSEL -> InlineCommunityDiscoveryCard(
                item = item,
                state = state,
                joined = joined,
                membershipEnabled = membershipEnabled,
                membershipInProgress = targetBusy,
                membershipErrorPresentation = errorPresentation,
                onToggleMembership = toggleMembership,
            )

            CommunityDiscoveryLayout.LIST -> InlineCommunityDiscoveryRow(
                item = item,
                state = state,
                joined = joined,
                membershipEnabled = membershipEnabled,
                membershipInProgress = targetBusy,
                membershipErrorPresentation = errorPresentation,
                onToggleMembership = toggleMembership,
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(
            if (layout == CommunityDiscoveryLayout.LIST) 6.dp else 5.dp,
        ),
    ) {
        if (layout == CommunityDiscoveryLayout.CAROUSEL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Discover communities",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onSeeAll) {
                    Text("See all")
                }
            }
        } else {
            Text(
                text = "Discover communities",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // M3225_ONE_SEARCH_FIELD_STYLE_V1 (reading-21): a collapsed M3 search
        // bar that opens community search.
        SearchEntryBar(
            text = if (layout == CommunityDiscoveryLayout.CAROUSEL) {
                "Search communities"
            } else {
                "Search communities by name or topic"
            },
            onClick = onSearch,
        )

        if (layout == CommunityDiscoveryLayout.LIST) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Popular communities",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSeeAll) {
                    Text("See all")
                }
            }
        }

        when {
            items.isNotEmpty() -> when (layout) {
                CommunityDiscoveryLayout.CAROUSEL -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = items,
                        key = { item -> "inline-discover-${item.community.id}" },
                    ) { item ->
                        renderItem(item)
                    }
                }

                CommunityDiscoveryLayout.LIST ->
                    items.forEach { item -> renderItem(item) }
            }

            discoveryState.loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InlineProgressIndicator()
                Text(
                    text = "Finding communities…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Text(
                text = if (discoveryState.fromCache) {
                    "Saved popular communities are unavailable right now. See all to browse."
                } else {
                    "Browse popular communities or search by name and topic."
                },
                modifier = Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InlineCommunityDiscoveryCard(
    item: CommunityDiscoveryItem,
    state: SubshellState,
    joined: Boolean,
    membershipEnabled: Boolean,
    membershipInProgress: Boolean,
    membershipErrorPresentation: ErrorPresentation?,
    onToggleMembership: () -> Unit,
) {
    val community = item.community
    // NEXT01A5_SELECTED_COMMUNITY_CONTINUITY_V1
    val selected =
        state.currentSubscriptionsCommunitySelectionMatches(community.name)
    Surface(
        modifier = Modifier.width(152.dp),
        onClick = { state.openSubscriptionsCommunitySelection(community.name) },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 132.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CommunityAvatar(
                    name = community.name,
                    accent = Color(community.accent),
                    modifier = Modifier.size(32.dp),
                    iconImageUri = community.iconImageUri,
                )
                Text(
                    text = state.subredditLabel(community.name),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            community.members
                .takeUnless { members -> members.isBlank() || members == "—" }
                ?.let { members ->
                    Text(
                        text = "$members members",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            Spacer(Modifier.weight(1f))
            membershipErrorPresentation?.let { presentation ->
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onToggleMembership,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = membershipEnabled && !membershipInProgress,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                if (membershipInProgress) {
                    InlineProgressIndicator()
                    Spacer(Modifier.width(4.dp))
                    Text(if (joined) "Leaving…" else "Joining…")
                } else if (joined) {
                    Icon(
                        imageVector = Symbols.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Joined")
                } else {
                    Icon(
                        imageVector = Symbols.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Join")
                }
            }
        }
    }
}

@Composable
private fun InlineCommunityDiscoveryRow(
    item: CommunityDiscoveryItem,
    state: SubshellState,
    joined: Boolean,
    membershipEnabled: Boolean,
    membershipInProgress: Boolean,
    membershipErrorPresentation: ErrorPresentation?,
    onToggleMembership: () -> Unit,
) {
    val community = item.community
    // NEXT01A5_SELECTED_COMMUNITY_CONTINUITY_V1
    val selected =
        state.currentSubscriptionsCommunitySelectionMatches(community.name)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { state.openSubscriptionsCommunitySelection(community.name) },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CommunityAvatar(
                name = community.name,
                accent = Color(community.accent),
                modifier = Modifier.size(36.dp),
                iconImageUri = community.iconImageUri,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.subredditLabel(community.name),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                communityMeta(community)?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                membershipErrorPresentation?.let { presentation ->
                    Text(
                        text = presentation.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedButton(
                onClick = onToggleMembership,
                enabled = membershipEnabled && !membershipInProgress,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                if (membershipInProgress) {
                    InlineProgressIndicator()
                    Spacer(Modifier.width(4.dp))
                    Text(if (joined) "Leaving…" else "Joining…")
                } else if (joined) {
                    Icon(
                        imageVector = Symbols.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Joined")
                } else {
                    Icon(
                        imageVector = Symbols.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Join")
                }
            }
        }
    }
}

@Composable
private fun HiddenSubscriptionsEntry(
    count: Int,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Symbols.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            // ISSUE220_HIDDEN_COMMUNITIES_ROW_WRAPS_V1: at large text the title
            // wraps and keeps a gap to "View" instead of running into it.
            Text(
                text = "Hidden communities",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            if (count > 0) {
                Text(
                    text = "$count hidden",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HiddenSubscriptionsContent(
    modifier: Modifier,
    state: SubshellState,
    listState: LazyListState,
    liveState: RedditSubscriptionsUiState,
    accountStableId: String,
    contentPadding: PaddingValues,
    onLoadRedditSubscriptions: (String, Boolean) -> Unit,
) {
    val hiddenCommunities = liveState.communities
        .filter { community -> state.isCommunityHidden(community.name) }
        .sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { community -> community.name },
        )

    RefreshableLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        isRefreshing = liveState.loading,
        onRefresh = { onLoadRedditSubscriptions(accountStableId, true) },
    ) {
        item("hidden-subscriptions-title") {
            SectionTitle(
                title = "Hidden communities",
                meta = "${hiddenCommunities.size} hidden",
            )
        }
        if (hiddenCommunities.isEmpty()) {
            item("hidden-subscriptions-empty") {
                EmptyCommunitySurface(
                    title = "No hidden communities",
                    description = "Communities you hide locally will appear here.",
                )
            }
        } else {
            items(
                items = hiddenCommunities,
                key = { community -> "hidden-${community.id}" },
            ) { community ->
                CommunityRow(
                    community = community,
                    state = state,
                    joined = true,
                    showMembershipAction = false,
                    showHiddenAction = true,
                    hidden = true,
                    supportingMeta = "Hidden locally",
                    onToggle = {},
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreCommunitiesContent(
    modifier: Modifier,
    state: SubshellState,
    listState: LazyListState,
    liveState: RedditSubscriptionsUiState,
    discoveryState: RedditCommunityDiscoveryUiState,
    visibleDiscoveryItems: List<CommunityDiscoveryItem>,
    joinedCommunityNames: Set<String>,
    discoveryDraft: String,
    normalizedDraft: String,
    discoveryMatchesDraft: Boolean,
    searchFocusRequester: FocusRequester,
    onDiscoveryDraftChanged: (String) -> Unit,
    onClearDiscovery: () -> Unit,
    accountStableId: String,
    contentPadding: PaddingValues,
    onLoadRedditSubscriptions: (String, Boolean) -> Unit,
    onLoadRedditCommunityDiscovery: (
        String,
        String?,
        CommunityDiscoverySort?,
        Boolean?,
        Boolean,
    ) -> Unit,
    communityMembershipState: CommunityMembershipUiState,
    onChangeCommunityMembership: (
        String,
        String,
        CommunityMembershipAction,
    ) -> Unit,
    onSubmitDiscovery: (String, CommunityDiscoverySort, Boolean) -> Unit,
) {
    val currentTypeahead = discoveryMatchesDraft && discoveryState.isAutocomplete
    val fullSearchForDraft = discoveryMatchesDraft && discoveryState.searchSort != null
    val suggestionsVisible = currentTypeahead || discoveryState.itemsAreAutocompleteFallback
    val canSubmitFullSearch = normalizedDraft.length in
        COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH..COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH
    val membershipTruthReady =
        liveState.loaded &&
            !liveState.fromCache &&
            !liveState.loading
    val degradedDiscoveryFailure = discoveryState.failureKind?.takeIf {
        discoveryMatchesDraft &&
            !currentTypeahead &&
            !discoveryState.itemsAreAutocompleteFallback &&
            discoveryState.items.isNotEmpty()
    }
    val degradedDiscoveryPresentation = degradedDiscoveryFailure?.let { failureKind ->
        redditCommunityDiscoveryErrorPresentation(
            failureKind = failureKind,
            details = discoveryState.failureDetails,
            retryable = discoveryState.failureRetryable,
            showingCachedContent = true,
        )
    }
    ReportViewportError(
        scopeKey = buildString {
            append("community-discovery:")
            append(accountStableId)
            append(':')
            append(discoveryState.searchQuery.orEmpty())
            append(':')
            append(discoveryState.searchSort?.name.orEmpty())
        },
        presentation = degradedDiscoveryPresentation,
        failureDetails = discoveryState.failureDetails,
        onRetry = if (degradedDiscoveryPresentation != null) {
            {
                onLoadRedditCommunityDiscovery(
                    accountStableId,
                    discoveryState.searchQuery,
                    discoveryState.searchSort,
                    discoveryState.autocompleteIncludeOver18,
                    true,
                )
            }
        } else {
            null
        },
    )

    RefreshableLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        isRefreshing = discoveryMatchesDraft && discoveryState.loading && !currentTypeahead,
        onRefresh = {
            onLoadRedditSubscriptions(accountStableId, true)
            onLoadRedditCommunityDiscovery(
                accountStableId,
                discoveryState.searchQuery,
                discoveryState.searchSort,
                discoveryState.autocompleteIncludeOver18,
                true,
            )
        },
    ) {
        item("live-discovery-controls") {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                // M3225_ONE_SEARCH_FIELD_STYLE_V1: the M3 search bar input field.
                SubshellSearchField(
                    query = discoveryDraft,
                    onQueryChange = { value ->
                        if (value.length <= COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH) {
                            onDiscoveryDraftChanged(value)
                        }
                    },
                    placeholder = "Search communities",
                    fieldModifier = Modifier.focusRequester(searchFocusRequester),
                    onSearch = {
                        onSubmitDiscovery(
                            discoveryDraft,
                            CommunityDiscoverySort.RELEVANCE,
                            true,
                        )
                    },
                    trailingIcon = {
                        when {
                            currentTypeahead && discoveryState.loading -> Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                InlineProgressIndicator()
                            }
                            discoveryDraft.isNotBlank() -> IconButton(
                                onClick = onClearDiscovery,
                            ) {
                                Icon(
                                    Symbols.Close,
                                    contentDescription = "Clear community search",
                                )
                            }
                        }
                    },
                )
                if (fullSearchForDraft) {
                    // M3225_SHORT_SINGLE_CHOICE_IS_A_SEGMENTED_ROW_V1
                    SegmentedChoiceRow(
                        options = listOf(
                            CommunityDiscoverySort.RELEVANCE,
                            CommunityDiscoverySort.ACTIVITY,
                        ),
                        selected = discoveryState.searchSort,
                        label = { sort ->
                            if (sort == CommunityDiscoverySort.RELEVANCE) "Best match" else "Most active"
                        },
                        onSelect = { sort ->
                            onSubmitDiscovery(normalizedDraft, sort, true)
                        },
                    )
                } else if (canSubmitFullSearch) {
                    Text(
                        "Suggestions update as you type. Press search for all communities.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: where the reader chose it.
        if (state.offersRandomNsfwCommunity(app.subshell.prototype.ui.RandomNsfwCommunityPlacement.COMMUNITIES)) {
            item("random-nsfw-community") {
                app.subshell.prototype.ui.app.RandomNsfwCommunityButton(
                    state,
                    app.subshell.prototype.ui.RandomNsfwCommunityPlacement.COMMUNITIES,
                )
            }
        }
        if (discoveryMatchesDraft) {
            discoveryState.failureKind?.let { failureKind ->
                when {
                    currentTypeahead -> item("live-discovery-status") {
                        AutocompleteFailureNotice(
                            presentation = redditCommunityDiscoverySuggestionErrorPresentation(
                                failureKind = failureKind,
                                details = discoveryState.failureDetails,
                                retryable = discoveryState.failureRetryable,
                                showingFallbackResults = false,
                            ),
                            onRetry = {
                                onLoadRedditCommunityDiscovery(
                                    accountStableId,
                                    discoveryState.searchQuery,
                                    null,
                                    state.showNsfwPosts,
                                    true,
                                )
                            },
                        )
                    }

                    discoveryState.itemsAreAutocompleteFallback -> item("live-discovery-status") {
                        AutocompleteFailureNotice(
                            presentation = redditCommunityDiscoverySuggestionErrorPresentation(
                                failureKind = failureKind,
                                details = discoveryState.failureDetails,
                                retryable = discoveryState.failureRetryable,
                                showingFallbackResults = true,
                            ),
                            onRetry = {
                                onLoadRedditCommunityDiscovery(
                                    accountStableId,
                                    discoveryState.searchQuery,
                                    discoveryState.searchSort,
                                    null,
                                    true,
                                )
                            },
                        )
                    }

                    discoveryState.items.isNotEmpty() -> Unit

                    else -> item("live-discovery-status") {
                        LiveCommunityDiscoveryStatus(
                            failureKind = failureKind,
                            failureDetails = discoveryState.failureDetails,
                            showingCachedList = false,
                            retryable = discoveryState.failureRetryable,
                            onRetry = {
                                onLoadRedditCommunityDiscovery(
                                    accountStableId,
                                    discoveryState.searchQuery,
                                    discoveryState.searchSort,
                                    discoveryState.autocompleteIncludeOver18,
                                    true,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (
            canSubmitFullSearch &&
            !fullSearchForDraft &&
            visibleDiscoveryItems.isEmpty()
        ) {
            item("search-all-communities-empty-suggestions") {
                SearchAllCommunitiesRow(
                    query = normalizedDraft,
                    onClick = {
                        onSubmitDiscovery(
                            normalizedDraft,
                            CommunityDiscoverySort.RELEVANCE,
                            true,
                        )
                    },
                )
            }
        }

        when {
            visibleDiscoveryItems.isNotEmpty() -> {
                item("live-discovery-title") {
                    SectionTitle(
                        title = when {
                            suggestionsVisible -> "Suggestions"
                            discoveryState.searchQuery != null -> "Search results"
                            else -> "Popular on Reddit"
                        },
                        meta = buildString {
                            if (discoveryState.fromCache) append("Saved · ")
                            append("${visibleDiscoveryItems.size} shown")
                        },
                    )
                }
                items(
                    items = visibleDiscoveryItems,
                    key = { item -> "discover-${item.community.id}" },
                ) { item ->
                    val authoritativeJoined =
                        item.community.name.lowercase() in joinedCommunityNames
                    val joined =
                        communityMembershipState.projectedJoined(
                            accountStableId = accountStableId,
                            communityName = item.community.name,
                            authoritativeJoined = authoritativeJoined,
                        )
                    val awaitingAuthorization =
                        communityMembershipState.matches(
                            accountStableId,
                            item.community.name,
                        ) &&
                            communityMembershipState.phase ==
                            CommunityMembershipPhase.AUTHORIZATION_REQUIRED
                    val membershipTargetBusy =
                        communityMembershipState.inProgressFor(
                            accountStableId,
                            item.community.name,
                        ) ||
                            awaitingAuthorization
                    val membershipFlowBusy =
                        communityMembershipState.inProgress ||
                            communityMembershipState.phase ==
                            CommunityMembershipPhase.AUTHORIZATION_REQUIRED
                    CommunityRow(
                        community = item.community,
                        state = state,
                        joined = joined,
                        nsfw = item.nsfw,
                        showJoinedBadge = false,
                        showMembershipAction = true,
                        membershipActionEnabledOverride =
                            membershipTruthReady &&
                                !membershipFlowBusy &&
                                !communityMembershipState.blocksActionFor(
                                    accountStableId,
                                    item.community.name,
                                ),
                        membershipInProgress = membershipTargetBusy,
                        membershipErrorPresentation =
                            if (
                                communityMembershipState.phase == CommunityMembershipPhase.FAILED &&
                                communityMembershipState.matches(
                                    accountStableId,
                                    item.community.name,
                                )
                            ) {
                                communityMembershipErrorPresentation(communityMembershipState)
                            } else {
                                null
                            },
                        onToggle = {
                            onChangeCommunityMembership(
                                accountStableId,
                                item.community.name,
                                communityMembershipActionForJoined(joined),
                            )
                        },
                    )
                }
                if (suggestionsVisible && canSubmitFullSearch && !fullSearchForDraft) {
                    item("search-all-communities") {
                        SearchAllCommunitiesRow(
                            query = normalizedDraft,
                            onClick = {
                                onSubmitDiscovery(
                                    normalizedDraft,
                                    CommunityDiscoverySort.RELEVANCE,
                                    true,
                                )
                            },
                        )
                    }
                }
            }

            discoveryState.loading &&
                discoveryMatchesDraft &&
                !currentTypeahead &&
                !discoveryState.itemsAreAutocompleteFallback -> {
                item("live-discovery-loading") {
                    LoadingCommunities()
                }
            }

            discoveryState.loaded &&
                discoveryMatchesDraft &&
                !currentTypeahead -> item("live-discovery-empty") {
                LiveCommunityDiscoveryEmpty(
                    hasUnfilteredResults = discoveryState.items.isNotEmpty(),
                    nsfwHidden = !state.showNsfwPosts &&
                        discoveryState.items.any { item -> item.nsfw },
                )
            }
        }
    }
}
@Composable
private fun LocalCommunitiesContent(
    modifier: Modifier,
    state: SubshellState,
    listState: LazyListState,
    joined: List<Community>,
    contentPadding: PaddingValues,
) {
    val joinedCommunities = joined.sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER) { community -> community.name },
    )

    RefreshableLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (joinedCommunities.isNotEmpty()) {
            item("your-title") {
                SectionTitle(
                    "Joined communities",
                    "${joinedCommunities.size} joined",
                )
            }
            items(joinedCommunities, key = { "joined-${it.id}" }) { community ->
                CommunityRow(
                    community = community,
                    state = state,
                    joined = true,
                    showMembershipAction = true,
                    showFavoriteAction = true,
                ) { state.toggleCommunity(community.id) }
            }
        } else {
            item("local-subscriptions-empty") {
                EmptyCommunitySurface(
                    title = "No joined communities yet",
                    description = "Use Discover to find communities.",
                )
            }
        }
    }
}

@Composable
private fun RecentCommunitiesContent(
    modifier: Modifier,
    state: SubshellState,
    listState: LazyListState,
    candidates: List<Community>,
    joinedCommunities: List<Community>,
    contentPadding: PaddingValues,
    showMembershipAction: Boolean,
) {
    val recentCommunities = state.recentCommunities(
        candidates = candidates,
        limit = RECENT_COMMUNITY_HISTORY_LIMIT,
    )
    val visitsByName = state.recentCommunityVisits.associateBy { visit ->
        visit.communityName.lowercase()
    }
    val joinedNames = joinedCommunities.map { community -> community.name.lowercase() }.toSet()
    val nowEpochMillis = System.currentTimeMillis()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (recentCommunities.isNotEmpty()) {
            item("recent-history-title") {
                SectionTitle(
                    title = "Recently visited",
                    meta = "${recentCommunities.size} visited",
                    actionLabel = "Clear",
                    onAction = state::clearRecentCommunities,
                )
            }
            items(
                items = recentCommunities,
                key = { community -> "history-${community.name.lowercase()}" },
            ) { community ->
                val visitedAt = visitsByName[community.name.lowercase()]?.visitedAtEpochMillis
                CommunityRow(
                    community = community,
                    state = state,
                    joined = community.name.lowercase() in joinedNames,
                    showMembershipAction = showMembershipAction,
                    showFavoriteAction = true,
                    supportingMeta = visitedAt?.let { timestamp ->
                        formatRecentCommunityAge(timestamp, nowEpochMillis)
                    },
                ) { state.toggleCommunity(community.id) }
            }
        } else {
            item("recent-history-empty") {
                EmptyCommunitySurface(
                    title = "No recent communities",
                    description = "Communities you open will appear here.",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomFeedsContent(
    modifier: Modifier,
    state: SubshellState,
    authenticated: Boolean,
    accountStableId: String,
    communities: List<Community>,
    customFeedsState: CustomFeedsUiState,
    customFeedPostsState: CustomFeedPostsUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadCustomFeedPosts: (String, String, Boolean) -> Unit,
    onAppendCustomFeedPosts: (String, String) -> Unit,
    onCreateCustomFeed: (CustomFeedCreateRequest) -> Unit,
    onUpdateCustomFeed: (CustomFeedUpdateRequest) -> Unit,
    onDeleteCustomFeed: (CustomFeedDeleteRequest) -> Unit,
    onResetCustomFeedCreate: () -> Unit,
    onResetCustomFeedManage: () -> Unit,
    createRequested: Boolean,
    onCreateRequestConsumed: () -> Unit,
    onCustomFeedFabVisibilityChanged: (Boolean) -> Unit,
) {
    val liveState = customFeedsState.takeIf { feeds ->
        feeds.accountStableId.isBlank() || feeds.accountStableId == accountStableId
    } ?: CustomFeedsUiState(accountStableId = accountStableId)
    val context = LocalContext.current
    val userNotices = LocalViewportErrorHostState.current
    val launcherShortcutsEnabled by rememberLauncherShortcutsEnabled()
    val launcherShortcutPublisher = remember(context) { AndroidLauncherShortcutPublisher(context) }
    val launcherShortcutPinSupported = remember(launcherShortcutPublisher) {
        launcherShortcutPublisher.isPinSupported()
    }
    // FEEDS02_CUSTOM_FEED_EDITOR_KEYED_ON_ACCOUNT_V1: a draft started as one
    // account is never created as another, and survives recreation.
    var editorVisible by rememberSaveable(accountStableId) { mutableStateOf(false) }
    var editingFeedPath by rememberSaveable(accountStableId) { mutableStateOf<String?>(null) }
    var selectedFeedPath by rememberSaveable(accountStableId) { mutableStateOf<String?>(null) }
    var deleteFeedPath by rememberSaveable(accountStableId) { mutableStateOf<String?>(null) }
    var feedName by rememberSaveable(accountStableId) { mutableStateOf("") }
    var selectedCommunities by rememberSaveable(accountStableId, stateSaver = CustomFeedCommunitySetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var visibility by rememberSaveable(accountStableId) { mutableStateOf(CustomFeedVisibility.PRIVATE) }
    var communityQuery by rememberSaveable(accountStableId) { mutableStateOf("") }
    var observedCreateRevision by remember(accountStableId) {
        mutableStateOf(liveState.createCompletionRevision)
    }
    var observedManageRevision by remember(accountStableId) {
        mutableStateOf(liveState.manageCompletionRevision)
    }
    val editingFeed = liveState.feeds.firstOrNull { feed -> feed.path == editingFeedPath }
    // LOWUI8_FEED_EDITOR_STAYS_AN_EDITOR_V1 (OCB-185): the mode follows the
    // path being edited, not whether the list still has it; a feed gone from
    // a loaded list is reported and cannot be saved (it never becomes Create).
    val editingExistingFeed = editingFeedPath != null
    val selectedFeed = liveState.feeds.firstOrNull { feed -> feed.path == selectedFeedPath }
    val deleteFeed = liveState.feeds.firstOrNull { feed -> feed.path == deleteFeedPath }
    val createBusy = liveState.createInProgress
    val manageBusy = liveState.manageInProgress
    val editorBusy = if (editingExistingFeed) manageBusy else createBusy
    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: waiting for the browser
    // (AWAITING_CALLBACK, no timeout) used to lock every way out. Closing
    // cancels the authorization (the resets do); only the write itself and
    // the brief browser launch stay locked.
    val editorCloseLocked = if (editingExistingFeed) {
        customFeedManageCloseLocked(liveState.managePhase)
    } else {
        customFeedCreateCloseLocked(liveState.createPhase)
    }
    val deleteCloseLocked = customFeedManageCloseLocked(liveState.managePhase)

    fun beginCreateEditor() {
        onResetCustomFeedCreate()
        onResetCustomFeedManage()
        editingFeedPath = null
        feedName = ""
        selectedCommunities = emptySet()
        visibility = CustomFeedVisibility.PRIVATE
        communityQuery = ""
        editorVisible = true
    }

    fun beginEditEditor(feed: CustomFeedSummary) {
        onResetCustomFeedCreate()
        onResetCustomFeedManage()
        editingFeedPath = feed.path
        feedName = feed.displayName.take(50)
        selectedCommunities = feed.subredditNames.toSet()
        visibility = CustomFeedVisibility.entries.firstOrNull { option ->
            option.apiValue.equals(feed.visibility, ignoreCase = true)
        } ?: CustomFeedVisibility.PRIVATE
        communityQuery = ""
        editorVisible = true
    }

    LaunchedEffect(createRequested) {
        if (createRequested) {
            onCreateRequestConsumed()
            beginCreateEditor()
        }
    }

    LaunchedEffect(selectedFeedPath, editorVisible, authenticated) {
        onCustomFeedFabVisibilityChanged(
            authenticated && selectedFeedPath == null && !editorVisible,
        )
    }

    LaunchedEffect(editorVisible, accountStableId, communities) {
        if (editorVisible && authenticated) {
            communities.map { community -> community.name }.chunked(100).forEach { names ->
                state.ensureCommunityIdentities(names)
            }
        }
    }

    LaunchedEffect(liveState.createCompletionRevision) {
        if (liveState.createCompletionRevision > observedCreateRevision) {
            editorVisible = false
            editingFeedPath = null
            feedName = ""
            selectedCommunities = emptySet()
            visibility = CustomFeedVisibility.PRIVATE
            communityQuery = ""
        }
        observedCreateRevision = liveState.createCompletionRevision
    }

    LaunchedEffect(liveState.manageCompletionRevision) {
        if (liveState.manageCompletionRevision > observedManageRevision) {
            editorVisible = false
            editingFeedPath = null
            deleteFeedPath = null
        }
        observedManageRevision = liveState.manageCompletionRevision
    }

    // LOWUI8_OPEN_FEED_CLOSED_ONLY_BY_A_LOADED_LIST_V1 (OCB-186): after a
    // restore the list is still empty; only a list loaded for this account
    // can say the open feed is gone.
    val feedListLoadedForAccount = liveState.accountStableId == accountStableId &&
        liveState.contentRevision > 0L &&
        !liveState.loading
    LaunchedEffect(liveState.feeds, feedListLoadedForAccount) {
        if (
            feedListLoadedForAccount &&
            selectedFeedPath != null &&
            liveState.feeds.none { feed -> feed.path == selectedFeedPath }
        ) {
            selectedFeedPath = null
        }
    }

    val editingFeedGone = editingExistingFeed && editingFeed == null && feedListLoadedForAccount
    if (editorVisible) {
        CustomFeedEditorDialog(
            state = state,
            editingFeed = editingFeed ?: editingFeedPath?.let { path ->
                CustomFeedSummary(
                    path = path,
                    displayName = feedName,
                    description = "",
                    visibility = "",
                    subredditNames = emptyList(),
                )
            },
            communities = communities,
            feedName = feedName,
            onFeedNameChange = { feedName = it.take(50) },
            selectedCommunities = selectedCommunities,
            onSelectedCommunitiesChange = { selectedCommunities = it },
            visibility = visibility,
            onVisibilityChange = { visibility = it },
            communityQuery = communityQuery,
            onCommunityQueryChange = { communityQuery = it },
            busy = editorBusy,
            closeLocked = editorCloseLocked,
            phaseMessage = if (editingFeedGone) {
                "This feed no longer exists."
            } else if (editingExistingFeed) {
                liveState.manageMessage?.takeUnless { liveState.managePhase == CustomFeedManagePhase.FAILED }
            } else {
                liveState.createMessage?.takeUnless { liveState.createPhase == CustomFeedCreatePhase.FAILED }
            },
            errorPresentation = if (editingExistingFeed) {
                if (liveState.managePhase == CustomFeedManagePhase.FAILED) {
                    customFeedManageErrorPresentation(liveState)
                } else {
                    null
                }
            } else if (liveState.createPhase == CustomFeedCreatePhase.FAILED) {
                customFeedCreateErrorPresentation(liveState)
            } else {
                null
            },
            submitBlocked = if (editingFeed != null) {
                liveState.managePhase == CustomFeedManagePhase.FAILED &&
                    liveState.manageFailureKind == CustomFeedManageFailureKind.OUTCOME_UNKNOWN
            } else if (editingExistingFeed) {
                // The feed being edited is not in the list: nothing to save.
                true
            } else {
                liveState.createPhase == CustomFeedCreatePhase.FAILED &&
                    liveState.createFailureKind == CustomFeedCreateFailureKind.OUTCOME_UNKNOWN
            },
            onDismiss = {
                if (!editorCloseLocked) {
                    val outcomeUnknown =
                        (
                            !editingExistingFeed &&
                                liveState.createPhase == CustomFeedCreatePhase.FAILED &&
                                liveState.createFailureKind ==
                                CustomFeedCreateFailureKind.OUTCOME_UNKNOWN
                        ) || (
                            editingExistingFeed &&
                                liveState.managePhase == CustomFeedManagePhase.FAILED &&
                                liveState.manageFailureKind ==
                                CustomFeedManageFailureKind.OUTCOME_UNKNOWN
                        )
                    if (outcomeUnknown) {
                        onRefresh()
                    }
                    editorVisible = false
                    editingFeedPath = null
                    if (!outcomeUnknown) {
                        onResetCustomFeedCreate()
                        onResetCustomFeedManage()
                    }
                }
            },
            onSubmit = {
                if (editingExistingFeed) {
                    if (editingFeed == null) return@CustomFeedEditorDialog
                    onUpdateCustomFeed(
                        CustomFeedUpdateRequest(
                            accountStableId = accountStableId,
                            path = editingFeed.path,
                            displayName = feedName.trim(),
                            subredditNames = selectedCommunities.sorted(),
                            visibility = visibility,
                        ),
                    )
                } else {
                    onCreateCustomFeed(
                        CustomFeedCreateRequest(
                            accountStableId = accountStableId,
                            displayName = feedName.trim(),
                            subredditNames = selectedCommunities.sorted(),
                            visibility = visibility,
                        ),
                    )
                }
            },
        )
    }

    // LOWUI6_DELETE_CANCEL_KEEPS_THE_READ_BACK_V1 (OCB-188): closing the delete
    // dialog after an OUTCOME_UNKNOWN delete keeps the pending outcome (so the
    // read-back can settle it) and refreshes, like the editor's dismiss.
    val closeDeleteDialog: () -> Unit = {
        val outcomeUnknown = liveState.managePhase == CustomFeedManagePhase.FAILED &&
            liveState.manageFailureKind == CustomFeedManageFailureKind.OUTCOME_UNKNOWN
        deleteFeedPath = null
        if (outcomeUnknown) onRefresh() else onResetCustomFeedManage()
    }
    if (deleteFeed != null) {
        AlertDialog(
            onDismissRequest = {
                if (!deleteCloseLocked) closeDeleteDialog()
            },
            title = { Text("Delete custom feed?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${deleteFeed.displayName} will be removed from your Reddit account.")
                    if (liveState.managePhase == CustomFeedManagePhase.FAILED) {
                        ErrorNotice(
                            presentation = customFeedManageErrorPresentation(liveState, deleting = true).copy(
                                primaryAction = null,
                            ),
                            diagnosticScopeKey = "community_custom_feed_manage",
                            onAction = {},
                        )
                    } else {
                        liveState.manageMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleteCloseLocked,
                    onClick = closeDeleteDialog,
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !manageBusy &&
                        !(
                            liveState.managePhase == CustomFeedManagePhase.FAILED &&
                                liveState.manageFailureKind ==
                                CustomFeedManageFailureKind.OUTCOME_UNKNOWN
                        ),
                    onClick = {
                        onDeleteCustomFeed(
                            CustomFeedDeleteRequest(
                                accountStableId = accountStableId,
                                path = deleteFeed.path,
                                displayName = deleteFeed.displayName,
                            ),
                        )
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (selectedFeed != null) {
        // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1
        PredictiveBackPage(onBack = { selectedFeedPath = null }, modifier = modifier) {
            CustomFeedBrowser(
                modifier = Modifier,
                state = state,
                accountStableId = accountStableId,
                feed = selectedFeed,
                postsState = customFeedPostsState,
                contentPadding = contentPadding,
                onBack = { selectedFeedPath = null },
                onRefresh = {
                    onLoadCustomFeedPosts(accountStableId, selectedFeed.path, true)
                },
                onLoadInitial = {
                    onLoadCustomFeedPosts(accountStableId, selectedFeed.path, false)
                },
                onAppend = {
                    onAppendCustomFeedPosts(accountStableId, selectedFeed.path)
                },
            )
        }
        return
    }

    RefreshableLazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        isRefreshing = authenticated && liveState.loading,
        onRefresh = if (authenticated) onRefresh else null,
    ) {
        when {
            !authenticated -> item("custom-feeds-sign-in") {
                EmptyCommunitySurface(
                    title = "Sign in to see custom feeds",
                    description = "Custom feeds belong to your Reddit account.",
                )
            }
            liveState.loading && liveState.feeds.isEmpty() -> item("custom-feeds-loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            liveState.failureKind != null && liveState.feeds.isEmpty() -> item("custom-feeds-failed") {
                EmptyCommunitySurface(
                    title = "Couldn’t load custom feeds",
                    description = when {
                        liveState.failureStatusCode != null ->
                            "Reddit returned HTTP ${liveState.failureStatusCode}. Pull to refresh or try again."
                        liveState.failureKind == CustomFeedFailureKind.PROTOCOL ->
                            "Reddit returned an unexpected response. Pull to refresh or try again."
                        liveState.failureKind == CustomFeedFailureKind.SESSION ->
                            "The Reddit session needs attention. Pull to refresh or reconnect."
                        else -> "Pull to refresh or try again."
                    },
                )
            }
            liveState.feeds.isEmpty() -> item("custom-feeds-empty") {
                CustomFeedsEmptyState(title = "No custom feeds yet")
            }
            else -> {
                item("custom-feeds-title") {
                    SectionTitle(
                        title = "Custom feeds",
                        meta = customFeedCountLabel(liveState.feeds.size),
                    )
                }
                items(
                    items = liveState.feeds,
                    key = CustomFeedSummary::path,
                ) { feed ->
                    CustomFeedRow(
                        feed = feed,
                        favorite = state.isCustomFeedFavorite(feed.path),
                        onOpen = { selectedFeedPath = feed.path },
                        onToggleFavorite = {
                            state.toggleFavoriteCustomFeed(feed.path)
                        },
                        onEdit = { beginEditEditor(feed) },
                        onDelete = {
                            onResetCustomFeedManage()
                            deleteFeedPath = feed.path
                        },
                        onAddToHomeScreen = if (
                            launcherShortcutsEnabled && launcherShortcutPinSupported
                        ) {
                            {
                                val requested = launcherShortcutPublisher.requestPin(
                                    LauncherShortcutEntry.CustomFeed(
                                        accountStableId = accountStableId,
                                        path = feed.path,
                                        displayName = customFeedShortcutLabel(feed.path, feed.displayName),
                                    ),
                                )
                                if (!requested) {
                                    userNotices.noticeFailure("Couldn't add the shortcut", "Your launcher did not accept it") // ISSUE222_NO_TOASTS_V1
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFeedEditorDialog(
    state: SubshellState,
    editingFeed: CustomFeedSummary?,
    communities: List<Community>,
    feedName: String,
    onFeedNameChange: (String) -> Unit,
    selectedCommunities: Set<String>,
    onSelectedCommunitiesChange: (Set<String>) -> Unit,
    visibility: CustomFeedVisibility,
    onVisibilityChange: (CustomFeedVisibility) -> Unit,
    communityQuery: String,
    onCommunityQueryChange: (String) -> Unit,
    busy: Boolean,
    closeLocked: Boolean,
    phaseMessage: String?,
    errorPresentation: ErrorPresentation?,
    submitBlocked: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    // ISSUE95_FEED_EDITOR_LISTS_EVERY_MEMBER_V1: the picker listed only the
    // account's subscriptions, so a member of the feed the reader does not
    // subscribe to (left later, or added on reddit.com) was counted and sent
    // back on Save but had no row to uncheck. Members are listed too, and
    // names compare without case.
    val pickerCommunities = remember(communities, editingFeed) {
        val listed = communities.map { community -> community.name.lowercase() }.toSet()
        communities + editingFeed?.subredditNames.orEmpty()
            .filter { name -> name.lowercase() !in listed }
            .distinctBy { name -> name.lowercase() }
            .map { name -> state.communityFor(name) ?: state.communityIdentityFor(name) }
    }
    val sortedCommunities = remember(pickerCommunities) {
        pickerCommunities.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { community -> community.name })
    }
    val visibleCommunities = remember(sortedCommunities, communityQuery) {
        val query = communityQuery.trim()
        if (query.isEmpty()) sortedCommunities else sortedCommunities.filter { community ->
            community.name.contains(query, ignoreCase = true) ||
                community.description.contains(query, ignoreCase = true)
        }
    }
    val valid = feedName.trim().isNotEmpty() && selectedCommunities.isNotEmpty()

    AdaptiveFullScreenDialog(
        title = if (editingFeed == null) "Create custom feed" else "Edit custom feed",
        onDismissRequest = onDismiss,
        closeEnabled = !closeLocked,
        closeLabel = "Cancel",
        actions = {
            TextButton(
                enabled = !busy && valid && !submitBlocked,
                onClick = onSubmit,
            ) {
                Text(if (editingFeed == null) "Create" else "Save")
            }
        },
    ) {
        errorPresentation?.let { presentation ->
            ErrorNotice(
                presentation = presentation,
                diagnosticScopeKey = "community_custom_feed",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                onAction = { action ->
                    if (action == ErrorActionKind.RETRY) onSubmit()
                },
            )
        } ?: phaseMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineProgressIndicator()
                Text(
                    text = if (editingFeed == null) "Creating feed…" else "Saving feed…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("feed-editor-intro") {
                Text(
                    text = "Choose the communities that should appear together.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item("feed-editor-name") {
                OutlinedTextField(
                    value = feedName,
                    onValueChange = onFeedNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Feed name") },
                    supportingText = { Text("${feedName.length}/50") },
                )
            }
            item("feed-editor-visibility") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Visibility", style = MaterialTheme.typography.titleSmall)
                    // M3225_SHORT_SINGLE_CHOICE_IS_A_SEGMENTED_ROW_V1
                    SegmentedChoiceRow(
                        options = CustomFeedVisibility.entries,
                        selected = visibility,
                        label = { it.displayLabel },
                        onSelect = { option -> if (!busy) onVisibilityChange(option) },
                        enabled = !busy,
                    )
                }
            }
            item("feed-editor-search") {
                SubshellSearchField(
                    query = communityQuery,
                    onQueryChange = onCommunityQueryChange,
                    placeholder = "Search communities",
                    enabled = !busy,
                )
            }
            item("feed-editor-community-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Communities", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${selectedCommunities.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (visibleCommunities.isEmpty()) {
                item("feed-editor-community-empty") {
                    Text(
                        text = if (communities.isEmpty()) {
                            "No subscribed communities are available."
                        } else {
                            "No communities match your search."
                        },
                        modifier = Modifier.padding(vertical = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = visibleCommunities,
                    key = { community -> "feed-picker-${community.name.lowercase()}" },
                ) { community ->
                    val checked = selectedCommunities.any { name ->
                        name.equals(community.name, ignoreCase = true)
                    }
                    val identity = state.communityIdentityFor(community.name)
                    val meta = communityMeta(identity) ?: communityMeta(community)
                    // ISSUE219_FEED_PICKER_ROWS_ARE_CHECKBOXES_V1: the row is the
                    // checkbox (LOWUI7), so TalkBack says "checkbox, checked".
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .toggleable(
                                value = checked,
                                enabled = !busy,
                                role = Role.Checkbox,
                                onValueChange = {
                                    onSelectedCommunitiesChange(
                                        if (checked) {
                                            selectedCommunities.filterNot { name ->
                                                name.equals(community.name, ignoreCase = true)
                                            }.toSet()
                                        } else {
                                            selectedCommunities + community.name
                                        },
                                    )
                                },
                            ),
                        shape = MaterialTheme.shapes.medium,
                        color = if (checked) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RemoteIdentityImage(
                                uri = identity.iconImageUri,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp).clip(CircleShape),
                            ) {
                                CommunityAvatar(
                                    name = community.name,
                                    accent = Color(identity.accent),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = state.subredditLabel(community.name),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                meta?.let { value ->
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val CustomFeedCommunitySetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { communities -> ArrayList(communities) },
    restore = { communities -> communities.toSet() },
)

@Composable
internal fun CustomFeedBrowser(
    modifier: Modifier,
    state: SubshellState,
    accountStableId: String,
    feed: CustomFeedSummary,
    postsState: CustomFeedPostsUiState,
    contentPadding: PaddingValues,
    videoViewportPadding: PaddingValues = PaddingValues(0.dp),
    showContextHeader: Boolean = true,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadInitial: () -> Unit,
    onAppend: () -> Unit,
) {
    val activeState = if (postsState.matches(accountStableId, feed.path)) {
        postsState
    } else {
        CustomFeedPostsUiState(accountStableId = accountStableId, feedPath = feed.path)
    }
    // FEEDS02_CUSTOM_FEED_SCROLL_KEYED_ON_FEED_V1: switching feeds at Home
    // keeps this composable, so the list state is keyed on the feed.
    val listState = rememberSaveable(accountStableId, feed.path, saver = LazyListState.Saver) {
        LazyListState()
    }
    // ISSUE234_FEED_POST_LAYOUTS_V1
    val postLayout = state.postLayout
    val gridColumns = rememberFeedGridColumns()
    // CONTENTFILTER02_EVERY_FEED_SURFACE_V1: a custom feed honours hidden
    // posts, the NSFW setting and content filters like every other feed.
    // CONTENTFILTER04_FILTERED_SUMMARY_V1
    // CONTENTFILTER06_REVEAL_KEYED_ON_ACCOUNT_V1
    var filteredRevealed by rememberSaveable(accountStableId, feed.path) { mutableStateOf(false) }
    val hiddenIdentities by remember(state) {
        derivedStateOf { hiddenPostIdentities(state.hiddenPostIds) }
    }
    // FEEDS04_DERIVED_ID_SET_READS_V1: hidden ids are read inside
    // derivedStateOf, not copied as a remember key on every recomposition.
    val filteredCount by remember(
        activeState.posts,
        state.showNsfwPosts,
        state.contentFilters,
    ) {
        derivedStateOf {
            activeState.posts.count { post ->
                !post.isHiddenBy(hiddenIdentities) && state.showsPost(post) && state.contentFilters.hides(post)
            }
        }
    }
    val filteredSummaryVisible = state.filteredPostsSummary && filteredCount > 0
    val showFilteredPosts = filteredSummaryVisible && filteredRevealed
    val visiblePosts by remember(
        activeState.posts,
        state.showNsfwPosts,
        state.contentFilters,
        showFilteredPosts,
    ) {
        derivedStateOf {
            activeState.posts.filter { post ->
                !post.isHiddenBy(hiddenIdentities) &&
                    state.showsPost(post) &&
                    (showFilteredPosts || !state.contentFilters.hides(post))
            }
        }
    }

    LaunchedEffect(accountStableId, feed.path) { onLoadInitial() }

    // FEEDS01_CUSTOM_FEED_APPEND_FAILURE_V1: a failure while posts are shown
    // was invisible, and the scroll gate never re-requested the same cursor.
    val degradedPostsPresentation = activeState.failureKind
        ?.takeIf { activeState.posts.isNotEmpty() }
        ?.let { failureKind ->
            customFeedPostsErrorPresentation(
                failureKind = failureKind,
                statusCode = activeState.failureStatusCode,
                retryable = activeState.failureRetryable,
                append = activeState.failedAppend,
                details = activeState.failureDetails,
            )
        }
    ReportViewportError(
        scopeKey = "custom-feed:$accountStableId:${feed.path}",
        presentation = degradedPostsPresentation,
        failureDetails = activeState.failureDetails,
        onRetry = if (degradedPostsPresentation != null) {
            if (activeState.failedAppend) onAppend else onRefresh
        } else {
            null
        },
    )
    val appendEnabled = activeState.nextCursor != null &&
        !activeState.loading &&
        !activeState.appending &&
        activeState.failureKind == null
    // FEEDS01_SEAMLESS_VIEWPORT_FILL_V1: filtered or short pages fill the
    // viewport by themselves, as on every other feed.
    var refreshGeneration by remember(accountStableId, feed.path) { mutableStateOf(0) }
    ObserveLiveFeedAutoAppend(
        listState = listState,
        sessionKey = "custom-feed|$accountStableId|${feed.path}|generation=$refreshGeneration",
        appendEnabled = appendEnabled,
        contentToken = activeState.nextCursor,
        loadedItemCount = activeState.posts.size,
        visibleItemCount = visiblePosts.size,
        onAppend = onAppend,
    )

    // ISSUE275_CUSTOM_FEED_VIDEOS_PLAY_IN_THE_FEED_V1: a custom feed sets up its
    // videos like Home and a community. Without its own viewport, a custom feed
    // shown at Home after the Home feed left Home's route holding playback with
    // no surfaces: a tap on play started and was revoked at once, and nothing
    // autoplayed.
    val revealedMediaIds = state.revealedMediaIds.toSet()
    val videoPostIds = remember(visiblePosts, revealedMediaIds, state.showNsfwMedia, postLayout) {
        // ISSUE234_FEED_POST_LAYOUTS_V1: only Large plays media in the feed.
        visiblePosts
            .filter { postLayout == PostLayout.LARGE }
            .filter { it.kind == PostKind.VIDEO && state.sensitiveMediaAutoplayAllowed(it) }
            .map(Post::id)
    }
    val terminalVideoId = rememberTerminalVideoId(
        videoPostIds,
        feedExhausted = activeState.nextCursor == null && !activeState.loading,
    )
    LaunchedEffect(videoPostIds, terminalVideoId) {
        state.updateVideoFeedContext(videoPostIds, terminalVideoId)
    }

    ObserveFeedVideoViewport(listState = listState, state = state)

    val animatedPostIds = remember(visiblePosts, revealedMediaIds, state.showNsfwMedia, postLayout) {
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

    Column(modifier = modifier.fillMaxSize()) {
        if (showContextHeader) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Symbols.ArrowBack, contentDescription = "Back to custom feeds")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = feed.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(feed.subredditNames.size)
                            append(if (feed.subredditNames.size == 1) " community" else " communities")
                            if (feed.visibility.isNotBlank()) {
                                append(" · ")
                                append(feed.visibility.replaceFirstChar { character -> character.uppercase() })
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
        GoogleFeedVideoViewport(
            state = state,
            sessionKey = "custom-feed|$accountStableId|${feed.path}",
            modifier = Modifier.weight(1f).fillMaxWidth(),
            viewportPadding = videoViewportPadding,
        ) {
            RefreshableLazyColumn(
                modifier = Modifier.fillMaxSize().then(gridColumns.measureModifier),
                state = listState,
                contentPadding = contentPadding,
                indicatorTopPadding = contentPadding.calculateTopPadding(),
                verticalArrangement = Arrangement.spacedBy(feedItemSpacing(postLayout, 8.dp)),
                isRefreshing = activeState.loading && activeState.posts.isNotEmpty(),
                onRefresh = {
                    refreshGeneration += 1
                    onRefresh()
                },
                loadNextEnabled = appendEnabled,
                loadNextToken = activeState.nextCursor,
                onLoadNext = onAppend,
            ) {
                if (filteredSummaryVisible) {
                    item("custom-feed-filtered-summary") {
                        FilteredPostsSummaryRow(
                            filteredCount = filteredCount,
                            revealed = filteredRevealed,
                            onToggle = { filteredRevealed = !filteredRevealed },
                        )
                    }
                }
                when {
                    activeState.loading && activeState.posts.isEmpty() -> item("custom-feed-posts-loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    activeState.failureKind != null && activeState.posts.isEmpty() -> item("custom-feed-posts-error") {
                        EmptyCommunitySurface(
                            title = "Couldn’t load ${feed.displayName}",
                            description = activeState.failureStatusCode?.let { status ->
                                "Reddit returned HTTP $status. Pull to refresh or try again."
                            } ?: "Pull to refresh or try again.",
                        )
                    }
                    activeState.posts.isEmpty() -> item("custom-feed-posts-empty") {
                        EmptyCommunitySurface(
                            title = "No posts in this feed",
                            description = "Pull to refresh or edit the communities in this feed.",
                        )
                    }
                    visiblePosts.isEmpty() -> item("custom-feed-posts-filtered") {
                        EmptyCommunitySurface(
                            title = "All posts are filtered",
                            description = "Hidden posts, the NSFW setting or your content filters hide every post loaded so far.",
                        )
                    }
                    else -> feedPostItems(visiblePosts, postLayout, gridColumns.count) { post, itemModifier ->
                        PostCard(
                            post = post,
                            state = state,
                            layout = postLayout,
                            onOpen = { state.openFromCustomFeed(post) },
                            modifier = itemModifier,
                        )
                    }
                }
                if (activeState.appending) {
                    item("custom-feed-posts-appending") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { InlineProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFeedsEmptyState(title: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Symbols.ViewList,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Group communities into a feed you can browse together.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FavoriteCustomFeedRow(
    feed: CustomFeedSummary,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Symbols.ViewList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = feed.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(feed.subredditNames.size)
                        append(if (feed.subredditNames.size == 1) " community" else " communities")
                        if (feed.visibility.isNotBlank()) {
                            append(" · ")
                            append(feed.visibility.replaceFirstChar { character ->
                                character.uppercase()
                            })
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    Symbols.StarFilled,
                    contentDescription = "Remove ${feed.displayName} from favorites",
                    tint = favoriteStarTint(favorite = true),
                )
            }
        }
    }
}

@Composable
private fun CustomFeedRow(
    feed: CustomFeedSummary,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToHomeScreen: (() -> Unit)? = null,
) {
    var menuExpanded by remember(feed.path) { mutableStateOf(false) }
    val context = LocalContext.current
    val userNotices = LocalViewportErrorHostState.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    feed.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (feed.description.isNotBlank()) {
                    Text(
                        feed.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    buildString {
                        append(feed.subredditNames.size)
                        append(if (feed.subredditNames.size == 1) " community" else " communities")
                        if (feed.visibility.isNotBlank()) {
                            append(" · ")
                            append(feed.visibility.replaceFirstChar { character -> character.uppercase() })
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (feed.subredditNames.isNotEmpty()) {
                    Text(
                        feed.subredditNames.take(4).joinToString(" · ") { name -> "r/$name" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (favorite) {
                        Symbols.StarFilled
                    } else {
                        Symbols.Star
                    },
                    contentDescription = if (favorite) {
                        "Remove ${feed.displayName} from favorites"
                    } else {
                        "Add ${feed.displayName} to favorites"
                    },
                    tint = favoriteStarTint(favorite),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Symbols.MoreVert, contentDescription = "Manage ${feed.displayName}")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit feed") },
                        leadingIcon = { Icon(Symbols.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    // REDDITURL05_CUSTOM_FEED_LINK_V1
                    if (customFeedLinkShareable(feed)) {
                        DropdownMenuItem(
                            text = { Text("Copy link") },
                            leadingIcon = { Icon(Symbols.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                copyCustomFeedLink(context, feed, userNotices)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share link") },
                            leadingIcon = { Icon(Symbols.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                shareCustomFeedLink(context, feed, userNotices)
                            },
                        )
                    }
                    // ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1
                    if (onAddToHomeScreen != null) {
                        DropdownMenuItem(
                            text = { Text("Add to home screen") },
                            leadingIcon = {
                                Icon(Symbols.AddToHomeScreen, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToHomeScreen()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete feed", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Symbols.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
@Composable
private fun SearchAllCommunitiesRow(
    query: String,
    onClick: () -> Unit,
) {
    ListCard(
        headline = "Search all communities for “$query”",
        headlineMaxLines = 2,
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        leading = {
            Icon(
                Symbols.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Icon(Symbols.ArrowForward, contentDescription = null)
        },
    )
}

@Composable
private fun AutocompleteFailureNotice(
    presentation: app.subshell.prototype.ui.error.ErrorPresentation,
    onRetry: () -> Unit,
) {
    ErrorNotice(
        presentation = presentation,
        diagnosticScopeKey = "community_discovery_suggestions",
        onAction = { action ->
            if (action == ErrorActionKind.RETRY) onRetry()
        },
    )
}

@Composable
private fun LiveSubscriptionsStatus(
    failureKind: RedditSubscriptionsUiFailureKind,
    failureDetails: app.subshell.prototype.data.error.FailureDetails?,
    showingCachedList: Boolean,
    retryable: Boolean,
    onRetry: () -> Unit,
) {
    ErrorNotice(
        presentation = redditSubscriptionsErrorPresentation(
            failureKind = failureKind,
            details = failureDetails,
            retryable = retryable,
            showingCachedContent = showingCachedList,
        ),
        diagnosticScopeKey = "community_subscriptions",
        onAction = { action ->
            if (action == ErrorActionKind.RETRY) onRetry()
        },
    )
}

@Composable
private fun LiveCommunityDiscoveryStatus(
    failureKind: RedditCommunityDiscoveryUiFailureKind,
    failureDetails: app.subshell.prototype.data.error.FailureDetails?,
    showingCachedList: Boolean,
    retryable: Boolean,
    onRetry: () -> Unit,
) {
    ErrorNotice(
        presentation = redditCommunityDiscoveryErrorPresentation(
            failureKind = failureKind,
            details = failureDetails,
            retryable = retryable,
            showingCachedContent = showingCachedList,
        ),
        diagnosticScopeKey = "community_discovery",
        onAction = { action ->
            if (action == ErrorActionKind.RETRY) onRetry()
        },
    )
}

@Composable
private fun LoadingCommunities() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LiveSubscriptionsEmpty() {
    EmptyCommunitySurface(
        title = "No communities yet",
        description = "Communities you join on Reddit will appear here.",
    )
}

@Composable
private fun LiveCommunityDiscoveryEmpty(
    hasUnfilteredResults: Boolean,
    nsfwHidden: Boolean,
) {
    EmptyCommunitySurface(
        title = if (hasUnfilteredResults) "No visible results" else "No communities found",
        description = when {
            nsfwHidden ->
                "Results hidden by your NSFW preference or because you already follow them."
            hasUnfilteredResults ->
                "The returned communities are already in your joined list."
            else -> "Try another name or topic."
        },
    )
}

internal fun formatRecentCommunityAge(
    visitedAtEpochMillis: Long,
    nowEpochMillis: Long,
): String {
    val elapsedMinutes = ((nowEpochMillis - visitedAtEpochMillis).coerceAtLeast(0L) / 60_000L)
    return when {
        elapsedMinutes < 1L -> "Just now"
        elapsedMinutes < 60L -> "$elapsedMinutes min ago"
        elapsedMinutes < 24L * 60L -> "${elapsedMinutes / 60L} h ago"
        elapsedMinutes < 48L * 60L -> "Yesterday"
        else -> "${elapsedMinutes / (24L * 60L)} d ago"
    }
}

@Composable
private fun EmptyCommunitySurface(
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    meta: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            meta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null) {
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun CommunityRow(
    community: Community,
    state: SubshellState,
    joined: Boolean,
    showMembershipAction: Boolean,
    nsfw: Boolean = false,
    showJoinedBadge: Boolean = false,
    showFavoriteAction: Boolean = false,
    showHiddenAction: Boolean = false,
    hidden: Boolean = false,
    supportingMeta: String? = null,
    membershipActionEnabledOverride: Boolean? = null,
    membershipInProgress: Boolean = false,
    membershipErrorPresentation: ErrorPresentation? = null,
    onToggle: () -> Unit,
) {
    // NEXT01A5_SELECTED_COMMUNITY_CONTINUITY_V1
    val selected =
        state.currentSubscriptionsCommunitySelectionMatches(community.name)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        // ISSUE220_COMMUNITY_ROW_ACTIONS_REFLOW_AT_LARGE_TEXT_V1: from 130 % text
        // the trailing buttons move under the name (as the community header), so
        // the name is not cut mid-word beside them.
        val rowLargeText = LocalDensity.current.fontScale >= 1.3f
        val rowActions: @Composable RowScope.() -> Unit = {
            Spacer(Modifier.width(8.dp))
            if (joined) {
                val communityMute = state.communityMuteFor(community.name)
                IconButton(
                    onClick = {
                        state.requestCommunityMuteEditor(community.name)
                    },
                ) {
                    Icon(
                        imageVector = if (communityMute != null) {
                            Symbols.VolumeUp
                        } else {
                            Symbols.VolumeOff
                        },
                        contentDescription = if (communityMute != null) {
                            "Edit mute for ${state.subredditLabel(community.name)}"
                        } else {
                            "Mute ${state.subredditLabel(community.name)}"
                        },
                        tint = if (communityMute != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (showFavoriteAction) {
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
            }
            if (showHiddenAction) {
                IconButton(
                    onClick = { state.toggleHiddenCommunity(community.name) },
                ) {
                    Icon(
                        imageVector = if (hidden) {
                            Symbols.Visibility
                        } else {
                            Symbols.VisibilityOff
                        },
                        contentDescription = if (hidden) {
                            "Show ${state.subredditLabel(community.name)} in Joined"
                        } else {
                            "Hide ${state.subredditLabel(community.name)} from Joined"
                        },
                        tint = if (hidden) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            when {
                showMembershipAction && joined -> FilledTonalButton(
                    onClick = onToggle,
                    enabled =
                        (
                            membershipActionEnabledOverride
                                ?: state.localMutationsAllowed
                        ) &&
                            !membershipInProgress,
                    contentPadding = PaddingValues(horizontal = 11.dp),
                ) {
                    if (membershipInProgress) {
                        InlineProgressIndicator()
                        Spacer(Modifier.width(4.dp))
                        Text("Leaving…")
                    } else {
                        Icon(
                            Symbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Joined")
                    }
                }
                showMembershipAction -> Button(
                    onClick = onToggle,
                    enabled =
                        (
                            membershipActionEnabledOverride
                                ?: state.localMutationsAllowed
                        ) &&
                            !membershipInProgress,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    if (membershipInProgress) {
                        InlineProgressIndicator()
                        Spacer(Modifier.width(4.dp))
                        Text("Joining…")
                    } else {
                        Text("Join")
                    }
                }
                showJoinedBadge || nsfw -> Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (showJoinedBadge) {
                        CommunityStatusBadge(
                            text = "Joined",
                            showCheck = true,
                        )
                    }
                    if (nsfw) {
                        CommunityStatusBadge(text = "18+")
                    }
                }
            }
        }
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // ISSUE219_SELECTED_COMMUNITY_ROW_IS_ANNOUNCED_V1: the open community in
                    // list-detail was colour only; the row now carries the selection.
                    .selectable(selected = selected) {
                        state.openSubscriptionsCommunitySelection(community.name)
                    }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommunityAvatar(
                    name = community.name,
                    accent = Color(community.accent),
                    modifier = Modifier.size(46.dp),
                    iconImageUri = community.iconImageUri,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // ISSUE220_COMMUNITY_ROW_NAME_WRAPS_V1: beside three trailing buttons
                    // the name had ~140 dp at large text; it wraps before it cuts,
                    // never right after "r/" (a word joiner keeps the prefix on).
                    Text(
                        state.subredditLabel(community.name).replaceFirst("/", "/\u2060"),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    communityMeta(community)?.let { meta ->
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    supportingMeta?.let { meta ->
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    membershipErrorPresentation?.let { presentation ->
                        Text(
                            text = presentation.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = presentation.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        presentation.technicalDetail?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!rowLargeText) rowActions()
            }
            if (rowLargeText) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 13.dp, end = 13.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = rowActions,
                )
            }
        }
    }
}

@Composable
private fun CommunityStatusBadge(
    text: String,
    showCheck: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCheck) {
                Icon(
                    Symbols.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun communityMeta(community: Community): String? = buildList {
    community.members
        .takeUnless { members -> members.isBlank() || members == "—" }
        ?.let { members -> add("$members members") }
    community.description
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let(::add)
}.joinToString(" · ").takeIf(String::isNotEmpty)

internal fun inlineCommunityDiscoveryItems(
    items: List<CommunityDiscoveryItem>,
    joinedCommunityNames: Set<String>,
    showNsfw: Boolean,
    limit: Int = INLINE_COMMUNITY_DISCOVERY_CAROUSEL_LIMIT,
): List<CommunityDiscoveryItem> {
    if (limit <= 0) return emptyList()
    return items
        .asSequence()
        .filter { item -> showNsfw || !item.nsfw }
        .filterNot { item -> item.community.name.lowercase() in joinedCommunityNames }
        .distinctBy { item -> item.community.name.lowercase() }
        .take(limit)
        .toList()
}

// ISSUE277_DISCOVERY_STRIPS_THE_R_PREFIX_V1: "r/celebs" found nothing; Reddit's
// community search wants the bare name, as the composer and flair searches give it.
internal fun normalizeDiscoveryDraft(value: String): String =
    value.trim()
        .replace(Regex("^/?[rR]/"), "")
        .trim()
        .replace(Regex("\\s+"), " ")

private const val COMMUNITY_TYPEAHEAD_DEBOUNCE_MS = 275L
private const val INLINE_COMMUNITY_DISCOVERY_CAROUSEL_LIMIT = 6
private const val INLINE_COMMUNITY_DISCOVERY_LIST_LIMIT = 3

private tailrec fun Context.findHiddenSubscriptionsActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHiddenSubscriptionsActivity()
    else -> null
}

// ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: only the write and the brief
// browser launch keep the custom feed dialogs open.
internal fun customFeedCreateCloseLocked(phase: CustomFeedCreatePhase): Boolean =
    phase == CustomFeedCreatePhase.CREATING || phase == CustomFeedCreatePhase.AUTHORIZING

internal fun customFeedManageCloseLocked(phase: CustomFeedManagePhase): Boolean =
    phase == CustomFeedManagePhase.SAVING ||
        phase == CustomFeedManagePhase.DELETING ||
        phase == CustomFeedManagePhase.AUTHORIZING
