package app.subshell.prototype.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.subshell.prototype.ui.error.redditReadHttpReason
import app.subshell.prototype.ui.error.redditReadHttpStatusHasOwnReason
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.ComposerInputMode
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.applyMarkdownAction
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailMessage
import app.subshell.prototype.data.account.RedditModmailParticipantMuteStatus
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiFailureKind
import app.subshell.prototype.ui.symbols.Symbols
import java.time.Duration
import java.time.Instant
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.ui.semantics.role

// MODMAIL01A1_READ_ONLY_INBOX_PRESENTATION_V1
// MODMAIL02_MODERATOR_THREAD_PRESENTATION_AND_COMPOSER_V1
// MODMAIL02_COMPACT_THREAD_COMPOSER_V2
// MODMAIL02_INLINE_COMPOSER_DOCK_V3
// MODMAIL02_CANONICAL_COMPOSER_BRIDGE_V4
// MODMAIL03A1_REPLY_AS_SUBREDDIT_V1
// MODMAIL03A1R1_EXPLICIT_IDENTITY_SEGMENT_V1
// MODMAIL03A1R2_REDDIT_SEMANTICS_POLISH_V1
// MODMAIL04A1_ARCHIVE_UNARCHIVE_PRESENTATION_V1
// MODMAIL04A1R1_CLICKABLE_VERIFY_STATUS_V1
// MODMAIL05_TRIAGE_PRESENTATION_V1
// MODMAIL05R1_QUEUE_SELECTOR_V1
// MODMAIL05R2_MATERIAL_BOTTOM_SHEET_QUEUE_SELECTOR_V1
// MODMAIL05R3_FULLY_EXPANDED_POLISHED_QUEUE_SHEET_V1
// MODMAIL06_READ_UNREAD_PRESENTATION_V1
// SUBSHELL_INBOXM3A1_MODMAIL_EXPRESSIVE_PRESENTATION_PREVIEW_V1
// SUBSHELL_INBOXM3A1R1_MODMAIL_VISUAL_REFINEMENT_PREVIEW_V1
// MODMAIL07A1_SELECTED_CONVERSATION_CONTINUITY_V1
@Composable
fun InboxModmailConversationRow(
    conversation: RedditModmailConversation,
    communityIconUri: String? = null,
    selected: Boolean = false,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            // STEP3_MODMAIL_ROW_SAYS_IT_IS_SELECTED_V1 (owner-approved): the
            // open conversation is announced as selected, not only coloured.
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.large,
        color = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            conversation.unread -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            )
        } else {
            null
        },
        onClick = onOpen,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                CommunityAvatar(
                    name = conversation.communityName,
                    accent = MaterialTheme.colorScheme.primary,
                    iconImageUri = communityIconUri,
                    modifier = Modifier.size(40.dp),
                )
            },
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = conversation.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (conversation.unread) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.unread) {
                        Badge { Text("New") }
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = buildString {
                            append("r/")
                            append(conversation.communityName)
                            conversation.participantName
                                ?.takeIf(String::isNotBlank)
                                ?.let { participant ->
                                    append(" · u/")
                                    append(participant.removePrefix("u/"))
                                }
                            if (conversation.isInternal) append(" · Internal")
                            if (conversation.highlighted) append(" · Highlighted")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (conversation.preview.isNotBlank()) {
                        Text(
                            text = conversation.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    text = modmailRelativeAgeLabel(conversation.lastUpdatedEpochSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
fun InboxModmailThreadHeader(
    conversation: RedditModmailConversation,
    participantMuteStatus: RedditModmailParticipantMuteStatus? = null,
    communityIconUri: String? = null,
    archiveInProgress: Boolean = false,
    archiveOutcomeUnknown: Boolean = false,
    readStateInProgress: Boolean = false,
    readStateOutcomeUnknown: Boolean = false,
    onSetArchived: (Boolean) -> Unit = {},
    onSetUnread: (Boolean) -> Unit = {},
    onTriageAction: (RedditModmailTriageAction) -> Unit = {},
    onVerifyArchived: () -> Unit = {},
    onVerifyReadState: () -> Unit = {},
    showBackButton: Boolean = true,
    onBack: () -> Unit,
) {
    var triageMenuExpanded by rememberSaveable(conversation.id) {
        mutableStateOf(false)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        Symbols.ArrowBack,
                        contentDescription = "Back to Modmail conversations",
                    )
                }
            }
            CommunityAvatar(
                name = conversation.communityName,
                accent = MaterialTheme.colorScheme.primary,
                iconImageUri = communityIconUri,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.subject,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("r/")
                        append(conversation.communityName)
                        conversation.participantName
                            ?.takeIf(String::isNotBlank)
                            ?.let { participant ->
                                append(" · u/")
                                append(participant.removePrefix("u/"))
                            }
                        if (conversation.archived) append(" · Archived")
                        if (conversation.highlighted) append(" · Highlighted")
                        if (participantMuteStatus?.muted == true) append(" · Muted")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                archiveInProgress || readStateInProgress -> {
                    InlineProgressIndicator(modifier = Modifier.padding(horizontal = 14.dp))
                }
                readStateOutcomeUnknown -> {
                    TextButton(onClick = onVerifyReadState) {
                        Text("Refresh to verify")
                    }
                }
                archiveOutcomeUnknown -> {
                    TextButton(onClick = onVerifyArchived) {
                        Text("Refresh to verify")
                    }
                }
                else -> {
                    if (!conversation.isInternal) {
                        TextButton(
                            onClick = {
                                onSetArchived(!conversation.archived)
                            },
                        ) {
                            Text(if (conversation.archived) "Unarchive" else "Archive")
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { triageMenuExpanded = true },
                        ) {
                            Icon(
                                Symbols.MoreVert,
                                contentDescription = "Modmail triage actions",
                            )
                        }
                        DropdownMenu(
                            expanded = triageMenuExpanded,
                            onDismissRequest = { triageMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (conversation.unread) "Mark read" else "Mark unread")
                                },
                                onClick = {
                                    triageMenuExpanded = false
                                    onSetUnread(!conversation.unread)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (conversation.highlighted) {
                                            "Remove highlight"
                                        } else {
                                            "Highlight"
                                        },
                                    )
                                },
                                onClick = {
                                    triageMenuExpanded = false
                                    onTriageAction(
                                        if (conversation.highlighted) {
                                            RedditModmailTriageAction.UNHIGHLIGHT
                                        } else {
                                            RedditModmailTriageAction.HIGHLIGHT
                                        },
                                    )
                                },
                            )
                            if (
                                !conversation.isInternal &&
                                !conversation.participantName.isNullOrBlank()
                            ) {
                                if (participantMuteStatus?.muted == true) {
                                    DropdownMenuItem(
                                        text = { Text("Unmute user") },
                                        onClick = {
                                            triageMenuExpanded = false
                                            onTriageAction(RedditModmailTriageAction.UNMUTE)
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Mute user · 3 days") },
                                        onClick = {
                                            triageMenuExpanded = false
                                            onTriageAction(RedditModmailTriageAction.MUTE_3_DAYS)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mute user · 7 days") },
                                        onClick = {
                                            triageMenuExpanded = false
                                            onTriageAction(RedditModmailTriageAction.MUTE_7_DAYS)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mute user · 28 days") },
                                        onClick = {
                                            triageMenuExpanded = false
                                            onTriageAction(RedditModmailTriageAction.MUTE_28_DAYS)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxModmailQueueFilters(
    selectedMode: RedditModmailListMode,
    enabled: Boolean,
    onSelect: (RedditModmailListMode) -> Unit,
) {
    var sheetVisible by rememberSaveable {
        mutableStateOf(false)
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val queueListState = rememberLazyListState()

    // M3225_SELECT_FIELDS_ARE_DROPDOWNS_V1 (#225, modinbox-08): the queue field
    // says it is a dropdown list and dims like an M3 disabled control; the
    // choice itself stays in a sheet (MODMAIL05R2).
    Surface(
        onClick = { sheetVisible = true },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { role = Role.DropdownList },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = if (enabled) 0.78f else 1f),
                )
                Text(
                    text = selectedMode.modmailQueueLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Symbols.ArrowDropDown,
                contentDescription = "Choose Modmail queue",
            )
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(
            modifier = Modifier.fillMaxHeight(),
            sheetState = sheetState,
            onDismissRequest = {
                sheetVisible = false
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = "Modmail queue",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Choose which Reddit queue to view",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .selectableGroup(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 80.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = RedditModmailListMode.entries,
                            key = { mode -> mode.name },
                        ) { mode ->
                            val selected = selectedMode == mode
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    // ISSUE219_MODMAIL_QUEUE_SAYS_WHICH_IS_SELECTED_V1: each
                                    // queue is a radio row (LOWUI7), not a plain clickable.
                                    .selectable(
                                        selected = selected,
                                        enabled = enabled,
                                        role = Role.RadioButton,
                                    ) {
                                        sheetVisible = false
                                        if (!selected) {
                                            onSelect(mode)
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = mode.modmailQueueLabel(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (selected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selected) {
                                        Icon(
                                            imageVector = Symbols.Check,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (queueListState.canScrollForward) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "More queues below",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Icon(
                                    imageVector = Symbols.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


internal fun RedditModmailListMode.modmailQueueLabel(): String = when (this) {
    RedditModmailListMode.ACTIVE -> "Active"
    RedditModmailListMode.NEW -> "New"
    RedditModmailListMode.IN_PROGRESS -> "In progress"
    RedditModmailListMode.FILTERED -> "Filtered"
    RedditModmailListMode.HIGHLIGHTED -> "Highlighted"
    RedditModmailListMode.ARCHIVED -> "Archived"
    RedditModmailListMode.APPEALS -> "Appeals"
    RedditModmailListMode.JOIN_REQUESTS -> "Join requests"
    RedditModmailListMode.MOD_DISCUSSIONS -> "Mod discussions"
    RedditModmailListMode.NOTIFICATIONS -> "Notifications"
}

internal fun RedditModmailListMode.modmailEmptyTitle(): String = when (this) {
    RedditModmailListMode.ACTIVE -> "Modmail is clear"
    RedditModmailListMode.NEW -> "No new Modmail"
    RedditModmailListMode.IN_PROGRESS -> "No Modmail in progress"
    RedditModmailListMode.FILTERED -> "No filtered Modmail"
    RedditModmailListMode.HIGHLIGHTED -> "No highlighted Modmail"
    RedditModmailListMode.ARCHIVED -> "No archived Modmail"
    RedditModmailListMode.APPEALS -> "No Modmail appeals"
    RedditModmailListMode.JOIN_REQUESTS -> "No join requests"
    RedditModmailListMode.MOD_DISCUSSIONS -> "No mod discussions"
    RedditModmailListMode.NOTIFICATIONS -> "No Modmail notifications"
}

internal fun RedditModmailListMode.modmailEmptySupportingText(): String = when (this) {
    RedditModmailListMode.ACTIVE -> "No active Modmail conversations are available."
    RedditModmailListMode.NEW -> "New conversations will appear here."
    RedditModmailListMode.IN_PROGRESS -> "Conversations being handled will appear here."
    RedditModmailListMode.FILTERED -> "Reddit-filtered conversations will appear here."
    RedditModmailListMode.HIGHLIGHTED -> "Highlighted conversations will appear here."
    RedditModmailListMode.ARCHIVED -> "Archived conversations will appear here."
    RedditModmailListMode.APPEALS -> "Appeal conversations will appear here."
    RedditModmailListMode.JOIN_REQUESTS -> "Join-request conversations will appear here."
    RedditModmailListMode.MOD_DISCUSSIONS -> "Internal moderator discussions will appear here."
    RedditModmailListMode.NOTIFICATIONS -> "Moderator notifications will appear here."
}

@Composable
fun InboxModmailMessageRow(
    message: RedditModmailMessage,
    communityName: String,
    authorTag: UserTag? = null,
    authorAvatarUri: String? = null,
    communityIconUri: String? = null,
) {
    val authorLabel = modmailMessageAuthorLabel(message, communityName)
    // ISSUE34_USER_TAGS_V1: only next to a person, never the community label.
    val shownAuthorTag = authorTag?.takeIf { authorLabel.startsWith("u/") }
    val semanticRole = modmailMessageSemanticRole(message)
    val roleBorder = when {
        message.isInternal -> BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
        )
        message.authorHidden -> BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
        )
        else -> null
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.large,
        color = if (message.isInternal || message.authorHidden) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = roleBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (message.authorHidden) {
                CommunityAvatar(
                    name = communityName,
                    accent = MaterialTheme.colorScheme.primary,
                    iconImageUri = communityIconUri,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                ModmailUserAvatar(
                    username = message.authorName,
                    avatarUri = authorAvatarUri,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        NameWithUserTag(tag = shownAuthorTag) {
                            Text(
                                text = authorLabel,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        semanticRole?.let { role ->
                            Text(
                                text = role,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    message.isInternal -> MaterialTheme.colorScheme.tertiary
                                    message.authorHidden -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Text(
                        text = modmailRelativeAgeLabel(message.createdEpochSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MarkdownText(text = message.body)
            }
        }
    }
}

internal fun modmailMessageAuthorLabel(
    message: RedditModmailMessage,
    communityName: String,
): String {
    val role = message.participatingAs.orEmpty().lowercase()
    return when {
        message.authorHidden -> "r/$communityName"
        role.contains("subreddit") -> "r/$communityName"
        message.authorName.startsWith("u/") -> message.authorName
        else -> "u/${message.authorName}"
    }
}

internal fun modmailMessageSemanticRole(message: RedditModmailMessage): String? {
    val role = message.participatingAs.orEmpty().lowercase()
    return when {
        message.isInternal -> "Internal note · moderators only"
        message.authorHidden -> {
            val moderator = message.authorName.removePrefix("u/")
            if (moderator.isBlank() || moderator == "[deleted]") {
                "Hidden moderator reply"
            } else {
                "Sent by u/$moderator · hidden from participant"
            }
        }
        role.contains("subreddit") || role.contains("moderator") || role == "mod" ->
            "Moderator reply"
        role.contains("participant") || role.contains("user") -> null
        role.isBlank() -> null
        else -> null
    }
}

@Composable
fun InboxModmailFailure(
    failureKind: RedditModmailUiFailureKind,
    httpStatusCode: Int?,
    retryable: Boolean,
    onRetry: () -> Unit,
) {
    val message = when (failureKind) {
        RedditModmailUiFailureKind.CONFIGURATION -> "Modmail is unavailable for this Reddit configuration."
        RedditModmailUiFailureKind.ACCOUNT_CHANGED -> "The active Reddit account changed."
        RedditModmailUiFailureKind.AUTHORIZATION -> "Reddit Modmail permission is required."
        RedditModmailUiFailureKind.SESSION -> "The Reddit session for this account needs attention."
        // ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1
        RedditModmailUiFailureKind.HTTP ->
            if (httpStatusCode != null && !redditReadHttpStatusHasOwnReason(httpStatusCode)) {
                "Reddit returned HTTP $httpStatusCode while loading Modmail."
            } else {
                redditReadHttpReason(httpStatusCode, "Reddit rejected the Modmail request.")
            }
        RedditModmailUiFailureKind.TRANSPORT -> "Couldn’t reach Reddit Modmail."
        RedditModmailUiFailureKind.PROTOCOL -> "Reddit returned an unexpected Modmail response."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (retryable) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun ModmailUserAvatar(
    username: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
) {
    val fallback = username
        .removePrefix("u/")
        .firstOrNull()
        ?.uppercase()
        ?: "?"
    RemoteIdentityImage(
        uri = avatarUri,
        contentDescription = "Avatar for u/${username.removePrefix("u/")}",
        modifier = modifier.clip(CircleShape),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallback,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModmailReplyIdentitySelector(
    moderatorUsername: String,
    moderatorAvatarUri: String?,
    communityName: String,
    communityIconUri: String?,
    replyAsSubreddit: Boolean,
    enabled: Boolean,
    onReplyAsUser: () -> Unit,
    onReplyAsSubreddit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // M3225_SELECT_FIELDS_ARE_DROPDOWNS_V1 (#225, modinbox-08): an M3 exposed
    // dropdown (a non-editable field with an anchored menu).
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (replyAsSubreddit) {
                "r/$communityName mod team"
            } else {
                "u/${moderatorUsername.removePrefix("u/")}"
            },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Replying as") },
            leadingIcon = {
                if (replyAsSubreddit) {
                    CommunityAvatar(
                        name = communityName,
                        accent = MaterialTheme.colorScheme.primary,
                        iconImageUri = communityIconUri,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    ModmailUserAvatar(
                        username = moderatorUsername,
                        avatarUri = moderatorAvatarUri,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                leadingIcon = {
                    ModmailUserAvatar(
                        username = moderatorUsername,
                        avatarUri = moderatorAvatarUri,
                        modifier = Modifier.size(28.dp),
                    )
                },
                text = {
                    Column {
                        Text("u/${moderatorUsername.removePrefix("u/")}")
                        Text(
                            "Reply as myself",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onReplyAsUser()
                },
                // ISSUE219_REPLY_IDENTITY_MENU_SAYS_WHICH_IS_SELECTED_V1
                modifier = Modifier.semantics { selected = !replyAsSubreddit },
            )
            DropdownMenuItem(
                leadingIcon = {
                    CommunityAvatar(
                        name = communityName,
                        accent = MaterialTheme.colorScheme.primary,
                        iconImageUri = communityIconUri,
                        modifier = Modifier.size(28.dp),
                    )
                },
                text = {
                    Column {
                        Text("r/$communityName mod team")
                        Text(
                            "Hide your username from the participant",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onReplyAsSubreddit()
                },
                modifier = Modifier.semantics { selected = replyAsSubreddit },
            )
        }
    }
}

internal enum class InboxModmailComposeMode {
    REPLY,
    INTERNAL_NOTE,
}

/**
 * Thin Modmail semantic adapter over the canonical shared reply composer.
 *
 * Layout, editor behavior, formatting controls, and IME behavior come from the same
 * CompactReplyComposer stack used by Messages and comments. Modmail only owns the
 * semantic mode switch and the transport arguments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxModmailComposer(
    conversation: RedditModmailConversation,
    moderatorUsername: String,
    moderatorAvatarUri: String? = null,
    communityIconUri: String? = null,
    sendInProgress: Boolean,
    sendOutcomeUnknown: Boolean,
    sendRevision: Long,
    onSend: (body: String, internal: Boolean, authorHidden: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sendBlocked: Boolean = false,
) {
    // ISSUE77_MODMAIL_DRAFT_SURVIVES_RECREATION_V1: the draft, like the mode
    // and identity below, belongs to its conversation and survives activity
    // recreation; an effect that ran on every entry used to wipe all three.
    val body = rememberSaveable(conversation.id, saver = TextFieldState.Saver) {
        TextFieldState()
    }
    var handledSendRevision by rememberSaveable(conversation.id) {
        mutableLongStateOf(sendRevision)
    }
    var modeName by rememberSaveable(conversation.id) {
        mutableStateOf(InboxModmailComposeMode.REPLY.name)
    }
    var replyAsSubreddit by rememberSaveable(conversation.id) {
        mutableStateOf(true)
    }
    val mode = runCatching { InboxModmailComposeMode.valueOf(modeName) }
        .getOrDefault(InboxModmailComposeMode.REPLY)

    // A send that succeeded in this conversation clears the draft once; the
    // handled revision is saved, so a recreation does not clear it again.
    LaunchedEffect(sendRevision) {
        if (sendRevision > handledSendRevision) {
            handledSendRevision = sendRevision
            body.edit {
                replace(0, length, "")
                selection = TextRange(0)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                InboxModmailComposeMode.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        selected = mode == candidate,
                        enabled = !sendInProgress,
                        onClick = { modeName = candidate.name },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = InboxModmailComposeMode.entries.size,
                        ),
                        icon = {},
                        label = {
                            Text(
                                if (candidate == InboxModmailComposeMode.REPLY) {
                                    "Reply"
                                } else {
                                    "Internal note"
                                },
                            )
                        },
                    )
                }
            }

            if (mode == InboxModmailComposeMode.INTERNAL_NOTE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                    ),
                ) {
                    Text(
                        text = "Visible to moderators only",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                ModmailReplyIdentitySelector(
                    moderatorUsername = moderatorUsername,
                    moderatorAvatarUri = moderatorAvatarUri,
                    communityName = conversation.communityName,
                    communityIconUri = communityIconUri,
                    replyAsSubreddit = replyAsSubreddit,
                    enabled = !sendInProgress,
                    onReplyAsUser = { replyAsSubreddit = false },
                    onReplyAsSubreddit = { replyAsSubreddit = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (sendOutcomeUnknown) {
                Text(
                    text = "Send outcome is unclear. Refresh this thread before sending again.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            CompactReplyComposer(
                state = body,
                media = null,
                // ISSUE78_MODMAIL_COMPOSER_LOCKED_WHILE_SENDING_V1: as the
                // comment reply (COMPOSER01): text typed after Send was erased
                // by the success clear.
                inputLocked = sendInProgress,
                placeholder = if (mode == InboxModmailComposeMode.INTERNAL_NOTE) {
                    "Internal note"
                } else {
                    "Reply"
                },
                sendEnabled = body.text.isNotBlank() &&
                    !sendInProgress &&
                    !sendBlocked &&
                    !sendOutcomeUnknown,
                onSend = {
                    onSend(
                        body.text.toString().trim(),
                        mode == InboxModmailComposeMode.INTERNAL_NOTE,
                        replyAsSubreddit &&
                            mode == InboxModmailComposeMode.REPLY,
                    )
                },
                onMediaReceived = {},
                onMediaRemoved = {},
                onPickImage = {},
                onPickGif = {},
                mediaEnabled = false,
                showMediaActions = false,
                longReplyTitle = if (mode == InboxModmailComposeMode.INTERNAL_NOTE) {
                    "Internal note"
                } else {
                    "Reply in r/${conversation.communityName}"
                },
                interactionActionsAtStart = true,
                maxCharacters = 10_000,
                sendContentDescription = when {
                    sendInProgress && mode == InboxModmailComposeMode.INTERNAL_NOTE ->
                        "Adding internal note"
                    sendInProgress -> "Sending Modmail reply"
                    mode == InboxModmailComposeMode.INTERNAL_NOTE -> "Add internal note"
                    else -> "Send Modmail reply"
                },
            )

            if (sendInProgress) {
                Text(
                    text = if (mode == InboxModmailComposeMode.INTERNAL_NOTE) {
                        "Adding internal note…"
                    } else {
                        "Sending reply…"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun modmailRelativeAgeLabel(
    epochSeconds: Long?,
    nowEpochSeconds: Long = Instant.now().epochSecond,
): String {
    if (epochSeconds == null || epochSeconds <= 0L) return ""
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
