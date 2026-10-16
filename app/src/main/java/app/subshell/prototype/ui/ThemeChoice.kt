package app.subshell.prototype.ui

import app.subshell.prototype.ui.theme.ThemeMode

// ISSUE31_PURE_BLACK_DARK_THEME_V1: the theme pickers offer pure black as a
// fourth theme, but it is stored as theme_mode = DARK plus
// pure_black_dark_theme = true. An older app restoring such a backup then
// gets Dark instead of rejecting an unknown theme_mode value.
internal enum class ThemeChoice(
    val label: String,
    val shortLabel: String,
    val mode: ThemeMode,
    val pureBlack: Boolean,
) {
    SYSTEM("System", "System", ThemeMode.SYSTEM, false),
    LIGHT("Light", "Light", ThemeMode.LIGHT, false),
    DARK("Dark", "Dark", ThemeMode.DARK, false),
    PURE_BLACK("Pure black", "Black", ThemeMode.DARK, true),
}

/**
 * The drawer's theme row is a quick switch: Light / Dark / Black. Following
 * the system is chosen in Settings; while it is on, no segment is selected.
 */
internal val drawerThemeChoices: List<ThemeChoice> = ThemeChoice.entries - ThemeChoice.SYSTEM

internal fun themeChoice(mode: ThemeMode, pureBlack: Boolean): ThemeChoice =
    if (mode == ThemeMode.DARK && pureBlack) {
        ThemeChoice.PURE_BLACK
    } else {
        ThemeChoice.entries.first { choice -> choice.mode == mode && !choice.pureBlack }
    }
