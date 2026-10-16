package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02d2b): in an open direct chat, editing the composer sends
 * typing (true while there is text, false when it is cleared) and sending a
 * message stops it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DirectChatTypingGateComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    @Test
    fun editingSendsTypingAndSendingStopsIt() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "me",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.navigate(MainDestination.INBOX)
        val item = InboxItem(
            id = "t4_chat",
            kind = ActivityKind.MESSAGE,
            title = "[direct chat room]",
            sender = "peer",
            age = "now",
            preview = "hello",
        )
        state.openInboxItem(item)
        val route = state.currentNavigationRoute as SubshellRoute.Conversation
        val typing = mutableListOf<Boolean>()

        composeRule.setContent {
            ConversationRouteHost(
                redditModeration = ModerationQueueEntryPoints(),
                redditModmail = ModmailEntryPoints(),
                commentMutations = CommentMutationEntryPoints(),
                redditInbox = InboxEntryPoints(),
                redditMessaging = MessagingEntryPoints(
                    onUpdateRedditDirectChatTyping = { _, _, _, isTyping -> typing += isTyping },
                ),
                state = state,
                route = route,
                padding = PaddingValues(),
                expandedWindowLayout = false,
                inboxPrimaryTabState = mutableStateOf(InboxPrimaryTab.MESSAGES),
                inboxModSectionState = mutableStateOf(InboxModSection.QUEUE),
            )
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<Boolean>(), typing)

        composeRule.onNode(hasSetTextAction()).performTextInput("hi")
        composeRule.waitForIdle()
        assertEquals(true, typing.last())

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.waitForIdle()
        assertEquals(false, typing.last())

        composeRule.onNode(hasSetTextAction()).performTextInput("again")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.waitForIdle()
        assertEquals(false, typing.last())
    }
}
