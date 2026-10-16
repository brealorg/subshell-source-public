package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiFailureKind
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.screens.CONVERSATION_RESTORE_MAX_EXTRA_PAGES
import app.subshell.prototype.ui.screens.ConversationRouteStep
import app.subshell.prototype.ui.screens.conversationRouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE48_CONVERSATION_WAITS_FOR_THE_LIVE_INBOX_V1: a conversation open when
 * the process died (or the activity was recreated) comes back. Live Reddit
 * conversations exist only in the live inbox, which loads after start, so the
 * restored route waits for it, pages it when the item is older, and is left
 * only when the loaded inbox does not have it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue48ConversationRestoreComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val live = InboxItem(
        id = "t4_restored1",
        kind = ActivityKind.MESSAGE,
        title = "Restored thread title",
        sender = "someone_else",
        age = "1h",
        preview = "Hi",
    )
    private val inbox = mutableStateOf(RedditInboxUiState())
    private val appends = mutableListOf<String>()

    private fun signedIn(token: String? = null) = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
        initialProcessSnapshotToken = token,
    )

    private fun restoredWhileTheInboxLoads(): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val before = signedIn()
        before.openInboxItem(live)
        assertTrue(before.currentNavigationRoute is SubshellRoute.Conversation)
        val restored = signedIn(encodeSubshellProcessSnapshot(before.processStateSnapshot()))
        assertTrue(restored.currentNavigationRoute is SubshellRoute.Conversation)

        inbox.value = RedditInboxUiState(accountStableId = account.stableId, loading = true)
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = restored,
                redditInbox = InboxEntryPoints(
                    redditInboxState = inbox.value,
                    onAppendRedditInbox = { accountId -> appends += accountId },
                ),
            )
        }
        composeRule.waitForIdle()
        assertTrue(restored.currentNavigationRoute is SubshellRoute.Conversation)
        assertEquals(
            1,
            composeRule.onAllNodesWithContentDescription("Loading conversation").fetchSemanticsNodes().size,
        )
        return restored
    }

    @Test
    fun theRestoredConversationWaitsForTheLiveInboxAndThenShows() {
        val restored = restoredWhileTheInboxLoads()

        inbox.value = RedditInboxUiState(accountStableId = account.stableId, items = listOf(live), loaded = true)
        composeRule.waitForIdle()

        assertTrue(restored.currentNavigationRoute is SubshellRoute.Conversation)
        assertTrue(composeRule.onAllNodesWithText("Restored thread title").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun anOlderConversationIsFoundOnTheNextInboxPage() {
        val restored = restoredWhileTheInboxLoads()

        inbox.value = RedditInboxUiState(accountStableId = account.stableId, loaded = true, nextCursor = "t4_page2")
        composeRule.waitForIdle()
        assertEquals(listOf(account.stableId), appends)
        assertTrue(restored.currentNavigationRoute is SubshellRoute.Conversation)

        inbox.value = RedditInboxUiState(accountStableId = account.stableId, items = listOf(live), loaded = true)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Restored thread title").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aConversationTheLoadedInboxDoesNotHaveIsLeft() {
        val restored = restoredWhileTheInboxLoads()

        inbox.value = RedditInboxUiState(accountStableId = account.stableId, loaded = true)
        composeRule.waitForIdle()

        assertTrue(restored.currentNavigationRoute !is SubshellRoute.Conversation)
    }

    @Test
    fun theRule() {
        fun step(inbox: RedditInboxUiState, found: Boolean = false, authenticated: Boolean = true, pages: Int = 0) =
            conversationRouteStep(found, authenticated, account.stableId, inbox, pages)
        val loaded = RedditInboxUiState(accountStableId = account.stableId, loaded = true)

        assertEquals(ConversationRouteStep.SHOW, step(loaded, found = true))
        assertEquals(ConversationRouteStep.LEAVE, step(loaded, authenticated = false))
        assertEquals(ConversationRouteStep.WAIT, step(RedditInboxUiState()))
        assertEquals(ConversationRouteStep.WAIT, step(RedditInboxUiState(accountStableId = account.stableId)))
        assertEquals(ConversationRouteStep.WAIT, step(loaded.copy(appending = true)))
        assertEquals(
            ConversationRouteStep.LEAVE,
            step(loaded.copy(failureKind = RedditInboxUiFailureKind.entries.first())),
        )
        assertEquals(ConversationRouteStep.LOAD_MORE, step(loaded.copy(nextCursor = "c")))
        assertEquals(
            ConversationRouteStep.LEAVE,
            step(loaded.copy(nextCursor = "c"), pages = CONVERSATION_RESTORE_MAX_EXTRA_PAGES),
        )
        assertEquals(ConversationRouteStep.LEAVE, step(loaded))
    }
}
