package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import app.subshell.prototype.ui.symbols.Symbols

// COMPOSE01A1_SHARED_BODY_HEADER_V1
/**
 * The "Body · counter · Preview/Edit" row above every shared Markdown field,
 * as the post composer draws it. The counter appears once the text is within
 * 1 000 characters of [maxLength] and turns to the error colour past it.
 * [required] adds the post composer's "Required" hint; a null
 * [onTogglePreview] hides the toggle (wide layouts show both panes).
 */
@Composable
fun ComposerBodyHeader(
    title: String,
    length: Int,
    maxLength: Int,
    previewVisible: Boolean,
    onTogglePreview: (() -> Unit)?,
    modifier: Modifier = Modifier,
    required: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (required) {
            Text(
                "Required",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.weight(1f))
        if (length >= maxLength - 1_000) {
            Text(
                "$length / $maxLength",
                style = MaterialTheme.typography.labelSmall,
                color = if (length > maxLength) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onTogglePreview != null) {
            TextButton(onClick = onTogglePreview) {
                Icon(
                    if (previewVisible) Symbols.Edit else Symbols.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (previewVisible) "Edit" else "Preview")
            }
        }
    }
}

// SUBSHELL_COMPOSE03_CONTEXT_NOTE_V1
/**
 * The one-line note under a compose surface's top bar that says what is
 * being written and where ("Editing your comment in r/x", "Only the body of
 * this post is edited…"). Same style on every surface.
 */
@Composable
fun ComposerContextNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
