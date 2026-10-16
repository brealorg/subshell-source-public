package app.subshell.prototype.ui

import androidx.compose.material.icons.rounded.Add
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ConnectAccountFailureKind
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState

internal enum class AccountConnectAction {
    CONNECT,
    CANCEL,
    RESET,
    NONE,
}

internal data class AccountConnectPresentation(
    val headline: String,
    val supporting: String,
    val action: AccountConnectAction,
    val actionLabel: String? = null,
)

internal fun accountSwitcherSessions(
    accounts: List<AccountSessionSummary>,
): List<AccountSessionSummary> =
    accounts.filter {
        it.source == AccountSessionSource.AUTHENTICATED
    }

internal fun accountSwitcherSessionLabel(
    account: AccountSessionSummary,
    selected: Boolean,
): String = when {
    account.state != AccountSessionState.CONNECTED && selected ->
        "Current account · Sign in again"

    account.state != AccountSessionState.CONNECTED ->
        "Sign in again"

    selected ->
        "Current account"

    else ->
        "Connected"
}

internal fun accountConnectPresentation(
    connectAccountState: ConnectAccountUiState,
    hasAuthenticatedAccounts: Boolean,
): AccountConnectPresentation = when (
    connectAccountState.phase
) {
    ConnectAccountPhase.UNAVAILABLE ->
        AccountConnectPresentation(
            headline = if (hasAuthenticatedAccounts) {
                "Add Reddit account unavailable"
            } else {
                "Reddit sign-in unavailable"
            },
            supporting = if (hasAuthenticatedAccounts) {
                "Adding another Reddit account isn't available in this build."
            } else {
                "Reddit sign-in isn't available in this build."
            },
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.IDLE,
    ConnectAccountPhase.CONNECTED ->
        AccountConnectPresentation(
            headline = if (hasAuthenticatedAccounts) {
                "Add Reddit account"
            } else {
                "Connect Reddit account"
            },
            supporting = if (hasAuthenticatedAccounts) {
                "Sign in with another Reddit account."
            } else {
                "Sign in to use your Reddit account in subshell."
            },
            action = AccountConnectAction.CONNECT,
            actionLabel = if (hasAuthenticatedAccounts) {
                "Add"
            } else {
                "Connect"
            },
        )

    ConnectAccountPhase.LAUNCHING ->
        AccountConnectPresentation(
            headline = "Opening Reddit sign-in",
            supporting = "Starting Reddit authorization.",
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.AWAITING_CALLBACK ->
        AccountConnectPresentation(
            headline = "Waiting for Reddit",
            supporting =
                "Finish sign-in in your browser, then return to subshell.",
            action = AccountConnectAction.CANCEL,
            actionLabel = "Cancel",
        )

    ConnectAccountPhase.EXCHANGING ->
        AccountConnectPresentation(
            headline = "Completing sign-in",
            supporting = "Finishing Reddit sign-in.",
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.RESOLVING_IDENTITY ->
        AccountConnectPresentation(
            headline = "Loading Reddit account",
            supporting = "Reading your Reddit account.",
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.SYNCING_REDDIT_FEED ->
        AccountConnectPresentation(
            headline = "Preparing Reddit account",
            supporting = "Loading the first Reddit feed.",
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.CANCELLED ->
        AccountConnectPresentation(
            headline = "Reddit sign-in cancelled",
            supporting = "No account was added.",
            action = AccountConnectAction.RESET,
            actionLabel = "Done",
        )

    ConnectAccountPhase.ERROR ->
        AccountConnectPresentation(
            headline = if (
                connectAccountState.failureKind ==
                ConnectAccountFailureKind.REAUTHENTICATION_ACCOUNT_MISMATCH
            ) {
                "Could not reconnect Reddit account"
            } else if (hasAuthenticatedAccounts) {
                "Could not add Reddit account"
            } else {
                "Could not connect Reddit account"
            },
            supporting =
                connectAccountFailureLabel(
                    connectAccountState.failureKind,
                ),
            action = if (connectAccountState.retryable) {
                AccountConnectAction.CONNECT
            } else {
                AccountConnectAction.RESET
            },
            actionLabel = if (connectAccountState.retryable) {
                "Retry"
            } else {
                "Dismiss"
            },
        )
}

private fun connectAccountFailureLabel(
    failureKind: ConnectAccountFailureKind?,
): String = when (failureKind) {
    ConnectAccountFailureKind.CONFIGURATION_REQUIRED ->
        "OAuth build configuration is incomplete."
    ConnectAccountFailureKind.BROWSER_LAUNCH ->
        "No compatible browser could open the authorization request."
    ConnectAccountFailureKind.CALLBACK_REJECTED ->
        "The callback did not match the pending authorization request."
    ConnectAccountFailureKind.CALLBACK_MISSING ->
        "The callback could not be recovered. Start sign-in again."
    ConnectAccountFailureKind.PROVIDER ->
        "Reddit returned an authorization error."
    ConnectAccountFailureKind.TOKEN_EXCHANGE ->
        "The authorization response could not be exchanged."
    ConnectAccountFailureKind.IDENTITY ->
        "The authenticated account identity could not be resolved."
    ConnectAccountFailureKind.REDDIT_FEED ->
        "The first Reddit feed could not be cached."
    ConnectAccountFailureKind.ACCOUNT_CONFLICT ->
        "That Reddit account conflicts with an account already known to subshell."
    ConnectAccountFailureKind.REAUTHENTICATION_ACCOUNT_MISMATCH ->
        "Reddit returned a different account than the one being reconnected. " +
            "Sign in with the account shown in Accounts and try again."
    ConnectAccountFailureKind.STORAGE ->
        "The encrypted account session could not be updated."
    null -> "The account connection could not be completed."
}
