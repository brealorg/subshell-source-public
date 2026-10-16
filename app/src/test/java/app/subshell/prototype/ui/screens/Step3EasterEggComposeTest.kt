package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (egg01): in Help & about, a long upward pull held from the bottom
 * reveals a cursor; the cursor opens the terminal, whose `help` (sent from the
 * keyboard) lists the commands, and Back returns to Help & about. A short
 * hold reveals nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3EasterEggComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun showHelp() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            HelpScreen(onBack = {})
        }
        composeRule.waitForIdle()
        // The revealed cursor blinks forever; the clock is driven by hand from here.
        composeRule.mainClock.autoAdvance = false
    }

    private fun pullFromTheBottomAndHold(holdMillis: Long) {
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, height - 20f))
            repeat(10) { moveBy(Offset(0f, -12f * density)) }
        }
        composeRule.mainClock.advanceTimeBy(holdMillis)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(100)
    }

    private fun cursors() = composeRule.onAllNodesWithText("_").fetchSemanticsNodes().size

    @Test
    fun aShortHoldRevealsNothing() {
        showHelp()

        pullFromTheBottomAndHold(holdMillis = 800)

        assertEquals(0, cursors())
    }

    @Test
    fun aLongHoldRevealsTheTerminalWhichAnswersHelpAndBackLeavesIt() {
        showHelp()

        pullFromTheBottomAndHold(holdMillis = 2_100)
        assertEquals(1, cursors())

        composeRule.onNode(hasClickAction() and hasText("_")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(500)
        val input = composeRule.onNode(hasContentDescription("Terminal command"))
        input.performTextInput("help")
        input.performImeAction()
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals(
            1,
            composeRule.onAllNodesWithText("commands: help clear whoami version uptime history exit", substring = true)
                .fetchSemanticsNodes().size,
        )

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals(1, composeRule.onAllNodesWithText("Help & about").fetchSemanticsNodes().size)
    }
}
