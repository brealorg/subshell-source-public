package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class Settings02BRestorePreviewPolicyTest {
    @Test
    fun identical_settings_report_zero_changes() {
        val settings = SubshellSettings()
        assertEquals(0, settingsRestoreChangeCount(settings, settings))
    }

    @Test
    fun changed_settings_are_counted_by_persisted_key() {
        val current = SubshellSettings()
        val target = current.copy(
            themeMode = ThemeMode.LIGHT,
            // SETTINGSREVIEW01_DYNAMIC_COLOUR_ON_BY_DEFAULT_V1: on is the default now.
            dynamicColor = false,
            voteColorPalette = VoteColorPalette.RED_BLUE,
            voteControlMode = VoteControlMode.COMPACT,
            showNsfwPosts = true,
        )

        assertEquals(5, settingsRestoreChangeCount(current, target))
    }

    @Test
    fun restore_preview_count_is_symmetric() {
        val first = SubshellSettings(dynamicColor = false, showNsfwPosts = false)
        val second = first.copy(dynamicColor = true, showNsfwPosts = true)

        assertEquals(
            settingsRestoreChangeCount(first, second),
            settingsRestoreChangeCount(second, first),
        )
    }
}
