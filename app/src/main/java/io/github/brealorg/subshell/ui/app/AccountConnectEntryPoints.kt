package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.ConnectAccountUiState

/**
 * The account and connect runtime as SubshellApp receives it from
 * MainActivity: the account session directory, logout and reconnect, the
 * online-visibility runtime and the connect-account UI state with its
 * connect, cancel and reset entry points. Property names and defaults are
 * the former SubshellApp parameters, unchanged.
 */
data class AccountConnectEntryPoints(
    val accountSessionDirectory: AccountSessionDirectory? = null,
    // ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1: identity lookups send it.
    val redditUserAgent: String? = null,
    val onLogoutActiveAccount: ((Boolean) -> Unit) -> Unit = { onComplete ->
        onComplete(false)
    },
    val onReconnectAccount: (String) -> Unit = {},
    val onlineVisibilityRuntime: io.github.brealorg.subshell.ui.components.OnlineVisibilityRuntime =
        io.github.brealorg.subshell.ui.components.OnlineVisibilityRuntime(),
    val connectAccountState: ConnectAccountUiState = ConnectAccountUiState.unavailable(),
    val onConnectAccount: () -> Unit = {},
    val onCancelConnectAccount: () -> Unit = {},
    val onResetConnectAccount: () -> Unit = {},
    // ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1: the active account's own over_18.
    val readNsfwAccountPreferences: (suspend (String) -> io.github.brealorg.subshell.data.account.NsfwPreferencesResult)? = null,
    // REDDITCHECK01_CHECK_REDDIT_ACCESS_V1: Diagnostics › Check Reddit access for an account.
    val checkRedditAccess: (suspend (String) -> io.github.brealorg.subshell.data.account.RedditAccessCheckResult)? = null,
)
