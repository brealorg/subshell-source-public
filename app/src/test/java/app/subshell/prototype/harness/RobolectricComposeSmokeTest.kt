package app.subshell.prototype.harness

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HARNESS01 smoke: proves that a composable renders on the JVM through
 * Robolectric so that composable wiring can be behaviour-tested without a
 * device. The SDK level is pinned to the highest API Robolectric 4.16
 * knows (36); compileSdk is 37, which it reports as not available. Bump
 * the level together with the Robolectric version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RobolectricComposeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aComposableRendersOnTheJvm() {
        composeRule.setContent { Text("harness ready") }
        composeRule.onNodeWithText("harness ready").assertIsDisplayed()
    }
}
