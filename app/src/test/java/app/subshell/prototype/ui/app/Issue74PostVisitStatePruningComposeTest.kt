package app.subshell.prototype.ui.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE74_POST_VISIT_STATE_IS_PRUNED_V1: a post visit's saved screen state was
 * kept for the life of the process after the visit left the route stack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue74PostVisitStatePruningComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var liveVisits by mutableStateOf(setOf(1, 2))
    private var shownVisit by mutableStateOf(1)
    private var freshStates = 0

    private fun show(visit: Int) {
        composeRule.runOnIdle { shownVisit = visit }
        composeRule.waitForIdle()
    }

    @Test
    fun aVisitThatLeftTheStackLosesItsStateAndALiveOneKeepsIt() {
        composeRule.setContent {
            val holder = rememberPostVisitPruningStateHolder(liveVisits)
            holder.SaveableStateProvider("post-t3_p74-$shownVisit") {
                val stateId = rememberSaveable { freshStates++ }
                Text("state $stateId")
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("state 0").assertExists()

        show(2)
        composeRule.onNodeWithText("state 1").assertExists()

        // Visit 1 is still on the stack: its state comes back.
        show(1)
        composeRule.onNodeWithText("state 0").assertExists()

        // Visit 1 is popped while visit 2 shows; its saved state is dropped.
        show(2)
        composeRule.runOnIdle { liveVisits = setOf(2) }
        composeRule.waitForIdle()
        show(1)
        composeRule.onNodeWithText("state 2").assertExists()
    }

    @Test
    fun onlyPostVisitKeysCarryAVisitId() {
        assertEquals(17, postVisitIdOfStateKey("post-t3_abc-17"))
        assertNull(postVisitIdOfStateKey("main-HOME"))
        assertNull(postVisitIdOfStateKey("public-user-workspace-someone"))
    }
}
