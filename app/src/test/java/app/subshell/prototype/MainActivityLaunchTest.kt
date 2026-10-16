package app.subshell.prototype

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HARNESS05: the real MainActivity under Robolectric. This build has no
 * native Client ID and the JVM has no Android Keystore, so a fresh install
 * lands on the connect gate — which is SubshellApp composed by onCreate with
 * every entry-point bundle wired. The test pins that the activity launches
 * into the gate, that the gate's Client ID path goes through the connect
 * wiring, and that the activity survives a configuration change.
 * Extractions out of MainActivity (bundle builders, authorization launches)
 * must keep this green.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MainActivityLaunchTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val workManager = object : ExternalResource() {
        override fun before() {
            ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(workManager)
        .around(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    @Test
    fun aFreshInstallWithoutAClientIdLaunchesIntoTheConnectGate() {
        composeRule.onNodeWithText("Connect subshell to Reddit").assertIsDisplayed()
        composeRule.onNodeWithText("Show me how to get a Client ID").assertIsDisplayed()
        composeRule.onNodeWithText("I already have a Client ID").assertIsDisplayed()
    }

    @Test
    fun theClientIdPathOpensTheEntryThroughTheConnectWiring() {
        composeRule.onNodeWithText("I already have a Client ID").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Paste your Client ID").assertIsDisplayed()
        composeRule.onNodeWithText("Save Client ID").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Connect subshell to Reddit").assertIsDisplayed()
    }

    @Test
    fun theActivitySurvivesAConfigurationChange() {
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Connect subshell to Reddit").assertIsDisplayed()
    }
}
