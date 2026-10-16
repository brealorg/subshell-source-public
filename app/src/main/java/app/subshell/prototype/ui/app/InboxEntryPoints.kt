package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.inbox.InboxNotificationTarget
import app.subshell.prototype.model.Post
import app.subshell.prototype.shortcuts.LauncherShortcutTarget

/**
 * The inbox runtime as SubshellApp receives it from MainActivity: the inbox
 * UI state, the load/append/read/resolve entry points, its authorization and
 * the notification and launcher shortcut hand-offs. Property names and
 * defaults of the former SubshellApp parameters are unchanged.
 */
data class InboxEntryPoints(
    val redditInboxState: RedditInboxUiState = RedditInboxUiState(),
    val onLoadRedditInbox: (String, Boolean) -> Unit = { _, _ -> },
    val onAppendRedditInbox: (String) -> Unit = {},
    val onMarkRedditInboxRead: (String, String) -> Unit = { _, _ -> },
    val onResolveRedditInboxTarget: (String, String, (Post?) -> Unit) -> Unit =
        { _, _, onResolved -> onResolved(null) },
    val onAuthorizeRedditInbox: (String) -> Unit = {},
    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1
    val onCancelRedditInboxAuthorization: () -> Unit = {},
    val inboxNotificationTarget: InboxNotificationTarget? = null,
    val onInboxNotificationConsumed: () -> Unit = {},
    // ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1: an Inbox launcher shortcut.
    val launcherShortcutTarget: LauncherShortcutTarget.Inbox? = null,
    val onLauncherShortcutConsumed: () -> Unit = {},
)
