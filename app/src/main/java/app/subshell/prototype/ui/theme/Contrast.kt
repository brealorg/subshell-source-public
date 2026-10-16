package app.subshell.prototype.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * ISSUE218_COLOURS_KEEP_THEIR_CONTRAST_V1: WCAG contrast ratio of two opaque
 * colours (1.0 … 21.0).
 */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * This colour, drawn over [background], moved toward black on a light
 * background (toward white on a dark one) until it reaches [minRatio].
 * Colours that already reach it are returned as drawn. Used where a colour
 * comes from data (community accents, comment rails) rather than the scheme,
 * so the stored colour never changes; only what is drawn does.
 */
internal fun Color.withMinContrast(background: Color, minRatio: Float): Color {
    val drawn = compositeOver(background)
    if (contrastRatio(drawn, background) >= minRatio) return drawn
    val target = if (background.luminance() > 0.5f) Color.Black else Color.White
    var step = 1
    while (step <= CONTRAST_STEPS) {
        val candidate = lerp(drawn, target, step / CONTRAST_STEPS.toFloat())
        if (contrastRatio(candidate, background) >= minRatio) return candidate
        step += 1
    }
    return target
}

private const val CONTRAST_STEPS = 40

/** Whether the current scheme is a dark one (its surface is dark). */
@Composable
internal fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
