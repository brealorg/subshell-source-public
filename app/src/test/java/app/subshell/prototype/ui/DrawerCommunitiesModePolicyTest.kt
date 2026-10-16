package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerCommunitiesModePolicyTest {
    @Test
    fun modeLabelsAndDefaultRemainStable() {
        assertEquals(
            listOf(
                DrawerCommunitiesMode.CURRENT,
                DrawerCommunitiesMode.COLLAPSIBLE,
                DrawerCommunitiesMode.FULL,
            ),
            DrawerCommunitiesMode.entries,
        )
        assertEquals(
            listOf("Simple", "Dropdown", "Full list"),
            DrawerCommunitiesMode.entries.map(DrawerCommunitiesMode::label),
        )
        assertEquals(
            DrawerCommunitiesMode.COLLAPSIBLE,
            SubshellSettings().drawerCommunitiesMode,
        )
    }

    @Test
    fun everyModeRoundTripsThroughSettingsStorage() {
        DrawerCommunitiesMode.entries.forEach { mode ->
            val settings = SubshellSettings(drawerCommunitiesMode = mode)

            val restored = SubshellSettings.fromStorageMap(settings.toStorageMap())

            assertEquals(mode, restored.drawerCommunitiesMode)
        }
    }

    @Test
    fun retiredViewAllKeyAlwaysCanonicalizesFalse() {
        DrawerCommunitiesMode.entries.forEach { mode ->
            assertNull(SubshellSettings(drawerCommunitiesMode = mode).toStorageMap()[SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES])
        }

        val legacy = SubshellSettings(
            drawerCommunitiesMode = DrawerCommunitiesMode.FULL,
        ).toStorageMap().toMutableMap()
        legacy[SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES] = "true"

        val restored = SubshellSettings.fromStorageMap(legacy)

        assertEquals(DrawerCommunitiesMode.FULL, restored.drawerCommunitiesMode)
        assertNull(restored.toStorageMap()[SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES])
    }

    @Test
    fun stateUpdatePersistsTheDedicatedPreference() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            persistPreference = { key, value -> persisted[key] = value },
        )

        state.updateDrawerCommunitiesMode(DrawerCommunitiesMode.FULL)

        assertEquals(DrawerCommunitiesMode.FULL, state.drawerCommunitiesMode)
        assertEquals(
            DrawerCommunitiesMode.FULL.name,
            persisted[SubshellSettings.DRAWER_COMMUNITIES_MODE],
        )
    }
}
