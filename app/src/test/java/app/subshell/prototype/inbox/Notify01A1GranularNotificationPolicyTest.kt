package app.subshell.prototype.inbox

import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Notify01A1GranularNotificationPolicyTest {
    @Test
    fun activityKindsMapToGranularPolicyWithoutDroppingPostRepliesOrRedditNotices() {
        val policy = InboxNotificationPolicy(
            replies = true,
            mentions = false,
            privateMessages = false,
            redditNotices = true,
            modmail = false,
        )

        assertTrue(policy.allows(ActivityKind.REPLY))
        assertTrue(policy.allows(ActivityKind.POST_REPLY))
        assertFalse(policy.allows(ActivityKind.MENTION))
        assertFalse(policy.allows(ActivityKind.MESSAGE))
        assertTrue(policy.allows(ActivityKind.MOD))
        assertFalse(policy.modmail)
    }

    @Test
    fun inboxEventsAreFilteredAfterExactNewUnreadSelection() {
        val items = listOf(
            item("t1_comment_reply", ActivityKind.REPLY),
            item("t1_post_reply", ActivityKind.POST_REPLY),
            item("t1_mention", ActivityKind.MENTION),
            item("t4_message", ActivityKind.MESSAGE),
            item("t4_notice", ActivityKind.MOD),
        )

        val events = inboxNotificationEvents(
            items = items,
            locallySeenIds = emptySet(),
            newEventKeys = listOf(
                "activity:t1_comment_reply",
                "activity:t1_post_reply",
                "activity:t1_mention",
                "message:t4_message",
                "activity:t4_notice",
            ),
            policy = InboxNotificationPolicy(
                replies = false,
                mentions = true,
                privateMessages = false,
                redditNotices = true,
                modmail = true,
            ),
        )

        assertEquals(
            listOf(ActivityKind.MENTION, ActivityKind.MOD),
            events.map(InboxNotificationEvent::kind),
        )
    }

    @Test
    fun modmailUsesSameGranularPolicyWithoutCreatingAnotherPipeline() {
        val conversation = RedditModmailConversation(
            id = "abc123",
            communityName = "subshell",
            subject = "Subject",
            participantName = "user",
            preview = "Preview",
            lastUpdatedEpochSeconds = 100L,
            lastUnreadEpochSeconds = 100L,
            latestMessageId = "msg1",
            unread = true,
            isInternal = false,
            messageCount = 1,
        )

        assertTrue(
            modmailNotificationEvents(
                conversations = listOf(conversation),
                newEventKeys = listOf("modmail:abc123:msg1"),
                policy = InboxNotificationPolicy(modmail = false),
            ).isEmpty(),
        )

        assertEquals(
            1,
            modmailNotificationEvents(
                conversations = listOf(conversation),
                newEventKeys = listOf("modmail:abc123:msg1"),
                policy = InboxNotificationPolicy(modmail = true),
            ).size,
        )
    }

    @Test
    fun granularSettingsRoundTripAndOldBackupsDefaultAllCategoriesOn() {
        val configured = SubshellSettings(
            inboxNotificationsEnabled = true,
            notifyReplies = false,
            notifyMentions = true,
            notifyPrivateMessages = false,
            notifyRedditNotices = true,
            notifyModmail = false,
        )

        assertEquals(
            configured,
            SubshellSettings.fromStorageMap(configured.toStorageMap()),
        )

        val legacy = configured.toStorageMap() - setOf(
            SubshellSettings.NOTIFY_REPLIES,
            SubshellSettings.NOTIFY_MENTIONS,
            SubshellSettings.NOTIFY_PRIVATE_MESSAGES,
            SubshellSettings.NOTIFY_REDDIT_NOTICES,
            SubshellSettings.NOTIFY_MODMAIL,
        )
        val restored = SubshellSettings.fromStorageMap(legacy)

        assertTrue(restored.notifyReplies)
        assertTrue(restored.notifyMentions)
        assertTrue(restored.notifyPrivateMessages)
        assertTrue(restored.notifyRedditNotices)
        assertTrue(restored.notifyModmail)
    }

    private fun item(
        id: String,
        kind: ActivityKind,
    ) = InboxItem(
        id = id,
        kind = kind,
        title = kind.name,
        sender = "u/example",
        age = "now",
        preview = "preview",
        unread = true,
    )
}
