package app.subshell.prototype.ui

import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.ui.screens.messageReplyAppearsInConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUI8d: LOWUI8_SAVED_EDIT_REACHES_EVERY_COPY_V1 (OCB-103),
 * LOWUI8_REPLY_CONFIRMATION_TOLERATES_A_WRONG_CLOCK_V1 (OCB-199).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowUi8dTest {
    @Test
    fun aSavedEditReachesTheFeedPageCopy() {
        val account = localFixtureAccountSummaries(listOf("root")).single()
        val state = SubshellState(initialAccountSessions = listOf(account))
        val queryKey = mainPostFeedQueryKey(account = account, source = FeedSource.HOME, sort = FeedSort.BEST)
        state.ensurePostPaging(queryKey)
        val post = state.postPagingSnapshot(queryKey).items.first()

        // Edited from the post screen and back on Home (no post route now).
        state.applyEditedPostBody(post.id, "    indented first line\nrest  ")

        val onHome = state.postPagingSnapshot(queryKey).items.first { item -> item.id == post.id }
        assertEquals("    indented first line\nrest", onHome.body)
    }

    private fun sent(completedAt: Long) = MessageReplyUiState(
        phase = MessageReplyPhase.SUCCEEDED,
        sentText = "see you",
        completedAtEpochSeconds = completedAt,
    )

    @Test
    fun aReplyIsConfirmedWithTheDeviceClockTenMinutesFast() {
        val serverNow = 1_700_000_000L
        val messages = listOf(
            MessageBubble(id = "m1", sender = "other", age = "", body = "hi", createdEpochSeconds = serverNow - 60),
            MessageBubble(id = "m2", sender = "me", age = "", body = "see you", outgoing = true, createdEpochSeconds = serverNow),
        )

        // m2 was not there when the reply was sent.
        assertTrue(
            messageReplyAppearsInConversation(messages, sent(completedAt = serverNow + 600), outgoingIdsBeforeSend = emptySet()),
        )
    }

    @Test
    fun anOlderMessageWithTheSameTextIsNotTheReply() {
        val serverNow = 1_700_000_000L
        val messages = listOf(
            MessageBubble(id = "m1", sender = "me", age = "", body = "see you", outgoing = true, createdEpochSeconds = serverNow - 1_200),
            MessageBubble(id = "m2", sender = "me", age = "", body = "later", outgoing = true, createdEpochSeconds = serverNow - 60),
        )

        assertFalse(
            messageReplyAppearsInConversation(messages, sent(completedAt = serverNow), outgoingIdsBeforeSend = setOf("m1", "m2")),
        )
    }
}
