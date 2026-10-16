package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.ConnectAccountUiState

/**
 * The account and connect runtime as SubshellApp receives it from
 * MainActivity: the account session directory, logout and reconnect, the
 * online-visibility runtime and the connect-account UI state with its
 * connect, cancel and reset entry points. Property names and defaults are
 * the former SubshellApp parameters, unchanged.
 */
data class AccountConnectEntryPoints(
    val accountSessionDirectory: AccountSessionDirectory? = null,
    val onLogoutActiveAccount: ((Boolean) -> Unit) -> Unit = { onComplete ->
        onComplete(false)
    },
    val onReconnectAccount: (String) -> Unit = {},
    val onlineVisibilityRuntime: app.subshell.prototype.ui.components.OnlineVisibilityRuntime =
        app.subshell.prototype.ui.components.OnlineVisibilityRuntime(),
    val connectAccountState: ConnectAccountUiState = ConnectAccountUiState.unavailable(),
    val onConnectAccount: () -> Unit = {},
    val onCancelConnectAccount: () -> Unit = {},
    val onResetConnectAccount: () -> Unit = {},
    // ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1: the active account's own over_18.
    val readNsfwAccountPreferences: (suspend (String) -> app.subshell.prototype.data.account.NsfwPreferencesResult)? = null,
)
