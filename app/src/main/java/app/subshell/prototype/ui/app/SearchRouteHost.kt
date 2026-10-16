package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ExpandedContextDiscoveryMaxWidth
import app.subshell.prototype.ui.ExpandedContextReadingMaxWidth
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.ExpandedContextSurface
import app.subshell.prototype.ui.app.ExpandedListDetailSurface
import app.subshell.prototype.ui.screens.SearchScreen
import app.subshell.prototype.ui.useExpandedSearchWorkspaceListDetail

@Composable
internal fun SearchRouteHost(
    state: SubshellState,
    padding: PaddingValues,
    expandedWindowLayout: Boolean,
    screenStateHolder: SaveableStateHolder,
    redditFeed: FeedEntryPoints,
    redditCommunity: CommunityEntryPoints,
    onResolveRedditNavigationTarget: (String, String, (Post?) -> Unit) -> Unit,
) {
    val redditCommunityDiscoveryState = redditCommunity.redditCommunityDiscoveryState
    val onLoadRedditCommunityDiscovery = redditCommunity.onLoadRedditCommunityDiscovery
    val redditFeedState = redditFeed.redditFeedState
    val onRefreshRedditFeed = redditFeed.onRefreshRedditFeed
    val onRefreshRedditSearch = redditFeed.onRefreshRedditSearch
    val onAppendRedditSearch = redditFeed.onAppendRedditSearch
    screenStateHolder.SaveableStateProvider("contextual-search") {
        val searchWorkspaceListDetail =
            useExpandedSearchWorkspaceListDetail(
                expandedWindow = expandedWindowLayout,
                tabletLayoutMode = state.tabletLayoutMode,
            )

        if (searchWorkspaceListDetail) {
            // NEXT01A2R1_SEARCH_WORKSPACE_TWO_PANE_V1
            ExpandedListDetailSurface(
                scaffoldPadding = padding,
                listTitle = "Search",
                listSubtitle = null,
                listContent = { listModifier ->
                    SearchScreen(
                        modifier = listModifier,
                        state = state,
                        redditFeedState = redditFeedState,
                        redditCommunityDiscoveryState =
                            redditCommunityDiscoveryState,
                        onLoadRedditCommunityDiscovery =
                            onLoadRedditCommunityDiscovery,
                        onRefreshRedditFeed = onRefreshRedditFeed,
                        onRefreshRedditSearch = onRefreshRedditSearch,
                        onAppendRedditSearch = onAppendRedditSearch,
                        onResolveRedditNavigationTarget =
                            onResolveRedditNavigationTarget,
                        onBack = { state.handleSearchBack() },
                    )
                },
                detailContent = { detailModifier ->
                    Box(
                        modifier = detailModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            // LOWUX3_SEARCH_DETAIL_PLACEHOLDER_FOLLOWS_THE_SCOPE_V1
                            // (OCB-121): Communities and People results are not posts.
                            text = when {
                                state.searchSubmittedQuery.isBlank() -> "Search for a post"
                                state.searchScope == SearchScope.COMMUNITIES -> "Select a community"
                                state.searchScope == SearchScope.USERS -> "Select a person"
                                else -> "Select a post"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        } else {
            ExpandedContextSurface(
                scaffoldPadding = padding,
                expanded = expandedWindowLayout,
                // M3224_READING_WIDTH_V1: search results is a feed, read at the reading width.
                maxWidth = ExpandedContextReadingMaxWidth,
            ) { contextModifier ->
                SearchScreen(
                    modifier = contextModifier,
                    state = state,
                    redditFeedState = redditFeedState,
                    redditCommunityDiscoveryState =
                        redditCommunityDiscoveryState,
                    onLoadRedditCommunityDiscovery =
                        onLoadRedditCommunityDiscovery,
                    onRefreshRedditFeed = onRefreshRedditFeed,
                    onRefreshRedditSearch = onRefreshRedditSearch,
                    onAppendRedditSearch = onAppendRedditSearch,
                    onResolveRedditNavigationTarget =
                        onResolveRedditNavigationTarget,
                    onBack = { state.handleSearchBack() },
                )
            }
        }
    }
}
