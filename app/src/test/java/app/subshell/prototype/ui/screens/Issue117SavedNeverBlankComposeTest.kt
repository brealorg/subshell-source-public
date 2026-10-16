package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE117_SAVED_ROW_NEVER_BLANK_V1: with another account's Saved state (or
 * before the entry refresh starts) the Saved row matched no branch and was
 * blank: no text, no retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue117SavedNeverBlankComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:someone",
        username = "someone",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun savedWithAnotherAccountsStateShowsLoadingNotABlankRow() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.selectUserProfileTab("someone", 2)
        val anotherAccountsSaved = RedditSavedHistoryUiState(
            accountStableId = "reddit:other",
            username = "someone",
            queryKey = redditMixedSavedHistoryQueryKey("reddit:other", "someone"),
            loaded = true,
        )
        composeRule.setContent {
            SubshellTheme {
                UserProfileScreen(
                    username = "someone",
                    state = state,
                    redditSavedHistoryState = anotherAccountsSaved,
                    // The refresh has not started yet.
                    onRefreshRedditSavedHistory = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            1,
            composeRule.onAllNodesWithText("Loading saved posts and comments…").fetchSemanticsNodes().size,
        )
    }
}
