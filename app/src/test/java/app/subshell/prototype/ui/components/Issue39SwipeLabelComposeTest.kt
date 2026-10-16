package app.subshell.prototype.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #39: while a card is dragged, the action label is never drawn under
 * the card — it stays hidden until it fits in the revealed strip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue39SwipeLabelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSurface() {
        val downvote = voteSwipeActionVisual(
            direction = -1,
            label = "Downvote",
            palette = VoteColorPalette.RED_BLUE,
            mode = VoteControlMode.COMFORTABLE,
            enabled = true,
        )
        composeRule.setContent {
            SwipeActionSurface(
                enabled = true,
                leftAction = null,
                rightAction = downvote,
                onLeft = {},
                onRight = {},
                modifier = Modifier.width(360.dp).height(96.dp),
            ) { contentModifier ->
                Box(contentModifier.height(96.dp).background(Color.White).testTag("card"))
            }
        }
    }

    private fun dragRightAndHold(distanceDp: Float) {
        composeRule.onNodeWithTag("card").performTouchInput {
            down(centerLeft + Offset(10f, 0f))
            val step = distanceDp * density / 10f
            repeat(10) { moveBy(Offset(step, 0f)) }
        }
        composeRule.waitForIdle()
    }

    private fun labelNodes() =
        composeRule.onAllNodesWithText("Downvote").fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }

    @Test
    fun aShortDragShowsNoLabelUnderTheCard() {
        setSurface()
        dragRightAndHold(60f)

        val cardLeft = composeRule.onNodeWithTag("card").getBoundsInRoot().left
        assertTrue("card should have moved: $cardLeft", cardLeft.value > 0f)
        assertEquals(emptyList<Any>(), labelNodes())
    }

    @Test
    fun aLongDragShowsTheWholeLabelInsideTheRevealedStrip() {
        setSurface()
        dragRightAndHold(220f)

        val cardLeft = composeRule.onNodeWithTag("card").getBoundsInRoot().left
        val labels = labelNodes()
        assertEquals(1, labels.size)
        val labelRightDp = labels.single().boundsInRoot.right /
            composeRule.density.density
        assertTrue(
            "label ends at $labelRightDp dp, card starts at $cardLeft",
            labelRightDp <= cardLeft.value,
        )
    }
}
