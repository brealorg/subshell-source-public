package app.subshell.prototype.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOX04: a later inbox page that carries more of a stored message thread
 * merges into it (messages by id in time order, unread if either part is)
 * instead of being dropped; other kinds keep the stored item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Inbox04ThreadMergeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database)
    private val account = "reddit:me"

    @After
    fun close() {
        cache.close()
    }

    private fun bubble(id: String, sender: String, at: Long, outgoing: Boolean) =
        MessageBubble(id = id, sender = sender, age = "1h", body = "body $id", outgoing = outgoing, createdEpochSeconds = at)

    private fun thread(unread: Boolean, at: Long, vararg bubbles: MessageBubble) = InboxItem(
        id = "t4_thread",
        kind = ActivityKind.MESSAGE,
        title = "Subject",
        sender = bubbles.first().sender,
        age = "1h",
        preview = bubbles.last().body,
        unread = unread,
        conversation = bubbles.toList(),
        createdEpochSeconds = at,
    )

    private fun reply(id: String) = InboxItem(
        id = id,
        kind = ActivityKind.REPLY,
        title = "Reply $id",
        sender = "peer",
        age = "1h",
        preview = "reply",
        unread = true,
    )

    @Test
    fun aThreadStoredFromYourRepliesGainsTheOtherSidesOlderMessagesFromTheNextPage() {
        val mine = bubble("t4_mine", "me", at = 200, outgoing = true)
        val theirs = bubble("t4_theirs", "peer", at = 100, outgoing = false)
        cache.replaceLiveInboxSnapshot(account, listOf(thread(unread = false, at = 200, mine), reply("t1_a")), nextCursor = "c1")

        cache.appendLiveInboxPage(
            accountStableId = account,
            expectedCursor = "c1",
            items = listOf(thread(unread = true, at = 100, theirs), reply("t1_b")),
            nextCursor = null,
        )

        val items = cache.liveInboxSnapshot(account)!!.items
        assertEquals(listOf("t4_thread", "t1_a", "t1_b"), items.map(InboxItem::id))
        val merged = items.first()
        assertEquals(listOf("t4_theirs", "t4_mine"), merged.conversation.map(MessageBubble::id))
        assertTrue(merged.unread)
        assertEquals("peer", merged.sender)
        assertEquals(200L, merged.createdEpochSeconds)
    }

    @Test
    fun anotherKindWithTheSameIdKeepsTheStoredItem() {
        cache.replaceLiveInboxSnapshot(account, listOf(reply("t1_a")), nextCursor = "c1")

        cache.appendLiveInboxPage(
            accountStableId = account,
            expectedCursor = "c1",
            items = listOf(reply("t1_a").copy(title = "changed")),
            nextCursor = null,
        )

        assertEquals("Reply t1_a", cache.liveInboxSnapshot(account)!!.items.single().title)
    }
}
