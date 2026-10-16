package app.subshell.prototype.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.accountSwitcherSessionLabel
import app.subshell.prototype.ui.accountSwitcherSessions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSwitcherSheet(
    state: SubshellState,
    accountConnect: AccountConnectEntryPoints,
    onManageAccounts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val onReconnectAccount = accountConnect.onReconnectAccount
    val onResetConnectAccount = accountConnect.onResetConnectAccount
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val productAccounts = accountSwitcherSessions(state.accountSessions)
    val reauthenticationAccount = productAccounts.firstOrNull { account ->
        account.state == AccountSessionState.REAUTHENTICATION_REQUIRED
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            if (reauthenticationAccount != null) {
                Text(
                    text = "u/${reauthenticationAccount.username} needs sign-in again",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            productAccounts.forEach { account ->
                val selected = account.stableId == state.activeAccountId
                // USERSTATUS01A1_ACCOUNT_IDENTITY_AVATAR_V1
                LaunchedEffect(account.stableId, account.username) {
                    state.ensureUserIdentity(account.username)
                }
                val accountAvatarUri =
                    state.userFor(account.username).avatarImageUri
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            state.switchAccount(account.stableId)
                            onDismiss()
                            if (
                                account.state ==
                                AccountSessionState.REAUTHENTICATION_REQUIRED
                            ) {
                                onResetConnectAccount()
                                onReconnectAccount(account.stableId)
                            }
                        },
                    content = { Text("u/${account.username}") },
                    supportingContent = {
                        Text(accountSwitcherSessionLabel(account, selected))
                    },
                    leadingContent = {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            app.subshell.prototype.ui.components.RemoteIdentityImage(
                                uri = accountAvatarUri,
                                contentDescription = "Avatar for u/${account.username}",
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                app.subshell.prototype.ui.components.RedditDefaultProfileAvatar(
                                    seed = account.username,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    },
                    trailingContent = if (selected) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Current account",
                            )
                        }
                    } else {
                        null
                    },
                )
            }

            if (productAccounts.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 4.dp))
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageAccounts),
                content = { Text("Manage accounts") },
                supportingContent = { Text("Accounts, privacy and connection") },
                leadingContent = {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}
