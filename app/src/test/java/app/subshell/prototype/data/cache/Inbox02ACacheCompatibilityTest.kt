package app.subshell.prototype.data.cache

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Inbox02ACacheCompatibilityTest {
    @Test
    fun newInboxMetadataRoundTrips() {
        val item = InboxItem(
            id = "t1_reply",
            kind = ActivityKind.POST_REPLY,
            title = "Post title",
            sender = "user",
            age = "1m",
            preview = "Reply",
            unread = true,
            targetPostId = "post",
            targetCommentId = "t1_reply",
            createdEpochSeconds = 1_000L,
            subreddit = "r/test",
            postTitle = "Post title",
            contextPermalink = "/r/test/comments/post/title/reply/",
        )

        assertEquals(
            item,
            SubshellCacheCodec.decodeInboxItem(SubshellCacheCodec.encodeInboxItem(item)),
        )
    }

    @Test
    fun legacyTenFieldInboxPayloadRemainsReadable() {
        val payload = legacyPack(
            "t1_legacy",
            "REPLY",
            "Legacy title",
            "legacy-user",
            "2m",
            "Legacy body",
            "true",
            "post",
            "t1_legacy",
            "",
        )
        val decoded = SubshellCacheCodec.decodeInboxItem(payload)

        assertEquals("t1_legacy", decoded.id)
        assertEquals(ActivityKind.REPLY, decoded.kind)
        assertNull(decoded.createdEpochSeconds)
        assertNull(decoded.subreddit)
        assertNull(decoded.postTitle)
    }

    private fun legacyPack(vararg values: String?): String = buildString {
        values.forEach { value ->
            if (value == null) {
                append("-1:")
            } else {
                append(value.length)
                append(':')
                append(value)
            }
        }
    }
}
