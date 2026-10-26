package app.subshell.prototype.data.cache

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * INBOXFAST01_A_REFRESHED_THREAD_KEEPS_ITS_OLDER_MESSAGES_V1: a refresh reads
 * only the first pages, so a stored thread keeps the older messages the fresh
 * page does not carry; its read state is the fresh one.
 */
class InboxFast01ThreadMergeTest {
    private fun bubble(id: String, sender: String, at: Long, outgoing: Boolean) =
        MessageBubble(id = id, sender = sender, age = "1h", body = "body $id", outgoing = outgoing, createdEpochSeconds = at)

    private fun thread(unread: Boolean, vararg bubbles: MessageBubble) = InboxItem(
        id = "t4_thread",
        kind = ActivityKind.MESSAGE,
        title = "Subject",
        sender = bubbles.first().sender,
        age = "1h",
        preview = bubbles.last().body,
        unread = unread,
        conversation = bubbles.toList(),
        createdEpochSeconds = bubbles.maxOf { it.createdEpochSeconds ?: 0L },
    )

    private val older = bubble("t4_a", "peer", at = 100, outgoing = false)
    private val mine = bubble("t4_b", "me", at = 200, outgoing = true)
    private val newest = bubble("t4_c", "peer", at = 300, outgoing = false)

    @Test
    fun aRefreshedThreadKeepsTheOlderMessagesItsFirstPageLacks() {
        val (items, _) = refreshedLiveInboxHead(
            storedItems = listOf(thread(unread = true, older, mine)),
            storedCursor = null,
            freshItems = listOf(thread(unread = false, mine, newest)),
            freshCursor = null,
            completeSinceEpochSeconds = 300,
        )

        val merged = items.single()
        assertEquals(listOf("t4_a", "t4_b", "t4_c"), merged.conversation.map(MessageBubble::id))
        // Read elsewhere since: Reddit's state wins, not "unread if either".
        assertFalse(merged.unread)
        assertEquals("body t4_c", merged.preview)
    }

    @Test
    fun otherKindsTakeTheFreshItem() {
        val stored = InboxItem(id = "t1_x", kind = ActivityKind.REPLY, title = "old", sender = "p", age = "1h", preview = "old", unread = true)
        val fresh = stored.copy(title = "new", unread = false)

        assertEquals(fresh, mergeRefreshedInboxItem(stored, fresh))
    }
}
