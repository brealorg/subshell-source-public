package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.NavigationLabelMode
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Settings03BVisualPreviewPolicyTest {
    @Test
    fun chromeIsAlwaysVisibleAtRest() {
        ChromeBehavior.entries.forEach { behavior ->
            assertTrue(navigationPreviewChromeVisible(behavior, scrolling = false))
        }
    }

    @Test
    fun hideOnScrollDisappearsOnlyInScrollingPreview() {
        assertFalse(
            navigationPreviewChromeVisible(
                ChromeBehavior.HIDE_ON_SCROLL,
                scrolling = true,
            ),
        )
        assertTrue(
            navigationPreviewChromeVisible(
                ChromeBehavior.ALWAYS_SHOW,
                scrolling = true,
            ),
        )
    }

    @Test
    fun navigationLabelPreviewMatchesProductModes() {
        assertTrue(navigationPreviewLabelVisible(NavigationLabelMode.ON, selected = false))
        assertTrue(navigationPreviewLabelVisible(NavigationLabelMode.ON, selected = true))
        assertFalse(navigationPreviewLabelVisible(NavigationLabelMode.PARTIAL, selected = false))
        assertTrue(navigationPreviewLabelVisible(NavigationLabelMode.PARTIAL, selected = true))
        assertFalse(navigationPreviewLabelVisible(NavigationLabelMode.OFF, selected = false))
        assertFalse(navigationPreviewLabelVisible(NavigationLabelMode.OFF, selected = true))
    }

    @Test
    fun pageScrollOrReplayDrivesTheAnimatedScrollingState() {
        assertFalse(navigationPreviewScrolling(pageScrolling = false, demoScrolling = false))
        assertTrue(navigationPreviewScrolling(pageScrolling = true, demoScrolling = false))
        assertTrue(navigationPreviewScrolling(pageScrolling = false, demoScrolling = true))
    }

    @Test
    fun interactionIndicatorDisappearsWhileScrolling() {
        assertTrue(navigationPreviewInteractionVisible(scrolling = false))
        assertFalse(navigationPreviewInteractionVisible(scrolling = true))
    }

    @Test
    fun interactionIndicatorGeometrySeparatesFabFromDrawerIndicator() {
        val containerWidth = 320.dp
        val drawerWidth = 27.dp
        val fabSize = 28.dp
        val minimumGap = 12.dp
        val leftFabX = navigationPreviewInteractionX(
            side = app.subshell.prototype.ui.InteractionSide.LEFT,
            containerWidth = containerWidth,
        )
        val rightFabX = navigationPreviewInteractionX(
            side = app.subshell.prototype.ui.InteractionSide.RIGHT,
            containerWidth = containerWidth,
        )
        val rightDrawerX = navigationPreviewDrawerIndicatorX(
            side = app.subshell.prototype.ui.InteractionSide.RIGHT,
            containerWidth = containerWidth,
            indicatorWidth = drawerWidth,
        )

        assertTrue(navigationPreviewInteractionEdgePadding() >= drawerWidth + minimumGap)
        assertTrue(leftFabX >= drawerWidth + minimumGap)
        assertTrue(rightDrawerX - (rightFabX + fabSize) >= minimumGap)
        assertTrue(navigationPreviewInteractionBottom(bottomBarVisible = true) > 20.dp)
    }

    @Test
    fun drawerPositionIndicatorMovesBetweenEdges() {
        val left = navigationPreviewDrawerIndicatorX(
            side = app.subshell.prototype.ui.InteractionSide.LEFT,
            containerWidth = 320.dp,
            indicatorWidth = 27.dp,
        )
        val right = navigationPreviewDrawerIndicatorX(
            side = app.subshell.prototype.ui.InteractionSide.RIGHT,
            containerWidth = 320.dp,
            indicatorWidth = 27.dp,
        )
        assertTrue(left == 0.dp)
        assertTrue(right > 250.dp)
    }

    @Test
    fun drawerPositionIndicatorStopsAboveBottomNavigation() {
        val drawerBottom =
            navigationPreviewDrawerIndicatorTop() + navigationPreviewDrawerIndicatorHeight()
        val gap = navigationPreviewBottomNavigationTop() - drawerBottom

        assertTrue(navigationPreviewDrawerIndicatorHeight() < 76.dp)
        assertTrue(gap >= 10.dp)
    }
}
