package app.subshell.prototype.ui.screens

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #27: the fullscreen media viewers follow the predictive back
 * gesture (the chrome hides, the media shrinks over a fading backdrop) and
 * close only on commit, through the same onBack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PredictiveMediaBackComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun event(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 400f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    private fun ui(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
    }

    private fun chromeShown(): Boolean =
        composeRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
    }

    @Test
    fun thePostViewerShrinksWithTheGestureAndClosesOnCommit() {
        var closed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        val post = SampleData.posts.first { it.kind == PostKind.IMAGE }
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MediaViewerScreen(post = post, state = SubshellState(), onBack = { closed += 1 })
        }
        composeRule.waitForIdle()
        assertTrue(chromeShown())

        ui { dispatcher.dispatchOnBackStarted(event(0f)) }
        ui { dispatcher.dispatchOnBackProgressed(event(1f)) }
        settle()
        assertFalse(chromeShown())
        assertEquals(0, closed)

        ui { dispatcher.onBackPressed() }
        assertEquals(1, closed)
    }

    @Test
    fun aCancelledGestureKeepsTheCommentViewerOpen() {
        var closed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CommentMediaViewerScreen(uri = "https://i.redd.it/example.jpg", onBack = { closed += 1 })
        }
        composeRule.waitForIdle()

        ui { dispatcher.dispatchOnBackStarted(event(0f)) }
        ui { dispatcher.dispatchOnBackProgressed(event(0.7f)) }
        settle()
        assertFalse(chromeShown())
        ui { dispatcher.dispatchOnBackCancelled() }
        settle()

        assertEquals(0, closed)
        assertTrue(chromeShown())

        ui { dispatcher.onBackPressed() }
        assertEquals(1, closed)
    }
}
