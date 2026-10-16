package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.ui.app.InboxEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the Conversation route branch of SubshellApp's route
 * dispatch, written before it moves to ConversationRouteHost. Current
 * behaviour, not a specification: in the expanded window a conversation is
 * the detail pane of the Inbox two-pane workspace (list titled Inbox with
 * its subtitle), the conversation's title is shown, a draft typed into the
 * reply field is kept per account and item, no read mutation is sent while
 * the live inbox does not match the account, and closing returns to Main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class ConversationRouteHostComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun conversationIsTheInboxDetailPaneAndClosesThroughState() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val readMutations = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditInbox = InboxEntryPoints(onMarkRedditInboxRead = { _, id -> readMutations += id }),
            )
        }
        composeRule.waitForIdle()

        val item = SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }
        val inboxTitlesBefore = count("Inbox")
        state.openInboxItem(item)
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Conversation)
        assertEquals(inboxTitlesBefore + 1, count("Inbox"))
        assertEquals(1, count("Activity, messages & moderation"))
        assertTrue(count(item.title) >= 1)
        // The live inbox state does not match this account: no read mutation.
        assertEquals(emptyList<String>(), readMutations)

        state.updateMessageConversationDraft(account.stableId, item.id, "hello there")
        composeRule.waitForIdle()
        assertEquals(1, count("hello there"))

        state.closeConversation()
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertEquals(inboxTitlesBefore, count("Inbox"))
    }
}
