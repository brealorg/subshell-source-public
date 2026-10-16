package app.subshell.prototype.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

// ISSUE32_ONE_LINE_TAB_LABELS_V1: labels in fixed-width tabs and segments
// stay on one line and shrink a little (down to 9 sp) when the text size is
// large, instead of breaking the word ("Syste/m") or clipping it
// ("Favorit", "Messa…").
@Composable
internal fun OneLineAutoSizeLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
) {
    // Outside a Material component the ambient style has no size; fall back
    // to labelLarge so the auto-size has a ceiling.
    val base = if (style.fontSize.isUnspecified) MaterialTheme.typography.labelLarge.merge(style) else style
    BasicText(
        text = text,
        modifier = modifier,
        style = base.copy(
            color = LocalContentColor.current,
            fontWeight = fontWeight ?: base.fontWeight,
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 9.sp,
            maxFontSize = base.fontSize,
        ),
    )
}

/**
 * ISSUE220_TABS_SCROLL_INSTEAD_OF_SHRINKING_V1: whether every label fits
 * [slotWidth] on one line at [style]'s own size. Tabs that do not fit scroll
 * instead of shrinking. Before the row is measured (no slot yet) they fit.
 */
@Composable
internal fun rememberLabelsFitSlots(
    labels: List<String>,
    slotWidth: Dp,
    style: TextStyle,
): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, slotWidth, style, density) {
        if (slotWidth <= 0.dp) return@remember true
        val maxWidthPx = with(density) { slotWidth.roundToPx() }
        labels.all { label ->
            measurer.measure(text = label, style = style, maxLines = 1, softWrap = false).size.width <= maxWidthPx
        }
    }
}
