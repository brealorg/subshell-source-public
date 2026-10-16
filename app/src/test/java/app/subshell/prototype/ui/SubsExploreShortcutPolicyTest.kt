package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubsExploreShortcutPolicyTest {
    @Test
    fun shortcutModeRoundTripsThroughSettingsStorage() {
        val legacy = SubshellSettings().toStorageMap().toMutableMap()
        legacy[SubshellSettings.DISCOVER_EXPANDED] = "true"
        legacy[SubshellSettings.SUBS_EXPLORE_SHORTCUT_MODE] = "HIDDEN"

        val restored = SubshellSettings.fromStorageMap(legacy)
        val canonical = restored.toStorageMap()

        assertNull(canonical[SubshellSettings.DISCOVER_EXPANDED])
        assertNull(canonical[SubshellSettings.SUBS_EXPLORE_SHORTCUT_MODE])
    }

    @Test
    fun contextualSearchOpensExploreAndRequestsFocus() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        state.navigate(MainDestination.SUBS)

        state.openCommunityExplore(focusSearch = true)

        val route = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(
            SecondaryDestination.COMMUNITY_EXPLORE,
            route.secondaryDestination,
        )
        assertEquals(1, state.communityExploreSearchFocusRequest)

        state.consumeCommunityExploreSearchFocusRequest(1)

        assertEquals(0, state.communityExploreSearchFocusRequest)
    }
}
