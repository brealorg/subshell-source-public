package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class A11y01A41TabletModeSettingsPolicyTest {
    @Test
    fun splitIsDefaultAndBothModesRoundTripThroughSettingsStorage() {
        assertEquals(TabletLayoutMode.SPLIT, SubshellSettings().tabletLayoutMode)

        TabletLayoutMode.entries.forEach { mode ->
            val stored = SubshellSettings(tabletLayoutMode = mode).toStorageMap()
            assertEquals(mode.name, stored.getValue(SubshellSettings.TABLET_LAYOUT_MODE))
            assertEquals(
                mode,
                SubshellSettings.fromStorageMap(stored).tabletLayoutMode,
            )
        }
    }

    @Test
    fun oldSettingsWithoutTabletModeDefaultToSplit() {
        val old = SubshellSettings().toStorageMap() - SubshellSettings.TABLET_LAYOUT_MODE
        assertEquals(
            TabletLayoutMode.SPLIT,
            SubshellSettings.fromStorageMap(old).tabletLayoutMode,
        )
    }
}
