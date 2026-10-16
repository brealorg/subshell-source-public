package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.ModerationQueueEntryPoints
import app.subshell.prototype.ui.app.ModmailEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (modmail02, modmail04a1, modmail05): SubshellApp hands the open
 * Modmail conversation's actions to the ViewModel entry points with the active
 * account and the conversation: archive, triage, send and choosing a queue
 * from the Inbox, and triage also from the Inbox pane beside an open message
 * conversation and beside a post opened from the mod queue (the three hosts
 * that show the Inbox).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3ModmailRouteWiringComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:mod",
        username = "mod_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val conversation = RedditModmailConversation(
        id = "c1",
        communityName = "subshell",
        subject = "Open question",
        participantName = "someone",
        preview = "Hello",
        lastUpdatedEpochSeconds = 1L,
        unread = false,
        isInternal = false,
        messageCount = 1,
    )
    private val events = mutableListOf<String>()

    // The list pane hides its tabs while a thread is open beside it, so a
    // thread is opened only after the Mod and Modmail tabs are chosen.
    private var modmail by mutableStateOf(
        RedditModmailUiState(
            accountStableId = "reddit:mod",
            loaded = true,
            mailCommunities = setOf("subshell"),
            conversations = emptyList(),
        ),
    )
    private lateinit var state: SubshellState

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        app.subshell.prototype.backup.SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
    }

    private fun show() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditModeration = ModerationQueueEntryPoints(
                    redditModerationState = RedditModerationUiState(
                        accountStableId = account.stableId,
                        moderatedCommunities = setOf("subshell"),
                        directoryLoaded = true,
                    ),
                ),
                redditInbox = InboxEntryPoints(
                    redditInboxState = RedditInboxUiState(accountStableId = account.stableId, loaded = true),
                ),
                redditModmail = ModmailEntryPoints(
                    redditModmailState = modmail,
                    onSelectRedditModmailListMode = { acct, mode -> events += "mode:$acct:$mode" },
                    onSendRedditModmailMessage = { acct, id, body, _, _ -> events += "send:$acct:$id:$body" },
                    onSetRedditModmailArchived = { acct, id, archived -> events += "archived:$acct:$id:$archived" },
                    onApplyRedditModmailTriage = { acct, id, action -> events += "triage:$acct:$id:$action" },
                ),
            )
        }
        composeRule.waitForIdle()
        // As a reader does: open Inbox from the navigation rail.
        composeRule.onAllNodes(hasContentDescription("Inbox") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tab(label: String) =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun openModmailSection(beforeThread: () -> Unit = {}) {
        click(tab("Mod"))
        click(tab("Modmail"))
        beforeThread()
        composeRule.runOnIdle {
            modmail = modmail.copy(
                conversations = listOf(conversation),
                selectedConversationId = conversation.id,
                thread = RedditModmailThread(conversation = conversation, messages = emptyList()),
            )
        }
        composeRule.waitForIdle()
    }

    private fun highlight() {
        click(hasContentDescription("Modmail triage actions"))
        click(hasText("Highlight") and hasClickAction())
    }

    private val highlighted = "triage:reddit:mod:c1:${RedditModmailTriageAction.HIGHLIGHT}"

    @Test
    fun theInboxHandsArchiveTriageSendAndQueueToTheEntryPoints() {
        show()
        openModmailSection(beforeThread = {
            // The queue field (it shows the current queue), not the Mod section's Queue tab.
            click(hasText("Queue") and hasText("Active") and hasClickAction())
            click(hasText("Archived") and hasClickAction())
        })

        click(hasText("Archive") and hasClickAction())
        highlight()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Hi")
        composeRule.waitForIdle()
        click(hasContentDescription("Send Modmail reply"))

        assertTrue(events.toString(), "archived:reddit:mod:c1:true" in events)
        assertTrue(events.toString(), highlighted in events)
        assertTrue(events.toString(), "send:reddit:mod:c1:Hi" in events)
        assertTrue(events.toString(), "mode:reddit:mod:${RedditModmailListMode.ARCHIVED}" in events)
    }

    @Test
    fun theInboxBesideAMessageConversationHandsTriageOn() {
        show()
        state.openInboxItem(SampleData.inbox.first { it.kind == ActivityKind.MESSAGE })
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Conversation)

        openModmailSection()
        highlight()

        assertEquals(listOf(highlighted), events.filter { it.startsWith("triage:") })
    }

    @Test
    fun theInboxBesideAPostFromTheModQueueHandsTriageOn() {
        show()
        state.navigate(MainDestination.INBOX)
        composeRule.waitForIdle()
        state.openModeratorQueueItem(
            item = RedditModeratorQueueItem(
                fullname = "t3_post123",
                kind = RedditModeratorQueueKind.POST,
                communityName = "subshell",
                author = "someone",
                summary = "A reported post",
            ),
            resolvedPost = Post(
                "t3_post123",
                "subshell",
                "someone",
                "1h",
                "A reported post",
                score = 1,
                comments = 0,
                kind = PostKind.TEXT,
            ),
        )
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)

        openModmailSection()
        highlight()

        assertEquals(listOf(highlighted), events.filter { it.startsWith("triage:") })
    }
}
