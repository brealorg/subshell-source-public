package app.subshell.prototype.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.MessageReplyFailureKind
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
 * STEP3 (messages02c4r3r1): a failed message reply shows the app's own words,
 * never Reddit's raw text; Dismiss clears it; a lost authorization offers
 * Reconnect and a missing media grant offers "Grant media access"; a reply
 * that may have been sent offers Refresh (only when the thread can refresh)
 * and Checked, and nothing is sent again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ConversationReplyErrorComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = "reddit:alice"
    private val thread = InboxItem(
        id = "t4_y",
        kind = ActivityKind.MESSAGE,
        title = "Hello",
        sender = "bob",
        age = "1h",
        preview = "Hi there",
        conversation = listOf(MessageBubble(id = "t4_y", sender = "bob", age = "1h", body = "Hi there")),
    )

    private val events = mutableListOf<String>()
    private val sends = mutableListOf<String>()

    private fun failed(kind: MessageReplyFailureKind, message: String? = null) = MessageReplyUiState(
        phase = if (kind == MessageReplyFailureKind.OUTCOME_UNKNOWN) {
            MessageReplyPhase.OUTCOME_UNKNOWN
        } else {
            MessageReplyPhase.FAILED
        },
        accountStableId = account,
        inboxItemId = thread.id,
        parentFullname = thread.id,
        failureKind = kind,
        message = message,
        sentText = "My reply",
        sentDraftText = "My reply",
        completedAtEpochSeconds = 1_800_000_000L,
        completionRevision = 1L,
    )

    private fun show(state: MessageReplyUiState, canRefresh: Boolean = true) {
        composeRule.setContent {
            ConversationScreen(
                item = thread,
                onBack = {},
                onRefresh = if (canRefresh) {
                    { events += "refresh" }
                } else {
                    null
                },
                accountStableId = account,
                replyText = "My reply",
                replyParentFullname = thread.id,
                messageReplyState = state,
                onSendPrivateMessageReply = { body, _ -> sends += body },
                onReconnectInbox = { events += "reconnect" },
                onAuthorizeMediaReply = { events += "media" },
                onResetMessageReplyState = { events += "reset" },
            )
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun click(label: String) {
        composeRule.onAllNodes(hasText(label) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun aRejectedReplyShowsTheAppsWordsNotRedditsAndDismissClearsIt() {
        show(failed(MessageReplyFailureKind.API, message = "RAW SERVER TEXT"))

        assertEquals(1, count("Couldn't send reply"))
        assertEquals(1, count("Reddit rejected the reply."))
        assertEquals(0, composeRule.onAllNodesWithText("RAW SERVER TEXT", substring = true).fetchSemanticsNodes().size)

        click("Dismiss")

        assertEquals(listOf("reset"), events)
    }

    @Test
    fun aMissingMediaGrantAsksForMediaAccess() {
        show(failed(MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED))

        click("Grant media access")

        assertEquals(listOf("media", "reset"), events)
    }

    @Test
    fun aLostAuthorizationOffersReconnect() {
        show(failed(MessageReplyFailureKind.AUTHORIZATION_REQUIRED))

        click("Reconnect")

        assertEquals(listOf("reconnect", "reset"), events)
    }

    @Test
    fun aReplyThatMayHaveBeenSentOffersRefreshAndCheckedAndSendsNothing() {
        show(failed(MessageReplyFailureKind.OUTCOME_UNKNOWN))

        assertEquals(1, count("Reply may have been sent"))
        click("Refresh")
        assertEquals(listOf("refresh"), events.toList())
        click("Checked")

        assertEquals("reset", events.last())
        assertEquals(emptyList<String>(), sends)
    }

    @Test
    fun withoutAWayToRefreshThereIsNoRefreshAction() {
        show(failed(MessageReplyFailureKind.OUTCOME_UNKNOWN), canRefresh = false)

        assertEquals(1, count("Reply may have been sent"))
        assertEquals(0, composeRule.onAllNodes(hasText("Refresh") and hasClickAction()).fetchSemanticsNodes().size)
    }
}
