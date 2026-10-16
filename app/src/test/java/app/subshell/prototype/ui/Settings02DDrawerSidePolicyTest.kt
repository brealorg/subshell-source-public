package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Settings02DDrawerSidePolicyTest {
    @Test
    fun legacyFollowLocksToTheCurrentlyResolvedPhysicalSide() {
        assertEquals(
            DrawerSidePreference.LEFT,
            explicitDrawerSidePreference(DrawerSidePreference.FOLLOW, InteractionSide.LEFT),
        )
        assertEquals(
            DrawerSidePreference.RIGHT,
            explicitDrawerSidePreference(DrawerSidePreference.FOLLOW, InteractionSide.RIGHT),
        )
    }

    @Test
    fun explicitDrawerSideNeverChangesWithInteractionSide() {
        assertEquals(
            DrawerSidePreference.LEFT,
            explicitDrawerSidePreference(DrawerSidePreference.LEFT, InteractionSide.RIGHT),
        )
        assertEquals(
            DrawerSidePreference.RIGHT,
            explicitDrawerSidePreference(DrawerSidePreference.RIGHT, InteractionSide.LEFT),
        )
    }

    @Test
    fun productDrawerChoicesContainOnlyLeftAndRight() {
        assertEquals(
            listOf(DrawerSidePreference.LEFT, DrawerSidePreference.RIGHT),
            selectableDrawerSidePreferences,
        )
        assertFalse(DrawerSidePreference.FOLLOW in selectableDrawerSidePreferences)
    }
}
