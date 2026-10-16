package app.subshell.prototype.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c6r4, 02c6r5r1, 02c7): a long direct chat keeps its newest
 * message in view: the image being sent shows at the bottom with "Sending…";
 * when Reddit's copy of a sent reply arrives it is shown (once, the local copy
 * goes) before the reply state is cleared; a new live message is followed
 * while the reader is at the bottom and not while they have scrolled up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ConversationTailFollowComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = "reddit:me"
    private val base = 1_800_000_000L

    // Alternating senders, so every message is its own group and the list overflows.
    private val history = (0 until 40).map { index ->
        val mine = index % 2 == 1
        MessageBubble(
            id = "m$index",
            sender = if (mine) "me" else "peer",
            age = "1h",
            body = "message $index",
            outgoing = mine,
            createdEpochSeconds = base + index * 600L,
        )
    }

    private fun chat(conversation: List<MessageBubble> = history) = InboxItem(
        id = "chat_1",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = "peer",
        age = "1h",
        preview = conversation.last().body,
        conversation = conversation,
    )

    private var item by mutableStateOf(chat())
    private var reply by mutableStateOf(MessageReplyUiState())
    private var live by mutableStateOf(DirectChatLiveUiState())
    private var resets = 0

    private fun show() {
        composeRule.setContent {
            ConversationScreen(
                item = item,
                onBack = {},
                accountStableId = account,
                ownUsername = "me",
                replyParentFullname = "t4_chat_1",
                messageReplyState = reply,
                directChatLiveState = live,
                directChatParticipantUsername = "peer",
                onResetMessageReplyState = { resets += 1 },
            )
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theImageBeingSentShowsAtTheBottomWithSending() {
        show()
        composeRule.onNodeWithText("message 39").assertIsDisplayed()

        composeRule.runOnIdle {
            reply = MessageReplyUiState(
                phase = MessageReplyPhase.SENDING,
                accountStableId = account,
                inboxItemId = "chat_1",
                parentFullname = "t4_chat_1",
                sentHadMedia = true,
                optimisticMediaUri = "file:///cache/picked.gif",
                optimisticMediaMimeType = "image/gif",
            )
        }
        composeRule.waitForIdle()

        // One "Sending…" on the new bubble at the bottom, one under the composer.
        assertEquals(2, count("Sending…"))
        composeRule.onAllNodesWithText("Sending…").fetchSemanticsNodes().indices.forEach { index ->
            composeRule.onAllNodesWithText("Sending…")[index].assertIsDisplayed()
        }
    }

    @Test
    fun redditsCopyOfASentReplyIsShownOnceBeforeTheReplyStateIsCleared() {
        val sentAt = base + 40 * 600L
        reply = MessageReplyUiState(
            phase = MessageReplyPhase.SUCCEEDED,
            accountStableId = account,
            inboxItemId = "chat_1",
            parentFullname = "t4_chat_1",
            sentText = "My new reply",
            completedAtEpochSeconds = sentAt,
            completionRevision = 1L,
        )
        show()
        assertEquals(1, count("My new reply"))
        assertEquals(0, resets)

        composeRule.runOnIdle {
            item = chat(
                history + MessageBubble(
                    id = "m_server",
                    sender = "me",
                    age = "now",
                    body = "My new reply",
                    outgoing = true,
                    createdEpochSeconds = sentAt + 2,
                ),
            )
        }
        composeRule.waitForIdle()

        assertEquals(1, count("My new reply"))
        composeRule.onNodeWithText("My new reply").assertIsDisplayed()
        assertEquals(1, resets)
    }

    @Test
    fun aNewLiveMessageIsFollowedAtTheBottomButNotWhenScrolledUp() {
        live = DirectChatLiveUiState(
            accountStableId = account,
            inboxItemId = "chat_1",
            participantUsername = "peer",
            live = true,
        )
        show()

        composeRule.runOnIdle {
            live = live.copy(
                messages = listOf(
                    MessageBubble(
                        id = "\$fresh1",
                        sender = "peer",
                        age = "now",
                        body = "fresh from peer",
                        createdEpochSeconds = base + 41 * 600L,
                    ),
                ),
                contentRevision = 1L,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("fresh from peer").assertIsDisplayed()

        composeRule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(0)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("message 0").assertIsDisplayed()

        composeRule.runOnIdle {
            live = live.copy(
                messages = live.messages + MessageBubble(
                    id = "\$fresh2",
                    sender = "peer",
                    age = "now",
                    body = "second fresh",
                    createdEpochSeconds = base + 42 * 600L,
                ),
                contentRevision = 2L,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("message 0").assertIsDisplayed()
        // Not followed: the new message is not composed, or composed below the view.
        if (count("second fresh") > 0) {
            composeRule.onNodeWithText("second fresh").assertIsNotDisplayed()
        }
    }
}
