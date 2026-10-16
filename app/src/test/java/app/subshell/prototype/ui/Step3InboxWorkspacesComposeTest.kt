package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailThread
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
 * STEP3 (next01a3a, next01a3b, next01a3c, next01a3d): in split mode the
 * inbox list pane keeps the tab and Mod section the reader chose when a
 * message conversation, an inbox post or a mod-queue post opens beside it,
 * and Back shows that tab's placeholder again; the Mod workspace has its own
 * placeholders; a modmail thread sits beside its list in split mode and fills
 * the screen in single mode; on a phone an open modmail thread hides the
 * bottom navigation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3InboxWorkspacesComposeTest {
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

    private fun conversation(id: String, subject: String) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = subject,
        participantName = "someone",
        preview = "Hello",
        lastUpdatedEpochSeconds = 1L,
        unread = false,
        isInternal = false,
        messageCount = 1,
    )

    private val open = conversation("c1", "Open question")
    private val other = conversation("c2", "Other question")

    // The list pane hides its tabs while a thread is open beside it, so a
    // thread is opened only after the tabs are chosen.
    private var modmail by mutableStateOf(
        RedditModmailUiState(
            accountStableId = "reddit:mod",
            loaded = true,
            mailCommunities = setOf("subshell"),
            conversations = listOf(open, other),
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
        state.updateTabletLayoutMode(TabletLayoutMode.SPLIT)
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
                redditModmail = ModmailEntryPoints(redditModmailState = modmail),
            )
        }
        composeRule.waitForIdle()
        // As a reader does: open Inbox from the navigation.
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

    private fun selected(label: String): Int =
        composeRule.onAllNodes(tab(label) and isSelected()).fetchSemanticsNodes().size

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun openThread() {
        composeRule.runOnIdle {
            modmail = modmail.copy(
                selectedConversationId = open.id,
                thread = RedditModmailThread(conversation = open, messages = emptyList()),
            )
        }
        composeRule.waitForIdle()
    }

    private val queueItem = RedditModeratorQueueItem(
        fullname = "t3_post123",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "someone",
        summary = "A reported post",
    )
    private val queuePost = Post(
        "t3_post123",
        "subshell",
        "someone",
        "1h",
        "A reported post",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    @Test
    fun theMessagesTabStaysChosenBesideAConversationAndAfterBack() {
        show()
        click(tab("Messages"))
        assertEquals(1, count("Select a conversation"))

        state.openInboxItem(SampleData.inbox.first { it.kind == ActivityKind.MESSAGE })
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Conversation)
        assertEquals(1, selected("Messages"))
        assertEquals(0, selected("Activity"))

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        assertEquals(1, count("Select a conversation"))
        assertEquals(1, selected("Messages"))
    }

    @Test
    fun theChosenTabStaysBesideAnInboxPostAndActivityComesBackAfterIt() {
        show()
        assertEquals(1, count("Select activity"))
        click(tab("Mod"))

        state.openInboxItem(SampleData.inbox.first { it.targetPostId == "m3-terminal" && it.targetCommentId == "c2" })
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        // The list pane beside the post shows the tab the reader chose.
        assertEquals(1, selected("Mod"))
        assertEquals(0, selected("Activity"))

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        assertEquals(1, count("Select a queue item"))
        click(tab("Activity"))
        assertEquals(1, count("Select activity"))
    }

    @Test
    fun theModWorkspaceHasItsPlaceholdersAndKeepsItsSectionBesideAQueuePost() {
        show()
        click(tab("Mod"))
        assertEquals(1, count("Select a queue item"))
        click(tab("Modmail"))
        assertEquals(1, count("Select a Modmail conversation"))

        // A queue post opened while the Modmail section is chosen: the list
        // pane beside it keeps Mod · Modmail.
        composeRule.runOnIdle { state.openModeratorQueueItem(queueItem, queuePost) }
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertEquals(1, selected("Mod"))
        assertEquals(1, selected("Modmail"))
        assertEquals(0, selected("Queue"))
    }

    @Test
    fun aModmailThreadSitsBesideItsListInSplitAndFillsTheScreenInSingle() {
        show()
        click(tab("Mod"))
        click(tab("Modmail"))
        openThread()

        assertTrue(composeRule.onAllNodes(hasText("Open question")).fetchSemanticsNodes().size >= 2)
        assertTrue(count("Other question") >= 1)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Back to Modmail conversations").fetchSemanticsNodes().size)

        composeRule.runOnIdle { state.updateTabletLayoutMode(TabletLayoutMode.SINGLE) }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithContentDescription("Back to Modmail conversations").fetchSemanticsNodes().size)
        assertEquals(0, count("Other question"))
    }

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    private fun item(name: String): List<Rect> =
        composeRule.onAllNodesWithContentDescription(name).fetchSemanticsNodes().map { it.boundsInRoot }

    // As Issue224AdaptiveComposeTest: a navigation item near the bottom edge.
    private fun bottomBarShown(): Boolean {
        val bottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        return item("Inbox").any { it.bottom > bottom - px(100f) && it.left > px(80f) }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun onAPhoneAnOpenModmailThreadHidesTheBottomNavigation() {
        show()
        click(tab("Mod"))
        click(tab("Modmail"))
        assertTrue("bottom bar with the list", bottomBarShown())

        openThread()
        assertTrue("no bottom bar over the thread", !bottomBarShown())

        composeRule.runOnIdle { modmail = modmail.copy(selectedConversationId = null, thread = null) }
        composeRule.waitForIdle()
        assertTrue("bottom bar back with the list", bottomBarShown())
    }
}
