package app.subshell.prototype.ui.error

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.app.redditAccessRefusedPresentation
import app.subshell.prototype.ui.components.ViewportErrorTrayHost
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ISSUE32_TRAY_MESSAGE_CAN_BE_READ_IN_FULL_V1 (owner, on the refused-access
 * notice: the message was cut off with no way to read it): a tray message
 * longer than two lines offers More, which shows all of it, and Less; a short
 * message has neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
// Native graphics measure text, so a cut-off message is cut off here too.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Issue32TrayMessageInFullTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val host = ViewportErrorHostState()

    private fun showTray() {
        composeRule.setContent {
            SubshellTheme {
                ViewportErrorTrayHost(state = host)
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun tap(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun aLongMessageOffersMoreAndLess() {
        val presentation = redditAccessRefusedPresentation()
        assertTrue(presentation.message.length > 300)
        host.present(scopeKey = "refused", presentation = presentation, failureDetails = null, onRetry = {})
        showTray()

        assertEquals(1, count("More"))
        assertEquals(1, count("Try again"))
        tap("More")
        assertEquals(0, count("More"))
        assertEquals(1, count("Less"))
        tap("Less")
        assertEquals(1, count("More"))
    }

    @Test
    fun aShortMessageHasNoMore() {
        host.presentNotice(scopeKey = "short", title = "Muted r/x", message = "Its posts are hidden.")
        showTray()

        assertEquals(1, count("Muted r/x"))
        assertEquals(0, count("More"))
    }
}
