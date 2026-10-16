package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.shortcuts.LauncherShortcutTarget

/**
 * The custom-feeds runtime as SubshellApp receives it from MainActivity: the
 * feeds and feed-posts UI states, the load/append entry points, the
 * create/update/delete/reset entry points and the hidden-subscriptions
 * unlock request, and the custom-feed launcher shortcut hand-off. Property
 * names and defaults of the former SubshellApp parameters are unchanged.
 */
data class CustomFeedsEntryPoints(
    val customFeedsState: CustomFeedsUiState = CustomFeedsUiState(),
    val customFeedPostsState: CustomFeedPostsUiState = CustomFeedPostsUiState(),
    val onRequestHiddenSubscriptionsUnlock: ((Boolean) -> Unit) -> Unit =
        { onResult -> onResult(false) },
    val onLoadCustomFeeds: (String, Boolean) -> Unit = { _, _ -> },
    val onLoadCustomFeedPosts: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val onAppendCustomFeedPosts: (String, String) -> Unit = { _, _ -> },
    val onCreateCustomFeed: (CustomFeedCreateRequest) -> Unit = {},
    val onUpdateCustomFeed: (CustomFeedUpdateRequest) -> Unit = {},
    val onDeleteCustomFeed: (CustomFeedDeleteRequest) -> Unit = {},
    val onResetCustomFeedCreate: () -> Unit = {},
    val onResetCustomFeedManage: () -> Unit = {},
    // ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1: a custom-feed launcher shortcut.
    val launcherShortcutTarget: LauncherShortcutTarget.CustomFeed? = null,
    val onLauncherShortcutConsumed: () -> Unit = {},
    // REDDITURL04_MULTIREDDIT_LINK_V1: metadata of a feed opened from a link.
    val onResolveCustomFeed: (String, String, (CustomFeedSummary?) -> Unit) -> Unit =
        { _, _, onResolved -> onResolved(null) },
)
