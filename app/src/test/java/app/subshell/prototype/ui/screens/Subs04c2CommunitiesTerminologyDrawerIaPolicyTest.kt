package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.DrawerCommunitiesMode
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Subs04c2CommunitiesTerminologyDrawerIaPolicyTest {
    @Test
    fun communitiesIsTheCanonicalMainDestinationLabel() {
        assertEquals("Communities", MainDestination.SUBS.label)
    }

    @Test
    fun drawerModesRemainSimpleDropdownAndFullList() {
        assertEquals(
            listOf("Simple", "Dropdown", "Full list"),
            DrawerCommunitiesMode.entries.map(DrawerCommunitiesMode::label),
        )
    }

    @Test
    fun retiredViewAllPreferenceCanNeverReactivateProductUi() {
        val legacy = SubshellSettings(
            drawerCommunitiesMode = DrawerCommunitiesMode.COLLAPSIBLE,
        ).toStorageMap().toMutableMap()
        legacy[SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES] = "true"

        val restored = SubshellSettings.fromStorageMap(legacy)

        assertEquals(DrawerCommunitiesMode.COLLAPSIBLE, restored.drawerCommunitiesMode)
        assertNull(restored.toStorageMap()[SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES])
    }
}
