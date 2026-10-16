package app.subshell.prototype.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a4): a post whose outcome is unknown says it may have been
 * published, and "Check again" reads back from Reddit — it never sends the
 * post a second time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CreatePostCheckAgainComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkAgainReadsBackInsteadOfSendingAgain() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            SubshellTheme {
                TextPostWriteStatus(
                    authenticated = true,
                    writeState = TextPostWriteUiState(phase = TextPostWritePhase.OUTCOME_UNKNOWN),
                    grantEnabled = true,
                    retryEnabled = true,
                    onGrantAccess = { calls += "grant" },
                    onRetry = { calls += "send" },
                    onReconcile = { calls += "check" },
                    onCancelAuthorization = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Post may have been published").fetchSemanticsNodes().size)
        composeRule.onNode(hasText("Check again") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("check"), calls)
    }
}
