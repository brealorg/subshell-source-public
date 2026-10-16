package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.account.OnlineVisibilityConfirmation
import app.subshell.prototype.data.account.OnlineVisibilityIssue
import app.subshell.prototype.data.account.OnlineVisibilityNotice
import app.subshell.prototype.data.account.OnlineVisibilityPhase
import app.subshell.prototype.data.account.OnlineVisibilityUiState

/** Single runtime parameter keeps the already-large SubshellApp signature bounded. */
data class OnlineVisibilityRuntime(
    val state: OnlineVisibilityUiState = OnlineVisibilityUiState(),
    val onOpen: (String) -> Unit = {},
    val onClose: (String) -> Unit = {},
    val onRefresh: (String) -> Unit = {},
    val onPropose: (String, Boolean) -> Unit = { _, _ -> },
    val onConfirm: (OnlineVisibilityConfirmation) -> Unit = {},
    val onCancel: () -> Unit = {},
)

/** Account-management actions are hosted by Settings, while the Home sheet stays a quick switcher. */
data class AccountSettingsRuntime(
    val connectAccountState: ConnectAccountUiState = ConnectAccountUiState.unavailable(),
    val onlineVisibility: OnlineVisibilityRuntime = OnlineVisibilityRuntime(),
    val onConnectAccount: () -> Unit = {},
    val onCancelConnectAccount: () -> Unit = {},
    val onResetConnectAccount: () -> Unit = {},
    val onReconnectAccount: (String) -> Unit = {},
    val onLogoutActiveAccount: ((Boolean) -> Unit) -> Unit = { onComplete ->
        onComplete(false)
    },
)

@Composable
internal fun OnlineVisibilitySettingRow(
    account: AccountSessionSummary,
    runtime: OnlineVisibilityRuntime,
    onReconnect: () -> Unit,
    interactionEnabled: Boolean,
) {
    val accountId = account.stableId
    DisposableEffect(accountId) {
        runtime.onOpen(accountId)
        onDispose { runtime.onClose(accountId) }
    }
    val state = runtime.state.takeIf { it.matches(accountId) } ?: OnlineVisibilityUiState()
    val canChange = interactionEnabled &&
        account.state == AccountSessionState.CONNECTED &&
        state.canChange &&
        state.confirmation == null
    val value = state.value

    // M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: a list row like the other settings, not a card.
    Box(Modifier.fillMaxWidth().settingsRowBleed()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ISSUE219_ONLINE_STATUS_ROW_IS_THE_SWITCH_V1: the row, not only the
            // switch, toggles and carries the label (LOWUI7a, one control per row).
            val rowToggleValue = value.takeIf { !state.busy && state.phase == OnlineVisibilityPhase.READY }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .then(
                        if (rowToggleValue != null) {
                            Modifier.toggleable(
                                value = rowToggleValue,
                                enabled = canChange,
                                role = Role.Switch,
                                onValueChange = { runtime.onPropose(accountId, it) },
                            )
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Show online status",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Allow Reddit to show when u/${account.username} is online",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    state.busy ->
                        InlineProgressIndicator()
                    value != null && state.phase == OnlineVisibilityPhase.READY ->
                        Switch(
                            // LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1 (OCB-157): the row above
                            // is the toggle since ISSUE219.
                            checked = value,
                            onCheckedChange = null,
                            enabled = canChange,
                        )
                    state.issue == OnlineVisibilityIssue.AUTHORIZATION &&
                        account.state == AccountSessionState.REAUTHENTICATION_REQUIRED ->
                        TextButton(
                            onClick = onReconnect,
                            enabled = interactionEnabled,
                        ) {
                            Text("Sign in again")
                        }
                    else ->
                        TextButton(
                            onClick = { runtime.onRefresh(accountId) },
                            enabled = interactionEnabled,
                        ) {
                            Text("Retry")
                        }
                }
            }

            if (state.phase == OnlineVisibilityPhase.UNAVAILABLE && state.issue == null) {
                Text(
                    "Reddit did not return this setting. Nothing has been changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.issue?.let { issue ->
                Text(
                    onlineVisibilityIssueMessage(issue) +
                        (state.httpStatusCode?.let { " HTTP $it." } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (issue == OnlineVisibilityIssue.CHANGED_ON_REDDIT) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            when (state.notice) {
                OnlineVisibilityNotice.SAVED ->
                    Text(
                        "Saved and checked on Reddit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                OnlineVisibilityNotice.CURRENT_VALUE_VERIFIED ->
                    Text(
                        "Reddit now reports the selected setting. The earlier save response was not confirmed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                null -> Unit
            }
        }
    }

    val confirmation = state.confirmation
    if (
        confirmation != null &&
        interactionEnabled &&
        account.state == AccountSessionState.CONNECTED
    ) {
        AlertDialog(
            onDismissRequest = runtime.onCancel,
            title = {
                Text(
                    if (confirmation.desiredValue) {
                        "Show online status?"
                    } else {
                        "Hide online status?"
                    },
                )
            },
            text = {
                Text(
                    "Change Reddit's online-visibility setting for u/${account.username}? " +
                        "This applies to your Reddit account, not just this device.",
                )
            },
            confirmButton = {
                TextButton(onClick = { runtime.onConfirm(confirmation) }) {
                    Text(if (confirmation.desiredValue) "Show status" else "Hide status")
                }
            },
            dismissButton = {
                TextButton(onClick = runtime.onCancel) { Text("Cancel") }
            },
        )
    }
}

internal fun onlineVisibilityIssueMessage(issue: OnlineVisibilityIssue): String = when (issue) {
    OnlineVisibilityIssue.AUTHORIZATION -> "Sign in again to read or change this Reddit account setting. No change will be sent automatically."
    OnlineVisibilityIssue.ACCOUNT_CHANGED -> "The active account changed. Reopen Accounts settings to load the current account."
    OnlineVisibilityIssue.HTTP -> "Reddit rejected the request. Refresh to check the current setting."
    OnlineVisibilityIssue.TRANSPORT -> "Could not reach Reddit. Refresh to read the setting again."
    OnlineVisibilityIssue.PROTOCOL -> "Reddit returned an unexpected response. The setting is unknown."
    OnlineVisibilityIssue.CONFIGURATION -> "Online visibility is not available for this connection."
    OnlineVisibilityIssue.CHANGED_ON_REDDIT -> "The setting changed since you reviewed it. The latest value is shown; no change was sent."
    OnlineVisibilityIssue.OUTCOME_UNKNOWN -> "Reddit may have applied the change. Refresh to check the current setting before making another change."
}
