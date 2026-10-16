package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.shortcuts.sameCustomFeedPath
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellState

// REDDITURL04_MULTIREDDIT_LINK_V1
// Opens a custom feed named by a link. The account's own feed opens as the
// drawer would open it; any other feed (another user's multireddit) opens
// at once under its name from the path, and its name, description and
// communities are read from Reddit. That read runs again when the open feed
// is restored without them.
@Composable
internal fun CustomFeedLinkEffect(
    state: SubshellState,
    customFeedsState: CustomFeedsUiState,
    activeCustomFeedPathState: MutableState<String?>,
    linkedCustomFeedState: MutableState<CustomFeedSummary?>,
    onResolveCustomFeed: (String, String, (CustomFeedSummary?) -> Unit) -> Unit,
) {
    var activeCustomFeedPath by activeCustomFeedPathState
    var linkedCustomFeed by linkedCustomFeedState
    val ownFeeds = customFeedsState
        .takeIf { feeds -> feeds.accountStableId == state.activeAccountId }
        ?.feeds
        .orEmpty()

    LaunchedEffect(state.pendingCustomFeedLinkPath) {
        val path = state.consumeCustomFeedLinkPath() ?: return@LaunchedEffect
        activeCustomFeedPath = ownFeeds.firstOrNull { feed -> sameCustomFeedPath(feed.path, path) }?.path ?: path
        state.navigate(MainDestination.HOME)
    }

    LaunchedEffect(state.activeAccountId, activeCustomFeedPath, ownFeeds) {
        val path = activeCustomFeedPath ?: return@LaunchedEffect
        // LOWUI8_OWN_FEED_LINK_MATCHES_AFTER_THE_LIST_LOADS_V1 (OCB-113): the
        // list may load after the link, with the path in another case or
        // without its slash; matched as the link was, the open path becomes
        // the list's own so the feed opens as the account's.
        val own = ownFeeds.firstOrNull { feed -> sameCustomFeedPath(feed.path, path) }
        if (own != null) {
            if (own.path != path) activeCustomFeedPath = own.path
            return@LaunchedEffect
        }
        if (linkedCustomFeed?.path == path) return@LaunchedEffect
        linkedCustomFeed = linkedCustomFeedPlaceholder(path)
        val account = state.activeAccountId
        onResolveCustomFeed(account, path) { feed ->
            if (feed != null && activeCustomFeedPath == path && state.activeAccountId == account) {
                linkedCustomFeed = feed.copy(path = path)
            }
        }
    }
}

/** The feed as the path names it, until Reddit says more. */
internal fun linkedCustomFeedPlaceholder(path: String): CustomFeedSummary =
    CustomFeedSummary(
        path = path,
        displayName = path.trim('/').substringAfterLast('/'),
        description = "",
        visibility = "",
        subredditNames = emptyList(),
    )
