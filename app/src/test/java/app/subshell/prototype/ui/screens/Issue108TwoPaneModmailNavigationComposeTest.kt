package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE108_TWO_PANE_MODMAIL_KEEPS_INBOX_NAVIGATION_V1: with Modmail in two
 * panes, opening a conversation hid the inbox tabs, the Queue/Modmail switch
 * and refresh from the list pane.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Issue108TwoPaneModmailNavigationComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    @Test
    fun theListPaneKeepsItsTabsWhileAConversationIsOpenBesideIt() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val account = AccountSessionSummary(
            stableId = "reddit:mod",
            username = "mod_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val conversation = RedditModmailConversation(
            id = "c108",
            communityName = "subshell",
            subject = "A question",
            participantName = "someone",
            preview = "Hello",
            lastUpdatedEpochSeconds = 1L,
            unread = false,
            isInternal = false,
            messageCount = 1,
        )
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditModerationState = RedditModerationUiState(
                    accountStableId = account.stableId,
                    moderatedCommunities = setOf("subshell"),
                ),
                redditModmailState = RedditModmailUiState(
                    accountStableId = account.stableId,
                    loaded = true,
                    conversations = listOf(conversation),
                    selectedConversationId = conversation.id,
                    thread = RedditModmailThread(conversation = conversation, messages = emptyList()),
                ),
                // The two-pane workspace shows the thread in the detail pane.
                showSelectedModmailThread = false,
            )
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Messages").fetchSemanticsNodes().isNotEmpty())
    }

    // STEP3 (next01a3d): in two panes the list pane owns the list's refresh
    // and paging; a full-screen workspace refreshes the open conversation and
    // pages nothing while it is open.
    private val listLoads = mutableListOf<Pair<String, Boolean>>()
    private val conversationOpens = mutableListOf<Pair<String, String>>()
    private val appends = mutableListOf<String>()

    private val split108 = RedditModmailConversation(
        id = "c108",
        communityName = "subshell",
        subject = "A question",
        participantName = "someone",
        preview = "Hello",
        lastUpdatedEpochSeconds = 1L,
        unread = false,
        isInternal = false,
        messageCount = 1,
    )

    private fun modmailInbox(modmail: RedditModmailUiState, showSelectedThread: Boolean) {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val account = AccountSessionSummary(
            stableId = "reddit:mod",
            username = "mod_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditModerationState = RedditModerationUiState(
                    accountStableId = account.stableId,
                    moderatedCommunities = setOf("subshell"),
                ),
                redditModmailState = modmail,
                onLoadRedditModmail = { acct, force -> listLoads += acct to force },
                onAppendRedditModmail = { acct -> appends += acct },
                onOpenRedditModmailConversation = { acct, id -> conversationOpens += acct to id },
                selectedPrimaryTab = InboxPrimaryTab.MOD,
                controlledModSection = InboxModSection.MODMAIL,
                showSelectedModmailThread = showSelectedThread,
            )
        }
        composeRule.waitForIdle()
    }

    private fun refresh() {
        composeRule.onAllNodes(hasContentDescription("Refresh Modmail")).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun inTwoPanesRefreshReloadsTheListNotTheOpenConversation() {
        modmailInbox(
            RedditModmailUiState(
                accountStableId = "reddit:mod",
                loaded = true,
                conversations = listOf(split108),
                selectedConversationId = split108.id,
                thread = RedditModmailThread(conversation = split108, messages = emptyList()),
            ),
            showSelectedThread = false,
        )
        listLoads.clear()

        refresh()

        assertEquals(listOf("reddit:mod" to true), listLoads)
        assertEquals(emptyList<Pair<String, String>>(), conversationOpens)
    }

    @Test
    fun fullScreenRefreshWhileAConversationLoadsReopensThatConversation() {
        modmailInbox(
            RedditModmailUiState(
                accountStableId = "reddit:mod",
                loaded = true,
                conversations = listOf(split108),
                selectedConversationId = split108.id,
                thread = null,
                threadLoading = false,
            ),
            showSelectedThread = true,
        )
        listLoads.clear()
        conversationOpens.clear()

        refresh()

        assertEquals(listOf("reddit:mod" to "c108"), conversationOpens)
        assertEquals(emptyList<Pair<String, Boolean>>(), listLoads.filter { it.second })
    }

    @Test
    fun inTwoPanesTheListAsksForTheNextPageWhenTheReaderScrollsToItsEnd() {
        modmailInbox(
            RedditModmailUiState(
                accountStableId = "reddit:mod",
                loaded = true,
                conversations = listOf(split108) + (1..40).map { split108.copy(id = "c$it", subject = "Question $it") },
                selectedConversationId = split108.id,
                thread = RedditModmailThread(conversation = split108, messages = emptyList()),
                nextCursor = "after1",
            ),
            showSelectedThread = false,
        )
        assertEquals(emptyList<String>(), appends)

        // The paging gate asks only once the reader scrolls (ScrollPagingLoadGate).
        repeat(8) {
            composeRule.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }

        assertEquals(listOf("reddit:mod"), appends)
    }

    @Test
    fun aFullScreenConversationPagesNothing() {
        modmailInbox(
            RedditModmailUiState(
                accountStableId = "reddit:mod",
                loaded = true,
                conversations = listOf(split108),
                selectedConversationId = split108.id,
                thread = null,
                nextCursor = "after1",
            ),
            showSelectedThread = true,
        )

        assertEquals(emptyList<String>(), appends)
    }
}
