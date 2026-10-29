package app.subshell.prototype.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.subshell.prototype.R

enum class CodeFont(
    val label: String,
    val typefaceName: String,
) {
    JETBRAINS_MONO("Balanced", "JetBrains Mono"),
    IOSEVKA("Narrow", "Iosevka"),
    RECURSIVE_MONO_CASUAL("Casual", "Recursive Mono Casual"),
}

val CodeFont.fontFamily: FontFamily
    get() = when (this) {
        CodeFont.JETBRAINS_MONO -> FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        )
        CodeFont.IOSEVKA -> FontFamily(
            Font(R.font.iosevka_compact_regular, FontWeight.Normal),
        )
        CodeFont.RECURSIVE_MONO_CASUAL -> FontFamily(
            Font(R.font.recursive_mono_casual_regular, FontWeight.Normal),
        )
    }

val LocalCodeFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
