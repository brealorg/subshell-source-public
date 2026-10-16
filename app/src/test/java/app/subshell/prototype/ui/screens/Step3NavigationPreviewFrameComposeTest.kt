package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (a11y01a411): the Navigation settings preview is one stable frame.
 * Switching between its five contexts keeps one "Live preview" title, shows
 * the context's own line, and changes neither the frame's height nor the
 * width of its status label. A wide window keeps every line on one row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h1200dp")
class Step3NavigationPreviewFrameComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val descriptions = mapOf(
        NavigationPreviewContext.NAVIGATION to
            "Scroll this page or tap the preview to replay the change.",
        NavigationPreviewContext.TABLET_MODE to
            "Switch modes to compare the same fixed preview frame.",
        NavigationPreviewContext.COMMUNITY_DISCOVERY to
            "Switch layouts to compare the same fixed preview frame.",
        NavigationPreviewContext.POST_SWIPE to
            "Swipe the card to preview the configured post actions.",
        NavigationPreviewContext.COMMENT_SWIPE to
            "Swipe the card to preview the configured comment actions.",
    )

    @Test
    fun everyContextKeepsTheSameFrameTitleAndStatusWidth() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        var previewContext by mutableStateOf(NavigationPreviewContext.NAVIGATION)
        composeRule.setContent {
            SubshellTheme {
                Box(Modifier.fillMaxWidth().testTag(FRAME)) {
                    NavigationSettingsPreview(
                        topBarBehavior = state.topBarBehavior,
                        bottomBarBehavior = state.bottomBarBehavior,
                        interactionSide = state.interactionSide,
                        drawerSide = state.resolvedDrawerSide,
                        navigationLabelMode = state.navigationLabelMode,
                        subredditLabelStyle = state.subredditLabelStyle,
                        tabletLayoutMode = state.tabletLayoutMode,
                        communityDiscoveryLayout = state.communityDiscoveryLayout,
                        postSwipeLeftAction = state.postSwipeLeftAction,
                        postSwipeRightAction = state.postSwipeRightAction,
                        commentSwipeLeftAction = state.commentSwipeLeftAction,
                        commentSwipeRightAction = state.commentSwipeRightAction,
                        voteColorPalette = state.voteColorPalette,
                        voteControlMode = state.voteControlMode,
                        reduceMotion = true,
                        previewContext = previewContext,
                        pageScrolling = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val frameHeights = mutableMapOf<NavigationPreviewContext, Dp>()
        val statusWidths = mutableMapOf<NavigationPreviewContext, Dp>()
        NavigationPreviewContext.entries.forEach { next ->
            composeRule.runOnIdle { previewContext = next }
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.waitForIdle()

            assertEquals(
                "one title in $next",
                1,
                composeRule.onAllNodesWithText("Live preview").fetchSemanticsNodes().size,
            )
            assertEquals(
                "its own line in $next",
                1,
                composeRule.onAllNodesWithText(descriptions.getValue(next))
                    .fetchSemanticsNodes().size,
            )
            descriptions.filterKeys { it != next }.values.forEach { other ->
                assertEquals(
                    "no other context's line in $next",
                    0,
                    composeRule.onAllNodesWithText(other).fetchSemanticsNodes().size,
                )
            }
            val frame = composeRule.onNodeWithTag(FRAME).getBoundsInRoot()
            frameHeights[next] = frame.bottom - frame.top
            statusWidths[next] = statusLabelWidth(statusLabel(next, state))
        }

        assertEquals(
            "frame heights $frameHeights",
            1,
            frameHeights.values.distinct().size,
        )
        assertEquals(
            "status label widths $statusWidths",
            1,
            statusWidths.values.distinct().size,
        )
    }

    private fun statusLabel(context: NavigationPreviewContext, state: SubshellState): String =
        when (context) {
            NavigationPreviewContext.NAVIGATION -> "At rest"
            NavigationPreviewContext.TABLET_MODE -> state.tabletLayoutMode.label
            NavigationPreviewContext.COMMUNITY_DISCOVERY -> state.communityDiscoveryLayout.label
            NavigationPreviewContext.POST_SWIPE -> "Post"
            NavigationPreviewContext.COMMENT_SWIPE -> "Comment"
        }

    /** The status pill sits in the header row, above anything the snapshot draws. */
    private fun statusLabelWidth(label: String): Dp {
        val nodes = composeRule.onAllNodes(hasText(label), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val topmost = nodes.minBy { it.boundsInRoot.top }
        return with(composeRule.density) { topmost.boundsInRoot.width.toDp() }
    }

    private companion object {
        const val FRAME = "navigation-preview-frame"
    }
}
