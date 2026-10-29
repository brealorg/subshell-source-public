package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.reading.READING_STATS_SUGGESTION_MIN_DAYS
import app.subshell.prototype.data.reading.ReadingStats
import app.subshell.prototype.data.reading.ReadingSummary
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.onDisk
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ISSUE268_SUGGESTIONS_HIDE_OR_LEAVE_V1: the signed-in account's Reddit
 * subscriptions and its Leave action, for the communities it never opens.
 * [subscribedNames] is null while they are not known (signed out, loading).
 */
internal data class ReadingStatsCommunities(
    val subscribedNames: List<String>? = null,
    val leavingName: String? = null,
    val leaveFailedName: String? = null,
    val onLeave: (String) -> Unit = {},
)

internal val LocalReadingStatsCommunities = staticCompositionLocalOf { ReadingStatsCommunities() }

/** Subscriptions only when Reddit's list for this signed-in account is loaded. */
internal fun readingStatsCommunities(
    accountStableId: String,
    authenticated: Boolean,
    subscriptions: RedditSubscriptionsUiState,
    membership: CommunityMembershipUiState,
    onChangeMembership: (String, String, CommunityMembershipAction) -> Unit,
): ReadingStatsCommunities {
    if (!authenticated || subscriptions.accountStableId != accountStableId || !subscriptions.loaded) {
        return ReadingStatsCommunities()
    }
    val leave = membership.takeIf {
        it.accountStableId == accountStableId && it.action == CommunityMembershipAction.LEAVE
    }
    return ReadingStatsCommunities(
        subscribedNames = subscriptions.communities.map { it.name },
        leavingName = leave?.takeIf {
            it.phase == CommunityMembershipPhase.SENDING ||
                it.phase == CommunityMembershipPhase.AUTHORIZING ||
                it.phase == CommunityMembershipPhase.AWAITING_CALLBACK
        }?.communityName,
        leaveFailedName = leave?.takeIf {
            it.phase == CommunityMembershipPhase.FAILED ||
                it.phase == CommunityMembershipPhase.AUTHORIZATION_REQUIRED
        }?.communityName,
        onLeave = { name -> onChangeMembership(accountStableId, name, CommunityMembershipAction.LEAVE) },
    )
}

internal const val READING_STATS_COMMUNITY_ROWS = 25

/** "1 h 5 min", "12 min", "under a minute". */
internal fun readingDurationLabel(seconds: Long): String {
    val minutes = seconds / 60L
    return when {
        minutes < 1L -> "under a minute"
        minutes < 60L -> "$minutes min"
        minutes % 60L == 0L -> "${minutes / 60L} h"
        else -> "${minutes / 60L} h ${minutes % 60L} min"
    }
}

internal fun readingCountsLabel(opens: Int, postOpens: Int): String = listOfNotNull(
    opens.takeIf { it > 0 }?.let { if (it == 1) "1 visit" else "$it visits" },
    postOpens.takeIf { it > 0 }?.let { if (it == 1) "1 post" else "$it posts" },
).joinToString(" · ")

/**
 * The subscribed communities "Your reading" suggests hiding or leaving:
 * none opened (page or post) in the summary's days, not already hidden.
 * Empty until recording has run [READING_STATS_SUGGESTION_MIN_DAYS] days.
 */
internal fun readingSuggestions(
    summary: ReadingSummary,
    subscribedNames: List<String>,
    isHidden: (String) -> Boolean,
): List<String> =
    if (!summary.suggestionsReady) {
        emptyList()
    } else {
        subscribedNames
            .filterNot { name -> summary.hasRead(name) || isHidden(name) }
            .distinctBy(String::lowercase)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

/**
 * ISSUE268_YOUR_READING_PAGE_V1 (#268): You › Library › Reading. Where the
 * time goes, which communities are read and which subscriptions are never
 * opened, from counts kept on this device only.
 */
@Composable
internal fun ReadingStatsPanel(state: SubshellState) {
    val store = ReadingStats.store(LocalContext.current)
    LaunchedEffect(store) { store.onDisk { load() } }
    val enabled by store.enabled.collectAsStateWithLifecycle()
    val revision by store.revision.collectAsStateWithLifecycle()
    val account = state.activeAccountId
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (enabled != true) {
            ReadingStatsOff(onTurnOn = { store.onDisk { setEnabled(true) } }, loading = enabled == null)
            return@Column
        }
        val summary by produceState<ReadingSummary?>(null, account, revision) {
            value = withContext(Dispatchers.IO) { store.summary(account) }
        }
        val current = summary ?: return@Column
        ReadingStatsTotals(current)
        ReadingStatsCommunityList(current, onOpen = state::openSubreddit)
        ReadingStatsSuggestionList(current, state)
        Text(
            text = "Counted only on this device: opens and time on screen per community, " +
                "nothing about what you read. Never sent anywhere and not in backups.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmDelete = "clear" }) { Text("Clear") }
            OutlinedButton(onClick = { confirmDelete = "off" }) { Text("Turn off") }
        }
    }

    confirmDelete?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (action == "off") "Turn off Your reading?" else "Clear Your reading?") },
            text = {
                Text(
                    if (action == "off") {
                        "What was counted is deleted and nothing more is counted."
                    } else {
                        "What was counted is deleted; counting starts again today."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        if (action == "off") store.onDisk { setEnabled(false) } else store.onDisk { clear() }
                    },
                ) { Text(if (action == "off") "Turn off" else "Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReadingStatsOff(onTurnOn: () -> Unit, loading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Your reading", style = MaterialTheme.typography.titleMedium)
            Text(
                "See where your time goes and which communities you actually read. " +
                    "Counted only on this device, never sent anywhere and not in backups.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onTurnOn, enabled = !loading) { Text("Turn on") }
        }
    }
}

@Composable
private fun ReadingStatsTotals(summary: ReadingSummary) {
    val since = remember(summary.sinceEpochDay) {
        LocalDate.ofEpochDay(summary.sinceEpochDay)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    val period = if (summary.days >= app.subshell.prototype.data.reading.READING_STATS_WINDOW_DAYS) {
        "Last ${summary.days} days"
    } else {
        "Since $since"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(period, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(readingDurationLabel(summary.totalSeconds), style = MaterialTheme.typography.headlineMedium)
        if (summary.homeSeconds > 0L) {
            Text(
                "Home feed ${readingDurationLabel(summary.homeSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingStatsCommunityList(summary: ReadingSummary, onOpen: (String) -> Unit) {
    Text("Communities you read", style = MaterialTheme.typography.titleSmall)
    if (summary.communities.isEmpty()) {
        Text(
            "Nothing counted yet. Communities and posts you open show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    summary.communities.take(READING_STATS_COMMUNITY_ROWS).forEach { community ->
        ListItem(
            content = { Text("r/${community.communityName}") },
            modifier = Modifier.clickable { onOpen(community.communityName) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            supportingContent = {
                Text(
                    listOf(
                        readingDurationLabel(community.seconds),
                        readingCountsLabel(community.opens, community.postOpens),
                    ).filter(String::isNotBlank).joinToString(" · "),
                )
            },
        )
    }
}

@Composable
private fun ReadingStatsSuggestionList(summary: ReadingSummary, state: SubshellState) {
    val communities = LocalReadingStatsCommunities.current
    val subscribed = communities.subscribedNames ?: return
    Text("Subscriptions you never open", style = MaterialTheme.typography.titleSmall)
    if (!summary.suggestionsReady) {
        val left = READING_STATS_SUGGESTION_MIN_DAYS - summary.days
        Text(
            "Shown after $READING_STATS_SUGGESTION_MIN_DAYS days of counting ($left to go).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val suggestions = readingSuggestions(summary, subscribed, state::isCommunityHidden)
    if (suggestions.isEmpty()) {
        Text(
            "You opened every community you subscribe to in the last ${summary.days} days.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        "Not opened in ${summary.days} days. Hide keeps them out of your lists; Leave unsubscribes on Reddit.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    suggestions.forEach { name ->
        val leaving = communities.leavingName.equals(name, ignoreCase = true)
        ListItem(
            content = { Text("r/$name") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            supportingContent = if (communities.leaveFailedName.equals(name, ignoreCase = true)) {
                { Text("Couldn't leave. Try again from the community page.", color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
            trailingContent = {
                Row {
                    TextButton(onClick = { state.toggleHiddenCommunity(name) }, enabled = !leaving) { Text("Hide") }
                    TextButton(onClick = { communities.onLeave(name) }, enabled = !leaving) {
                        Text(if (leaving) "Leaving…" else "Leave")
                    }
                }
            },
        )
    }
}
