package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.ui.screens.InboxScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOXUNREAD01_NOTHING_UNREAD_LOADS_NO_OLDER_PAGES_V1 (found on the Pixel
 * while checking #279): a drag on Activity › Unread with nothing unread read
 * every older Inbox page in turn under "You're all caught up".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class InboxUnread01NoOlderPagesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val readReply = InboxItem(
        id = "t1_read1",
        kind = ActivityKind.REPLY,
        title = "Replied to your comment",
        sender = "someone",
        age = "2h",
        preview = "Already read",
        unread = false,
    )

    @Test
    fun nothingUnreadAsksForNoOlderPageButAllStillDoes() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val appends = mutableListOf<String>()
        composeRule.setContent {
            SubshellTheme {
                InboxScreen(
                    state = state,
                    redditInboxState = RedditInboxUiState(
                        accountStableId = account.stableId,
                        items = listOf(readReply),
                        loaded = true,
                        nextCursor = "t1_older",
                    ),
                    onAppendRedditInbox = { accountId -> appends += accountId },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Unread").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), appends)

        composeRule.onNodeWithText("Unread").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals(listOf(account.stableId), appends)
    }
}
