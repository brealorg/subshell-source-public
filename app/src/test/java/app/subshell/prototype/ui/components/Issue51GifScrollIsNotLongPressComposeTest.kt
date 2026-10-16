package app.subshell.prototype.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE51_SCROLL_IS_NOT_A_LONG_PRESS_V1: a scroll that starts on a comment
 * GIF used to open "Media actions" and stall the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue51GifScrollIsNotLongPressComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private var longPresses = 0
    private var taps = 0
    private lateinit var scroll: ScrollState

    private fun showAGifInAScrollingThread() {
        composeRule.setContent {
            scroll = rememberScrollState()
            Column(Modifier.verticalScroll(scroll)) {
                // The comment body around the media (tap-to-collapse).
                Box(Modifier.size(300.dp).clickable { taps += 1 }) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("gif")
                            .longPressOnly(label = "Media actions") { longPresses += 1 },
                    )
                }
                Spacer(Modifier.height(3000.dp))
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aScrollStartingOnTheGifScrollsAndOpensNothing() {
        showAGifInAScrollingThread()

        composeRule.onNodeWithTag("gif", useUnmergedTree = true).performTouchInput { swipeUp(durationMillis = 150) }
        composeRule.waitForIdle()

        assertEquals(0, longPresses)
        assertTrue("the list scrolled", scroll.value > 0)
    }

    @Test
    fun aLongPressStillOpensMediaActions() {
        showAGifInAScrollingThread()

        composeRule.onNodeWithTag("gif", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(1, longPresses)
        assertEquals(0, taps)
    }

    @Test
    fun aTapReachesTheCommentAroundTheGif() {
        showAGifInAScrollingThread()

        composeRule.onNodeWithTag("gif", useUnmergedTree = true).performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(0, longPresses)
        assertEquals(1, taps)
    }
}
