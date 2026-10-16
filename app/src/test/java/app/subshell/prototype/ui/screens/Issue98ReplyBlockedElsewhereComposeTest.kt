package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE98_REPLY_BLOCKED_ELSEWHERE_V1: while a reply in another conversation
 * is sending or unconfirmed, Send here is off and says why (it used to look
 * enabled and do nothing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue98ReplyBlockedElsewhereComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val unknownInX = MessageReplyUiState(
        phase = MessageReplyPhase.OUTCOME_UNKNOWN,
        accountStableId = "reddit:alice",
        inboxItemId = "t4_x",
        parentFullname = "t4_x",
    )

    private fun show(replyState: MessageReplyUiState) {
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = "t4_y",
                    kind = ActivityKind.MESSAGE,
                    title = "Hello",
                    sender = "bob",
                    age = "1h",
                    preview = "Hi there",
                ),
                onBack = {},
                accountStableId = "reddit:alice",
                replyText = "My reply",
                replyParentFullname = "t4_y",
                messageReplyState = replyState,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUnconfirmedReplyInAnotherConversationTurnsSendOffAndSaysWhy() {
        show(unknownInX)

        composeRule.onNode(hasContentDescription("Send reply")).assertIsNotEnabled()
        assertEquals(
            1,
            composeRule.onAllNodesWithText(
                "A message in another conversation may have been sent. Open it and confirm before sending here.",
            ).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun withNothingPendingElsewhereSendIsOn() {
        show(MessageReplyUiState())

        composeRule.onNode(hasContentDescription("Send reply")).assertIsEnabled()
    }

    @Test
    fun theOwnConversationsStateIsNotBlockedElsewhere() {
        assertNull(messageReplyBlockedElsewhereMessage(unknownInX, scoped = unknownInX))
        assertEquals(
            "Another message is still being sent.",
            messageReplyBlockedElsewhereMessage(
                unknownInX.copy(phase = MessageReplyPhase.SENDING),
                scoped = MessageReplyUiState(),
            ),
        )
    }
}
