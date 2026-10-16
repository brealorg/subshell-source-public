package app.subshell.prototype.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02b, 02b22, 02c, 02c1, 02c2, 02c4): a conversation's header
 * names the chat partner (direct chat) or the subject and sender (message
 * thread); the composer says Message or Reply, sends the typed text once,
 * cannot send while a reply is on its way, without a reply target or without
 * write access; a reply whose answer was lost offers Refresh and Checked; a
 * sent reply shows at once and only once; leaving a direct chat after a sent
 * reply refreshes the inbox; messages from one sender within five minutes on
 * one day form one group.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ConversationScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:alice"
    private val sends = mutableListOf<String>()
    private val events = mutableListOf<String>()

    private fun chat(conversation: List<MessageBubble> = emptyList()) = InboxItem(
        id = "t4_chat",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = "peer",
        age = "1h",
        preview = "Hi from peer",
        conversation = conversation,
    )

    private fun thread(conversation: List<MessageBubble> = emptyList()) = InboxItem(
        id = "t4_pm",
        kind = ActivityKind.MESSAGE,
        title = "Plans",
        sender = "bob",
        age = "1h",
        preview = "See you",
        conversation = conversation,
    )

    private fun state(phase: MessageReplyPhase, item: String, sentText: String? = null) = MessageReplyUiState(
        phase = phase,
        accountStableId = account,
        inboxItemId = item,
        parentFullname = item,
        sentText = sentText,
        sentDraftText = sentText,
        failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN.takeIf { phase == MessageReplyPhase.OUTCOME_UNKNOWN },
        completedAtEpochSeconds = (System.currentTimeMillis() / 1_000L).takeIf { phase != MessageReplyPhase.SENDING },
        completionRevision = if (phase == MessageReplyPhase.SENDING) 0L else 1L,
    )

    private fun show(
        item: InboxItem,
        reply: MessageReplyUiState = MessageReplyUiState(),
        target: String? = item.id,
        writeEnabled: Boolean = true,
        visible: () -> Boolean = { true },
    ) {
        composeRule.setContent {
            var text by mutableStateOf("")
            if (visible()) {
                ConversationScreen(
                    item = item,
                    onBack = {},
                    writeEnabled = writeEnabled,
                    onRefresh = { events += "refresh" },
                    accountStableId = account,
                    replyText = text,
                    onReplyTextChange = { text = it },
                    replyParentFullname = target,
                    messageReplyState = reply,
                    onSendPrivateMessageReply = { body, _ -> sends += body },
                    onResetMessageReplyState = { events += "checked" },
                    onRefreshInboxAfterSuccessfulDirectChatExit = { events += "inbox" },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun nodes(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun type(value: String) {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(value)
        composeRule.waitForIdle()
    }

    @Test
    fun theHeaderNamesTheChatPartnerOrTheSubjectAndSender() {
        show(chat())
        assertTrue(count("peer") >= 1)
        assertEquals(1, count("Direct chat"))
        assertEquals(0, count("[direct chat room]"))
    }

    @Test
    fun aMessageThreadHeaderShowsItsSubjectAndSender() {
        show(thread())
        assertTrue(count("Plans") >= 1)
        assertTrue(count("u/bob") >= 1)
    }

    @Test
    fun theTypedReplyIsSentOnce() {
        show(thread())
        assertEquals(1, count("Reply"))
        type("hi there")

        click(hasContentDescription("Send reply") and hasClickAction())

        assertEquals(listOf("hi there"), sends)
    }

    @Test
    fun aDirectChatSaysMessageAndSendsIt() {
        show(chat())
        assertEquals(1, count("Message"))
        type("hello")

        click(hasContentDescription("Send message") and hasClickAction())

        assertEquals(listOf("hello"), sends)
    }

    @Test
    fun aReplyOnItsWayCannotBeSentAgain() {
        show(thread(), reply = state(MessageReplyPhase.SENDING, "t4_pm", "first"))
        type("again")

        assertEquals(1, nodes(hasContentDescription("Sending reply")))
        assertEquals(0, nodes(hasContentDescription("Sending reply") and isEnabled()))
    }

    @Test
    fun withoutAReplyTargetNothingCanBeSent() {
        show(chat(), target = null)
        type("hello")

        assertEquals(1, count("This chat does not expose a reply target."))
        assertEquals(0, nodes(hasContentDescription("Send message") and isEnabled()))
    }

    @Test
    fun withoutWriteAccessThereIsNoComposer() {
        show(thread(), writeEnabled = false)

        assertEquals(0, nodes(hasSetTextAction()))
        assertEquals(0, nodes(hasContentDescription("Send reply")))
    }

    @Test
    fun aLostAnswerOffersRefreshAndCheckedAndBlocksSending() {
        show(thread(), reply = state(MessageReplyPhase.OUTCOME_UNKNOWN, "t4_pm", "My reply"))

        assertEquals(1, count("Reply may have been sent"))
        assertEquals(0, nodes(hasContentDescription("Send reply") and isEnabled()))
        click(hasText("Refresh") and hasClickAction())
        click(hasText("Checked") and hasClickAction())

        assertEquals(listOf("refresh", "checked"), events)
    }

    @Test
    fun aSentReplyShowsAtOnceAndOnlyOnce() {
        val sent = state(MessageReplyPhase.SUCCEEDED, "t4_pm", "My new reply")
        val incoming = MessageBubble(id = "t4_pm", sender = "bob", age = "1h", body = "See you")
        var item by mutableStateOf(thread(listOf(incoming)))
        composeRule.setContent {
            ConversationScreen(
                item = item,
                onBack = {},
                accountStableId = account,
                replyParentFullname = "t4_pm",
                messageReplyState = sent,
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, count("My new reply"))

        val arrived = MessageBubble(
            id = "t4_mine",
            sender = "alice",
            age = "now",
            body = "My new reply",
            outgoing = true,
            createdEpochSeconds = sent.completedAtEpochSeconds!! + 2,
        )
        composeRule.runOnIdle { item = thread(listOf(incoming, arrived)) }
        composeRule.waitForIdle()
        assertEquals(1, count("My new reply"))
    }

    @Test
    fun leavingADirectChatAfterASentReplyRefreshesTheInbox() {
        var open by mutableStateOf(true)
        show(chat(), reply = state(MessageReplyPhase.SUCCEEDED, "t4_chat", "hello"), visible = { open })

        composeRule.runOnIdle { open = false }
        composeRule.waitForIdle()

        assertEquals(listOf("inbox"), events)
    }

    @Test
    fun leavingAMessageThreadOrAQuietChatDoesNotRefreshTheInbox() {
        var open by mutableStateOf(true)
        show(thread(), reply = state(MessageReplyPhase.SUCCEEDED, "t4_pm", "hello"), visible = { open })
        composeRule.runOnIdle { open = false }
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun messagesFromOneSenderWithinFiveMinutesOnOneDayFormOneGroup() {
        val noon = LocalDate.of(2026, 3, 10).atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
        val midnight = LocalDate.of(2026, 3, 11).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        fun bubble(id: String, sender: String, at: Long?) = MessageBubble(id = id, sender = sender, age = "", body = id, createdEpochSeconds = at)

        val groups = conversationMessageGroups(
            listOf(
                bubble("a", "bob", noon),
                bubble("b", "bob", noon + 4 * 60),
                bubble("c", "bob", noon + 10 * 60),
                bubble("d", "carol", noon + 11 * 60),
                bubble("e", "carol", midnight - 60),
                bubble("f", "carol", midnight + 60),
                bubble("g", "carol", null),
                bubble("h", "carol", null),
            ),
        )

        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d"), listOf("e"), listOf("f"), listOf("g", "h")), groups.map { group -> group.messages.map { it.id } })
    }
}
