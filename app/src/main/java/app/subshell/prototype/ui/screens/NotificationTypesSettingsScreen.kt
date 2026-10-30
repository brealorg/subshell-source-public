package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import app.subshell.prototype.ui.symbols.Symbols

// NOTIFY01A1R2_NOTIFICATION_TYPES_SUBSCREEN_PRESENTATION_V1
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationTypesSettingsScreen(
    state: SubshellState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state.inboxNotificationsEnabled

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text("Notification types") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Symbols.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            scrollBehavior = routeTopBarScroll,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("notification-types-supporting") {
                Text(
                    text = "Choose what you want to be notified about.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item("notification-types-group") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        NotificationTypeRow(
                            title = "Replies",
                            description = "Post and comment replies",
                            checked = state.notifyReplies,
                            enabled = enabled,
                            onCheckedChange = state::updateNotifyReplies,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = "Mentions",
                            description = "Username mentions",
                            checked = state.notifyMentions,
                            enabled = enabled,
                            onCheckedChange = state::updateNotifyMentions,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = "Private messages",
                            description = "Reddit messages",
                            checked = state.notifyPrivateMessages,
                            enabled = enabled,
                            onCheckedChange =
                                state::updateNotifyPrivateMessages,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = "Reddit notices",
                            description = "System messages",
                            checked = state.notifyRedditNotices,
                            enabled = enabled,
                            onCheckedChange =
                                state::updateNotifyRedditNotices,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = "Modmail",
                            description = "Moderator conversations",
                            checked = state.notifyModmail,
                            enabled = enabled,
                            onCheckedChange = state::updateNotifyModmail,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NotificationTypeRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: an M3 ListItem inside its card,
    // with ListItem's own disabled colours.
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = null)
        },
        enabled = enabled,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

@Composable
internal fun NotificationTypeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
