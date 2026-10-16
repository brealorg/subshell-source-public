package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.inbox.ModmailNotificationTarget

/**
 * The modmail runtime as SubshellApp receives it from MainActivity: the UI
 * state, the ViewModel entry points and the notification hand-off. Property
 * names and defaults are the former SubshellApp parameters, unchanged.
 */
data class ModmailEntryPoints(
    val redditModmailState: RedditModmailUiState = RedditModmailUiState(),
    val onLoadRedditModmail: (String, Boolean) -> Unit = { _, _ -> },
    val onAppendRedditModmail: (String) -> Unit = {},
    val onSelectRedditModmailListMode: (String, RedditModmailListMode) -> Unit = { _, _ -> },
    val onOpenRedditModmailConversation: (String, String) -> Unit = { _, _ -> },
    val onSendRedditModmailMessage: (String, String, String, Boolean, Boolean) -> Unit =
        { _, _, _, _, _ -> },
    val onSetRedditModmailArchived: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val onSetRedditModmailUnread: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val onVerifyRedditModmailReadState: (String, String) -> Unit = { _, _ -> },
    val onApplyRedditModmailTriage: (String, String, RedditModmailTriageAction) -> Unit =
        { _, _, _ -> },
    val onCloseRedditModmailConversation: () -> Unit = {},
    val onAuthorizeRedditModmail: (String) -> Unit = {},
    val modmailNotificationTarget: ModmailNotificationTarget? = null,
    val onModmailNotificationConsumed: () -> Unit = {},
)
