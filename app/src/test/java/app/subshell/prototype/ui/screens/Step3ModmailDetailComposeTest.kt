package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.CustomFeedsEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.app.MainRouteHost
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
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.ModerationQueueEntryPoints
import app.subshell.prototype.ui.app.ModmailEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (modmail07a1): a full-screen modmail conversation has a Back button
 * and system Back closes it; the two-pane detail has no Back button, and the
 * open conversation's row says it is selected.
 */
@RunWith(RobolectricTestRunner::class)
class Step3ModmailDetailComposeTest {
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

    private fun state() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun modmailState() = RedditModmailUiState(
        accountStableId = account.stableId,
        loaded = true,
        conversations = listOf(open, other),
        selectedConversationId = open.id,
        thread = RedditModmailThread(conversation = open, messages = emptyList()),
    )

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun aFullScreenConversationHasBackAndSystemBackClosesIt() {
        val state = state()
        var closed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            InboxModmailThreadSurface(
                thread = RedditModmailThread(conversation = open, messages = emptyList()),
                state = state,
                redditModmailState = modmailState(),
                accountStableId = account.stableId,
                authenticated = true,
                listState = rememberLazyListState(),
                applySystemBars = true,
                onOpenRedditModmailConversation = { _, _ -> },
                onSendRedditModmailMessage = { _, _, _, _, _ -> },
                onSetRedditModmailArchived = { _, _, _ -> },
                onSetRedditModmailUnread = { _, _, _ -> },
                onVerifyRedditModmailReadState = { _, _ -> },
                onApplyRedditModmailTriage = { _, _, _ -> },
                onCloseRedditModmailConversation = { closed += 1 },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Back to Modmail conversations").performClick()
        assertEquals(1, closed)
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(2, closed)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp")
    fun theTwoPaneDetailHasNoBackButtonAndTheOpenRowIsSelected() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = state()
        state.navigate(MainDestination.INBOX)
        val route = state.currentNavigationRoute as SubshellRoute.Main
        composeRule.setContent {
            MainRouteHost(
                customFeeds = CustomFeedsEntryPoints(),
                redditFeed = FeedEntryPoints(),
                redditCommunity = CommunityEntryPoints(),
                redditModeration = ModerationQueueEntryPoints(
                    redditModerationState = RedditModerationUiState(
                        accountStableId = account.stableId,
                        moderatedCommunities = setOf("subshell"),
                        directoryLoaded = true,
                    ),
                ),
                redditModmail = ModmailEntryPoints(redditModmailState = modmailState()),
                redditInbox = InboxEntryPoints(
                    redditInboxState = RedditInboxUiState(accountStableId = account.stableId, loaded = true),
                ),
                state = state,
                route = route,
                padding = PaddingValues(),
                screenStateHolder = rememberSaveableStateHolder(),
                density = LocalDensity.current,
                layoutDirection = LocalLayoutDirection.current,
                navInteractionSources = remember { MainDestination.entries.associateWith { MutableInteractionSource() } },
                navigationBarInsetHeight = 0.dp,
                expandedWindowLayout = true,
                modmailWorkspaceListDetail = true,
                expandedMainLayout = true,
                chromeScrollConnection = remember { object : NestedScrollConnection {} },
                topBarHiddenFraction = 0f,
                bottomBarHiddenFraction = 0f,
                stableMainContentBottomPadding = 96.dp,
                mainYouProfileContent = { _, _, _, _, _, _ -> },
                inboxUnreadCount = 0,
                activeCustomFeed = null,
                authenticatedLandingPending = false,
                authenticatedHomeWithoutSubscriptions = false,
                pendingAuthenticatedLandingAccountIdState = remember { mutableStateOf<String?>(null) },
                inboxContextualSelectionState = remember { mutableStateOf<InboxContextualSelection?>(null) },
                inboxPrimaryTabState = remember { mutableStateOf(InboxPrimaryTab.MOD) },
                inboxModSectionState = remember { mutableStateOf(InboxModSection.MODMAIL) },
                suppressYouClickState = remember { mutableStateOf(false) },
                accountSwitcherVisibleState = remember { mutableStateOf(false) },
                customFeedFabVisibleState = remember { mutableStateOf(false) },
                customFeedCreateRequestedState = remember { mutableStateOf(false) },
                activeCustomFeedPathState = remember { mutableStateOf<String?>(null) },
            )
        }
        composeRule.waitForIdle()

        // The open conversation is in the list and in the detail pane beside it.
        assertTrue(composeRule.onAllNodes(hasText("Open question")).fetchSemanticsNodes().size >= 2)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Back to Modmail conversations").fetchSemanticsNodes().size)
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText("Open question"))).assertIsSelected()
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText("Other question"))).assertIsNotSelected()
    }

    // STEP3 (next01a3d): the two-pane detail says what to do before a
    // conversation is chosen and while it loads.
    private fun twoPane(modmail: RedditModmailUiState) {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = state()
        state.navigate(MainDestination.INBOX)
        val route = state.currentNavigationRoute as SubshellRoute.Main
        composeRule.setContent {
            MainRouteHost(
                customFeeds = CustomFeedsEntryPoints(),
                redditFeed = FeedEntryPoints(),
                redditCommunity = CommunityEntryPoints(),
                redditModeration = ModerationQueueEntryPoints(
                    redditModerationState = RedditModerationUiState(
                        accountStableId = account.stableId,
                        moderatedCommunities = setOf("subshell"),
                        directoryLoaded = true,
                    ),
                ),
                redditModmail = ModmailEntryPoints(redditModmailState = modmail),
                redditInbox = InboxEntryPoints(
                    redditInboxState = RedditInboxUiState(accountStableId = account.stableId, loaded = true),
                ),
                state = state,
                route = route,
                padding = PaddingValues(),
                screenStateHolder = rememberSaveableStateHolder(),
                density = LocalDensity.current,
                layoutDirection = LocalLayoutDirection.current,
                navInteractionSources = remember { MainDestination.entries.associateWith { MutableInteractionSource() } },
                navigationBarInsetHeight = 0.dp,
                expandedWindowLayout = true,
                modmailWorkspaceListDetail = true,
                expandedMainLayout = true,
                chromeScrollConnection = remember { object : NestedScrollConnection {} },
                topBarHiddenFraction = 0f,
                bottomBarHiddenFraction = 0f,
                stableMainContentBottomPadding = 96.dp,
                mainYouProfileContent = { _, _, _, _, _, _ -> },
                inboxUnreadCount = 0,
                activeCustomFeed = null,
                authenticatedLandingPending = false,
                authenticatedHomeWithoutSubscriptions = false,
                pendingAuthenticatedLandingAccountIdState = remember { mutableStateOf<String?>(null) },
                inboxContextualSelectionState = remember { mutableStateOf<InboxContextualSelection?>(null) },
                inboxPrimaryTabState = remember { mutableStateOf(InboxPrimaryTab.MOD) },
                inboxModSectionState = remember { mutableStateOf(InboxModSection.MODMAIL) },
                suppressYouClickState = remember { mutableStateOf(false) },
                accountSwitcherVisibleState = remember { mutableStateOf(false) },
                customFeedFabVisibleState = remember { mutableStateOf(false) },
                customFeedCreateRequestedState = remember { mutableStateOf(false) },
                activeCustomFeedPathState = remember { mutableStateOf<String?>(null) },
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp")
    fun withoutAChosenConversationTheDetailAsksForOne() {
        twoPane(modmailState().copy(selectedConversationId = null, thread = null))

        assertEquals(1, composeRule.onAllNodes(hasText("Select a Modmail conversation")).fetchSemanticsNodes().size)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp")
    fun aChosenConversationThatIsLoadingSaysSo() {
        twoPane(modmailState().copy(thread = null, threadLoading = true))

        assertTrue(composeRule.onAllNodes(hasText("Loading conversation…")).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, composeRule.onAllNodes(hasText("Select a Modmail conversation")).fetchSemanticsNodes().size)
    }
}
