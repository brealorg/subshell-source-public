package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.COMPOSER_FLAIR_TEXT_MAX_LENGTH
import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.ui.symbols.Symbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostFlairPickerSheet(
    required: Boolean,
    options: List<ComposerFlair>,
    selected: ComposerFlair?,
    onSelect: (ComposerFlair) -> Unit,
    onDismiss: () -> Unit,
    // LOWUX3_POST_FLAIR_CAN_BE_REMOVED_V1 (OCB-233): where flair is optional and
    // set, "No flair" removes it.
    onRemove: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            // COMPOSER07_FLAIR_PICKER_SCROLL_AND_LIMITS_V1: a long flair list scrolls.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose post flair", style = MaterialTheme.typography.headlineSmall)
            if (options.isEmpty()) {
                Text(
                    if (required) "This community requires flair, but no selectable flair was returned."
                    else "No post flair is available for this community.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (onRemove != null && !required && selected != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = onRemove,
                    ) {
                        Text("No flair", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
                    }
                }
                options.forEach { option ->
                    var editedText by remember(option.templateId, selected?.text) {
                        mutableStateOf(if (selected?.templateId == option.templateId) selected.text else option.text)
                    }
                    val isSelected = selected?.templateId == option.templateId
                    // ISSUE219_FLAIR_PICKER_SAYS_WHICH_IS_SELECTED_V1: selection was colour
                    // only; the selectable Surface announces it and a check shows it.
                    Surface(
                        selected = isSelected,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        onClick = { onSelect(option.copy(text = postFlairSelectionText(option, editedText))) },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    option.text.ifBlank { "Editable flair" },
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium,
                                )
                                if (isSelected) {
                                    Icon(Symbols.Check, contentDescription = null)
                                }
                            }
                            if (option.editable) {
                                OutlinedTextField(
                                    value = editedText,
                                    onValueChange = {
                                        if (it.length <= COMPOSER_FLAIR_TEXT_MAX_LENGTH) editedText = it
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    label = { Text("Flair text") },
                                    supportingText = {
                                        Text("${editedText.length} / $COMPOSER_FLAIR_TEXT_MAX_LENGTH")
                                    },
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}

// COMPOSER07_FLAIR_PICKER_SCROLL_AND_LIMITS_V1: Reddit's limit is 64 characters
// (as in the composer); the text is sent trimmed, and an editable flair left
// blank keeps the flair's own text.
internal fun postFlairSelectionText(option: ComposerFlair, editedText: String): String {
    if (!option.editable) return option.text
    return editedText.trim().take(COMPOSER_FLAIR_TEXT_MAX_LENGTH).ifBlank { option.text.trim() }
}
