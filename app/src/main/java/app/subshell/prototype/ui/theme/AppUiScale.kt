package app.subshell.prototype.ui.theme

// ISSUE278_UI_SIZE_SCALES_THE_WHOLE_APP_V1 (owner 2026-10-28, reader request):
// one size for the whole interface — bars, rows, buttons, padding and text —
// applied by SubshellTheme to LocalDensity.density. Text size stays relative on
// top. Portable through the settings map.
enum class AppUiScale(val factor: Float, val label: String) {
    SMALLEST(0.85f, "85 %"),
    SMALLER(0.90f, "90 %"),
    DEFAULT(1.0f, "100 %"),
    LARGER(1.10f, "110 %"),
}
