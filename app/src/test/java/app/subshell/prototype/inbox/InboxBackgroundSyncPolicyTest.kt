package app.subshell.prototype.inbox

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxBackgroundSyncPolicyTest {
    @Test
    fun firstBackgroundRunBaselinesExistingUnreadWithoutCallingItNew() {
        val decision = reconcileInboxBackgroundEventHistory(
            initialized = false,
            previousEventKeys = emptyList(),
            currentEventKeys = listOf("activity:t1_old", "message:t4_old"),
        )

        assertTrue(decision.newEventKeys.isEmpty())
        assertEquals(
            listOf("activity:t1_old", "message:t4_old"),
            decision.retainedEventKeys,
        )
    }

    @Test
    fun laterRunOnlySurfacesPreviouslyUnseenEventKeys() {
        val decision = reconcileInboxBackgroundEventHistory(
            initialized = true,
            previousEventKeys = listOf("activity:t1_old", "message:t4_old"),
            currentEventKeys = listOf("message:t4_new", "activity:t1_old"),
        )

        assertEquals(listOf("message:t4_new"), decision.newEventKeys)
        assertEquals(
            listOf("message:t4_new", "activity:t1_old", "message:t4_old"),
            decision.retainedEventKeys,
        )
    }

    @Test
    fun messageThreadUsesLatestIncomingBubbleAsEventIdentity() {
        val item = InboxItem(
            id = "t4_root",
            kind = ActivityKind.MESSAGE,
            title = "Thread",
            sender = "u/example",
            age = "now",
            preview = "latest",
            unread = true,
            conversation = listOf(
                MessageBubble(
                    id = "t4_first",
                    sender = "u/example",
                    age = "2m",
                    body = "one",
                    outgoing = false,
                ),
                MessageBubble(
                    id = "t4_mine",
                    sender = "You",
                    age = "1m",
                    body = "two",
                    outgoing = true,
                ),
                MessageBubble(
                    id = "t4_latest",
                    sender = "u/example",
                    age = "now",
                    body = "three",
                    outgoing = false,
                ),
            ),
        )

        assertEquals("message:t4_latest", item.inboxBackgroundEventKey())
    }

    @Test
    fun unreadSelectionExcludesLocallySeenItems() {
        val activity = InboxItem(
            id = "t1_reply",
            kind = ActivityKind.REPLY,
            title = "Reply",
            sender = "u/example",
            age = "now",
            preview = "reply",
            unread = true,
        )
        val message = InboxItem(
            id = "t4_thread",
            kind = ActivityKind.MESSAGE,
            title = "Message",
            sender = "u/example",
            age = "now",
            preview = "message",
            unread = true,
            conversation = listOf(
                MessageBubble(
                    id = "t4_message",
                    sender = "u/example",
                    age = "now",
                    body = "message",
                    outgoing = false,
                ),
            ),
        )

        assertEquals(
            listOf("message:t4_message"),
            inboxBackgroundUnreadEventKeys(
                items = listOf(activity, message),
                locallySeenIds = setOf("t1_reply"),
            ),
        )
    }
}
