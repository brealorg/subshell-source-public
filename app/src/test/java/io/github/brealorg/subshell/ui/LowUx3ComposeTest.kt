package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditAccountHistoryUiState
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.data.redditAccountHistoryQueryKey
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.screens.InboxScreen
import io.github.brealorg.subshell.ui.screens.UserProfileScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUX3: LOWUX3_INBOX_EMPTY_STATE_WAITS_FOR_THE_INBOX_V1 (OCB-223),
 * LOWUX3_ONE_COMMENTS_AUTHORIZATION_CARD_V1 (OCB-285).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUx3ComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun count(text: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun theInboxDoesNotSayNoActivityWhileItLoads() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        composeRule.setContent {
            SubshellTheme {
                InboxScreen(
                    state = state,
                    redditInboxState = RedditInboxUiState(accountStableId = account.stableId, loading = true),
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, count("No activity yet"))
    }

    @Test
    fun theCommentsTabShowsOneAuthorizationCard() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        state.selectUserProfileTab("me_user", 1)
        val history = RedditAccountHistoryUiState(
            accountStableId = account.stableId,
            username = "me_user",
            kind = RedditAccountHistoryKind.COMMENTS,
            queryKey = redditAccountHistoryQueryKey(account.stableId, "me_user", RedditAccountHistoryKind.COMMENTS),
            authorizationRequired = true,
        )
        composeRule.setContent {
            SubshellTheme { UserProfileScreen(username = "me_user", state = state, redditAccountHistoryState = history) }
        }
        composeRule.waitForIdle()

        assertEquals(1, count("Enable comments"))
    }
}
