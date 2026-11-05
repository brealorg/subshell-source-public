package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.ui.SubshellRoute
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (inbox01a12): an inbox item with no post to open (and no message
 * thread) opens its own detail and is marked read, without a Reddit lookup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3InboxFallbackDetailComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun anItemWithNothingToOpenShowsItsOwnDetailAndIsMarkedRead() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val account = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val mention = InboxItem(
            id = "t1_mention",
            kind = ActivityKind.MENTION,
            title = "A mention without a post",
            sender = "someone_else",
            age = "1h",
            preview = "Hello",
            unread = true,
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val marked = mutableListOf<String>()
        var lookups = 0
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(accountStableId = account.stableId, items = listOf(mention), loaded = true),
                onResolveRedditInboxTarget = { _, _, _ -> lookups += 1 },
                onMarkRedditInboxRead = { _, id -> marked += id },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("A mention without a post", substring = true).performClick()
        composeRule.waitForIdle()

        val route = state.currentNavigationRoute
        assertEquals(mention.id, (route as SubshellRoute.Conversation).inboxItemId)
        assertEquals(listOf(mention.id), marked)
        assertEquals(0, lookups)
    }
}
