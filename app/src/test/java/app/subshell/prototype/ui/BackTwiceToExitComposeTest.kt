package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #26: with "Press Back twice to exit" on, the first Back at the true
 * root only shows a prompt in the viewport tray; the next Back within the
 * window reaches the system. Off (the default) changes nothing, and Back
 * that has somewhere to go is never swallowed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BackTwiceToExitComposeTest {
    private val composeRule = createComposeRule()

    // Display-density guard for the Robolectric `lightZ … Infinity` launch
    // flake; same whole-app setup as RootBackComposeTest, where it struck.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private fun setUp(backTwiceToExit: Boolean): Pair<SubshellState, OnBackPressedDispatcher> {
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
            initialBackTwiceToExit = backTwiceToExit,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state to dispatcher
    }

    private fun promptCount(): Int =
        composeRule.onAllNodesWithText(BackTwiceToExitNotice.title).fetchSemanticsNodes().size

    private fun pressBack(dispatcher: OnBackPressedDispatcher) {
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun offByDefaultBackAtTheRootReachesTheSystem() {
        val (state, dispatcher) = setUp(backTwiceToExit = false)
        assertFalse(state.backTwiceToExit)
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun theFirstBackPromptsAndTheSecondWithinTheWindowReachesTheSystem() {
        val (_, dispatcher) = setUp(backTwiceToExit = true)
        assertTrue(dispatcher.hasEnabledCallbacks())

        assertEquals(0, promptCount())

        pressBack(dispatcher)
        assertEquals(1, promptCount())
        // Armed: nothing in the app claims the next Back.
        assertFalse(dispatcher.hasEnabledCallbacks())

        // After the window the prompt hides and is needed again.
        composeRule.mainClock.advanceTimeBy(BackTwiceToExitWindowMillis + 1_000)
        composeRule.waitForIdle()
        assertEquals(0, promptCount())
        assertTrue(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun dismissingThePromptDisarmsItAndTheNextBackPromptsAgain() {
        val (_, dispatcher) = setUp(backTwiceToExit = true)
        pressBack(dispatcher)
        assertEquals(1, promptCount())

        // #222: the prompt is a neutral notice, so its close button says "notice".
        composeRule.onNodeWithContentDescription("Dismiss notice").performClick()
        composeRule.waitForIdle()
        assertTrue(dispatcher.hasEnabledCallbacks())

        pressBack(dispatcher)
        assertEquals(1, promptCount())
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun leavingTheRootDisarmsThePrompt() {
        val (state, dispatcher) = setUp(backTwiceToExit = true)
        pressBack(dispatcher)
        assertFalse(dispatcher.hasEnabledCallbacks())

        state.navigate(MainDestination.INBOX)
        composeRule.waitForIdle()
        pressBack(dispatcher)
        assertEquals(MainDestination.HOME, state.destination)
        // Back at the root, the first Back prompts again instead of exiting.
        assertTrue(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun backWithSomewhereToGoIsNotSwallowed() {
        val (state, dispatcher) = setUp(backTwiceToExit = true)
        state.selectFeedSource(FeedSource.POPULAR)
        composeRule.waitForIdle()

        pressBack(dispatcher)
        assertEquals(FeedSource.HOME, state.feedSource)
        assertEquals(0, promptCount())
        assertTrue(dispatcher.hasEnabledCallbacks())
    }
}
