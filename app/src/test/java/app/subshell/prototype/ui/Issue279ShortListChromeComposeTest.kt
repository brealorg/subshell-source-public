package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.rememberChromeScrollConnection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE279_ONLY_A_LIST_THAT_MOVES_HIDES_THE_CHROME_V1 (#279, reader on 0.86):
 * with three Inbox messages an upward drag hid the top bar and the bottom
 * navigation although nothing scrolled, and they never came back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue279ShortListChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aListThatFitsTheScreenLeavesTheBarsShown() {
        val state = list(items = 3)

        composeRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertTrue(state.chromeVisible)
    }

    @Test
    fun aListThatScrollsStillHidesAndShowsThem() {
        val state = list(items = 60)

        composeRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertFalse(state.chromeVisible)

        composeRule.onNodeWithTag("list").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertTrue(state.chromeVisible)
    }

    private fun list(items: Int): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState()
        composeRule.setContent {
            val connection = rememberChromeScrollConnection(state = state, density = LocalDensity.current)
            Box(Modifier.fillMaxSize().nestedScroll(connection)) {
                LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                    items(items) { index ->
                        Text("Message $index", Modifier.fillMaxWidth().height(72.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return state
    }
}
