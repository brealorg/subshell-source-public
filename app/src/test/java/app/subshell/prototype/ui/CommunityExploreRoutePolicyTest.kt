package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityExploreRoutePolicyTest {
    @Test
    fun exploreAndNestedCommunityUseTheCanonicalRouteStack() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        val community = SampleData.communities.first().name

        state.navigate(MainDestination.SUBS)
        state.openSecondary(SecondaryDestination.COMMUNITY_EXPLORE)

        val explore = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(MainDestination.SUBS, explore.destination)
        assertEquals(
            SecondaryDestination.COMMUNITY_EXPLORE,
            explore.secondaryDestination,
        )

        val restored = SubshellState(
            initialProcessSnapshotToken = encodeSubshellProcessSnapshot(
                state.processStateSnapshot(),
            ),
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        assertEquals(
            SecondaryDestination.COMMUNITY_EXPLORE,
            (restored.currentNavigationRoute as SubshellRoute.Secondary)
                .secondaryDestination,
        )

        state.openSubreddit(community)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)

        assertTrue(state.navigateBack())
        assertEquals(
            SecondaryDestination.COMMUNITY_EXPLORE,
            (state.currentNavigationRoute as SubshellRoute.Secondary)
                .secondaryDestination,
        )

        assertTrue(state.navigateBack())
        val joined = state.currentNavigationRoute as SubshellRoute.Main
        assertEquals(MainDestination.SUBS, joined.destination)
    }
}
