package app.subshell.prototype

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class SubshellNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainDestinationsAndPostContextAreReachable() {
        dismissOnboardingIfNeeded()

        composeRule.onNodeWithContentDescription("Subs").performClick()
        composeRule.onNodeWithText("Your subreddits").assertIsDisplayed()
        composeRule.onNodeWithText("Discover subreddits").performClick()
        composeRule.onNodeWithText("Recommended for you").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Inbox").performClick()
        composeRule.onNodeWithText("2 unread").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.onNodeWithText("My distraction-free Linux setup after six months").performClick()
        composeRule.onNodeWithText("Add a comment").assertIsDisplayed()
        composeRule.onNodeWithText("bash").assertIsDisplayed()
    }

    @Test
    fun systemBackFromBackupRestoreReturnsToSettings() {
        dismissOnboardingIfNeeded()

        composeRule.onNodeWithContentDescription("You").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Saved setups").performClick()
        composeRule.onNodeWithText("Backup & restore").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Saved setups").assertIsDisplayed()
    }

    private fun dismissOnboardingIfNeeded() {
        if (composeRule.onAllNodesWithText("Use Right").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Use Right").performClick()
        }
        if (composeRule.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Later").performClick()
        }
    }
}
