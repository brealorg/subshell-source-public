package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.applyMarkdownAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import app.subshell.prototype.data.moderation.ModeratorSavedResponse
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditRemovalMessage
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.account.RedditRemovalReason

// MODRESPONSE01A2_REMOVAL_RESPONSE_DRAFT_V1
/**
 * The moderator's draft of the removal response inside the remove dialog.
 * Off by default: selecting a reason never sends anything. The body is
 * pre-filled from the selected reason until the moderator edits it; an
 * edited body is kept when the reason changes.
 */
internal class RemovalResponseDraft(
    initialType: RedditRemovalMessageType = RedditRemovalMessageType.PRIVATE,
) {
    var enabled by mutableStateOf(false)
    var type by mutableStateOf(initialType)
    var body by mutableStateOf("")
        private set
    var edited by mutableStateOf(false)
        private set

    fun edit(value: String) {
        body = value.take(RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS)
        edited = true
    }

    fun prefillFrom(reason: RedditRemovalReason?) {
        body = removalResponsePrefill(reason = reason, current = body, edited = edited)
    }

    fun useTemplate(text: String) {
        body = text.take(RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS)
        edited = true
    }

    /** The message to send, or null when the moderator did not ask for one. */
    fun toMessageOrNull(reason: RedditRemovalReason?): RedditRemovalMessage? =
        removalResponseMessageOrNull(enabled = enabled, type = type, body = body, reason = reason)

    val blocksConfirm: Boolean
        get() = enabled && body.isBlank()
}

internal fun removalResponsePrefill(
    reason: RedditRemovalReason?,
    current: String,
    edited: Boolean,
): String = if (edited) current else reason?.message.orEmpty()

internal fun removalResponseMessageOrNull(
    enabled: Boolean,
    type: RedditRemovalMessageType,
    body: String,
    reason: RedditRemovalReason?,
): RedditRemovalMessage? {
    if (!enabled) return null
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return null
    return RedditRemovalMessage(
        type = type,
        body = trimmed,
        title = (reason?.title ?: "Removed").take(RedditRemovalMessage.MAX_REMOVAL_MESSAGE_TITLE_CHARS),
    )
}

internal fun removalResponseTypeLabel(type: RedditRemovalMessageType): String = when (type) {
    RedditRemovalMessageType.PUBLIC -> "Public comment as you"
    RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT -> "Public comment as the subreddit"
    RedditRemovalMessageType.PRIVATE -> "Private modmail (your name hidden)"
    RedditRemovalMessageType.PRIVATE_EXPOSED -> "Private modmail (your name shown)"
}

internal fun removalResponseTypeDescription(
    type: RedditRemovalMessageType,
    kind: RedditModeratorQueueKind,
): String = when (type) {
    RedditRemovalMessageType.PUBLIC,
    RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT,
    -> when (kind) {
        RedditModeratorQueueKind.POST -> "Reddit posts it as a distinguished, stickied comment."
        RedditModeratorQueueKind.COMMENT -> "Reddit posts it as a distinguished reply."
    }
    RedditRemovalMessageType.PRIVATE,
    RedditRemovalMessageType.PRIVATE_EXPOSED,
    -> "Sent from the subreddit's modmail."
}

// MODRESPONSE01A3_SAVED_RESPONSES_PICKER_V1
/** Saved responses the picker offers: explicit choice, editable after use. */
data class SavedResponseActions(
    val responses: List<ModeratorSavedResponse>,
    val onSave: (title: String, body: String) -> ModeratorSavedResponse?,
    val onDelete: (id: String) -> Unit,
    // MODRESPONSE01A5_LAST_USED_MESSAGE_TYPE_V1
    val initialType: RedditRemovalMessageType = RedditRemovalMessageType.PRIVATE,
    val onTypeUsed: (RedditRemovalMessageType) -> Unit = {},
)

// MODRESPONSE01A2R2_EDITOR_OUT_OF_THE_ALERT_DIALOG_V1
// A text field inside a wrap-content AlertDialog with a scrolling body jumps
// on every keyboard height change (the dialog re-measures, the scroll state
// clamps). The remove dialog therefore only summarizes the response; the
// text is edited in a full-screen editor whose field fills the remaining
// height under imePadding, so nothing re-centres or clamps while typing.
@Composable
internal fun RemovalResponseSection(
    draft: RemovalResponseDraft,
    kind: RedditModeratorQueueKind,
    modifier: Modifier = Modifier,
    savedResponses: SavedResponseActions? = null,
) {
    var editorOpen by remember(draft) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1 (OCB-161)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = draft.enabled,
                    role = Role.Switch,
                    onValueChange = { checked ->
                        draft.enabled = checked
                        if (checked) editorOpen = true
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Send a removal message", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Optional. You review it before anything is sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.enabled,
                onCheckedChange = null,
            )
        }
        if (!draft.enabled) return@Column
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editorOpen = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(removalResponseTypeLabel(draft.type), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = draft.body.trim().ifEmpty { "No message yet — tap to write one." },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draft.blocksConfirm) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { editorOpen = true }) {
                Text(if (draft.body.isBlank()) "Write" else "Edit")
            }
        }
    }
    if (editorOpen) {
        RemovalResponseEditor(
            draft = draft,
            kind = kind,
            savedResponses = savedResponses,
            onDone = { editorOpen = false },
        )
    }
}

@Composable
internal fun RemovalResponseEditor(
    draft: RemovalResponseDraft,
    kind: RedditModeratorQueueKind,
    savedResponses: SavedResponseActions?,
    onDone: () -> Unit,
) {
    var saveTitle by remember(draft) { mutableStateOf<String?>(null) }
    var pickerOpen by remember(draft) { mutableStateOf(false) }
    // LOWUI6_SAVED_RESPONSE_DELETE_IS_CONFIRMED_V1 (OCB-163): the trash sits
    // next to the row that applies the response, and a delete is final.
    var pendingDelete by remember(draft) { mutableStateOf<ModeratorSavedResponse?>(null) }
    var typeMenuOpen by remember(draft) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // COMPOSE01A1_SHARED_EDITOR_V1: the shared Markdown field/dock edit a
    // TextFieldState; the draft mirrors it so the dialog can build the message.
    val editor = rememberTextFieldState(initialText = draft.body)
    LaunchedEffect(editor) {
        snapshotFlow { editor.text.toString() }.collect { text ->
            // LOWUI8_REMOVAL_MESSAGE_FIELD_STOPS_AT_THE_LIMIT_V1 (OCB-162): the
            // draft keeps 10 000 characters; the field is cut to match, so
            // what it shows is what is sent.
            val limit = RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS
            if (text.length > limit) {
                editor.edit { replace(limit, length, "") }
                return@collect
            }
            if (text != draft.body) draft.edit(text)
        }
    }
    fun applyFormat(action: MarkdownAction) {
        val edit = applyMarkdownAction(
            text = editor.text.toString(),
            selectionStart = editor.selection.start,
            selectionEnd = editor.selection.end,
            action = action,
        )
        editor.edit {
            replace(0, length, edit.text)
            selection = TextRange(edit.selectionStart, edit.selectionEnd)
        }
    }
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // A card with margins, not the whole screen. Its height is bounded
        // (not wrap-content) and the field takes the remaining space, so a
        // keyboard height change only shortens the field from the bottom.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(12.dp)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Removal message",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    ComposerPrimaryAction(text = "Done", enabled = true, onClick = onDone)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Send as", style = MaterialTheme.typography.labelMedium)
                        Text(removalResponseTypeLabel(draft.type), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            removalResponseTypeDescription(draft.type, kind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        TextButton(onClick = { typeMenuOpen = true }) { Text("Change") }
                        DropdownMenu(
                            expanded = typeMenuOpen,
                            onDismissRequest = { typeMenuOpen = false },
                        ) {
                            RedditRemovalMessageType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(removalResponseTypeLabel(type))
                                            Text(
                                                removalResponseTypeDescription(type, kind),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        draft.type = type
                                        savedResponses?.onTypeUsed?.invoke(type)
                                        typeMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (savedResponses != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { pickerOpen = true },
                            enabled = savedResponses.responses.isNotEmpty(),
                        ) {
                            Text(
                                if (savedResponses.responses.isEmpty()) "No saved responses" else "Use saved response…",
                            )
                        }
                        TextButton(
                            onClick = { saveTitle = "" },
                            enabled = draft.body.isNotBlank(),
                        ) {
                            Text("Save as response…")
                        }
                    }
                }
                PostStyleComposerEditor(
                    state = editor,
                    placeholder = "Message",
                    focusRequester = focusRequester,
                    onFormat = ::applyFormat,
                    onMediaReceived = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    minLines = 4,
                    maxLines = Int.MAX_VALUE,
                )
                Text(
                    "${draft.body.length}/${RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS}" +
                        " · Reddit adds the removed item's details.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (
                        draft.blocksConfirm ||
                        draft.body.length >= RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                ComposerFormattingDock(
                    state = editor,
                    onFormat = ::applyFormat,
                    onReturnToEditor = { focusRequester.requestFocus() },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    if (savedResponses != null && pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Saved responses") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(savedResponses.responses, key = { it.id }) { response ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft.useTemplate(response.body)
                                    editor.edit { replace(0, length, draft.body) }
                                    pickerOpen = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(response.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    response.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { pendingDelete = response }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete ${response.title}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Close") }
            },
        )
    }
    val deleting = pendingDelete
    if (savedResponses != null && deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${deleting.title}\"?") },
            text = { Text("This saved response is removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        savedResponses.onDelete(deleting.id)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
    if (savedResponses != null && saveTitle != null) {
        SaveAsResponseDialog(
            title = saveTitle.orEmpty(),
            onTitleChange = { saveTitle = it },
            body = draft.body,
            savedResponses = savedResponses,
            onClose = { saveTitle = null },
        )
    }
}

/** STEP3: Save needs a title and some message text. */
internal fun saveAsResponseEnabled(title: String, body: String): Boolean =
    title.isNotBlank() && body.isNotBlank()

/**
 * STEP3: saves the draft under [title]; true when the dialog may close, false
 * when the list is full and the dialog has to stay and say so.
 */
internal fun saveAsResponse(title: String, body: String, savedResponses: SavedResponseActions): Boolean =
    savedResponses.onSave(title, body) != null

/**
 * STEP3: the "Save as response" dialog, out of RemovalResponseEditor. Its decisions
 * are [saveAsResponseEnabled] and [saveAsResponse], tested directly: a Material
 * AlertDialog holding an OutlinedTextField never idles under Robolectric. [onClose] runs after a save or a dismiss; a save refused
 * because the list is full keeps the dialog open and says why.
 */
@Composable
internal fun SaveAsResponseDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    savedResponses: SavedResponseActions,
    onClose: () -> Unit,
) {
    val pendingTitle = title
    // LOWUX1_SAVE_AS_RESPONSE_SAYS_WHEN_IT_IS_FULL_V1 (OCB-164): a new title
    // past the 50-response cap was refused while the dialog closed as saved;
    // the dialog stays and says why.
    var saveRefused by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {
            saveRefused = false
            onClose()
        },
        title = { Text("Save as response") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pendingTitle,
                    onValueChange = { onTitleChange(it.take(ModeratorSavedResponse.MAX_TITLE_CHARS)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
                )
                if (saveRefused) {
                    Text(
                        "You have ${ModeratorSavedResponse.MAX_RESPONSES} saved responses. " +
                            "Delete one, or use the title of one to replace it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Saves the current message text for reuse on this device. A response with the same title is replaced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (saveAsResponse(pendingTitle, body, savedResponses)) {
                        saveRefused = false
                        onClose()
                    } else {
                        saveRefused = true
                    }
                },
                enabled = saveAsResponseEnabled(pendingTitle, body),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel") }
        },
    )
}
