package app.subshell.prototype.ui.app

import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState

/**
 * The feed runtime as SubshellApp receives it from MainActivity: the main
 * feed UI state with its refresh/append entry points, the search
 * refresh/append entry points, and the subscriptions UI state with its live
 * and cached loaders. Property names and defaults are the former SubshellApp
 * parameters, unchanged.
 */
data class FeedEntryPoints(
    val redditFeedState: RedditFeedUiState = RedditFeedUiState(),
    val onRefreshRedditFeed: (
        String,
        RedditMainFeed,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    val onAppendRedditFeed: (
        String,
        RedditMainFeed,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    val onRefreshRedditSearch: (String, String, String?) -> Unit = { _, _, _ -> },
    val onAppendRedditSearch: (String, String, String?) -> Unit = { _, _, _ -> },
    val redditSubscriptionsState: RedditSubscriptionsUiState =
        RedditSubscriptionsUiState(),
    val onLoadRedditSubscriptions: (String, Boolean) -> Unit = { _, _ -> },
    val onLoadCachedRedditSubscriptions: (String) -> Unit = {},
)
