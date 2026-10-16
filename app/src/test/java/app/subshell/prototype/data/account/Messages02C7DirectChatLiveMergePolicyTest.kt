package app.subshell.prototype.data.account

import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02C7DirectChatLiveMergePolicyTest {
    @Test
    fun authoritativeInboxProjectionConsumesMatchingLiveTextAndMedia() {
        val authoritative = listOf(
            MessageBubble(
                id = "t4_text",
                sender = "me",
                age = "now",
                body = "hello",
                outgoing = true,
                createdEpochSeconds = 100L,
            ),
            MessageBubble(
                id = "t4_image",
                sender = "me",
                age = "now",
                body = "sent an image\nhttps://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media123",
                outgoing = true,
                createdEpochSeconds = 101L,
            ),
        )
        val live = listOf(
            MessageBubble(
                id = "\$matrix_text",
                sender = "me",
                age = "now",
                body = "hello",
                outgoing = true,
                createdEpochSeconds = 101L,
            ),
            MessageBubble(
                id = "\$matrix_image",
                sender = "me",
                age = "now",
                body = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media123",
                outgoing = true,
                createdEpochSeconds = 102L,
            ),
            MessageBubble(
                id = "\$matrix_incoming",
                sender = "peer",
                age = "now",
                body = "new live reply",
                outgoing = false,
                createdEpochSeconds = 103L,
            ),
        )

        val merged = mergeDirectChatLiveMessages(authoritative, live)

        assertEquals(3, merged.size)
        assertEquals(listOf("t4_text", "t4_image", "\$matrix_incoming"), merged.map { it.id })
    }

    @Test
    fun repeatedTextOutsideTightTimestampWindowIsNotCollapsed() {
        val authoritative = listOf(
            MessageBubble(
                id = "old",
                sender = "peer",
                age = "1m",
                body = "ok",
                outgoing = false,
                createdEpochSeconds = 100L,
            ),
        )
        val live = listOf(
            MessageBubble(
                id = "new",
                sender = "peer",
                age = "now",
                body = "ok",
                outgoing = false,
                createdEpochSeconds = 120L,
            ),
        )

        assertEquals(2, mergeDirectChatLiveMessages(authoritative, live).size)
    }

    @Test
    fun matrixMediaIdentityParsesMxcAndDownloadForms() {
        assertEquals(
            "mediaABC",
            redditMatrixMediaIdFromMessageBody("mxc://reddit.com/mediaABC"),
        )
        assertEquals(
            "mediaXYZ",
            redditMatrixMediaIdFromMessageBody(
                "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/mediaXYZ",
            ),
        )
        assertTrue(redditMatrixMediaIdFromMessageBody("plain text") == null)
    }
}
