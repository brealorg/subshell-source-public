package app.subshell.prototype.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.USER_TAG_LABEL_MAX_LENGTH
import app.subshell.prototype.ui.USER_TAG_NOTE_MAX_LENGTH
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.UserTagColor

// USERACTION01A1_PROFILE_BLOCK_CONFIRMATION_V1
@Composable
internal fun UserBlockConfirmationDialog(
    username: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block u/$username?") },
        text = {
            Text(
                "Reddit will add this account to your blocked users. " +
                    "You can unblock it again from this profile.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Block")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ISSUE34_USER_TAGS_V1
@Composable
internal fun UserTagEditorDialog(
    username: String,
    existing: UserTag?,
    onDismiss: () -> Unit,
    onSave: (UserTag?) -> Unit,
) {
    var label by rememberSaveable(username) { mutableStateOf(existing?.label.orEmpty()) }
    var colorName by rememberSaveable(username) {
        mutableStateOf((existing?.color ?: UserTagColor.GREY).name)
    }
    var note by rememberSaveable(username) { mutableStateOf(existing?.note.orEmpty()) }
    val color = UserTagColor.valueOf(colorName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Private note on u/$username") },
        text = {
            UserTagEditorFields(
                label = label,
                onLabelChange = { label = it },
                color = color,
                onColorChange = { colorName = it.name },
                note = note,
                onNoteChange = { note = it },
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        UserTag(
                            label = label,
                            color = color,
                            note = note,
                            // TAGCOLOR01: an unknown stored colour survives
                            // unless the user picked a colour.
                            unknownColorName = existing?.unknownColorName
                                ?.takeIf { color == existing.color },
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(
                        onClick = { onSave(null) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

/** The editor's fields, stateless: tag (one line, capped), colour swatches, note (capped). */
@Composable
internal fun UserTagEditorFields(
    label: String,
    onLabelChange: (String) -> Unit,
    color: UserTagColor,
    onColorChange: (UserTagColor) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Only you see this. It is kept for this account and never sent to Reddit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = label,
            onValueChange = { value ->
                onLabelChange(value.replace('\n', ' ').take(USER_TAG_LABEL_MAX_LENGTH))
            },
            label = { Text("Tag") },
            singleLine = true,
            supportingText = { Text("${label.length}/$USER_TAG_LABEL_MAX_LENGTH") },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserTagColor.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { option ->
                        val colors = userTagChipColors(option)
                        val selected = option == color
                        // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-165)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.container)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onColorChange(option) },
                                )
                                .semantics {
                                    contentDescription = userTagColorName(option)
                                },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { value -> onNoteChange(value.take(USER_TAG_NOTE_MAX_LENGTH)) },
            label = { Text("Note") },
            minLines = 3,
            maxLines = 6,
            supportingText = { Text("${note.length}/$USER_TAG_NOTE_MAX_LENGTH") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun userTagColorName(color: UserTagColor): String = when (color) {
    UserTagColor.GREY -> "Grey"
    UserTagColor.RED -> "Red"
    UserTagColor.ORANGE -> "Orange"
    UserTagColor.YELLOW -> "Yellow"
    UserTagColor.GREEN -> "Green"
    UserTagColor.TEAL -> "Teal"
    UserTagColor.BLUE -> "Blue"
    UserTagColor.PURPLE -> "Purple"
}
