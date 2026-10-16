package app.subshell.prototype.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// COMPOSE01A1_SHARED_MARKDOWN_PREVIEW_V1
/**
 * The rendered-Markdown preview every composer shows behind its preview
 * toggle: the same card the post composer has used, now shared.
 */
@Composable
fun MarkdownComposerPreview(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            if (text.isBlank()) {
                Text(
                    "Nothing to preview yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MarkdownText(text = text)
            }
        }
    }
}
