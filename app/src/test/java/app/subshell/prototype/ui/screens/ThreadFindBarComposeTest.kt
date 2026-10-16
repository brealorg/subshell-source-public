package app.subshell.prototype.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThreadFindBarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingStepsAndClosingReachTheCallbacks() {
        val calls = mutableListOf<String>()
        var typed = ""
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            ThreadFindBar(
                query = query,
                onQueryChange = { value -> query = value; typed = value },
                countLabel = "2/5",
                countDescription = "Match 2 of 5",
                onNext = { calls += "next" },
                onPrevious = { calls += "previous" },
                onClose = { calls += "close" },
            )
        }
        composeRule.onNodeWithText(THREAD_FIND_OPEN_LABEL).performTextInput("kotlin")
        composeRule.onNodeWithText("kotlin").performImeAction()
        composeRule.onNodeWithContentDescription("Next match").performClick()
        composeRule.onNodeWithContentDescription("Previous match").performClick()
        composeRule.onNodeWithContentDescription("Close find").performClick()
        composeRule.onNodeWithText("2/5").fetchSemanticsNode()

        assertEquals("kotlin", typed)
        assertEquals(listOf("next", "next", "previous", "close"), calls)
    }
}
