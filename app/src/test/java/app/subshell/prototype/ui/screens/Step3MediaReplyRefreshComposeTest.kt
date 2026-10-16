package app.subshell.prototype.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.auth.MessageReplyCoordinator
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c4r2): a reply that carried an image is marked as such
 * when Reddit accepts it, and the conversation then reads the thread again
 * so Reddit's own message replaces the temporary one; a text reply is not
 * marked and asks for nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3MediaReplyRefreshComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val metadata = NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null)

    private fun reply(media: Boolean) = MessageReplyRequest(
        accountStableId = "acct",
        inboxItemId = "t4_item",
        parentFullname = "t4_item",
        channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
        text = if (media) "" else "hello",
        media = if (media) MessageReplyMediaAttachment("content://x/1", "image/png") else null,
    )

    private fun accepted(media: Boolean): MessageReplyUiState {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = MessageReplyCoordinator(
            messageReplyWriter = { request -> AuthenticatedRedditMessageReplyResult.Accepted(request, request.text, metadata) },
            loadRedditInbox = { _, _ -> },
            scope = scope,
        )
        coordinator.sendRedditMessageReply(reply(media))
        val state = coordinator.messageReplyUiState.value
        scope.cancel()
        return state
    }

    @Test
    fun anAcceptedReplyIsMarkedWhenItCarriedAnImage() {
        val withImage = accepted(media = true)
        val textOnly = accepted(media = false)

        assertEquals(MessageReplyPhase.SUCCEEDED, withImage.phase)
        assertTrue(withImage.sentHadMedia)
        assertEquals(MessageReplyPhase.SUCCEEDED, textOnly.phase)
        assertFalse(textOnly.sentHadMedia)
    }

    @Test
    fun theConversationReadsTheThreadAgainAfterAnImageReply() {
        var refreshes = 0
        var replyState by mutableStateOf(MessageReplyUiState())
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = "t4_item",
                    kind = ActivityKind.MESSAGE,
                    title = "A thread",
                    sender = "someone_else",
                    age = "1h",
                    preview = "Hi",
                ),
                onBack = {},
                onRefresh = { refreshes += 1 },
                accountStableId = "acct",
                messageReplyState = replyState,
            )
        }
        composeRule.waitForIdle()
        val before = refreshes

        composeRule.runOnIdle { replyState = accepted(media = false) }
        composeRule.waitForIdle()
        assertEquals(before, refreshes)

        composeRule.runOnIdle { replyState = accepted(media = true).copy(completionRevision = 2L) }
        composeRule.waitForIdle()
        assertEquals(before + 1, refreshes)
    }
}
