package app.subshell.prototype.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.subshell.prototype.data.account.ModeratorReportConfirmation
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.ModeratorReportsNotice
import app.subshell.prototype.data.account.ModeratorReportsUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorReportReason
import app.subshell.prototype.data.account.moderatorReportCountLabel
import app.subshell.prototype.data.account.moderatorReportTargetOrNull
import app.subshell.prototype.data.account.reportConfirmationOrNull
import app.subshell.prototype.data.account.reportTarget
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color

// MODERATION02A2_ROOT_OWNED_REPORT_INSPECTOR_ENTRY_POINTS_V1
// One root dialog survives removal of a queue row. No report bodies are put in
// navigation saved state, a public Post model, logs, or disk storage.
internal data class ModeratorReportsEntryPoints(
    val hasModeratorAccess: Boolean,
    val canInspect: (String) -> Boolean,
    val open: (ModeratorReportTarget?) -> Unit,
)

internal val LocalModeratorReports = staticCompositionLocalOf<ModeratorReportsEntryPoints?> { null }

@Composable
internal fun ModeratorReportLookupButton(communityName: String? = null) {
    val entries = LocalModeratorReports.current ?: return
    if (!entries.hasModeratorAccess || (communityName != null && !entries.canInspect(communityName))) return
    // MODERATION02A2R2_VISIBLE_LOOKUP_BUTTON_V1
    FilledTonalButton(
        onClick = { entries.open(null) },
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Find reports")
    }
}

@Composable
internal fun ModeratorReportsButton(item: RedditModeratorQueueItem) {
    val entries = LocalModeratorReports.current ?: return
    if (!entries.canInspect(item.communityName)) return
    // MODERATION02A2R2_VISIBLE_ROW_REPORT_BUTTON_V1
    OutlinedButton(
        onClick = { entries.open(item.reportTarget()) },
        modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("View reports")
    }
}

@Composable
internal fun ModeratorReportMenuItem(target: ModeratorReportTarget?, onDismissMenu: () -> Unit) {
    val entries = LocalModeratorReports.current ?: return
    val community = target?.communityName ?: return
    if (!entries.canInspect(community)) return
    DropdownMenuItem(
        text = { Text("Moderator reports") },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null) },
        onClick = {
            onDismissMenu()
            entries.open(target)
        },
    )
}

// MODERATION02A2_SHARED_REPORT_DIALOG_READ_CONFIRM_READBACK_V1
// MODERATION02A2R3_EXPRESSIVE_REPORT_WORKSPACE_V1
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeratorReportsDialog(
    accountStableId: String,
    state: ModeratorReportsUiState,
    moderationState: RedditModerationUiState,
    actionState: RedditModeratorActionUiState,
    onOpen: (ModeratorReportTarget?) -> Unit,
    onRefresh: () -> Unit,
    onConfirm: (ModeratorReportConfirmation) -> Unit,
    onAuthorize: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible || state.accountStableId != accountStableId) return
    var lookup by remember(accountStableId, state.target) { mutableStateOf("") }
    var invalidLookup by remember(accountStableId, state.target) { mutableStateOf(false) }
    var lookupExpanded by remember(accountStableId, state.target) { mutableStateOf(false) }
    var pending by remember(accountStableId, state.target, state.revision) {
        mutableStateOf<ModeratorReportConfirmation?>(null)
    }
    val confirmation = reportConfirmationOrNull(state, accountStableId)
    val actionBusy = actionState.inProgress
    val busy = state.busy || actionBusy
    // Queue-derived display projection; never an authoritative report snapshot or write permission.
    val lookupContent = moderatorReportLookupContent(accountStableId, moderationState, state)
    val reportListState = key(accountStableId, state.target?.fullname) { rememberLazyListState() }
    // MODERATION02A2R3_COMPACT_REPORT_TOP_BAR_V1, M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1
    AdaptiveFullScreenDialog(
        title = if (state.target == null) "Find reports" else "Reports",
        onDismissRequest = onDismiss,
        closeLabel = "Close reports",
        navigationIcon = if (state.target != null) {
            {
                IconButton(onClick = { onOpen(null) }, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Choose another item")
                }
            }
        } else {
            null
        },
        actions = {
            if (state.target != null) {
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh reports")
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close reports") }
            }
        },
    ) {
        LazyColumn(
            state = reportListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // MODERATION02A2R2_LOOKUP_AND_DETAIL_SEPARATE_V1
            // MODERATION02A2R3_QUEUE_FIRST_COLLAPSIBLE_MANUAL_LOOKUP_V1
            if (state.target == null) {
                item("queue-heading") {
                    ReportSectionHeading("In the loaded queue")
                    lookupContent.queueMessage?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(lookupContent.queueItems, key = { _, item -> "lookup-queue-${item.fullname}" }) { _, item ->
                    ModeratorReportTargetButton(
                        target = item.reportTarget(),
                        summary = item.summary,
                        supportingText = moderatorReportCountLabel(item),
                        enabled = !busy,
                        onClick = { onOpen(item.reportTarget()) },
                    )
                }
                if (lookupContent.recentTargets.isNotEmpty()) {
                    item("recent-heading") { ReportSectionHeading("Recently opened · this session") }
                    itemsIndexed(lookupContent.recentTargets, key = { _, target -> "recent-${target.fullname}" }) { _, target ->
                        ModeratorReportTargetButton(
                            target = target,
                            summary = null,
                            supportingText = null,
                            enabled = !busy,
                            onClick = { onOpen(target) },
                        )
                    }
                }
                item("manual-lookup") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.animateContentSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !busy, role = Role.Button) { lookupExpanded = !lookupExpanded }
                                    .semantics { stateDescription = if (lookupExpanded) "Expanded" else "Collapsed" }
                                    .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Open by link or ID", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Icon(if (lookupExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                            }
                            if (lookupExpanded) {
                                Column(
                                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedTextField(
                                        value = lookup,
                                        onValueChange = { lookup = it.take(2_000); invalidLookup = false },
                                        label = { Text("Reddit link or t3_/t1_ ID") },
                                        supportingText = {
                                            Text(if (invalidLookup) "Enter one valid Reddit post/comment link or full ID." else "Also works for items no longer in the queue.")
                                        },
                                        isError = invalidLookup,
                                        enabled = !busy,
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val target = moderatorReportTargetOrNull(lookup)
                                            if (target == null) invalidLookup = true else onOpen(target)
                                        },
                                        enabled = !busy && lookup.isNotBlank(),
                                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                                    ) {
                                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Open reports")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val target = state.target
            if (target != null) {
                item("target") {
                    // MODERATION02A2R3_CONTENT_BEFORE_TECHNICAL_ID_V1
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ModeratorMetadataPill(if (target.fullname.startsWith("t3_")) "Post" else "Comment")
                            target.communityName?.let { ModeratorMetadataPill("r/$it") }
                        }
                        state.item?.let { item ->
                            Text(item.summary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        SelectionContainer {
                            Text(target.fullname, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.busy) {
                item("progress") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        InlineProgressIndicator()
                        Text(if (state.mutating) "Checking and updating report settings…" else "Reading reports from Reddit…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.notice?.let { notice ->
                item("notice") {
                    ModeratorReportNotice(notice)
                    if (notice == ModeratorReportsNotice.AUTHORIZATION_REQUIRED &&
                        actionState.request?.accountStableId == accountStableId &&
                        actionState.request.thingFullname == state.target?.fullname &&
                        actionState.phase == RedditModeratorActionPhase.AUTHORIZATION_REQUIRED
                    ) {
                        TextButton(onClick = onAuthorize) { Text("Authorize moderator actions") }
                    }
                }
            }
            state.item?.reportDetails?.let { reports ->
                item("status") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportStatusBadge(reports.ignored)
                        Text(
                            reports.totalCount?.let { "${moderatorReportQuantity(it)} returned" } ?: "Total report count unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (reports.incomplete) {
                            Text("Some report details were unavailable or shortened.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                val userReasons = reports.userReports
                if (userReasons == null || userReasons.isEmpty()) {
                    item("user-empty") {
                        ReportEmptySection(
                            text = if (userReasons == null || reports.incomplete) "User report details unavailable." else "No user reports returned.",
                            unavailable = userReasons == null || reports.incomplete,
                        )
                    }
                } else {
                    item("user-heading") { ReportSectionHeading("User reports") }
                    itemsIndexed(userReasons, key = { index, _ -> "user-$index" }) { _, reason -> ReportReasonRow(reason, false) }
                }
                val modReasons = reports.moderatorReports
                if (modReasons == null || modReasons.isEmpty()) {
                    item("mod-empty") {
                        ReportEmptySection(
                            text = if (modReasons == null || reports.incomplete) "Moderator report details unavailable." else "No moderator reports returned.",
                            unavailable = modReasons == null || reports.incomplete,
                        )
                    }
                } else {
                    item("mod-heading") { ReportSectionHeading("Moderator reports") }
                    itemsIndexed(modReasons, key = { index, _ -> "mod-$index" }) { _, reason -> ReportReasonRow(reason, true) }
                }
                item("action") {
                    // MODERATION02A2R3_REPORT_CONTROL_GROUP_V1
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Report handling", style = MaterialTheme.typography.titleSmall)
                            if (confirmation != null) {
                                val ignoring = confirmation.action == RedditModeratorAction.IGNORE_REPORTS
                                Text(
                                    if (ignoring) "Stop future report alerts for this item." else "Allow future report alerts for this item again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { pending = confirmation }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Icon(if (ignoring) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (ignoring) "Ignore reports…" else "Unignore reports…")
                                }
                            } else if (!state.busy) {
                                Text("A known report setting and moderator permission are required before changing it.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    val pinned = pending ?: return
    val ignoring = pinned.action == RedditModeratorAction.IGNORE_REPORTS
    // MODERATION02A2R3_TARGET_BOUND_COMPACT_CONFIRMATION_V1
    AlertDialog(
        onDismissRequest = { pending = null },
        icon = { Icon(if (ignoring) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive, contentDescription = null) },
        title = { Text(if (ignoring) "Ignore reports?" else "Unignore reports?") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.item?.let { Text(it.summary, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        Text("r/${pinned.target.communityName}", style = MaterialTheme.typography.labelMedium)
                        Text(pinned.target.fullname, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(if (ignoring) "Future reports on this item will no longer trigger notifications or appear in moderation listings." else "Future reports on this item can trigger notifications and appear in moderation listings again.")
                Text(if (ignoring) "Undo with Unignore reports." else "You can switch this off again with Ignore reports.", style = MaterialTheme.typography.bodySmall)
                Text("This does not approve, remove or mark the content as spam, or ban its author.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = confirm@{
                    val current = pending ?: return@confirm
                    if (current != reportConfirmationOrNull(state, accountStableId) || busy) return@confirm
                    pending = null
                    onConfirm(current)
                },
                enabled = pinned == confirmation && !busy,
            ) { Text(if (ignoring) "Ignore reports" else "Unignore reports") }
        },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
    )
}

@Composable
private fun ReportSectionHeading(text: String) {
    Text(text, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ModeratorReportTargetButton(
    target: ModeratorReportTarget,
    summary: String?,
    supportingText: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // M3225_LIST_ROWS_ARE_LIST_ITEMS_V1
        ListItem(
            modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { ModeratorItemGlyph(isPost = target.fullname.startsWith("t3_")) },
            content = {
                Text(
                    summary ?: if (target.fullname.startsWith("t3_")) "Post" else "Comment",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column {
                    Text(listOfNotNull(target.communityName?.let { "r/$it" }, target.fullname).joinToString(" · "))
                    supportingText?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            },
        )
    }
}

@Composable
private fun ReportStatusBadge(ignored: Boolean?) {
    val label = when (ignored) {
        true -> "Reports ignored"
        false -> "Reports active"
        null -> "Report status unavailable"
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = when (ignored) {
            null -> MaterialTheme.colorScheme.errorContainer
            true -> MaterialTheme.colorScheme.secondaryContainer
            false -> MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = when (ignored) {
            null -> MaterialTheme.colorScheme.onErrorContainer
            true -> MaterialTheme.colorScheme.onSecondaryContainer
            false -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (ignored) { null -> Icons.Rounded.Info; true -> Icons.Rounded.NotificationsOff; false -> Icons.Rounded.NotificationsActive }, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReportEmptySection(text: String, unavailable: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReportReasonRow(reason: RedditModeratorReportReason, moderator: Boolean) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reason.reason, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (moderator) {
                reason.moderatorName?.let { Text("Moderator: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                ModeratorMetadataPill(reason.count?.let(::moderatorReportQuantity) ?: "Count unavailable", emphasized = true)
            }
        }
    }
}

@Composable
private fun ModeratorReportNotice(notice: ModeratorReportsNotice) {
    val verified = notice == ModeratorReportsNotice.VERIFIED || notice == ModeratorReportsNotice.VERIFIED_AFTER_UNKNOWN
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (verified) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (verified) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (verified) Icons.Rounded.CheckCircle else Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(moderatorReportsNoticeText(notice), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun moderatorReportsNoticeText(notice: ModeratorReportsNotice): String = when (notice) {
    ModeratorReportsNotice.INVALID_TARGET -> "Enter one valid post/comment link or fullname."
    ModeratorReportsNotice.NOT_FOUND -> "Reddit did not return the exact target. Its report status is unknown."
    ModeratorReportsNotice.READ_FAILED -> "Could not read report details. Refresh to check again; no report state is assumed."
    ModeratorReportsNotice.NO_PERMISSION -> "Reddit did not confirm Posts moderation permission for this target. Report settings cannot be changed."
    ModeratorReportsNotice.STATE_UNKNOWN -> "Reddit did not return a known report-ignore setting. No change can be sent."
    ModeratorReportsNotice.SETTINGS_CHANGED -> "Report settings changed since you opened the dialog. Review the current state before confirming again."
    ModeratorReportsNotice.BUSY -> "Another moderator action is in progress. This action was not queued. Refresh before confirming again."
    ModeratorReportsNotice.AUTHORIZATION_REQUIRED -> "Moderator authorization is required. After authorizing, refresh reports and confirm again. No write is resumed automatically."
    ModeratorReportsNotice.WRITE_FAILED -> "The report action could not be completed. Refresh the target before making a new explicit choice."
    ModeratorReportsNotice.VERIFIED -> "Report setting verified by a fresh read from Reddit."
    ModeratorReportsNotice.VERIFIED_AFTER_UNKNOWN -> "The write response was unclear. A fresh read now confirms the displayed report setting. The write was not retried."
    ModeratorReportsNotice.NOT_CONFIRMED -> "The requested report setting could not be confirmed. Refresh and inspect the current state before acting again. No retry was sent."
    ModeratorReportsNotice.ACCOUNT_CHANGED -> "The active account changed. Reopen reports with the intended moderator account."
}
