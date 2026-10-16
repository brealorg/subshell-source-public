package app.subshell.prototype.ui.theme

// ISSUE32_APP_TEXT_SIZE_V1: an in-app text size that multiplies the system
// font scale for all text in the app (SubshellTheme applies it to
// LocalDensity.fontScale), so layouts that already adapt to large system
// text keep working. Portable through the settings map.
enum class AppTextScale(val factor: Float, val label: String) {
    SMALL(0.85f, "85 %"),
    DEFAULT(1.0f, "100 %"),
    LARGE(1.15f, "115 %"),
    LARGER(1.30f, "130 %"),
}
