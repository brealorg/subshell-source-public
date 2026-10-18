package app.subshell.prototype.ui.components

import app.subshell.prototype.ui.error.ReportFailureDiagnostic
import androidx.compose.material3.IconButton


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.ModeratorLogUiState
import app.subshell.prototype.data.account.RedditModeratorLogEntry
import app.subshell.prototype.ui.symbols.Symbols
import java.time.Duration
import java.time.Instant

// MODERATION02A4_MOD_LOG_ENTRYPOINTS_V1
data class ModeratorLogEntryPoints(
    val canInspect: (String) -> Boolean = { false },
    val open: (String) -> Unit = {},
)

val LocalModeratorLog = staticCompositionLocalOf { ModeratorLogEntryPoints() }

@Composable
fun ModeratorLogButton(
    communityName: String,
    onBeforeOpen: () -> Unit = {},
) {
    val entryPoints = LocalModeratorLog.current
    if (!entryPoints.canInspect(communityName)) return

    OutlinedButton(
        modifier = Modifier.heightIn(min = 48.dp),
        onClick = {
            onBeforeOpen()
            entryPoints.open(communityName)
        },
    ) {
        Icon(
            imageVector = Symbols.History,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Mod log")
    }
}

// MODERATION02A4_MATERIAL_MOD_LOG_SHEET_V1
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorLogDialog(
    accountStableId: String,
    state: ModeratorLogUiState,
    onRefresh: () -> Unit,
    onAppend: () -> Unit,
    onAuthorize: () -> Unit,
    onDismiss: () -> Unit,
    embeddedInModToolsWorkspace: Boolean = false,
    onCancelAuthorization: () -> Unit = {},
) {
    if (!state.visible) return
    val community = state.communityName ?: return
    if (!state.matches(accountStableId, community)) return

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ReportFailureDiagnostic(
        scopeKey = "moderator_log",
        active = state.failureKind != null,
        failureDetails = state.failureDetails,
    )

    val content: @Composable () -> Unit = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("mod-log-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (embeddedInModToolsWorkspace) {
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Symbols.ArrowBack,
                                        contentDescription = "Back to Mod tools",
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mod log",
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "r/$community",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                enabled = !state.loading && !state.authorizing,
                                onClick = onRefresh,
                            ) {
                                Icon(
                                    imageVector = Symbols.Refresh,
                                    contentDescription = "Refresh mod log",
                                )
                            }
                        }
                    }
                    if (state.authorizationRequired) {
                        item("mod-log-authorization") {
                            ModeratorLogNotice(
                                title = "Mod log permission required",
                                message = "Reddit needs the modlog permission to read moderation history for r/$community.",
                                // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: waiting for the
                                // browser used to hide every action.
                                actionLabel = if (state.authorizing) "Cancel authorization" else "Authorize",
                                onAction = if (state.authorizing) onCancelAuthorization else onAuthorize,
                            )
                        }
                    }

                    // LOWUX1_MOD_LOG_PAGE_FAILURE_AT_THE_BOTTOM_V1 (OCB-147): with
                    // entries loaded, a failure is a failed next page; it is shown
                    // where the reader is, at the bottom, and Retry asks for that
                    // page again instead of reloading from the top.
                    val pageFailure = state.failureKind != null && state.entries.isNotEmpty() &&
                        state.nextCursor != null
                    if (state.failureKind != null && !pageFailure) {
                        item("mod-log-failure") {
                            ModeratorLogNotice(
                                title = "Mod log unavailable",
                                message = buildString {
                                    append("Reddit moderation history could not be loaded.")
                                    state.failureHttpStatusCode?.let { code ->
                                        append(" HTTP ")
                                        append(code)
                                        append('.')
                                    }
                                },
                                actionLabel = if (state.failureRetryable) "Retry" else null,
                                onAction = onRefresh,
                            )
                        }
                    }

                    if (state.loading && state.entries.isEmpty()) {
                        item("mod-log-loading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InlineProgressIndicator()
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Loading mod log…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (
                        state.loaded &&
                        state.entries.isEmpty() &&
                        !state.loading &&
                        state.failureKind == null &&
                        !state.authorizationRequired
                    ) {
                        item("mod-log-empty") {
                            ModeratorLogNotice(
                                title = "No recent actions",
                                message = "Reddit returned no recent moderation actions for r/$community.",
                                actionLabel = null,
                                onAction = {},
                            )
                        }
                    }

                    items(
                        items = state.entries,
                        key = RedditModeratorLogEntry::id,
                    ) { entry ->
                        ModeratorLogRow(entry)
                    }

                    state.nextCursor?.let { cursor ->
                        item("mod-log-next-$cursor") {
                            LaunchedEffect(cursor) {
                                if (!state.loading && !state.authorizationRequired && state.failureKind == null) {
                                    onAppend()
                                }
                            }
                            if (pageFailure && !state.loading) {
                                ModeratorLogNotice(
                                    title = "Couldn't load more of the mod log",
                                    message = buildString {
                                        append("Reddit did not send the next page.")
                                        state.failureHttpStatusCode?.let { code ->
                                            append(" HTTP ")
                                            append(code)
                                            append('.')
                                        }
                                    },
                                    actionLabel = if (state.failureRetryable) "Retry" else null,
                                    onAction = onAppend,
                                )
                            } else if (state.loading) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    InlineProgressIndicator()
                                }
                            }
                        }
                    }
                }

    }
    if (embeddedInModToolsWorkspace) {
        content()
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            content()
        }
    }
}

@Composable
private fun ModeratorLogRow(entry: RedditModeratorLogEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            content = {
                Text(
                    text = moderationLogActionLabel(entry.action),
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = buildString {
                            append("u/")
                            append(entry.moderatorName)
                            append(" · ")
                            append(
                                moderatorLogRelativeAgeLabel(
                                    epochSeconds = entry.createdEpochSeconds,
                                ),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.targetTitle?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    entry.targetAuthor?.let { author ->
                        Text(
                            text = "Target: u/$author",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val note = entry.details ?: entry.description
                    note?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ModeratorLogNotice(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            actionLabel?.let { label ->
                TextButton(onClick = onAction) {
                    Text(label)
                }
            }
        }
    }
}

// MODERATION02A4_UI_OWNS_RELATIVE_AGE_FORMATTING_V1
internal fun moderatorLogRelativeAgeLabel(
    epochSeconds: Long,
    nowEpochSeconds: Long = Instant.now().epochSecond,
): String {
    if (epochSeconds <= 0L) return ""
    val seconds = Duration.between(
        Instant.ofEpochSecond(epochSeconds),
        Instant.ofEpochSecond(nowEpochSeconds),
    ).seconds.coerceAtLeast(0L)
    return when {
        seconds < 60L -> "now"
        seconds < 3_600L -> "${seconds / 60L}m"
        seconds < 86_400L -> "${seconds / 3_600L}h"
        seconds < 604_800L -> "${seconds / 86_400L}d"
        else -> "${seconds / 604_800L}w"
    }
}

internal fun moderationLogActionLabel(rawAction: String): String =
    when (rawAction.trim().lowercase()) {
        "approvelink" -> "Approved post"
        "approvecomment" -> "Approved comment"
        "removelink" -> "Removed post"
        "removecomment" -> "Removed comment"
        "spamlink" -> "Marked post as spam"
        "spamcomment" -> "Marked comment as spam"
        "ignorereports" -> "Ignored reports"
        "unignorereports" -> "Restored reports"
        "lock" -> "Locked"
        "unlock" -> "Unlocked"
        "distinguish" -> "Changed distinguish"
        "sticky" -> "Pinned post"
        "unsticky" -> "Unpinned post"
        "marknsfw" -> "Marked NSFW"
        "unmarknsfw" -> "Removed NSFW"
        else -> rawAction
            .trim()
            .replace('_', ' ')
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
    }
