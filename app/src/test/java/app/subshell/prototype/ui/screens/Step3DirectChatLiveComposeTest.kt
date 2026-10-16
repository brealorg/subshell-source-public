package app.subshell.prototype.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.account.DirectChatDeleteFailureKind
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02d2a, 02d3b): the open direct chat shows the peer's typing
 * and never a "Seen" receipt; deleting an own live message asks first, and an
 * unknown outcome offers no second delete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DirectChatLiveComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = "reddit:test"
    private val item = InboxItem(
        id = "chat_1",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = "peer",
        age = "1h",
        preview = "hi",
        conversation = listOf(MessageBubble(id = "m1", sender = "peer", age = "1h", body = "hi")),
    )
    private val ownLive = MessageBubble(id = "\$own1", sender = "me", age = "now", body = "mine", outgoing = true)

    private fun live(
        peerTyping: Boolean = false,
        deleteFailureKind: DirectChatDeleteFailureKind? = null,
    ) = DirectChatLiveUiState(
        accountStableId = account,
        inboxItemId = item.id,
        participantUsername = "peer",
        live = true,
        peerTyping = peerTyping,
        messages = listOf(ownLive),
        deleteFailureKind = deleteFailureKind,
    )

    private fun show(state: DirectChatLiveUiState, deleted: MutableList<String> = mutableListOf()): MutableList<String> {
        val holder = mutableStateOf(state)
        composeRule.setContent {
            ConversationScreen(
                item = item,
                onBack = {},
                accountStableId = account,
                ownUsername = "me",
                directChatLiveState = holder.value,
                directChatParticipantUsername = "peer",
                onDeleteDirectChatMessage = { deleted += it },
            )
        }
        composeRule.waitForIdle()
        return deleted
    }

    @Test
    fun thePeersTypingIsShownAndNoReadReceiptIs() {
        show(live(peerTyping = true))

        composeRule.onNodeWithText("u/peer is typing…").assertExists()
        composeRule.onAllNodesWithText("Seen").assertCountEquals(0)
    }

    @Test
    fun deletingAnOwnLiveMessageAsksFirstAndSendsItsEventId() {
        val deleted = show(live())

        composeRule.onAllNodesWithContentDescription("Message actions").onFirst().performClick()
        composeRule.onNodeWithText("Delete message").performClick()
        composeRule.onNodeWithText(
            "This will delete the message for everyone in this chat. This can't be undone.",
        ).assertExists()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf("\$own1"), deleted)
    }

    @Test
    fun anUnknownDeleteOutcomeOffersNoSecondDelete() {
        val deleted = show(live(deleteFailureKind = DirectChatDeleteFailureKind.OUTCOME_UNKNOWN))

        composeRule.onAllNodesWithContentDescription("Message actions").onFirst().performClick()
        composeRule.onNodeWithText("Delete message").performClick()
        composeRule.onNodeWithText(
            "Deletion wasn't confirmed. Check the chat in another Reddit client before trying again.",
        ).assertExists()
        composeRule.onAllNodesWithText("Delete").assertCountEquals(0)
        assertEquals(emptyList<String>(), deleted)
    }
}
