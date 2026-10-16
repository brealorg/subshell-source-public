package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.DirectChatStartPhase
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.RedditPeopleSearchItem
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
 * STEP3 (messages02d5b, messages02d5b2): a new message picks its recipient
 * from the people search and sends the first message with the same docked
 * composer a conversation uses (its formatting actions are there), and only
 * Send starts the chat. A start whose outcome is unknown offers "Refresh
 * Inbox" and blocks Send; a definite failure offers no refresh, and typing
 * afterwards clears it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3NewMessageSendComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:alice"
    private val starts = mutableListOf<DirectChatStartRequest>()
    private val inboxLoads = mutableListOf<Pair<String, Boolean>>()
    private var resets = 0

    private fun newState(): SubshellState {
        val alice = AccountSessionSummary(
            stableId = account,
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
        people: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
        start: () -> DirectChatStartUiState = { DirectChatStartUiState() },
    ) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                NewMessageRouteHost(
                    state = state,
                    redditCommunity = CommunityEntryPoints(
                        redditCommunityDiscoveryState = RedditCommunityDiscoveryUiState(peopleSearch = people),
                    ),
                    redditInbox = InboxEntryPoints(onLoadRedditInbox = { id, force -> inboxLoads += id to force }),
                    directChatStartState = start(),
                    onStartRedditDirectChat = { starts += it },
                    onResetRedditDirectChatStartState = { resets += 1 },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun clickable(matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onAllNodes(matcher and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).onFirst()

    private fun isEnabled(node: SemanticsNodeInteraction): Boolean =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null

    private fun click(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private val sendButton get() = clickable(hasContentDescription("Send message"))

    private fun typeInEditor(text: String) {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        composeRule.waitForIdle()
    }

    /** A compose link picks the recipient, so the composer is open at once. */
    private fun withRecipient(state: SubshellState, name: String = "bob") {
        state.openRedditTarget(RedditTarget.Compose(name))
    }

    @Test
    fun aChosenPersonGetsTheFirstMessageOnlyWhenSendIsTapped() {
        val state = newState()
        host(
            state,
            people = RedditPeopleSearchUiState(
                accountStableId = account,
                searchQuery = "bo",
                items = listOf(RedditPeopleSearchItem(username = "bob", title = "bob", description = "")),
                loaded = true,
            ),
        )

        composeRule.onNodeWithText("Search Reddit users").performTextInput("bo")
        composeRule.waitForIdle()
        click(clickable(hasText("u/bob")))

        assertEquals(1, composeRule.onAllNodesWithText("Direct Chat recipient").fetchSemanticsNodes().size)

        typeInEditor("Hi")
        assertEquals(emptyList<DirectChatStartRequest>(), starts)

        click(sendButton)

        assertEquals(
            listOf(DirectChatStartRequest(accountStableId = account, participantUsername = "bob", text = "Hi", media = null)),
            starts,
        )
    }

    @Test
    fun theFirstMessageUsesTheSharedComposerWithItsFormattingActions() {
        val state = newState()
        withRecipient(state)
        host(state)

        // #232: the formatting actions wait behind Aa.
        composeRule.onNode(hasContentDescription("Formatting") and isToggleable())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        for (label in listOf("Bold", "Italic", "Link")) {
            assertEquals(label, 1, composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size)
        }
        assertFalse(isEnabled(sendButton))
        assertEquals(emptyList<DirectChatStartRequest>(), starts)
    }

    @Test
    fun aStartWhoseOutcomeIsUnknownOffersRefreshInboxAndBlocksSend() {
        val state = newState()
        withRecipient(state)
        var start by mutableStateOf(DirectChatStartUiState())
        host(state, start = { start })
        typeInEditor("Hi")

        composeRule.runOnIdle {
            start = DirectChatStartUiState(
                phase = DirectChatStartPhase.OUTCOME_UNKNOWN,
                accountStableId = account,
                participantUsername = "bob",
                message = "Reddit may have started this chat.",
                completionRevision = 1L,
            )
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Reddit may have started this chat.").fetchSemanticsNodes().size)
        assertFalse(isEnabled(sendButton))
        click(clickable(hasText("Refresh Inbox")))

        assertEquals(listOf(account to true), inboxLoads)
        assertEquals(emptyList<DirectChatStartRequest>(), starts)
    }

    @Test
    fun aDefiniteFailureOffersNoRefreshAndTypingClearsIt() {
        val state = newState()
        withRecipient(state)
        host(
            state,
            start = {
                DirectChatStartUiState(
                    phase = DirectChatStartPhase.FAILED,
                    accountStableId = account,
                    participantUsername = "bob",
                    message = "Reddit refused the chat.",
                    completionRevision = 1L,
                )
            },
        )

        assertEquals(1, composeRule.onAllNodesWithText("Reddit refused the chat.").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Refresh Inbox").fetchSemanticsNodes().size)
        val before = resets

        typeInEditor("Again")

        assertTrue(resets > before)
    }
}
