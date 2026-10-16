package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.DirectChatStartPhase
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.NewMessageRouteHost
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The new-message route in its own host: the people search asks the
 * discovery loader for a typed query, and the post-send handoff
 * (SUBSHELL_MESSAGES02D5D) refreshes the inbox once when a started chat has
 * no authoritative item yet, rather than routing on the start response.
 *
 * STEP3 (messages02d5d): a started chat opens the inbox's own Direct Chat
 * item for that person when the inbox already has it (no extra refresh);
 * without it, after the one refresh settles, the route goes back to the
 * inbox; while the handoff is pending the screen says "Message sent", Send
 * is off and typing does not clear the started chat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class NewMessageRouteHostComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingAPersonQueryAsksTheDiscoveryLoaderForIt() {
        val state = newState()
        val queries = mutableListOf<String>()
        host(
            state,
            onSearchPeople = { _, query -> queries += query },
        )

        composeRule.onNodeWithText("Search Reddit users").performTextInput("alice")
        // The screen debounces the query by 300 ms before it asks the loader.
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()

        assertTrue(queries.isNotEmpty())
        assertTrue(queries.last().contains("alice"))
    }

    @Test
    fun aStartedChatWithoutAnAuthoritativeItemRefreshesTheInboxOnce() {
        val state = newState()
        val refreshes = mutableListOf<Boolean>()
        host(
            state,
            directChatStartState = DirectChatStartUiState(
                phase = DirectChatStartPhase.SUCCEEDED,
                accountStableId = state.activeAccountId,
                participantUsername = "alice",
                completionRevision = 1L,
            ),
            onLoadInbox = { _, force -> refreshes += force },
        )
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        assertEquals(listOf(true), refreshes)
    }

    @Test
    fun theComposerRendersWithoutAStartedChat() {
        host(newState())

        composeRule.onNodeWithText("New message").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Who do you want to message?")
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // REDDITURL04_COMPOSE_LINK_V1
    @Test
    fun aComposeLinkOpensWithTheRecipientChosenAndTheTextWritten() {
        val state = newState()
        state.openRedditTarget(RedditTarget.Compose("bob", subject = "Hi", message = "About your post"))
        host(state)

        assertTrue(
            composeRule.onAllNodesWithText("bob", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("About your post", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(null, state.consumeNewMessagePrefill())
    }

    private fun startedChat(participant: String = "bob") = DirectChatStartUiState(
        phase = DirectChatStartPhase.SUCCEEDED,
        accountStableId = "reddit:alice",
        participantUsername = participant,
        completionRevision = 1L,
    )

    private fun directChatWith(sender: String) = InboxItem(
        id = "t4_chat_$sender",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = sender,
        age = "now",
        preview = "hello",
    )

    @Test
    fun aStartedChatOpensTheInboxsDirectChatItemWithoutAnotherRefresh() {
        val state = newState()
        val refreshes = mutableListOf<Boolean>()
        var resets = 0
        val item = directChatWith("bob")
        handoffHost(
            state,
            directChatStartState = startedChat(),
            inbox = RedditInboxUiState(accountStableId = "reddit:alice", items = listOf(item), loaded = true),
            onLoadInbox = { _, force -> refreshes += force },
            onReset = { resets += 1 },
        )
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        val route = state.currentNavigationRoute
        assertTrue(route is SubshellRoute.Conversation)
        assertEquals(item.id, (route as SubshellRoute.Conversation).inboxItemId)
        assertEquals(1, resets)
        assertEquals(emptyList<Boolean>(), refreshes)
    }

    @Test
    fun withoutTheItemTheRouteGoesBackToTheInboxAfterTheRefreshSettles() {
        val state = newState()
        val refreshes = mutableListOf<Boolean>()
        var resets = 0
        handoffHost(
            state,
            directChatStartState = startedChat(),
            inbox = RedditInboxUiState(accountStableId = "reddit:alice", loaded = true),
            onLoadInbox = { _, force -> refreshes += force },
            onReset = { resets += 1 },
        )
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        assertEquals(listOf(true), refreshes)
        assertEquals(0, resets)

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.waitForIdle()

        assertEquals(1, resets)
        assertFalse(state.currentNavigationRoute is SubshellRoute.Secondary)
        assertEquals(listOf(true), refreshes)
    }

    @Test
    fun whileTheHandoffIsPendingSendIsOffAndTypingKeepsTheStartedChat() {
        val state = newState()
        state.openRedditTarget(RedditTarget.Compose("bob", message = "Hi"))
        var resets = 0
        handoffHost(
            state,
            directChatStartState = startedChat(),
            // A loading inbox holds the handoff, so only the screen is observed.
            inbox = RedditInboxUiState(accountStableId = "reddit:alice", loading = true),
            onReset = { resets += 1 },
        )

        assertEquals(1, composeRule.onAllNodesWithText("Message sent").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Opening Direct Chat…").fetchSemanticsNodes().size)
        val send = composeRule.onAllNodes(
            hasContentDescription("Sending message") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
        ).onFirst()
        assertTrue(send.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) != null)

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(" there")
        composeRule.waitForIdle()

        assertEquals(0, resets)
    }

    private fun handoffHost(
        state: SubshellState,
        directChatStartState: DirectChatStartUiState,
        inbox: RedditInboxUiState,
        onLoadInbox: (String, Boolean) -> Unit = { _, _ -> },
        onReset: () -> Unit = {},
    ) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                NewMessageRouteHost(
                    state = state,
                    redditCommunity = CommunityEntryPoints(
                        redditCommunityDiscoveryState = RedditCommunityDiscoveryUiState(),
                    ),
                    redditInbox = InboxEntryPoints(redditInboxState = inbox, onLoadRedditInbox = onLoadInbox),
                    directChatStartState = directChatStartState,
                    onStartRedditDirectChat = {},
                    onResetRedditDirectChatStartState = onReset,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** The people search only runs for an authenticated account. */
    private fun newState(): SubshellState {
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.openSecondary(SecondaryDestination.NEW_MESSAGE)
        return state
    }

    private fun host(
        state: SubshellState,
        directChatStartState: DirectChatStartUiState = DirectChatStartUiState(),
        onSearchPeople: (String, String) -> Unit = { _, _ -> },
        onLoadInbox: (String, Boolean) -> Unit = { _, _ -> },
    ) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                NewMessageRouteHost(
                    state = state,
                    redditCommunity = CommunityEntryPoints(
                        redditCommunityDiscoveryState = RedditCommunityDiscoveryUiState(),
                        onLoadRedditCommunityDiscovery = { account, query, _, _, _ ->
                            onSearchPeople(account, query.orEmpty())
                        },
                    ),
                    redditInbox = InboxEntryPoints(onLoadRedditInbox = onLoadInbox),
                    directChatStartState = directChatStartState,
                    onStartRedditDirectChat = {},
                    onResetRedditDirectChatStartState = {},
                )
            }
        }
        composeRule.waitForIdle()
    }
}
