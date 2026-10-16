package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.DrawerCommunitiesMode
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Subs04cCommunityNavigationDiscoveryPolicyTest {
    @Test
    fun communityNavigationModesHaveSingleOwnedSemantics() {
        assertEquals("Simple", DrawerCommunitiesMode.CURRENT.label)
        assertEquals("Dropdown", DrawerCommunitiesMode.COLLAPSIBLE.label)
        assertEquals("Full list", DrawerCommunitiesMode.FULL.label)
    }

    @Test
    fun retiredExploreAndViewAllKeysRemainBackupCompatibleOnly() {
        val legacy = SubshellSettings(
            drawerCommunitiesMode = DrawerCommunitiesMode.CURRENT,
        ).toStorageMap().toMutableMap()
        legacy[SubshellSettings.DISCOVER_EXPANDED] = "true"
        legacy[SubshellSettings.SUBS_EXPLORE_SHORTCUT_MODE] = "ALWAYS"
        legacy[SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES] = "true"

        val restored = SubshellSettings.fromStorageMap(legacy)
        val canonical = restored.toStorageMap()

        assertEquals(DrawerCommunitiesMode.CURRENT, restored.drawerCommunitiesMode)
        assertNull(canonical[SubshellSettings.DISCOVER_EXPANDED])
        assertNull(canonical[SubshellSettings.SUBS_EXPLORE_SHORTCUT_MODE])
        assertNull(canonical[SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES])
    }

    @Test
    fun searchFirstEntryUsesCanonicalCommunityExploreRouteWithFocus() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        state.navigate(MainDestination.SUBS)

        state.openCommunityExplore(focusSearch = true)

        val route = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(SecondaryDestination.COMMUNITY_EXPLORE, route.secondaryDestination)
        assertEquals(1, state.communityExploreSearchFocusRequest)
    }
}
