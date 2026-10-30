package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.subshell.prototype.communityposts.MAX_WATCHED_COMMUNITIES
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.symbols.Symbols

/**
 * ISSUE239_WATCHED_COMMUNITIES_IN_SETTINGS_V1 (#239): Settings › Notifications
 * › New posts lists the communities that notify the active account about new
 * posts; each can be removed here. They are added from a community's ⋮.
 */
@Composable
internal fun NewPostNotificationsSettingsScreen(
    state: SubshellState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val watched = state.watchedCommunityNames
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text("New posts") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Symbols.ArrowBack, contentDescription = "Back")
                }
            },
            scrollBehavior = routeTopBarScroll,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("new-posts-supporting") {
                Text(
                    text = newPostNotificationsExplanation(state.communityPostNotificationsAvailable, watched.isEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            // ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1 (owner): the reader
            // decides; off by default. Their own posts and comments never notify.
            item("new-posts-comments") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    NotificationTypeRow(
                        title = "Also new comments",
                        description = "A notification for each new comment in these communities. " +
                            "Your own posts and comments never notify you.",
                        checked = state.notifyNewComments,
                        enabled = state.communityPostNotificationsAvailable,
                        onCheckedChange = state::updateNotifyNewComments,
                    )
                }
            }
            if (watched.isNotEmpty()) {
                item("new-posts-communities") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            watched.forEachIndexed { index, name ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                val label = state.subredditLabel(name)
                                ListItem(
                                    content = { Text(label) },
                                    trailingContent = {
                                        IconButton(onClick = { state.setWatchingCommunityPosts(name, watch = false) }) {
                                            Icon(Symbols.Close, contentDescription = "Stop notifications from $label")
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun newPostNotificationsExplanation(available: Boolean, empty: Boolean): String = when {
    !available -> "Sign in to a Reddit account to get notifications about new posts."
    empty -> "No communities yet. Open a community and choose ⋮ › Notify about new posts."
    else -> "A notification for each new post, checked every 15 minutes. " +
        "Add a community from its ⋮ menu; up to $MAX_WATCHED_COMMUNITIES."
}

/** The summary under Settings › Notifications › New posts. */
internal fun newPostNotificationsSummary(count: Int): String = when (count) {
    0 -> "Off for every community"
    1 -> "1 community"
    else -> "$count communities"
}
