package app.subshell.prototype.inbox

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxNotificationsPolicyTest {
    @Test
    fun activityAndMessageEventsKeepExactInboxItemTargets() {
        val activity = InboxItem(
            id = "t1_reply",
            kind = ActivityKind.REPLY,
            title = "Reply",
            sender = "u/replier",
            age = "now",
            preview = "activity",
            unread = true,
        )
        val message = InboxItem(
            id = "t4_thread_root",
            kind = ActivityKind.MESSAGE,
            title = "Thread",
            sender = "u/sender",
            age = "now",
            preview = "message",
            unread = true,
            conversation = listOf(
                MessageBubble(
                    id = "t4_latest_incoming",
                    sender = "u/sender",
                    age = "now",
                    body = "message",
                    outgoing = false,
                ),
            ),
        )

        assertEquals(
            listOf(
                InboxNotificationEvent(
                    eventKey = "activity:t1_reply",
                    inboxItemId = "t1_reply",
                    kind = ActivityKind.REPLY,
                    title = "Reply",
                    sender = "u/replier",
                    preview = "activity",
                ),
                InboxNotificationEvent(
                    eventKey = "message:t4_latest_incoming",
                    inboxItemId = "t4_thread_root",
                    kind = ActivityKind.MESSAGE,
                    title = "Thread",
                    sender = "u/sender",
                    preview = "message",
                ),
            ),
            inboxNotificationEvents(
                items = listOf(activity, message),
                locallySeenIds = emptySet(),
                newEventKeys = listOf(
                    "activity:t1_reply",
                    "message:t4_latest_incoming",
                ),
            ),
        )
    }

    @Test
    fun locallySeenOrNonNewItemsCannotProduceNotifications() {
        val item = InboxItem(
            id = "t1_seen",
            kind = ActivityKind.REPLY,
            title = "Reply",
            sender = "u/replier",
            age = "now",
            preview = "activity",
            unread = true,
        )

        assertEquals(
            emptyList<InboxNotificationEvent>(),
            inboxNotificationEvents(
                items = listOf(item),
                locallySeenIds = setOf("t1_seen"),
                newEventKeys = listOf("activity:t1_seen"),
            ),
        )
        assertEquals(
            emptyList<InboxNotificationEvent>(),
            inboxNotificationEvents(
                items = listOf(item),
                locallySeenIds = emptySet(),
                newEventKeys = listOf("activity:t1_other"),
            ),
        )
    }

    @Test
    fun notificationTargetRequiresExactActionAccountItemAndEvent() {
        assertEquals(
            InboxNotificationTarget(
                accountStableId = "reddit:test",
                inboxItemId = "t4_root",
                eventKey = "message:t4_latest",
            ),
            inboxNotificationTarget(
                action = INBOX_NOTIFICATION_ACTION_OPEN,
                accountStableId = " reddit:test ",
                inboxItemId = " t4_root ",
                eventKey = " message:t4_latest ",
            ),
        )

        assertNull(
            inboxNotificationTarget(
                action = "other",
                accountStableId = "reddit:test",
                inboxItemId = "t4_root",
                eventKey = "message:t4_latest",
            ),
        )
    }
}
