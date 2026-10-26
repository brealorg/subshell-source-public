package app.subshell.prototype.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOXORDER01: stored rows below a refresh that were read on another device
 * stop counting as unread once Reddit's unread list says so, and a later page
 * takes its place in time instead of landing below older rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InboxOrder01StoredRowsTest {
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

    @Test
    fun aStoredRowReadElsewhereTurnsReadAndOneStillUnreadStays() {
        val (items, _) = refreshedLiveInboxHead(
            storedItems = listOf(item("i3", 300), item("readElsewhere", 100), item("stillUnread", 90)),
            storedCursor = "old",
            freshItems = listOf(item("i4", 400), item("i3", 300)),
            freshCursor = "fresh",
            completeSinceEpochSeconds = 300,
            unreadItemIds = setOf("i4", "i3", "stillUnread"),
        )

        val byId = items.associateBy(InboxItem::id)
        assertFalse(byId.getValue("readElsewhere").unread)
        assertTrue(byId.getValue("stillUnread").unread)
    }

    @Test
    fun anUnknownUnreadListLeavesStoredRowsAsTheyAre() {
        val (items, _) = refreshedLiveInboxHead(
            storedItems = listOf(item("i3", 300), item("old", 100)),
            storedCursor = "old",
            freshItems = listOf(item("i3", 300)),
            freshCursor = "fresh",
            completeSinceEpochSeconds = 300,
            unreadItemIds = null,
        )

        assertTrue(items.single { it.id == "old" }.unread)
    }

    @Test
    fun aLaterPageTakesItsPlaceInTimeAboveAnOlderStoredThread() {
        // The first page: recent inbox rows and an old thread only the sent
        // history reached.
        cache.replaceLiveInboxSnapshot(account, listOf(item("i3", 300), item("oldSent", 50)), nextCursor = "page2")

        cache.appendLiveInboxPage(account, expectedCursor = "page2", items = listOf(item("i2", 200), item("i1", 100)), nextCursor = "page3")

        assertEquals(listOf("i3", "i2", "i1", "oldSent"), cache.liveInboxSnapshot(account)!!.items.map(InboxItem::id))
    }

    private fun item(id: String, at: Long) = InboxItem(
        id = id,
        kind = ActivityKind.REPLY,
        title = "Reply $id",
        sender = "peer",
        age = "1h",
        preview = "reply",
        unread = true,
        createdEpochSeconds = at,
    )
}
