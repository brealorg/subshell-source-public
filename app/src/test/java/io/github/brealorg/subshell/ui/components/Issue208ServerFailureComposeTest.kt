package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.brealorg.subshell.data.account.RedditModerationUiFailureKind
import io.github.brealorg.subshell.data.account.RedditModmailUiFailureKind
import io.github.brealorg.subshell.ui.error.REDDIT_READ_SERVER_PROBLEM_REASON
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1 (#208): the mod queue and
 * Modmail failure panels say a 5xx is a server problem, not a rejection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue208ServerFailureComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modQueueServerErrorIsAServerProblem() {
        composeRule.setContent {
            SubshellTheme {
                InboxModeratorQueueFailure(
                    failureKind = RedditModerationUiFailureKind.HTTP,
                    httpStatusCode = 500,
                    retryable = true,
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText(REDDIT_READ_SERVER_PROBLEM_REASON, substring = true).assertIsDisplayed()
    }

    @Test
    fun modmailServerErrorIsAServerProblem() {
        composeRule.setContent {
            SubshellTheme {
                InboxModmailFailure(
                    failureKind = RedditModmailUiFailureKind.HTTP,
                    httpStatusCode = 503,
                    retryable = true,
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText(REDDIT_READ_SERVER_PROBLEM_REASON, substring = true).assertIsDisplayed()
    }
}
