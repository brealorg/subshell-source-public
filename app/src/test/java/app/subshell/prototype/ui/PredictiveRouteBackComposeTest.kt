package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.PredictiveRouteBackMinScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #27: Back that pops the route stack follows the predictive gesture.
 * The screen shrinks with the progress, the route stays until the gesture
 * commits, a cancelled gesture leaves it in place at full size, and reduce
 * motion keeps the screen still.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PredictiveRouteBackComposeTest {
    private val composeRule = createComposeRule()

    // Display-density guard for the Robolectric `lightZ … Infinity` launch
    // flake; same whole-app setup as RootBackComposeTest, where it struck.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private fun setUp(reduceMotion: Boolean = false): Pair<SubshellState, OnBackPressedDispatcher> {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
            initialReduceMotion = reduceMotion,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        state.openSubreddit("android")
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
        return state to dispatcher
    }

    /** Rendered width of the community title, which lives inside the scaffold. */
    private fun titleWidth(state: SubshellState): Float =
        composeRule.onAllNodesWithText(state.subredditLabel("android"))
            .fetchSemanticsNodes()
            .first()
            .boundsInWindow
            .width

    private fun ui(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
    }

    private fun event(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 400f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    @Test
    fun theScreenFollowsTheGestureAndThePopWaitsForTheCommit() {
        val (state, dispatcher) = setUp()
        val fullWidth = titleWidth(state)

        ui { dispatcher.dispatchOnBackStarted(event(0f)) }
        ui { dispatcher.dispatchOnBackProgressed(event(1f)) }
        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
        assertEquals(fullWidth * PredictiveRouteBackMinScale, titleWidth(state), fullWidth * 0.02f)

        ui { dispatcher.onBackPressed() }
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
    }

    @Test
    fun aCancelledGestureKeepsTheRouteAndReturnsToFullSize() {
        val (state, dispatcher) = setUp()
        val fullWidth = titleWidth(state)

        ui { dispatcher.dispatchOnBackStarted(event(0f)) }
        ui { dispatcher.dispatchOnBackProgressed(event(0.6f)) }
        ui { dispatcher.dispatchOnBackCancelled() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
        assertEquals(fullWidth, titleWidth(state), 0.5f)
    }

    @Test
    fun reduceMotionKeepsTheScreenStillButStillPopsOnCommit() {
        val (state, dispatcher) = setUp(reduceMotion = true)
        val fullWidth = titleWidth(state)

        ui { dispatcher.dispatchOnBackStarted(event(0f)) }
        ui { dispatcher.dispatchOnBackProgressed(event(1f)) }
        assertEquals(fullWidth, titleWidth(state), 0.5f)

        ui { dispatcher.onBackPressed() }
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
    }
}
