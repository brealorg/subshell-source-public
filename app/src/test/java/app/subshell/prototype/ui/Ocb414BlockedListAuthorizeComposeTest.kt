package app.subshell.prototype.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.RedditBlockedAccountsUiState
import app.subshell.prototype.ui.screens.BlockedAccountsError
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OCB-414: the blocked-list error offers Authorize, not a Retry that fails the same way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Ocb414BlockedListAuthorizeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anAuthorizationFailureOffersAuthorizeAndNothingWhileTheBrowserIsOpen() {
        var authorizes = 0
        var retries = 0
        var state by androidx.compose.runtime.mutableStateOf(
            RedditBlockedAccountsUiState(
                accountStableId = "reddit:me",
                failureKind = ContentSafetyFailureKind.AUTHORIZATION,
                retryable = true,
            ),
        )
        composeRule.setContent {
            SubshellTheme {
                BlockedAccountsError(state = state, onRetry = { retries++ }, onAuthorize = { authorizes++ })
            }
        }

        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
        composeRule.onNodeWithText("Authorize").performClick()
        assertEquals(1, authorizes)
        assertEquals(0, retries)

        state = state.copy(authorizing = true)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Authorize").assertCountEquals(0)
    }
}
