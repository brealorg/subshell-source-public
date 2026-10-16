package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// SUBSHELL_COMPOSE02_PRIMARY_ACTIONS_V1
// One primary-action family for every compose surface: a filled Button with
// a verb in top bars (Create post, Crosspost, Edit post, Edit comment), and
// the filled send icon in docked composers (CompactReplyComposer). Discard
// is a secondary, error-coloured text action and only exists where a draft
// survives closing the surface.

/** Filled top-bar primary action; [text] is the verb or its progress form. */
@Composable
fun ComposerPrimaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.padding(end = 8.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Text(text)
    }
}

/** Secondary, error-coloured "Discard" for surfaces that keep a draft. */
@Composable
fun ComposerDiscardAction(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ISSUE222_DISABLED_DISCARD_LOOKS_DISABLED_V1: the colour goes through the
    // button colours, so a disabled Discard is greyed out instead of staying red.
    TextButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Discard")
    }
}

// SUBSHELL_COMPOSE05_EDIT_DISCARD_CONFIRM_V1
/**
 * Whether closing an edit surface must ask first: the edited text differs
 * from the original once surrounding whitespace is ignored (the same
 * normalisation the Save button uses).
 */
fun editHasUnsavedChanges(original: String, current: String): Boolean =
    original.trim() != current.trim()

/**
 * "Discard changes?" for the edit surfaces, which keep no draft: closing
 * with unsaved edits asks first, like Create post's discard confirmation.
 */
@Composable
fun ComposerDiscardChangesDialog(
    subject: String,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard changes?") },
        text = { Text("Your edits to this $subject will be lost.") },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text("Keep editing") }
        },
    )
}
