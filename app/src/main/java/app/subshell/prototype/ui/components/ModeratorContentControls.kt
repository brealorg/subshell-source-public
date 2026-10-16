package app.subshell.prototype.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.subshell.prototype.data.account.ModeratorContentControlConfirmation
import app.subshell.prototype.data.account.ModeratorContentControlsNotice
import app.subshell.prototype.data.account.ModeratorContentControlsUiState
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.contentControlConfirmationOrNull
import app.subshell.prototype.data.account.isContentControl
import app.subshell.prototype.data.account.reportTarget
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// MODERATION02A3_ROOT_OWNED_CONTENT_CONTROL_ENTRY_POINTS_V1
internal data class ModeratorContentControlsEntryPoints(
    val canInspect: (String) -> Boolean,
    val open: (ModeratorReportTarget) -> Unit,
)

internal val LocalModeratorContentControls =
    staticCompositionLocalOf<ModeratorContentControlsEntryPoints?> { null }

@Composable
internal fun ModeratorContentControlsButton(
    item: RedditModeratorQueueItem,
) {
    val entries = LocalModeratorContentControls.current ?: return
    if (!entries.canInspect(item.communityName)) return
    OutlinedButton(
        onClick = { entries.open(item.reportTarget()) },
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Rounded.AdminPanelSettings,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Controls")
    }
}

@Composable
internal fun ModeratorContentControlsMenuItem(
    target: ModeratorReportTarget?,
    onDismissMenu: () -> Unit,
) {
    val entries = LocalModeratorContentControls.current ?: return
    val resolved = target ?: return
    val community = resolved.communityName ?: return
    if (!entries.canInspect(community)) return
    DropdownMenuItem(
        text = { Text("Moderation controls") },
        leadingIcon = {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                contentDescription = null,
            )
        },
        onClick = {
            onDismissMenu()
            entries.open(resolved)
        },
    )
}

// MODERATION02A3_MATERIAL_CONTENT_CONTROL_DIALOG_V1
@Composable
internal fun ModeratorContentControlsDialog(
    accountStableId: String,
    activeUsername: String,
    state: ModeratorContentControlsUiState,
    actionState: RedditModeratorActionUiState,
    onRefresh: () -> Unit,
    onConfirm: (ModeratorContentControlConfirmation) -> Unit,
    onAuthorize: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible || state.accountStableId != accountStableId) return

    val item = state.item
    val actionBusy = actionState.inProgress
    val busy = state.busy || actionBusy
    var pending by remember(
        accountStableId,
        state.target?.fullname,
        state.revision,
    ) {
        mutableStateOf<ModeratorContentControlConfirmation?>(null)
    }

    val lockAction = when (item?.contentState?.locked) {
        false -> RedditModeratorAction.LOCK
        true -> RedditModeratorAction.UNLOCK
        null -> null
    }
    val distinguishAction = when (item?.contentState?.distinguished) {
        false -> RedditModeratorAction.DISTINGUISH
        true -> RedditModeratorAction.UNDISTINGUISH
        null -> null
    }
    val lockConfirmation = lockAction?.let { action ->
        contentControlConfirmationOrNull(
            state = state,
            activeAccountStableId = accountStableId,
            activeUsername = activeUsername,
            action = action,
        )
    }
    val distinguishConfirmation = distinguishAction?.let { action ->
        contentControlConfirmationOrNull(
            state = state,
            activeAccountStableId = accountStableId,
            activeUsername = activeUsername,
            action = action,
        )
    }

    // M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1
    AdaptiveFullScreenDialog(
        title = "Moderation controls",
        onDismissRequest = onDismiss,
        closeLabel = "Close moderation controls",
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !busy && state.target != null,
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Refresh moderation controls",
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineProgressIndicator()
                        Spacer(Modifier.width(10.dp))
                        Text("Checking current Reddit state…")
                    }
                }

                item != null -> {
                    ModeratorControlTargetCard(item)

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Conversation",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        ModeratorControlStateBadge(
                            label = when (item.contentState.locked) {
                                true -> "Locked"
                                false -> "Unlocked"
                                null -> "Lock state unavailable"
                            },
                            known = item.contentState.locked != null,
                            iconLocked = item.contentState.locked == true,
                        )
                        FilledTonalButton(
                            onClick = {
                                lockConfirmation?.let { pending = it }
                            },
                            enabled = lockConfirmation != null && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                if (item.contentState.locked == true) {
                                    Icons.Rounded.LockOpen
                                } else {
                                    Icons.Rounded.Lock
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (item.contentState.locked == true) {
                                    "Unlock"
                                } else {
                                    "Lock"
                                },
                            )
                        }

                        Text(
                            "Moderator identity",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        ModeratorDistinguishStateBadge(
                            distinguished = item.contentState.distinguished,
                        )
                        FilledTonalButton(
                            onClick = {
                                distinguishConfirmation?.let { pending = it }
                            },
                            enabled =
                                distinguishConfirmation != null &&
                                    !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (item.contentState.distinguished == true) {
                                    "Undistinguish"
                                } else {
                                    "Distinguish as moderator"
                                },
                            )
                        }
                        if (
                            !item.author.equals(
                                activeUsername.removePrefix("u/"),
                                ignoreCase = true,
                            )
                        ) {
                            Text(
                                "Only content authored by the active moderator can be distinguished here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        "The exact target is not available. Refresh or reopen the item before changing moderator controls.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.notice?.let { notice ->
                ModeratorContentControlNoticeCard(
                    notice = notice,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            val authorizationRequest = actionState.request
            if (
                authorizationRequest != null &&
                authorizationRequest.thingFullname == state.target?.fullname &&
                authorizationRequest.action.isContentControl() &&
                actionState.phase ==
                    RedditModeratorActionPhase.AUTHORIZATION_REQUIRED
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Moderator authorization required",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Authorize modposts, then refresh this target and confirm the action again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onAuthorize) {
                            Text("Authorize")
                        }
                    }
                }
            }
        }
    }

    pending?.let { pinned ->
        val current = contentControlConfirmationOrNull(
            state = state,
            activeAccountStableId = accountStableId,
            activeUsername = activeUsername,
            action = pinned.action,
        )
        val labels = moderatorContentControlLabels(pinned.action)
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = {
                Icon(
                    when (pinned.action) {
                        RedditModeratorAction.LOCK ->
                            Icons.Rounded.Lock
                        RedditModeratorAction.UNLOCK ->
                            Icons.Rounded.LockOpen
                        else ->
                            Icons.Rounded.AdminPanelSettings
                    },
                    contentDescription = null,
                )
            },
            title = { Text(labels.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.item?.let { selected ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    selected.summary,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "r/${pinned.target.communityName} · ${pinned.target.fullname}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(labels.description)
                    Text(
                        labels.reversal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = confirm@{
                        val exact = current ?: return@confirm
                        if (exact != pinned || busy) return@confirm
                        pending = null
                        onConfirm(exact)
                    },
                    enabled = current == pinned && !busy,
                ) {
                    Text(labels.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private data class ModeratorContentControlLabels(
    val title: String,
    val confirm: String,
    val description: String,
    val reversal: String,
)

private fun moderatorContentControlLabels(
    action: RedditModeratorAction,
): ModeratorContentControlLabels = when (action) {
    RedditModeratorAction.LOCK -> ModeratorContentControlLabels(
        title = "Lock this item?",
        confirm = "Lock",
        description = "New replies will be blocked until a moderator unlocks this item.",
        reversal = "You can reverse this with Unlock after Reddit confirms the locked state.",
    )
    RedditModeratorAction.UNLOCK -> ModeratorContentControlLabels(
        title = "Unlock this item?",
        confirm = "Unlock",
        description = "New replies can be added again after Reddit confirms the unlocked state.",
        reversal = "You can lock the item again later.",
    )
    RedditModeratorAction.DISTINGUISH -> ModeratorContentControlLabels(
        title = "Distinguish as moderator?",
        confirm = "Distinguish",
        description = "This contribution will be marked as an official moderator contribution.",
        reversal = "Only your own contribution is eligible here. You can undistinguish it later.",
    )
    RedditModeratorAction.UNDISTINGUISH -> ModeratorContentControlLabels(
        title = "Remove moderator distinction?",
        confirm = "Undistinguish",
        description = "The moderator distinction will be removed from this contribution.",
        reversal = "You can distinguish your own contribution again later.",
    )
    else -> error("Unsupported moderator content control action")
}

@Composable
private fun ModeratorControlTargetCard(
    item: RedditModeratorQueueItem,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                item.summary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "r/${item.communityName} · u/${item.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                item.fullname,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeratorControlStateBadge(
    label: String,
    known: Boolean,
    iconLocked: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color =
            if (known) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (known) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
        // LOWUI7_NO_DOUBLE_READOUTS_V1 (OCB-146): the merged label is read once.
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (iconLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ModeratorDistinguishStateBadge(
    distinguished: Boolean?,
) {
    val label = when (distinguished) {
        true -> "Distinguished as moderator"
        false -> "Not distinguished"
        null -> "Distinguish state unavailable"
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color =
            if (distinguished == null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        contentColor =
            if (distinguished == null) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ModeratorContentControlNoticeCard(
    notice: ModeratorContentControlsNotice,
    modifier: Modifier = Modifier,
) {
    val verified =
        notice == ModeratorContentControlsNotice.VERIFIED ||
            notice == ModeratorContentControlsNotice.VERIFIED_AFTER_UNKNOWN
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color =
            if (verified) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (verified) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (verified) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                moderatorContentControlNoticeText(notice),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun moderatorContentControlNoticeText(
    notice: ModeratorContentControlsNotice,
): String = when (notice) {
    ModeratorContentControlsNotice.NOT_FOUND ->
        "Reddit did not return the exact target."
    ModeratorContentControlsNotice.READ_FAILED ->
        "Could not read current moderator controls. Refresh before acting."
    ModeratorContentControlsNotice.NO_PERMISSION ->
        "Reddit did not confirm Posts moderation permission for this target."
    ModeratorContentControlsNotice.STATE_UNKNOWN ->
        "Reddit did not return a usable lock or distinction state."
    ModeratorContentControlsNotice.STATE_CHANGED ->
        "The target changed since you opened the confirmation. Review the current state."
    ModeratorContentControlsNotice.BUSY ->
        "Another moderation action is in progress. This action was not queued."
    ModeratorContentControlsNotice.AUTHORIZATION_REQUIRED ->
        "Moderator authorization is required. Refresh and confirm again after authorizing."
    ModeratorContentControlsNotice.WRITE_FAILED ->
        "The moderation action could not be completed. Refresh before making another choice."
    ModeratorContentControlsNotice.VERIFIED ->
        "The requested state was confirmed by a fresh Reddit response."
    ModeratorContentControlsNotice.VERIFIED_AFTER_UNKNOWN ->
        "The write response was unclear, but a fresh Reddit response confirms the requested state."
    ModeratorContentControlsNotice.NOT_CONFIRMED ->
        "The requested state could not be confirmed. No automatic retry was sent."
    ModeratorContentControlsNotice.ACCOUNT_CHANGED ->
        "The active moderator account changed. Reopen the controls with the intended account."
}
