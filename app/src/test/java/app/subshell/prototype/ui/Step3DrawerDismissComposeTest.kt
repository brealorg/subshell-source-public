package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (issue23): with swipe actions on, the drawer gives up only drag-to-
 * open; once opened through its button it still closes on a scrim tap and on
 * Back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DrawerDismissComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun compose(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
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
        )
        state.updateSwipeActionsEnabled(true)
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state
    }

    private fun scrims() =
        composeRule.onAllNodesWithContentDescription("Close navigation menu").fetchSemanticsNodes().size

    private fun openDrawer(state: SubshellState) {
        composeRule.runOnIdle { state.requestDrawer() }
        composeRule.waitForIdle()
        assertEquals("drawer should be open", 1, scrims())
    }

    @Test
    fun anOpenDrawerClosesOnAScrimTap() {
        val state = compose()
        assertEquals(0, scrims())
        openDrawer(state)

        composeRule.onAllNodesWithContentDescription("Close navigation menu").onFirst().performTouchInput {
            val x = if (state.resolvedDrawerSide == InteractionSide.LEFT) width - 20f else 20f
            click(Offset(x, centerY))
        }
        composeRule.waitForIdle()

        assertEquals(0, scrims())
    }

    @Test
    fun anOpenDrawerClosesOnBack() {
        val state = compose()
        openDrawer(state)

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(0, scrims())
    }
}
