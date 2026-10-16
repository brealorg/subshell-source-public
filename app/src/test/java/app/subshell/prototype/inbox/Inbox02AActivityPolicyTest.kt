package app.subshell.prototype.inbox

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Inbox02AActivityPolicyTest {
    @Test
    fun exactCommentTargetUsesCanonicalT1Fullname() {
        val item = item(
            id = "t1_reply",
            kind = ActivityKind.REPLY,
            targetPostId = "post",
            targetCommentId = "reply",
        )

        val target = item.canonicalInboxTarget() as InboxTarget.ExactComment
        assertEquals("post", target.postId)
        assertEquals("t1_reply", target.commentFullname)
        assertEquals("t1_reply", canonicalRedditCommentFullname("t1_reply"))
        assertNull(canonicalRedditCommentFullname("t1_"))
    }

    @Test
    fun clearBoundaryAndDismissAreActivityOnly() {
        val activity = item(
            id = "t1_activity",
            kind = ActivityKind.MENTION,
            createdEpochSeconds = 100L,
        )
        val message = item(
            id = "t4_message",
            kind = ActivityKind.MESSAGE,
            createdEpochSeconds = 100L,
        )

        assertTrue(activity.hiddenByActivityLifecycle(emptySet(), 100L))
        assertTrue(activity.hiddenByActivityLifecycle(setOf(activity.id), 0L))
        assertFalse(message.hiddenByActivityLifecycle(setOf(message.id), 200L))
    }

    @Test
    fun sectioningUsesTodayYesterdayAndEarlier() {
        val zone = ZoneId.of("UTC")
        val now = 3L * 86_400L + 12L * 3_600L

        assertEquals(InboxActivitySection.TODAY, inboxActivitySection(now - 60L, now, zone))
        assertEquals(InboxActivitySection.YESTERDAY, inboxActivitySection(now - 86_400L, now, zone))
        assertEquals(InboxActivitySection.EARLIER, inboxActivitySection(now - 172_800L, now, zone))
    }

    private fun item(
        id: String,
        kind: ActivityKind,
        targetPostId: String? = null,
        targetCommentId: String? = null,
        createdEpochSeconds: Long? = null,
    ) = InboxItem(
        id = id,
        kind = kind,
        title = "Title",
        sender = "user",
        age = "1m",
        preview = "Preview",
        targetPostId = targetPostId,
        targetCommentId = targetCommentId,
        createdEpochSeconds = createdEpochSeconds,
    )
}
