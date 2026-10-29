package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.availableFeedSorts
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.offline.OFFLINE_SAVE_POST_COUNT
import app.subshell.prototype.offline.OfflineSaveScheduler
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.noticeInfo
import app.subshell.prototype.ui.toRedditFeedSort
import app.subshell.prototype.ui.toRedditMainFeedOrNull
import app.subshell.prototype.ui.components.MainScreenHeader
import app.subshell.prototype.ui.components.chromeHorizontalSides
import app.subshell.prototype.ui.screens.InboxContextualSelection
import app.subshell.prototype.ui.symbols.Symbols

/**
 * The Scaffold's top bar: the inbox contextual-selection bar while activity
 * is selected, otherwise the main screen header with the feed sort, time
 * period, hide-read and search actions, plus the status-bar scrim above it.
 * Hidden inside a route and while a modmail thread is open.
 *
 * Moved out of SubshellApp verbatim. The hidden fraction arrives as the
 * animated State, so it is still read in the graphics layer only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    state: SubshellState,
    inContext: Boolean,
    modmailThreadOpen: Boolean,
    topBarHiddenFractionState: State<Float>,
    activeInboxContextualSelection: InboxContextualSelection?,
    activeCustomFeed: CustomFeedSummary?,
    subsCommunityActions: Boolean,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
) {
    val topBarHiddenFraction by topBarHiddenFractionState
    val context = LocalContext.current
    val userNotices = LocalViewportErrorHostState.current
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    if (!inContext && !modmailThreadOpen) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -size.height * topBarHiddenFraction
                    }
                    .windowInsetsPadding(
                        // M3223_CUTOUT_IS_SAFE_V1: clear of a landscape display cutout too.
                        WindowInsets.safeDrawing.only(chromeHorizontalSides() + WindowInsetsSides.Top),
                    ),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                val selection = activeInboxContextualSelection
                if (selection != null) {
                    TopAppBar(
                        title = {
                            Text("${selection.selectedCount} selected")
                        },
                        navigationIcon = {
                            IconButton(onClick = selection.onClose) {
                                Icon(
                                    Symbols.Close,
                                    contentDescription = "Exit selection",
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                enabled = selection.canMarkRead,
                                onClick = selection.onMarkRead,
                            ) {
                                Icon(
                                    Symbols.DoneAll,
                                    contentDescription = "Mark selected activity as read",
                                )
                            }
                            IconButton(onClick = selection.onDismiss) {
                                Icon(
                                    Symbols.VisibilityOff,
                                    contentDescription = "Dismiss selected activity",
                                )
                            }
                            IconButton(onClick = selection.onSelectAll) {
                                Icon(
                                    Symbols.SelectAll,
                                    contentDescription = if (
                                        selection.allVisibleSelected
                                    ) {
                                        "Deselect all activity"
                                    } else {
                                        "Select all activity"
                                    },
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                } else {
                    MainScreenHeader(
                        title = state.selectedSubreddit?.let(state::subredditLabel)
                            ?: activeCustomFeed?.let { feed -> "Feed: ${feed.displayName}" }
                            ?: if (state.destination == MainDestination.HOME) state.feedSource.label else state.destination.label,
                        state = state,
                        feedSort = if (
                            state.destination == MainDestination.HOME &&
                            activeCustomFeed == null
                        ) state.feedSort else null,
                        feedSortOptions = availableFeedSorts(state.feedSource),
                        onFeedSortSelected = { sort ->
                            pendingAuthenticatedLandingAccountId = null
                            state.updateFeedSort(sort)
                        },
                        listingTimePeriod = state.listingTimePeriod,
                        onListingTimePeriodSelected = { timePeriod ->
                            pendingAuthenticatedLandingAccountId = null
                            state.updateListingTimePeriod(timePeriod)
                        },
                        hideReadPosts = if (
                            state.destination == MainDestination.HOME &&
                            activeCustomFeed == null
                        ) {
                            state.hideReadPosts
                        } else {
                            null
                        },
                        hasReadPosts = state.readPostIds.isNotEmpty(),
                        onHideReadPostsChanged = state::updateHideReadPosts,
                        onResetReadPosts = state::resetReadPostHistory,
                        // ISSUE265_SAVE_FOR_OFFLINE_V1
                        onSaveForOffline = state.feedSource.toRedditMainFeedOrNull()
                            ?.takeIf {
                                state.destination == MainDestination.HOME &&
                                    activeCustomFeed == null &&
                                    state.activeAccountIsAuthenticated
                            }
                            ?.let { mainFeed ->
                                {
                                    OfflineSaveScheduler.enqueue(
                                        context = context,
                                        accountStableId = state.activeAccountId,
                                        mainFeed = mainFeed,
                                        sort = state.feedSort.toRedditFeedSort(),
                                        timePeriod = state.listingTimePeriod,
                                        includeNsfw = state.showNsfwPosts,
                                    )
                                    userNotices.noticeInfo(
                                        "Saving for offline",
                                        "The top $OFFLINE_SAVE_POST_COUNT posts, their comments and images download in the background",
                                    )
                                }
                            },
                        onSearchClick = if (subsCommunityActions) {
                            { state.openCommunityExplore(focusSearch = true) }
                        } else {
                            { state.openSearch() }
                        },
                        searchContentDescription = if (subsCommunityActions) {
                            "Search communities"
                        } else {
                            "Search"
                        },
                        // ISSUE234_LAYOUT_CHOICE_BESIDE_THE_FEED_V1: Home and its custom feeds.
                        postLayoutButton = state.destination == MainDestination.HOME &&
                            state.selectedSubreddit == null,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars),
                // M3223_STATUS_STRIP_MATCHES_THE_BAR_V1 (#223, foundations-06): the
                // strip behind the status bar is the bar's own colour, not surface.
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
        }
    }
}
