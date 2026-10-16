package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// M3225_ONE_INLINE_PROGRESS_INDICATOR_V1 (#225, foundations-15): one small
// spinner for rows, buttons and fields instead of a size and stroke tuned per
// call site; screen-level waits use CircularProgressIndicator's own default
// size, and media overlays keep theirs.
internal val InlineProgressIndicatorSize = 20.dp

@Composable
internal fun InlineProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
) {
    CircularProgressIndicator(
        modifier = modifier.size(InlineProgressIndicatorSize),
        color = color,
        strokeWidth = 2.dp,
    )
}
