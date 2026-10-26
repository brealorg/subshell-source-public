package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.HomeScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE248_FIRST_FEED_PAGE_REPORTS_FULLY_DRAWN_V1 (#248): Home reports fully
 * drawn once its first posts show, not at the first frame, so the start-up
 * benchmark's timeToFullDisplay measures a usable feed. (The activity's own
 * reporter waits for a real drawn frame, which Robolectric does not produce;
 * the test hands Home a reporter that reports at once.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue248ReportDrawnComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun homeReportsFullyDrawnOnlyOnceItsPostsShow() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        var reported = false
        val owner = object : FullyDrawnReporterOwner {
            override val fullyDrawnReporter = FullyDrawnReporter(executor = { it.run() }) { reported = true }
        }
        val state = SubshellState()
        // The landing after sign-in holds the feed back (no posts yet).
        var landingPending by mutableStateOf(true)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalFullyDrawnReporterOwner provides owner,
                ) {
                    HomeScreen(state = state, authenticatedLandingPending = landingPending)
                }
            }
        }
        composeRule.waitForIdle()
        assertFalse(reported)

        composeRule.runOnIdle { landingPending = false }
        composeRule.waitForIdle()

        assertTrue(reported)
    }
}
