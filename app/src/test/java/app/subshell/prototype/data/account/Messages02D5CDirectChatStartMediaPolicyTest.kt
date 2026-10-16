package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Messages02D5CDirectChatStartMediaPolicyTest {
    @Test
    fun textOnlyStartStillNormalizes() {
        val normalized = DirectChatStartRequest(
            accountStableId = " reddit:acct ",
            participantUsername = " u/example_user ",
            text = " hello ",
        ).normalizedDirectChatStartRequest()

        assertEquals("reddit:acct", normalized.accountStableId)
        assertEquals("example_user", normalized.participantUsername)
        assertEquals("hello", normalized.text)
        assertNull(normalized.media)
    }

    @Test
    fun gifOnlyStartNormalizesMediaAndKeepsTextBlank() {
        val normalized = DirectChatStartRequest(
            accountStableId = "reddit:acct",
            participantUsername = "example_user",
            text = "   ",
            media = MessageReplyMediaAttachment(
                uri = " content://picker/example ",
                mimeType = " image/gif ",
                sourceLinkUri = " https://example.invalid/source ",
            ),
        ).normalizedDirectChatStartRequest()

        assertEquals("", normalized.text)
        assertEquals("content://picker/example", requireNotNull(normalized.media).uri)
        assertEquals("image/gif", requireNotNull(normalized.media).mimeType)
        assertEquals(
            "https://example.invalid/source",
            requireNotNull(normalized.media).sourceLinkUri,
        )
    }

    @Test
    fun textAndMediaTogetherFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectChatStartRequest(
                accountStableId = "reddit:acct",
                participantUsername = "example_user",
                text = "caption",
                media = MessageReplyMediaAttachment(
                    uri = "content://picker/example",
                    mimeType = "image/png",
                ),
            ).normalizedDirectChatStartRequest()
        }
    }

    @Test
    fun emptyTextAndNoMediaFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectChatStartRequest(
                accountStableId = "reddit:acct",
                participantUsername = "example_user",
                text = "   ",
            ).normalizedDirectChatStartRequest()
        }
    }

    @Test
    fun videoFirstMessageIsOutOfScope() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectChatStartRequest(
                accountStableId = "reddit:acct",
                participantUsername = "example_user",
                text = "",
                media = MessageReplyMediaAttachment(
                    uri = "content://picker/example",
                    mimeType = "video/mp4",
                ),
            ).normalizedDirectChatStartRequest()
        }
    }
}
