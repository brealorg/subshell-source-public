package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.ui.components.AdaptiveFullScreenDialog
import app.subshell.prototype.ui.theme.SubshellTheme
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
 * ISSUE247_LAYOUT_FOLLOWS_WINDOW_SIZE_CLASSES_V1 (#247): the rail, the two
 * panes and the full-screen dialog follow Google's window size classes of
 * the window (medium from 600 dp, expanded from 840 dp) — the same edges as
 * the hard-coded ones before, now read from the window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue247WindowSizeClassesTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private fun sizeClass(widthDp: Int) =
        WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp.toFloat(), 800f)

    @Test
    fun theRailStartsAtMediumAndTwoPanesAtExpanded() {
        assertFalse(useNavigationRail(sizeClass(599)))
        assertTrue(useNavigationRail(sizeClass(600)))
        assertFalse(useExpandedMainLayout(sizeClass(839)))
        assertTrue(useExpandedMainLayout(sizeClass(840)))
    }

    /** Left edge of the You destination, in dp: a rail item sits at the start. */
    private fun youLeftDp(): Float {
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
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        val you = hasContentDescription("You") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
        val left = composeRule.onNode(you).fetchSemanticsNode().boundsInRoot.left
        return left / context.resources.displayMetrics.density
    }

    @Test
    @Config(qualifiers = "w599dp-h800dp")
    fun justBelowMediumTheBottomBarStays() {
        assertTrue("You sits at the end of the bottom bar", youLeftDp() > 300f)
    }

    @Test
    @Config(qualifiers = "w600dp-h800dp")
    fun atMediumTheRailTakesOver() {
        assertTrue("You sits in the rail at the start", youLeftDp() < 120f)
    }

    private fun dialogBodyWidthDp(): Float {
        composeRule.setContent {
            SubshellTheme {
                AdaptiveFullScreenDialog(title = "Remove this post?", onDismissRequest = {}) {
                    Box(Modifier.fillMaxWidth().testTag("body")) { Text("Form body") }
                }
            }
        }
        val width = composeRule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot.width
        return width / ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
    }

    @Test
    @Config(qualifiers = "w599dp-h800dp")
    fun justBelowMediumAFormDialogFillsTheWindow() {
        assertEquals(599f, dialogBodyWidthDp(), 1f)
    }

    @Test
    @Config(qualifiers = "w600dp-h800dp")
    fun atMediumAFormDialogIsABasicDialog() {
        assertTrue(dialogBodyWidthDp() < 590f)
    }
}
