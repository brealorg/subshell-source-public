package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerDynamicGroupsPolicyTest {
    @Test
    fun bothDynamicGroupsStartCollapsed() {
        val initial = DrawerDynamicGroupExpansion()
        assertFalse(initial.communitiesExpanded)
        assertFalse(initial.customFeedsExpanded)
    }

    @Test
    fun openingCommunitiesClosesCustomFeeds() {
        val result = toggleDrawerDynamicGroup(
            current = DrawerDynamicGroupExpansion(customFeedsExpanded = true),
            group = DrawerDynamicGroup.COMMUNITIES,
        )

        assertTrue(result.communitiesExpanded)
        assertFalse(result.customFeedsExpanded)
    }

    @Test
    fun openingCustomFeedsClosesCommunities() {
        val result = toggleDrawerDynamicGroup(
            current = DrawerDynamicGroupExpansion(communitiesExpanded = true),
            group = DrawerDynamicGroup.CUSTOM_FEEDS,
        )

        assertFalse(result.communitiesExpanded)
        assertTrue(result.customFeedsExpanded)
    }

    @Test
    fun closingOneGroupDoesNotOpenTheOther() {
        assertEquals(
            DrawerDynamicGroupExpansion(),
            toggleDrawerDynamicGroup(
                current = DrawerDynamicGroupExpansion(communitiesExpanded = true),
                group = DrawerDynamicGroup.COMMUNITIES,
            ),
        )
        assertEquals(
            DrawerDynamicGroupExpansion(),
            toggleDrawerDynamicGroup(
                current = DrawerDynamicGroupExpansion(customFeedsExpanded = true),
                group = DrawerDynamicGroup.CUSTOM_FEEDS,
            ),
        )
    }
}
