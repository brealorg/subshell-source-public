package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.symbols.Symbols

// CONTENTFILTER04_FILTERED_SUMMARY_V1: optional (Settings, off by default).
// Says how many loaded posts the content filters hide in this feed; Show
// lets them through for the rest of the visit, Hide filters them again.
@Composable
internal fun FilteredPostsSummaryRow(
    filteredCount: Int,
    revealed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Symbols.FilterAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = filteredPostsSummaryText(filteredCount, revealed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggle) {
                Text(if (revealed) "Hide" else "Show")
            }
        }
    }
}

internal fun filteredPostsSummaryText(filteredCount: Int, revealed: Boolean): String {
    val posts = if (filteredCount == 1) "1 post" else "$filteredCount posts"
    return if (revealed) "Showing $posts your filters hide" else "$posts hidden by your filters"
}
