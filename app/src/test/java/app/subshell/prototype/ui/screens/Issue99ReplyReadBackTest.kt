package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.auth.MessageReplyCoordinator
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE99_UNKNOWN_REPLY_READ_BACK_V1: a message reply whose answer was lost
 * keeps what was sent, reloads the inbox, and counts as sent once it shows up
 * in the thread; until then nothing is re-sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue99ReplyReadBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aLostAnswerKeepsWhatWasSentAndReloadsTheInbox() {
        val reloads = mutableListOf<String>()
        val coordinator = MessageReplyCoordinator(
            messageReplyWriter = {
                AuthenticatedRedditMessageReplyResult.Failed(kind = MessageReplyFailureKind.OUTCOME_UNKNOWN)
            },
            loadRedditInbox = { account, _ -> reloads += account },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        coordinator.sendRedditMessageReply(
            MessageReplyRequest(
                accountStableId = "reddit:alice",
                inboxItemId = "t4_y",
                parentFullname = "t4_y",
                channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
                text = "My reply",
            ),
        )

        val state = coordinator.messageReplyUiState.value
        assertEquals(MessageReplyPhase.OUTCOME_UNKNOWN, state.phase)
        assertEquals("My reply", state.sentText)
        assertEquals(true, state.completedAtEpochSeconds != null)
        assertEquals(listOf("reddit:alice"), reloads)
    }

    private val sentAt = 1_800_000_000L
    private val unknown = MessageReplyUiState(
        phase = MessageReplyPhase.OUTCOME_UNKNOWN,
        accountStableId = "reddit:alice",
        inboxItemId = "t4_y",
        parentFullname = "t4_y",
        failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
        sentText = "My reply",
        sentDraftText = "My reply",
        completedAtEpochSeconds = sentAt,
        completionRevision = 1L,
    )

    private fun show(thread: List<MessageBubble>, onReset: () -> Unit, onText: (String) -> Unit) {
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = "t4_y",
                    kind = ActivityKind.MESSAGE,
                    title = "Hello",
                    sender = "bob",
                    age = "1h",
                    preview = "Hi there",
                    conversation = thread,
                ),
                onBack = {},
                accountStableId = "reddit:alice",
                replyText = "My reply",
                onReplyTextChange = onText,
                replyParentFullname = "t4_y",
                messageReplyState = unknown,
                onResetMessageReplyState = onReset,
            )
        }
        composeRule.waitForIdle()
    }

    private val incoming = MessageBubble(id = "t4_y", sender = "bob", age = "1h", body = "Hi there")

    @Test
    fun theReplyFoundInTheThreadCountsAsSent() {
        var resets = 0
        val texts = mutableListOf<String>()
        show(
            thread = listOf(
                incoming,
                MessageBubble(
                    id = "t4_mine",
                    sender = "alice",
                    age = "now",
                    body = "My reply",
                    outgoing = true,
                    createdEpochSeconds = sentAt + 3,
                ),
            ),
            onReset = { resets += 1 },
            onText = { texts += it },
        )

        assertEquals(1, resets)
        assertEquals(true, "" in texts)
    }

    @Test
    fun withoutTheReplyInTheThreadTheWarningStays() {
        var resets = 0
        val texts = mutableListOf<String>()
        show(thread = listOf(incoming), onReset = { resets += 1 }, onText = { texts += it })

        assertEquals(0, resets)
        assertEquals(false, "" in texts)
    }
}
