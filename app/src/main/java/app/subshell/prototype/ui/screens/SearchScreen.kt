package app.subshell.prototype.ui.screens

// SEARCH02A12_SEARCH_INTENT_ONLY_ZERO_QUERY_V5
// SEARCH02A12_POPULAR_DATA_PATH_RETIRED_V9

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import app.subshell.prototype.ui.RandomNsfwCommunityPlacement
import app.subshell.prototype.ui.app.RandomNsfwCommunityButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.ui.components.routeTopBarWindowInsets
import app.subshell.prototype.ui.identityKey
import app.subshell.prototype.ui.scopeSummary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.RedditTarget
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.app.presentRedditLinkUnavailable
import app.subshell.prototype.ui.app.RedditLinkUnavailableReason
import app.subshell.prototype.data.REDDIT_SEARCH_QUERY_MAX_LENGTH
import app.subshell.prototype.model.COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.liveMainPostFeedQueryKey
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.ui.ContentFilters
import app.subshell.prototype.ui.ObserveLiveFeedAutoAppend
import app.subshell.prototype.ui.SearchArea
import app.subshell.prototype.ui.SearchDirectAction
import app.subshell.prototype.ui.SearchIndexKind
import app.subshell.prototype.ui.SearchProviderKind
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.ZeroQueryCommunitySuggestion
import app.subshell.prototype.ui.components.FilteredPostsSummaryRow
import app.subshell.prototype.ui.selectZeroQueryCommunitySuggestions
import app.subshell.prototype.ui.canAppendRedditFeed
import app.subshell.prototype.ui.pagingFooterKey
import app.subshell.prototype.ui.planSearchQuery
import app.subshell.prototype.ui.postIdOrNull
import app.subshell.prototype.ui.rankSearchMatches
import app.subshell.prototype.ui.rerankSearchProviderResults
import app.subshell.prototype.ui.rememberFeedScrollState
import app.subshell.prototype.ui.feedItemSpacing
import app.subshell.prototype.ui.feedPostItemKeys
import app.subshell.prototype.ui.feedPostItems
import app.subshell.prototype.ui.forFeed
import app.subshell.prototype.ui.rememberFeedGridColumns
import app.subshell.prototype.ui.components.PostLayoutMenuButton
import app.subshell.prototype.ui.searchFeedScrollKey
import app.subshell.prototype.ui.searchPostFeedQueryKey
import app.subshell.prototype.ui.hiddenPostIdentities
import app.subshell.prototype.ui.isHiddenBy
import app.subshell.prototype.ui.components.CommunityAvatar
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.components.RepositoryPagingFooter
import app.subshell.prototype.ui.components.RefreshableLazyColumn
import app.subshell.prototype.ui.components.accentFor
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ReportViewportError
import app.subshell.prototype.ui.error.redditFeedAppendErrorPresentation
import app.subshell.prototype.ui.error.redditFeedErrorPresentation
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.delay
import app.subshell.prototype.ui.components.SubshellSearchField
import app.subshell.prototype.ui.components.ListCard

internal fun shouldLoadLivePeopleDraftSuggestions(
    authenticated: Boolean,
    query: String,
    scope: SearchScope,
    area: SearchArea,
    hasDirectAction: Boolean,
): Boolean =
    authenticated &&
        !hasDirectAction &&
        area != SearchArea.CURRENT_COMMUNITY &&
        (scope == SearchScope.ALL || scope == SearchScope.USERS) &&
        query.trim().length in 2..PEOPLE_DRAFT_AUTOCOMPLETE_MAX_QUERY_LENGTH

private const val PEOPLE_DRAFT_AUTOCOMPLETE_MAX_QUERY_LENGTH = 25
// Reddit's people search takes up to 100 characters (the data layer's
// REDDIT_PEOPLE_SEARCH_MAX_QUERY_LENGTH; ui/ does not import the network layer).
private const val REDDIT_PEOPLE_SEARCH_MAX_QUERY_LENGTH = 100

// SEARCH01C2B_FEDERATED_RESULT_HIERARCHY_V1
// SEARCH01C2B1_TOP_FIELD_SUBMIT_ACTION_V1
internal const val SEARCH01C2B_ALL_COMMUNITY_PREVIEW_LIMIT = 3
internal const val SEARCH01C2B_ALL_PEOPLE_PREVIEW_LIMIT = 2

internal fun shouldShowSearchDraftSuggestions(
    draftQuery: String,
    submittedQuery: String,
    hasUnsubmittedChanges: Boolean,
): Boolean =
    draftQuery.trim().length >= 2 &&
        (hasUnsubmittedChanges || submittedQuery.trim().isBlank())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SubshellState,
    redditFeedState: RedditFeedUiState = RedditFeedUiState(),
    redditCommunityDiscoveryState: RedditCommunityDiscoveryUiState =
        RedditCommunityDiscoveryUiState(),
    onLoadRedditCommunityDiscovery: (
        String,
        String?,
        CommunityDiscoverySort?,
        Boolean?,
        Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    onRefreshRedditFeed: (
        String,
        RedditMainFeed,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    onRefreshRedditSearch: (String, String, String?) -> Unit = { _, _, _ -> },
    onAppendRedditSearch: (String, String, String?) -> Unit = { _, _, _ -> },
    onResolveRedditNavigationTarget: (String, String, (Post?) -> Unit) -> Unit =
        { _, _, onResolved -> onResolved(null) },
    onBack: () -> Unit,
    // NEXT01A2_SEARCH_LIST_DETAIL_SELECTION_V1
    onOpenPostSelection: ((Post) -> Unit)? = null,
    showWorkspaceControls: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val draftQuery = state.searchQuery.trim()
    val submittedQuery = state.searchSubmittedQuery.trim()
    val queryPlan = remember(
        draftQuery,
        state.searchArea,
        state.activeAccountIsAuthenticated,
    ) {
        planSearchQuery(
            rawQuery = draftQuery,
            area = state.searchArea,
            authenticated = state.activeAccountIsAuthenticated,
        )
    }
    val query = draftQuery
    val directAction = queryPlan.directAction
    val draftReady = directAction == null && query.length >= 2
    val submittedResultsActive = submittedQuery.length >= 2
    val submittedScopeIncludesPosts =
        state.searchScope == SearchScope.ALL || state.searchScope == SearchScope.POSTS
    val submittedScopeIncludesCommunities =
        state.searchScope == SearchScope.ALL || state.searchScope == SearchScope.COMMUNITIES
    val submittedScopeIncludesUsers =
        state.searchScope == SearchScope.ALL || state.searchScope == SearchScope.USERS
    val draftScopeIncludesCommunities =
        state.searchScope == SearchScope.ALL || state.searchScope == SearchScope.COMMUNITIES
    val draftScopeIncludesUsers =
        state.searchScope == SearchScope.ALL || state.searchScope == SearchScope.USERS
    val draftSuggestionsActive = shouldShowSearchDraftSuggestions(
        draftQuery = query,
        submittedQuery = submittedQuery,
        hasUnsubmittedChanges = state.searchHasUnsubmittedChanges,
    )
    val authenticatedRedditSearch = state.activeAccountIsAuthenticated &&
        submittedScopeIncludesPosts
    val liveSearchCommunity = state.searchOriginCommunity
        .takeIf { state.searchArea == SearchArea.CURRENT_COMMUNITY }
    val liveSearchEligible = authenticatedRedditSearch &&
        submittedQuery.length in 2..REDDIT_SEARCH_QUERY_MAX_LENGTH
    val pagingKey = remember(
        state.activeAccountSession,
        state.searchOriginDestination,
        state.searchOriginCommunity,
        submittedQuery,
        state.searchScope,
        state.searchArea,
    ) {
        searchPostFeedQueryKey(
            account = state.activeAccountSession,
            destination = state.searchOriginDestination,
            originCommunity = state.searchOriginCommunity,
            query = submittedQuery,
            scope = state.searchScope,
            area = state.searchArea,
        )
    }
    val activeRedditFeedState = if (
        liveSearchEligible && redditFeedState.matches(
            accountStableId = state.activeAccountId,
            queryKey = pagingKey,
        )
    ) {
        redditFeedState
    } else {
        RedditFeedUiState()
    }
    val liveSearchSubmitted = liveSearchEligible && redditFeedState.matches(
        accountStableId = state.activeAccountId,
        queryKey = pagingKey,
    )
    LaunchedEffect(
        pagingKey,
        submittedQuery,
        directAction,
        state.searchScope,
        state.postPagingInvalidationRevision,
    ) {
        if (
            submittedResultsActive &&
            submittedScopeIncludesPosts &&
            (!authenticatedRedditSearch || submittedQuery.length <= REDDIT_SEARCH_QUERY_MAX_LENGTH)
        ) {
            state.ensurePostPaging(pagingKey)
        }
    }
    val pagingSnapshot = state.postPagingSnapshot(pagingKey)
    val areaCommunities = state.communities.filter { state.matchesSearchArea(it.name) }
    val autocompleteQuery = queryPlan.communityAutocompleteQuery.takeIf {
        draftSuggestionsActive
    }
    val activeCommunityDiscoveryState = if (
        autocompleteQuery != null && redditCommunityDiscoveryState.matches(
            accountStableId = state.activeAccountId,
            searchQuery = autocompleteQuery,
            searchSort = null,
            autocompleteIncludeOver18 = state.showNsfwPosts,
        )
    ) {
        redditCommunityDiscoveryState
    } else {
        RedditCommunityDiscoveryUiState(accountStableId = state.activeAccountId)
    }
    LaunchedEffect(
        state.activeAccountIsAuthenticated,
        state.activeAccountId,
        autocompleteQuery,
        state.showNsfwPosts,
    ) {
        if (state.activeAccountIsAuthenticated && autocompleteQuery != null) {
            delay(250L)
            onLoadRedditCommunityDiscovery(
                state.activeAccountId,
                autocompleteQuery,
                null,
                state.showNsfwPosts,
                false,
            )
        }
    }
    val remoteCommunityCandidates = activeCommunityDiscoveryState.items
        .asSequence()
        .filter { item -> state.showNsfwPosts || !item.nsfw }
        .map { item -> item.community }
        .filter { community -> state.matchesSearchArea(community.name) }
        .toList()
    val draftCommunityResults = if (query.length >= 2) {
        rankSearchMatches(
            rawQuery = query,
            values = (remoteCommunityCandidates + areaCommunities)
                .distinctBy { community -> community.name.lowercase() },
            primaryText = { community -> community.name },
            secondaryText = { community -> community.description },
            boost = { community ->
                state.searchIndexBoostFor(SearchIndexKind.COMMUNITY, community.name)
            },
        )
    } else {
        emptyList()
    }
    val communitySuggestions = if (
        draftSuggestionsActive &&
        state.searchArea != SearchArea.CURRENT_COMMUNITY &&
        draftScopeIncludesCommunities
    ) {
        draftCommunityResults.take(4)
    } else {
        emptyList()
    }

    val draftPeopleAutocompleteQuery = query.trim().takeIf { normalizedQuery ->
        draftSuggestionsActive &&
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = state.activeAccountIsAuthenticated,
                query = normalizedQuery,
                scope = state.searchScope,
                area = state.searchArea,
                hasDirectAction = directAction != null,
            )
    }
    val activeDraftPeopleSearchState = if (
        draftPeopleAutocompleteQuery != null &&
        redditCommunityDiscoveryState.peopleSearch.matches(
            accountStableId = state.activeAccountId,
            searchQuery = draftPeopleAutocompleteQuery,
        )
    ) {
        redditCommunityDiscoveryState.peopleSearch
    } else {
        RedditPeopleSearchUiState(
            accountStableId = state.activeAccountId,
            searchQuery = draftPeopleAutocompleteQuery,
        )
    }
    LaunchedEffect(
        state.activeAccountIsAuthenticated,
        state.activeAccountId,
        draftPeopleAutocompleteQuery,
        state.searchScope,
        state.searchArea,
    ) {
        if (draftPeopleAutocompleteQuery != null) {
            delay(250L)
            // SEARCH01C2A2_UNIFIED_DRAFT_COMMUNITIES_PEOPLE_SUGGESTIONS
            onLoadRedditCommunityDiscovery(
                state.activeAccountId,
                draftPeopleAutocompleteQuery,
                null,
                null,
                false,
            )
        }
    }
    val draftRemotePeopleResults = if (draftPeopleAutocompleteQuery != null) {
        rerankSearchProviderResults(
            rawQuery = draftPeopleAutocompleteQuery,
            values = activeDraftPeopleSearchState.items
                .asSequence()
                .filter { item -> state.showNsfwPosts || !item.nsfw }
                .map { item ->
                    SearchUserResult(
                        username = item.username,
                        description = item.description.ifBlank { item.title },
                        metadata = "",
                        avatarImageUri = item.avatarImageUri,
                    )
                }
                .toList(),
            primaryText = { user -> user.username },
            secondaryText = { user -> user.description },
            boost = { user ->
                state.searchIndexBoostFor(SearchIndexKind.USER, user.username)
            },
        )
    } else {
        emptyList()
    }

    val submittedDirectorySearchEligible =
        state.activeAccountIsAuthenticated &&
            submittedResultsActive &&
            directAction == null &&
            (submittedScopeIncludesCommunities || submittedScopeIncludesUsers) &&
            state.searchArea != SearchArea.CURRENT_COMMUNITY &&
            submittedQuery.length <= COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH
    val submittedCommunitySearchEligible =
        submittedDirectorySearchEligible && submittedScopeIncludesCommunities
    val submittedPeopleSearchEligible =
        submittedDirectorySearchEligible && submittedScopeIncludesUsers
    val retainedSubmittedCommunityItems = remember(
        state.activeAccountId,
        submittedQuery,
        state.searchSubmissionRevision,
    ) {
        mutableStateOf<List<CommunityDiscoveryItem>>(emptyList())
    }
    // ISSUE57_RESUBMIT_REFILLS_RETAINED_RESULTS_V1: the holder above is
    // emptied by every submit; a resubmit of the same query loads nothing new
    // (the coordinator keeps a loaded answer), so the copy also runs per submit.
    LaunchedEffect(
        redditCommunityDiscoveryState.contentRevision,
        submittedCommunitySearchEligible,
        state.activeAccountId,
        submittedQuery,
        state.searchSubmissionRevision,
    ) {
        if (
            submittedCommunitySearchEligible &&
            redditCommunityDiscoveryState.matches(
                accountStableId = state.activeAccountId,
                searchQuery = submittedQuery,
                searchSort = CommunityDiscoverySort.RELEVANCE,
                autocompleteIncludeOver18 = null,
            )
        ) {
            retainedSubmittedCommunityItems.value =
                redditCommunityDiscoveryState.items
        }
    }
    LaunchedEffect(
        submittedCommunitySearchEligible,
        state.activeAccountId,
        submittedQuery,
        state.searchSubmissionRevision,
    ) {
        if (submittedCommunitySearchEligible) {
            onLoadRedditCommunityDiscovery(
                state.activeAccountId,
                submittedQuery,
                CommunityDiscoverySort.RELEVANCE,
                null,
                false,
            )
        }
    }
    LaunchedEffect(
        submittedPeopleSearchEligible,
        state.activeAccountId,
        submittedQuery,
        state.searchSubmissionRevision,
    ) {
        if (submittedPeopleSearchEligible) {
            // SEARCH01C2A1_PEOPLE_PROVIDER_OWNS_ITS_SUBMITTED_DISPATCH
            onLoadRedditCommunityDiscovery(
                state.activeAccountId,
                submittedQuery,
                null,
                null,
                false,
            )
        }
    }
    val submittedRemoteCommunities = retainedSubmittedCommunityItems.value
        .asSequence()
        .filter { item -> state.showNsfwPosts || !item.nsfw }
        .map { item -> item.community }
        .filter { community -> state.matchesSearchArea(community.name) }
        .toList()
    val submittedCommunityResults = if (
        submittedResultsActive && submittedScopeIncludesCommunities
    ) {
        rankSearchMatches(
            rawQuery = submittedQuery,
            values = (submittedRemoteCommunities + areaCommunities)
                .distinctBy { community -> community.name.lowercase() },
            primaryText = { community -> community.name },
            secondaryText = { community -> community.description },
            boost = { community ->
                state.searchIndexBoostFor(SearchIndexKind.COMMUNITY, community.name)
            },
        )
    } else {
        emptyList()
    }
    val zeroQueryActive = query.isBlank() && submittedQuery.isBlank()
val trendingSearchSeeds = if (zeroQueryActive) state.trendingSearchSeeds else emptyList()

    LaunchedEffect(zeroQueryActive) {
        if (zeroQueryActive) state.refreshTrendingSearchSeedsIfStale()
    }

    val recentIndexedPeople = remember(
        state.activeAccountId,
        state.searchIndexEntries.toList(),
    ) {
        state.recentSearchIndexEntries(SearchIndexKind.USER, limit = 4)
    }
    // FEEDS04_CACHED_COMMUNITY_IDENTITY_OFF_MAIN_V1: derived, so a stored
    // identity that loads after the first frame reaches this list.
    val recentCommunities by remember(state.activeAccountId, areaCommunities) {
        derivedStateOf { state.recentCommunities(areaCommunities, limit = 4) }
    }
    val recentCommunityNames = recentCommunities
        .map { community -> community.name.lowercase() }
        .toSet()
    val suggestedCommunities = remember(
        state.activeAccountId,
        state.activeAccountIsAuthenticated,
        state.effectiveFavoriteCommunityNames,
        state.joinedCommunityIds.toList(),
        state.searchIndexEntries.toList(),
        areaCommunities,
        recentCommunityNames,
    ) {
        val areaByName = areaCommunities
            .distinctBy { community -> community.name.lowercase() }
            .associateBy { community -> community.name.lowercase() }
        val favoriteCandidates = state.favoriteCommunities(areaCommunities)
            .filter { community -> state.matchesSearchArea(community.name) }
            .map { community ->
            ZeroQueryCommunitySuggestion(
                name = community.name,
                description = community.description,
                imageUri = community.iconImageUri,
                favorite = true,
                joined = community.id in state.joinedCommunityIds,
                interactionBoost = state.searchIndexBoostFor(
                    SearchIndexKind.COMMUNITY,
                    community.name,
                ),
            )
        }
        val indexedCandidates = state
            .recentSearchIndexEntries(SearchIndexKind.COMMUNITY, limit = 12)
            .mapNotNull { entry ->
                val community = areaByName[entry.key.lowercase()]
                val communityName = community?.name ?: entry.primaryText
                if (!state.matchesSearchArea(communityName)) return@mapNotNull null
                ZeroQueryCommunitySuggestion(
                    name = communityName,
                    description = community?.description ?: entry.secondaryText,
                    imageUri = community?.iconImageUri ?: entry.imageUri,
                    favorite = state.isCommunityFavorite(communityName),
                    joined = community?.id?.let { id ->
                        id in state.joinedCommunityIds
                    } == true,
                    interactionBoost = state.searchIndexBoostFor(
                        SearchIndexKind.COMMUNITY,
                        communityName,
                    ),
                )
            }
        val joinedCandidates = areaCommunities
            .filter { community -> community.id in state.joinedCommunityIds }
            .map { community ->
                ZeroQueryCommunitySuggestion(
                    name = community.name,
                    description = community.description,
                    imageUri = community.iconImageUri,
                    favorite = state.isCommunityFavorite(community.name),
                    joined = true,
                    interactionBoost = state.searchIndexBoostFor(
                        SearchIndexKind.COMMUNITY,
                        community.name,
                    ),
                )
            }
        val genericCandidates = if (state.activeAccountIsAuthenticated) {
            emptyList()
        } else {
            areaCommunities.map { community ->
                ZeroQueryCommunitySuggestion(
                    name = community.name,
                    description = community.description,
                    imageUri = community.iconImageUri,
                )
            }
        }
        selectZeroQueryCommunitySuggestions(
            authenticated = state.activeAccountIsAuthenticated,
            candidates = favoriteCandidates + indexedCandidates + joinedCandidates + genericCandidates,
            recentCommunityNames = recentCommunityNames,
            limit = 4,
        )
    }
    val localPostQuery = submittedQuery
    val matchingPostResults = if (
        submittedResultsActive && submittedScopeIncludesPosts
    ) {
        if (authenticatedRedditSearch) {
            run {
                val hiddenIdentities = hiddenPostIdentities(state.hiddenPostIds)
                pagingSnapshot.items.filter { post -> !post.isHiddenBy(hiddenIdentities) }
            }
        } else {
            pagingSnapshot.items.filter { post ->
                state.matchesSearchArea(post.community) &&
                    listOf(post.title, post.body, post.community, post.author, post.flair.orEmpty())
                        .any { value -> value.contains(localPostQuery, ignoreCase = true) }
            }
        }
    } else {
        emptyList()
    }
    // CONTENTFILTER04_FILTERED_SUMMARY_V1: the count row search shares with
    // the feeds; Show reveals the filtered results for this query.
    // CONTENTFILTER06_REVEAL_KEYED_ON_ACCOUNT_V1: the paging key carries
    // the account, query, scope and area.
    var filteredRevealed by rememberSaveable(pagingKey) { mutableStateOf(false) }
    // CONTENTFILTER02_EVERY_FEED_SURFACE_V1
    val filteredSearchResults = searchPostResults(
        matching = matchingPostResults,
        showsPost = state::showsPost,
        filters = state.contentFilters,
        summaryEnabled = state.filteredPostsSummary,
        revealed = filteredRevealed,
        communityPage = state.searchOriginCommunity.takeIf {
            state.searchArea == SearchArea.CURRENT_COMMUNITY
        },
    )
    val filteredCount = filteredSearchResults.filteredCount
    val filteredSummaryVisible = filteredSearchResults.summaryVisible
    val postResults = filteredSearchResults.posts
    val draftUserResults = if (
        !state.activeAccountIsAuthenticated &&
        query.length >= 2
    ) {
        rankSearchMatches(
            rawQuery = query,
            values = state.users,
            primaryText = { user -> user.username },
            secondaryText = { user -> user.description },
            boost = { user ->
                state.searchIndexBoostFor(SearchIndexKind.USER, user.username)
            },
        ).map { user ->
            SearchUserResult(
                username = user.username,
                description = user.description,
                metadata = user.karma,
                avatarImageUri = user.avatarImageUri,
            )
        }
    } else {
        emptyList()
    }
    val retainedSubmittedPeopleItems = remember(
        state.activeAccountId,
        submittedQuery,
        state.searchSubmissionRevision,
    ) {
        mutableStateOf<List<RedditPeopleSearchItem>>(emptyList())
    }
    // ISSUE57_RESUBMIT_REFILLS_RETAINED_RESULTS_V1 (people, as above).
    LaunchedEffect(
        redditCommunityDiscoveryState.peopleSearch.contentRevision,
        submittedPeopleSearchEligible,
        state.activeAccountId,
        submittedQuery,
        state.searchSubmissionRevision,
    ) {
        if (
            submittedPeopleSearchEligible &&
            redditCommunityDiscoveryState.peopleSearch.matches(
                accountStableId = state.activeAccountId,
                searchQuery = submittedQuery,
            )
        ) {
            retainedSubmittedPeopleItems.value =
                redditCommunityDiscoveryState.peopleSearch.items
        }
    }
    val activePeopleSearchState = if (
        submittedPeopleSearchEligible &&
        redditCommunityDiscoveryState.peopleSearch.matches(
            accountStableId = state.activeAccountId,
            searchQuery = submittedQuery,
        )
    ) {
        redditCommunityDiscoveryState.peopleSearch
    } else {
        RedditPeopleSearchUiState(
            accountStableId = state.activeAccountId,
            searchQuery = submittedQuery.takeIf(String::isNotEmpty),
        )
    }
    val submittedUserResults = when {
        !submittedResultsActive || !submittedScopeIncludesUsers -> emptyList()
        state.activeAccountIsAuthenticated -> rerankSearchProviderResults(
            rawQuery = submittedQuery,
            values = retainedSubmittedPeopleItems.value
                .asSequence()
                .filter { item -> state.showNsfwPosts || !item.nsfw }
                .map { item ->
                    SearchUserResult(
                        username = item.username,
                        description = item.description.ifBlank { item.title },
                        metadata = "",
                        avatarImageUri = item.avatarImageUri,
                    )
                }
                .toList(),
            primaryText = { user -> user.username },
            secondaryText = { user -> user.description },
            boost = { user ->
                state.searchIndexBoostFor(SearchIndexKind.USER, user.username)
            },
        )
        else -> rankSearchMatches(
            rawQuery = submittedQuery,
            values = state.users,
            primaryText = { user -> user.username },
            secondaryText = { user -> user.description },
            boost = { user ->
                state.searchIndexBoostFor(SearchIndexKind.USER, user.username)
            },
        ).map { user ->
            SearchUserResult(
                username = user.username,
                description = user.description,
                metadata = user.karma,
                avatarImageUri = user.avatarImageUri,
            )
        }
    }
    val userSuggestions = when {
        state.activeAccountIsAuthenticated &&
            draftPeopleAutocompleteQuery != null ->
            draftRemotePeopleResults.take(4)
        !state.activeAccountIsAuthenticated &&
            draftScopeIncludesUsers &&
            SearchProviderKind.USERS in queryPlan.providers ->
            draftUserResults.take(3)
        else -> emptyList()
    }
    val showSubmittedPostResults = submittedResultsActive &&
        submittedScopeIncludesPosts &&
        !state.searchHasUnsubmittedChanges
    val showSubmittedCommunityResults = submittedResultsActive &&
        submittedScopeIncludesCommunities &&
        !state.searchHasUnsubmittedChanges
    val showSubmittedUserResults = submittedResultsActive &&
        submittedScopeIncludesUsers &&
        !state.searchHasUnsubmittedChanges
    val searchReadFailure = activeRedditFeedState.failureKind?.takeUnless { failure ->
        failure == RedditFeedUiFailureKind.END_REACHED
    }
    val missingSearchCommunity = searchCommunityNotFound(
        failureKind = searchReadFailure,
        failureDetails = activeRedditFeedState.failureDetails,
        community = liveSearchCommunity,
    )
    val degradedSearchPresentation = if (
        liveSearchSubmitted &&
        searchReadFailure != null &&
        postResults.isNotEmpty()
    ) {
        if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
            redditFeedAppendErrorPresentation(
                failureKind = searchReadFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
            ).copy(title = "Couldn't load more search results")
        } else {
            redditFeedErrorPresentation(
                failureKind = searchReadFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
                showingCachedContent = true,
            ).copy(title = "Couldn't refresh search")
        }
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "search:${state.activeAccountId}:${pagingKey.value}",
        presentation = degradedSearchPresentation,
        failureDetails = activeRedditFeedState.failureDetails,
        onRetry = if (degradedSearchPresentation != null) {
            if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
                {
                    onAppendRedditSearch(
                        state.activeAccountId,
                        submittedQuery,
                        liveSearchCommunity,
                    )
                }
            } else {
                {
                    onRefreshRedditSearch(
                        state.activeAccountId,
                        submittedQuery,
                        liveSearchCommunity,
                    )
                }
            }
        } else {
            null
        },
    )
    val liveAppendEnabled = canAppendRedditFeed(
        live = liveSearchSubmitted,
        nextCursor = pagingSnapshot.nextCursor,
        state = activeRedditFeedState,
    )
    val explicitSubmitScrollToTop = remember { mutableStateOf(false) }

    fun submitCurrentSearch(area: SearchArea = state.searchArea): String? {
        val submitted = state.submitSearch() ?: return null
        // SEARCH01C2B_EXPLICIT_SUBMIT_SCROLLS_TO_FEDERATED_TOP
        explicitSubmitScrollToTop.value = true
        // ISSUE112_RESUBMIT_USES_THE_NEW_SCOPE_V1: the composition's
        // submittedScopeIncludesPosts was read before the scope/area change
        // that triggered this resubmit, so Communities → Posts never fetched
        // posts and the reverse sent a search nobody showed.
        val scopeIncludesPosts =
            state.searchScope == SearchScope.ALL || state.searchScope == SearchScope.POSTS
        if (
            state.activeAccountIsAuthenticated &&
            scopeIncludesPosts &&
            submitted.length in 2..REDDIT_SEARCH_QUERY_MAX_LENGTH
        ) {
            onRefreshRedditSearch(
                state.activeAccountId,
                submitted,
                state.searchOriginCommunity.takeIf {
                    area == SearchArea.CURRENT_COMMUNITY
                },
            )
        }
        return submitted
    }

    fun applySearchArea(area: SearchArea) {
        if (area == state.searchArea) return
        val resubmitActiveQuery = submittedQuery.isNotBlank() &&
            !state.searchHasUnsubmittedChanges
        state.updateSearchArea(area)
        if (resubmitActiveQuery) submitCurrentSearch(area)
    }

    fun applySearchScope(scope: SearchScope) {
        if (scope == state.searchScope) return
        val resubmitActiveQuery = submittedQuery.isNotBlank() &&
            !state.searchHasUnsubmittedChanges
        state.updateSearchScope(scope)
        if (resubmitActiveQuery) submitCurrentSearch()
    }

    val linkNoticeHost = LocalViewportErrorHostState.current
    val linkUriHandler = LocalUriHandler.current
    fun openDirectSearchAction(action: SearchDirectAction) {
        state.submitSearch()
        if (state.openRedditTarget(action.target)) return
        // ISSUE113_SEARCH_SHARE_LINK_TAKES_THE_LINK_PATH_V1: only the link path
        // asks Reddit where a share link leads (and says when it cannot); the
        // action did nothing here.
        (action.target as? RedditTarget.ShareLink)?.let { shareLink ->
            linkUriHandler.openUri(shareLink.url)
            return
        }
        val postId = action.target.postIdOrNull() ?: return
        val dispatchAccount = state.activeAccountId
        val dispatchRoute = state.currentNavigationRoute
        onResolveRedditNavigationTarget(
            dispatchAccount,
            postId,
        ) { resolvedPost ->
            // ISSUE114_SEARCH_LINK_RESOLUTION_CHECKS_ITS_ORIGIN_V1: the read can
            // take seconds; an answer for another account or after the reader
            // left search is dropped (it opened A's post on B's screen), and a
            // post that cannot be loaded says so (it did nothing).
            if (
                !resolvedTargetStillWanted(
                    dispatchAccount = dispatchAccount,
                    dispatchRoute = dispatchRoute,
                    currentAccount = state.activeAccountId,
                    currentRoute = state.currentNavigationRoute,
                )
            ) {
                return@onResolveRedditNavigationTarget
            }
            val opened = resolvedPost != null && state.openRedditTarget(
                target = action.target,
                resolvedPost = resolvedPost,
            )
            if (!opened) {
                linkNoticeHost?.presentRedditLinkUnavailable(
                    RedditLinkUnavailableReason.POST_UNAVAILABLE,
                )
            }
        }
    }
    val pagingFooterKey = if (showSubmittedPostResults) {
        pagingSnapshot.pagingFooterKey
    } else {
        null
    }
    // ISSUE234_FEED_POST_LAYOUTS_V1: All mixes posts with people and
    // communities, so Grid shows as List there.
    val postsScopeLayout = state.postLayout
    val allScopeLayout = state.postLayout.forFeed(gridAllowed = false)
    val gridColumns = rememberFeedGridColumns()
    val searchItemKeys = remember(
        postsScopeLayout,
        gridColumns.count,
        query,
        submittedQuery,
        state.searchScope,
        state.searchArea,
        state.searchHasUnsubmittedChanges,
        state.recentSearches.toList(),
        // ISSUE206_SAVED_SEARCHES_V1
        state.savedSearches.toList(),
        state.offersRandomNsfwCommunity(RandomNsfwCommunityPlacement.SEARCH),
        recentIndexedPeople,
        recentCommunities,
        suggestedCommunities,
        directAction,
        showSubmittedPostResults,
        showSubmittedCommunityResults,
        showSubmittedUserResults,
        postResults,
        submittedCommunityResults,
        communitySuggestions,
        submittedUserResults,
        userSuggestions,
        activePeopleSearchState,
        pagingFooterKey,
        // LOWUI8_SEARCH_KEYS_MATCH_THE_ROWS_V1 (OCB-251): everything the list
        // below reads to choose its rows is a key here too.
        filteredSummaryVisible,
        trendingSearchSeeds,
        state.trendingSearchRefreshInProgress,
    ) {
        buildList {
            when {
                query.isBlank() && submittedQuery.isBlank() -> {
                    // ISSUE211_RANDOM_NSFW_COMMUNITY_V1
                    if (state.offersRandomNsfwCommunity(RandomNsfwCommunityPlacement.SEARCH)) {
                        add("random-nsfw-community")
                    }
                    if (state.savedSearches.isNotEmpty()) {
                        add("saved-searches-title")
                        addAll(state.savedSearches.map { saved -> savedSearchItemKey(saved) })
                    }
                    if (state.recentSearches.isNotEmpty()) add("recent-searches")
                    if (recentIndexedPeople.isNotEmpty()) {
                        add("recent-people-title")
                        addAll(
                            recentIndexedPeople.map { entry ->
                                "recent-user-${entry.key}"
                            },
                        )
                    }
                    if (recentCommunities.isNotEmpty()) {
                        add("recent-communities-title")
                        addAll(
                            recentCommunities.map { community ->
                                "recent-community-${community.id}"
                            },
                        )
                    }
                    if (suggestedCommunities.isNotEmpty()) {
                        add("suggested-communities-title")
                        addAll(
                            suggestedCommunities.map { community ->
                                "suggested-community-${community.name.lowercase()}"
                            },
                        )
                    }
                    if (trendingSearchSeeds.isNotEmpty()) {
                        add("trending-search-title")
                        addAll(trendingSearchSeeds.map { seed -> "google-trend-${seed.title.lowercase()}" })
                    }
                    if (
                        state.savedSearches.isEmpty() &&
                        state.recentSearches.isEmpty() &&
                        recentIndexedPeople.isEmpty() &&
                        recentCommunities.isEmpty() &&
                        suggestedCommunities.isEmpty() &&
                        trendingSearchSeeds.isEmpty() &&
                        // LOWUX3_SEARCH_HUB_EMPTY_STATE_IGNORES_OTHER_FEEDS_V1 (OCB-252):
                        // a Home refresh hid it; no search runs with no query.
                        !state.trendingSearchRefreshInProgress
                    ) {
                        add("search-zero-state")
                    }
                }
                directAction != null -> add("direct-action")
                query.isNotEmpty() && query.length < 2 -> add("minimum-query")
                else -> {
                    if (communitySuggestions.isNotEmpty()) {
                        add("community-suggestions-title")
                        addAll(
                            communitySuggestions.map { community ->
                                "community-suggestion-${community.id}"
                            },
                        )
                    }
                    if (userSuggestions.isNotEmpty()) {
                        add("user-suggestions-title")
                        addAll(
                            userSuggestions.map { user ->
                                "user-suggestion-${user.username}"
                            },
                        )
                    }
                    when (state.searchScope) {
                        SearchScope.ALL -> if (submittedResultsActive) {
                            if (
                                showSubmittedCommunityResults &&
                                submittedCommunityResults.isNotEmpty()
                            ) {
                                add("community-results-title")
                                addAll(
                                    submittedCommunityResults.take(SEARCH01C2B_ALL_COMMUNITY_PREVIEW_LIMIT).map { community ->
                                        "community-${community.id}"
                                    },
                                )
                            }
                            if (
                                showSubmittedUserResults &&
                                (
                                    submittedUserResults.isNotEmpty() ||
                                        (
                                            state.activeAccountIsAuthenticated &&
                                                (
                                                    activePeopleSearchState.loading ||
                                                        activePeopleSearchState.failureKind != null
                                                    )
                                            )
                                    )
                            ) {
                                add("user-results-title")
                            }
                            if (
                                state.activeAccountIsAuthenticated &&
                                showSubmittedUserResults &&
                                submittedUserResults.isEmpty() &&
                                (
                                    activePeopleSearchState.loading ||
                                        activePeopleSearchState.failureKind != null
                                    )
                            ) {
                                add("people-status")
                            }
                            if (
                                showSubmittedUserResults &&
                                submittedUserResults.isNotEmpty()
                            ) {
                                addAll(
                                    submittedUserResults.take(SEARCH01C2B_ALL_PEOPLE_PREVIEW_LIMIT).map { user ->
                                        "user-${user.username}"
                                    },
                                )
                            }
                            if (showSubmittedPostResults) {
                                add("submitted-results-title")
                                if (filteredSummaryVisible) add("search-filtered-summary")
                                if (postResults.isEmpty()) add("no-posts")
                                else addAll(feedPostItemKeys(postResults, allScopeLayout, gridColumns.count, ::searchPostKey))
                            }
                        }
                        SearchScope.POSTS -> if (showSubmittedPostResults) {
                            add("submitted-results-title")
                            if (filteredSummaryVisible) add("search-filtered-summary")
                            if (postResults.isEmpty()) add("no-posts")
                            else addAll(feedPostItemKeys(postResults, postsScopeLayout, gridColumns.count, ::searchPostKey))
                        }
                        SearchScope.COMMUNITIES -> if (showSubmittedCommunityResults) {
                            add("community-results-title")
                            if (submittedCommunityResults.isEmpty()) add("no-communities")
                            else addAll(
                                submittedCommunityResults.map { community ->
                                    "community-${community.id}"
                                },
                            )
                        }
                        SearchScope.USERS -> if (showSubmittedUserResults) {
                            add("user-results-title")
                            if (submittedUserResults.isEmpty()) add("no-users")
                            else addAll(
                                submittedUserResults.map { user -> "user-${user.username}" },
                            )
                        }
                    }
                }
            }
            pagingFooterKey?.let(::add)
            if (
                liveSearchEligible &&
                showSubmittedPostResults &&
                !state.searchHasUnsubmittedChanges
            ) {
                add("reddit-paging")
            }
        }
    }
    val resultsListState = rememberFeedScrollState(
        state = state,
        scrollKey = searchFeedScrollKey(
            destination = state.searchOriginDestination,
            originCommunity = state.searchOriginCommunity,
            query = submittedQuery.ifBlank { query },
            scope = state.searchScope,
            area = state.searchArea,
        ),
        itemKeys = searchItemKeys,
    )
    LaunchedEffect(explicitSubmitScrollToTop.value, resultsListState) {
        if (explicitSubmitScrollToTop.value) {
            explicitSubmitScrollToTop.value = false
            resultsListState.scrollToItem(0)
        }
    }

    ObserveLiveFeedAutoAppend(
        listState = resultsListState,
        sessionKey = "${pagingKey.value}|generation=${pagingSnapshot.generation}",
        appendEnabled = showSubmittedPostResults &&
            !state.searchHasUnsubmittedChanges &&
            liveAppendEnabled,
        contentToken = pagingSnapshot.nextCursor,
        loadedItemCount = pagingSnapshot.items.size,
        visibleItemCount = postResults.size,
        onAppend = {
            onAppendRedditSearch(
                state.activeAccountId,
                submittedQuery,
                liveSearchCommunity,
            )
        },
    )

    LaunchedEffect(
        state.searchFocusRequest,
        state.openKeyboardOnSearch,
        showWorkspaceControls,
    ) {
        if (showWorkspaceControls && state.openKeyboardOnSearch) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter),
        ) {
            if (showWorkspaceControls) {
                SearchWorkspaceTopBar(
                    query = state.searchQuery,
                    hint = state.currentSearchHint,
                    focusRequester = focusRequester,
                    onQueryChange = { updated ->
                        state.updateSearchQuery(
                            if (state.activeAccountIsAuthenticated) {
                                updated.take(REDDIT_SEARCH_QUERY_MAX_LENGTH)
                            } else {
                                updated
                            },
                        )
                    },
                    onSearch = {
                        if (directAction != null) {
                            openDirectSearchAction(directAction)
                        } else {
                            submitCurrentSearch()
                        }
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    onBack = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onBack()
                    },
                )

                if (
                    submittedResultsActive &&
                    !state.searchHasUnsubmittedChanges &&
                    (
                        state.availableSearchAreas.size > 1 ||
                            state.availableSearchScopes.size > 1 ||
                            state.savedSearchesAvailable
                    )
                ) {
                    SearchContextFilters(
                        state = state,
                        onAreaSelected = ::applySearchArea,
                        onScopeSelected = ::applySearchScope,
                    )
                }
            }

            RefreshableLazyColumn(
                modifier = Modifier.fillMaxSize().then(gridColumns.measureModifier),
                state = resultsListState,
                // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1
                scrollPositionIndicator = state.scrollPositionIndicator,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 10.dp,
                    end = 16.dp,
                    bottom = 36.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (state.searchScope == SearchScope.POSTS) feedItemSpacing(postsScopeLayout, 6.dp) else 6.dp,
                ),
                enabled = showSubmittedPostResults &&
                    !state.searchHasUnsubmittedChanges,
                isRefreshing = if (authenticatedRedditSearch) {
                    activeRedditFeedState.refreshing
                } else {
                    pagingSnapshot.refreshing
                },
                onRefresh = if (
                    showSubmittedPostResults &&
                    !state.searchHasUnsubmittedChanges
                ) {
                    if (liveSearchEligible) {
                        {
                            onRefreshRedditSearch(
                                state.activeAccountId,
                                submittedQuery,
                                liveSearchCommunity,
                            )
                        }
                    } else {
                        { state.refreshPostPaging(pagingKey) }
                    }
                } else {
                    null
                },
                loadNextEnabled = if (
                    !showSubmittedPostResults ||
                    state.searchHasUnsubmittedChanges
                ) {
                    false
                } else if (authenticatedRedditSearch) {
                    liveAppendEnabled
                } else {
                    pagingSnapshot.canAppend
                },
                loadNextToken = if (authenticatedRedditSearch) {
                    pagingSnapshot.nextCursor
                } else {
                    pagingSnapshot.items.size
                },
                onLoadNext = {
                    if (authenticatedRedditSearch) {
                        onAppendRedditSearch(
                            state.activeAccountId,
                            submittedQuery,
                            liveSearchCommunity,
                        )
                    } else {
                        state.appendPostPaging(pagingKey)
                    }
                },
            ) {
                if (query.isBlank() && submittedQuery.isBlank()) {
                    // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: where the reader chose it.
                    if (state.offersRandomNsfwCommunity(RandomNsfwCommunityPlacement.SEARCH)) {
                        item("random-nsfw-community") {
                            RandomNsfwCommunityButton(state, RandomNsfwCommunityPlacement.SEARCH)
                        }
                    }
                    // ISSUE206_SAVED_SEARCHES_V1 (#206): first in the hub.
                    if (state.savedSearches.isNotEmpty()) {
                        item("saved-searches-title") {
                            WorkspaceSectionHeading("Saved searches")
                        }
                        items(
                            state.savedSearches,
                            key = { saved -> savedSearchItemKey(saved) },
                        ) { saved ->
                            SavedSearchRow(
                                search = saved,
                                onRun = {
                                    state.applySavedSearch(saved)
                                    submitCurrentSearch()
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                },
                                onRemove = { state.removeSavedSearch(saved) },
                            )
                        }
                    }
                    if (state.recentSearches.isNotEmpty()) {
                        item("recent-searches") {
                            RecentSearchChips(
                                searches = state.recentSearches.take(8),
                                onSearch = { recent ->
                                    state.updateSearchQuery(
                                        recent.take(REDDIT_SEARCH_QUERY_MAX_LENGTH),
                                    )
                                    submitCurrentSearch()
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                },
                                onClear = state::clearRecentSearches,
                            )
                        }
                    }
                    if (recentIndexedPeople.isNotEmpty()) {
                        item("recent-people-title") {
                            WorkspaceSectionHeading(
                                title = "Recent people",
                                trailing = {
                                    TextButton(
                                        onClick = {
                                            state.clearSearchIndex(SearchIndexKind.USER)
                                        },
                                    ) {
                                        Text("Clear")
                                    }
                                },
                            )
                        }
                        items(
                            recentIndexedPeople,
                            key = { entry -> "recent-user-${entry.key}" },
                        ) { entry ->
                            CompactUserResult(
                                username = entry.primaryText,
                                description = entry.secondaryText,
                                metadata = "",
                                avatarImageUri = entry.imageUri,
                                onClick = {
                                    state.openSearchUser(
                                        username = entry.primaryText,
                                        description = entry.secondaryText,
                                        avatarImageUri = entry.imageUri,
                                    )
                                },
                            )
                        }
                    }
                    if (recentCommunities.isNotEmpty()) {
                        item("recent-communities-title") {
                            WorkspaceSectionHeading("Recent communities")
                        }
                        items(
                            recentCommunities,
                            key = { community -> "recent-community-${community.id}" },
                        ) { community ->
                            CompactCommunityResult(
                                name = community.name,
                                description = community.description,
                                iconImageUri = community.iconImageUri,
                                onClick = {
                                    state.openSearchCommunity(
                                        name = community.name,
                                        description = community.description,
                                        iconImageUri = community.iconImageUri,
                                    )
                                },
                            )
                        }
                    }
                    if (suggestedCommunities.isNotEmpty()) {
                        item("suggested-communities-title") {
                            WorkspaceSectionHeading("Suggested communities")
                        }
                        items(
                            suggestedCommunities,
                            key = { suggestion ->
                                "suggested-community-${suggestion.name.lowercase()}"
                            },
                        ) { suggestion ->
                            CompactCommunityResult(
                                name = suggestion.name,
                                description = suggestion.description,
                                iconImageUri = suggestion.imageUri,
                                onClick = {
                                    state.openSearchCommunity(
                                        name = suggestion.name,
                                        description = suggestion.description,
                                        iconImageUri = suggestion.imageUri,
                                    )
                                },
                            )
                        }
                    }
                    if (trendingSearchSeeds.isNotEmpty()) {
                        item("trending-search-title") {
                            WorkspaceSectionHeading(
                                title = "Trending now",
                                trailing = {
                                    Text(
                                        "Google Trends",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                        items(
                            trendingSearchSeeds,
                            key = { seed -> "google-trend-${seed.title.lowercase()}" },
                        ) { seed ->
                            TrendingSearchResult(
                                title = seed.title,
                                approximateTraffic = seed.approximateTraffic,
                                onClick = {
                                    state.updateSearchQuery(
                                        seed.title.take(REDDIT_SEARCH_QUERY_MAX_LENGTH),
                                    )
                                    submitCurrentSearch()
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                },
                            )
                        }
                    }
                    if (
                        state.savedSearches.isEmpty() &&
                        state.recentSearches.isEmpty() &&
                        recentIndexedPeople.isEmpty() &&
                        recentCommunities.isEmpty() &&
                        suggestedCommunities.isEmpty() &&
                        trendingSearchSeeds.isEmpty() &&
                        // LOWUX3_SEARCH_HUB_EMPTY_STATE_IGNORES_OTHER_FEEDS_V1 (OCB-252):
                        // a Home refresh hid it; no search runs with no query.
                        !state.trendingSearchRefreshInProgress
                    ) {
                        item("search-zero-state") {
                            SearchWorkspaceEmptyState()
                        }
                    }
                } else if (directAction != null) {
                    item("direct-action") {
                        DirectSearchActionResult(
                            action = directAction,
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                openDirectSearchAction(directAction)
                            },
                        )
                    }
                } else if (query.isNotEmpty() && query.length < 2) {
                    item("minimum-query") {
                        SearchWorkspaceHint("Keep typing to search Reddit.")
                    }
                } else {
                    if (communitySuggestions.isNotEmpty()) {
                        item("community-suggestions-title") {
                            WorkspaceSectionHeading("Communities")
                        }
                        items(
                            communitySuggestions,
                            key = { community -> "community-suggestion-${community.id}" },
                        ) { community ->
                            CompactCommunityResult(
                                name = community.name,
                                description = community.description,
                                iconImageUri = community.iconImageUri,
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    state.openSubreddit(community.name)
                                },
                            )
                        }
                    }
                    if (userSuggestions.isNotEmpty()) {
                        item("user-suggestions-title") {
                            WorkspaceSectionHeading("People")
                        }
                        items(
                            userSuggestions,
                            key = { user -> "user-suggestion-${user.username}" },
                        ) { user ->
                            CompactUserResult(
                                username = user.username,
                                description = user.description,
                                metadata = user.metadata,
                                avatarImageUri = user.avatarImageUri,
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    state.openSearchUser(
                                        username = user.username,
                                        description = user.description,
                                        avatarImageUri = user.avatarImageUri,
                                    )
                                },
                            )
                        }
                    }
                    when (state.searchScope) {
                        SearchScope.ALL -> if (submittedResultsActive) {
                            // SEARCH01C2_ALL_PROVIDER_ORDER_COMMUNITIES_PEOPLE_POSTS
                            if (
                                showSubmittedCommunityResults &&
                                submittedCommunityResults.isNotEmpty()
                            ) {
                                item("community-results-title") {
                                    WorkspaceSectionHeading(
                                        title = "Communities",
                                        trailing = {
                                            TextButton(
                                                onClick = {
                                                    applySearchScope(SearchScope.COMMUNITIES)
                                                },
                                            ) {
                                                Text("See all")
                                            }
                                        },
                                    )
                                }
                                items(
                                    submittedCommunityResults.take(SEARCH01C2B_ALL_COMMUNITY_PREVIEW_LIMIT),
                                    key = { "community-${it.id}" },
                                ) { community ->
                                    CompactCommunityResult(
                                        name = community.name,
                                        description = community.description,
                                        iconImageUri = community.iconImageUri,
                                        onClick = {
                                            state.openSearchCommunity(
                                                name = community.name,
                                                description = community.description,
                                                iconImageUri = community.iconImageUri,
                                            )
                                        },
                                    )
                                }
                            }
                            if (showSubmittedUserResults) {
                                if (
                                    submittedUserResults.isNotEmpty() ||
                                    (
                                        state.activeAccountIsAuthenticated &&
                                            (
                                                activePeopleSearchState.loading ||
                                                    activePeopleSearchState.failureKind != null
                                                )
                                        )
                                ) {
                                    item("user-results-title") {
                                        WorkspaceSectionHeading(
                                            title = if (state.activeAccountIsAuthenticated) {
                                                "People"
                                            } else {
                                                "People on this device"
                                            },
                                            trailing = {
                                                if (state.activeAccountIsAuthenticated) {
                                                    TextButton(
                                                        onClick = {
                                                            applySearchScope(SearchScope.USERS)
                                                        },
                                                    ) {
                                                        Text("See all")
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                if (
                                    state.activeAccountIsAuthenticated &&
                                    submittedUserResults.isEmpty() &&
                                    (
                                        activePeopleSearchState.loading ||
                                            activePeopleSearchState.failureKind != null
                                        )
                                ) {
                                    item("people-status") {
                                        LivePeopleSearchStatus(
                                            query = submittedQuery,
                                            state = activePeopleSearchState,
                                        )
                                    }
                                }
                                items(
                                    submittedUserResults.take(SEARCH01C2B_ALL_PEOPLE_PREVIEW_LIMIT),
                                    key = { "user-${it.username}" },
                                ) { user ->
                                    CompactUserResult(
                                        username = user.username,
                                        description = user.description,
                                        metadata = user.metadata,
                                        avatarImageUri = user.avatarImageUri,
                                        onClick = {
                                            state.openSearchUser(
                                                username = user.username,
                                                description = user.description,
                                                avatarImageUri = user.avatarImageUri,
                                            )
                                        },
                                    )
                                }
                            }
                            if (showSubmittedPostResults) {
                                item("submitted-results-title") {
                                    WorkspaceSectionHeading(
                                        if (state.searchHasUnsubmittedChanges) {
                                            "Posts for “$submittedQuery”"
                                        } else {
                                            "Posts"
                                        },
                                    )
                                }
                                if (filteredSummaryVisible) {
                                    item("search-filtered-summary") {
                                        FilteredPostsSummaryRow(
                                            filteredCount = filteredCount,
                                            revealed = filteredRevealed,
                                            onToggle = { filteredRevealed = !filteredRevealed },
                                        )
                                    }
                                }
                                if (postResults.isEmpty()) {
                                    item("no-posts") {
                                        if (liveSearchSubmitted && missingSearchCommunity != null) {
                                            SearchWorkspaceHint("Couldn't find r/$missingSearchCommunity")
                                        } else if (liveSearchSubmitted && searchReadFailure != null) {
                                            ErrorNotice(
                                                presentation = redditFeedErrorPresentation(
                                                    failureKind = searchReadFailure,
                                                    details = activeRedditFeedState.failureDetails,
                                                    retryable = activeRedditFeedState.failureRetryable,
                                                    showingCachedContent = false,
                                                ).copy(title = "Couldn't load search results"),
                                                diagnosticScopeKey = "search_posts",
                                                onAction = { action ->
                                                    if (action == ErrorActionKind.RETRY) {
                                                        onRefreshRedditSearch(
                                                            state.activeAccountId,
                                                            submittedQuery,
                                                            liveSearchCommunity,
                                                        )
                                                    }
                                                },
                                            )
                                        } else {
                                            NoResults(
                                                query = submittedQuery,
                                                live = authenticatedRedditSearch,
                                                submitted = liveSearchSubmitted,
                                                inProgress = activeRedditFeedState.inProgress,
                                                filtered = filteredCount > 0,
                                            )
                                        }
                                    }
                                }
                                feedPostItems(postResults, allScopeLayout, gridColumns.count, ::searchPostKey) { post, itemModifier ->
                                    PostCard(
                                        post,
                                        state,
                                        modifier = itemModifier,
                                        layout = allScopeLayout,
                                        onOpen = {
                                            // NEXT01A2_SEARCH_LIST_DETAIL_SELECTION_V1
                                            onOpenPostSelection?.invoke(post)
                                                ?: state.openFromSearch(post)
                                        },
                                    )
                                }
                            }
                        }
                        SearchScope.POSTS -> if (showSubmittedPostResults) {
                            item("submitted-results-title") {
                                WorkspaceSectionHeading(
                                    if (state.searchHasUnsubmittedChanges) {
                                        "Results for “$submittedQuery”"
                                    } else {
                                        "Posts"
                                    },
                                    // ISSUE234_LAYOUT_CHOICE_BESIDE_THE_FEED_V1
                                    trailing = { PostLayoutMenuButton(state) },
                                )
                            }
                            if (filteredSummaryVisible) {
                                item("search-filtered-summary") {
                                    FilteredPostsSummaryRow(
                                        filteredCount = filteredCount,
                                        revealed = filteredRevealed,
                                        onToggle = { filteredRevealed = !filteredRevealed },
                                    )
                                }
                            }
                            if (postResults.isEmpty()) {
                                item("no-posts") {
                                    if (liveSearchSubmitted && missingSearchCommunity != null) {
                                        SearchWorkspaceHint("Couldn't find r/$missingSearchCommunity")
                                    } else if (liveSearchSubmitted && searchReadFailure != null) {
                                        ErrorNotice(
                                            presentation = redditFeedErrorPresentation(
                                                failureKind = searchReadFailure,
                                                details = activeRedditFeedState.failureDetails,
                                                retryable = activeRedditFeedState.failureRetryable,
                                                showingCachedContent = false,
                                            ).copy(title = "Couldn't load search results"),
                                            diagnosticScopeKey = "search_posts",
                                            onAction = { action ->
                                                if (action == ErrorActionKind.RETRY) {
                                                    onRefreshRedditSearch(
                                                        state.activeAccountId,
                                                        submittedQuery,
                                                        liveSearchCommunity,
                                                    )
                                                }
                                            },
                                        )
                                    } else {
                                        NoResults(
                                            query = submittedQuery,
                                            live = authenticatedRedditSearch,
                                            submitted = liveSearchSubmitted,
                                            inProgress = activeRedditFeedState.inProgress,
                                            filtered = filteredCount > 0,
                                        )
                                    }
                                }
                            }
                            feedPostItems(postResults, postsScopeLayout, gridColumns.count, ::searchPostKey) { post, itemModifier ->
                                PostCard(
                                    post,
                                    state,
                                    modifier = itemModifier,
                                    layout = postsScopeLayout,
                                    onOpen = {
                                        // NEXT01A2_SEARCH_LIST_DETAIL_SELECTION_V1
                                        onOpenPostSelection?.invoke(post)
                                            ?: state.openFromSearch(post)
                                    },
                                )
                            }
                        }
                        SearchScope.COMMUNITIES -> if (showSubmittedCommunityResults) {
                            item("community-results-title") {
                                WorkspaceSectionHeading("Communities")
                            }
                            if (submittedCommunityResults.isEmpty()) {
                                item("no-communities") { NoResults(submittedQuery) }
                            }
                            items(
                                submittedCommunityResults,
                                key = { "community-${it.id}" },
                            ) { community ->
                                CompactCommunityResult(
                                    name = community.name,
                                    description = community.description,
                                    iconImageUri = community.iconImageUri,
                                    onClick = {
                                        state.openSearchCommunity(
                                            name = community.name,
                                            description = community.description,
                                            iconImageUri = community.iconImageUri,
                                        )
                                    },
                                )
                            }
                        }
                        SearchScope.USERS -> if (showSubmittedUserResults) {
                            item("user-results-title") {
                                WorkspaceSectionHeading("People")
                            }
                            if (submittedUserResults.isEmpty()) {
                                item("no-users") {
                                    if (state.activeAccountIsAuthenticated) {
                                        LivePeopleSearchStatus(
                                            query = submittedQuery,
                                            state = activePeopleSearchState,
                                        )
                                    } else {
                                        NoResults(submittedQuery)
                                    }
                                }
                            }
                            items(
                                submittedUserResults,
                                key = { "user-${it.username}" },
                            ) { user ->
                                CompactUserResult(
                                    username = user.username,
                                    description = user.description,
                                    metadata = user.metadata,
                                    avatarImageUri = user.avatarImageUri,
                                    onClick = {
                                        state.openSearchUser(
                                            username = user.username,
                                            description = user.description,
                                            avatarImageUri = user.avatarImageUri,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                if (pagingFooterKey != null) {
                    item(key = pagingFooterKey) {
                        RepositoryPagingFooter(
                            snapshot = pagingSnapshot,
                            onRetry = { state.retryPostPaging(pagingKey) },
                        )
                    }
                }
                if (
                    liveSearchEligible &&
                    showSubmittedPostResults &&
                    !state.searchHasUnsubmittedChanges
                ) {
                    item("reddit-paging") {
                        RedditPagingControl(
                            state = activeRedditFeedState,
                            hasPosts = liveSearchSubmitted && postResults.isNotEmpty(),
                            canAppend = liveSearchSubmitted &&
                                pagingSnapshot.nextCursor != null,
                            onRefresh = {
                                onRefreshRedditSearch(
                                    state.activeAccountId,
                                    submittedQuery,
                                    liveSearchCommunity,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun SearchWorkspaceTopBar(
    query: String,
    hint: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // M3223_ONE_ROUTE_TOP_BAR_V1: the search band takes the status bar inset
        // like the route top bar.
        Row(
            modifier = Modifier
                .windowInsetsPadding(routeTopBarWindowInsets())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // M3225_ONE_SEARCH_FIELD_STYLE_V1: the M3 search bar input field.
            SubshellSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = hint,
                fieldModifier = Modifier.focusRequester(focusRequester),
                // LOWUI8_KEYBOARD_SEARCH_FOLLOWS_THE_BUTTON_V1 (OCB-253): the
                // keyboard's Search refuses what the Search button refuses.
                onSearch = { if (query.trim().length >= 2) onSearch() },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Symbols.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                trailingIcon = {
                    // SEARCH01C2B1_TOP_FIELD_SUBMIT_ACTION
                    IconButton(
                        onClick = onSearch,
                        enabled = query.trim().length >= 2,
                    ) {
                        Icon(
                            Symbols.Search,
                            contentDescription = "Search",
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchContextFilters(
    state: SubshellState,
    onAreaSelected: (SearchArea) -> Unit,
    onScopeSelected: (SearchScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ISSUE206_SAVED_SEARCHES_V1 (#206): save or forget the search on screen.
        val current = state.currentSavedSearch
        if (state.savedSearchesAvailable && current != null) {
            val saved = state.isSearchSaved(current)
            FilterChip(
                selected = saved,
                onClick = {
                    if (saved) state.removeSavedSearch(current) else state.saveSearch(current)
                },
                label = { Text(if (saved) "Saved" else "Save search") },
                leadingIcon = {
                    Icon(
                        if (saved) Symbols.BookmarkFilled else Symbols.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
        if (state.availableSearchAreas.size > 1) {
            state.availableSearchAreas.forEach { area ->
                FilterChip(
                    selected = state.searchArea == area,
                    onClick = { onAreaSelected(area) },
                    label = { Text(state.searchAreaLabel(area)) },
                )
            }
        }
        if (state.availableSearchScopes.size > 1) {
            state.availableSearchScopes.forEach { scope ->
                FilterChip(
                    selected = state.searchScope == scope,
                    onClick = { onScopeSelected(scope) },
                    label = { Text(scope.label) },
                )
            }
        }
    }
}

@Composable
private fun TrendingSearchResult(
    title: String,
    approximateTraffic: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Symbols.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (approximateTraffic.isNotBlank()) {
                    Text(
                        "$approximateTraffic Google searches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun savedSearchItemKey(search: SavedSearch): String =
    "saved-search-" + search.identityKey()

@Composable
private fun SavedSearchRow(
    search: SavedSearch,
    onRun: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListCard(
        headline = search.query,
        supporting = search.scopeSummary(),
        onClick = onRun,
        onClickLabel = "Run search",
        leading = {
            Icon(
                Symbols.BookmarkFilled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Symbols.MoreVert,
                        contentDescription = "Options for saved search ${search.query}",
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun RecentSearchChips(
    searches: List<String>,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WorkspaceSectionHeading("Recent searches") {
            TextButton(onClick = onClear) { Text("Clear") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            searches.forEach { recent ->
                AssistChip(
                    onClick = { onSearch(recent) },
                    label = {
                        Text(
                            recent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Symbols.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DirectSearchActionResult(
    action: SearchDirectAction,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Symbols.Search, contentDescription = null)
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    action.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Icon(
                Symbols.ArrowForward,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun WorkspaceSectionHeading(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        trailing?.invoke()
    }
}

@Composable
private fun CompactCommunityResult(
    name: String,
    description: String,
    iconImageUri: String?,
    onClick: () -> Unit,
) {
    ListCard(
        headline = "r/$name",
        supporting = description,
        onClick = onClick,
        leading = {
            CommunityAvatar(
                name = name,
                accent = accentFor(name),
                modifier = Modifier.size(40.dp),
                iconImageUri = iconImageUri,
            )
        },
        trailing = {
            Icon(Symbols.ArrowForward, contentDescription = null)
        },
    )
}

private data class SearchUserResult(
    val username: String,
    val description: String,
    val metadata: String,
    val avatarImageUri: String?,
)

@Composable
private fun LivePeopleSearchStatus(
    query: String,
    state: RedditPeopleSearchUiState,
) {
    val message = when {
        // LOWUX3_LONG_PEOPLE_QUERY_SAYS_SO_V1 (OCB-250): Reddit's people search
        // takes 100 characters; a longer query sent nothing and read
        // "Searching…" for good.
        query.trim().length > REDDIT_PEOPLE_SEARCH_MAX_QUERY_LENGTH ->
            "People search takes up to $REDDIT_PEOPLE_SEARCH_MAX_QUERY_LENGTH characters. Shorten the search."
        state.loading -> "Searching Reddit people for “$query”…"
        state.failureKind != null -> "Couldn't load Reddit people. Submit the search again to retry."
        state.loaded -> "No people found for “$query”."
        else -> "Searching Reddit people for “$query”…"
    }
    SearchWorkspaceHint(message)
}

@Composable
private fun CompactUserResult(
    username: String,
    description: String,
    metadata: String,
    avatarImageUri: String?,
    onClick: () -> Unit,
) {
    ListCard(
        headline = "u/$username",
        supporting = description.ifBlank { metadata.ifBlank { "Reddit profile" } },
        onClick = onClick,
        leading = {
            CommunityAvatar(
                name = username,
                accent = accentFor(username),
                modifier = Modifier.size(40.dp),
                iconImageUri = avatarImageUri,
            )
        },
        trailing = if (metadata.isNotBlank()) {
            { Text(metadata, style = MaterialTheme.typography.labelMedium) }
        } else {
            null
        },
    )
}

@Composable
private fun SearchWorkspaceEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Symbols.Search,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Search Reddit",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Find posts, communities and people, or paste a Reddit link.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchWorkspaceHint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// REDDITSEARCH02_COMMUNITY_NOT_FOUND_V1: the data layer answers a search in a
// community that does not exist with HTTP 404 (see searchPosts); that is not a
// failed search, so it gets its own hint and no Retry.
internal fun searchCommunityNotFound(
    failureKind: RedditFeedUiFailureKind?,
    failureDetails: FailureDetails?,
    community: String?,
): String? = community
    ?.trim()
    ?.removePrefix("r/")
    ?.takeIf { name ->
        name.isNotEmpty() &&
            failureKind == RedditFeedUiFailureKind.HTTP &&
            failureDetails?.httpStatusCode == 404
    }

@Composable
private fun NoResults(
    query: String,
    live: Boolean = false,
    submitted: Boolean = false,
    inProgress: Boolean = false,
    filtered: Boolean = false,
) {
    SearchWorkspaceHint(
        when {
            // CONTENTFILTER06_SEARCH_ALL_FILTERED_V1
            filtered -> "All posts for “$query” are filtered"
            !live -> "No local results for “$query”"
            inProgress -> "Searching Reddit for “$query”…"
            submitted -> "No Reddit posts found for “$query”"
            else -> "Search is ready for “$query”"
        },
    )
}

/** The post results search shows, after content filters and the count row. */
internal data class SearchPostResults(
    val posts: List<Post>,
    val filteredCount: Int,
    val summaryVisible: Boolean,
)

internal fun searchPostResults(
    matching: List<Post>,
    showsPost: (Post) -> Boolean,
    filters: ContentFilters,
    summaryEnabled: Boolean,
    revealed: Boolean,
    communityPage: String? = null,
): SearchPostResults {
    // CONTENTFILTER06_SEARCH_IN_COMMUNITY_KEEPS_ITS_POSTS_V1: a search
    // restricted to one community exempts that community's own filter, like
    // the community page does.
    val shown = matching.filter(showsPost)
    val unfiltered = shown.filterNot { post ->
        if (communityPage != null) {
            filters.hidesOnCommunityPage(post, communityPage)
        } else {
            filters.hides(post)
        }
    }
    val filteredCount = shown.size - unfiltered.size
    val summaryVisible = summaryEnabled && filteredCount > 0
    return SearchPostResults(
        posts = if (summaryVisible && revealed) shown else unfiltered,
        filteredCount = filteredCount,
        summaryVisible = summaryVisible,
    )
}

// ISSUE114 / ISSUE124: an answer read from Reddit is used only while the
// account and the route that asked for it are still current.
internal fun resolvedTargetStillWanted(
    dispatchAccount: String,
    dispatchRoute: SubshellRoute,
    currentAccount: String,
    currentRoute: SubshellRoute,
): Boolean = dispatchAccount == currentAccount && dispatchRoute == currentRoute

private fun searchPostKey(post: Post): String = "search-${post.id}"
