package io.github.brealorg.subshell.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import io.github.brealorg.subshell.data.RedditFeedSort
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditMainFeed
import io.github.brealorg.subshell.data.account.RedditCommunityDiscoveryUiState
import io.github.brealorg.subshell.data.account.RedditFeedUiState
import io.github.brealorg.subshell.model.CommunityDiscoverySort
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.ExpandedContextDiscoveryMaxWidth
import io.github.brealorg.subshell.ui.ExpandedContextReadingMaxWidth
import io.github.brealorg.subshell.ui.SearchScope
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.app.ExpandedContextSurface
import io.github.brealorg.subshell.ui.app.ExpandedListDetailSurface
import io.github.brealorg.subshell.ui.screens.SearchScreen
import io.github.brealorg.subshell.ui.useExpandedSearchWorkspaceListDetail

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
