package app.subshell.prototype.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOXSSOT01: the stored inbox is observed (a write by the background sync
 * reaches whoever follows it), and a refresh puts the fresh first page on
 * top of the stored pages instead of replacing them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InboxSsot01StoredInboxTest {
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
    fun aWriteAfterObservingStartedReachesTheObserver() = runBlocking {
        cache.replaceLiveInboxSnapshot(account, listOf(item("old")), nextCursor = null)
        val first = withTimeout(5_000) { cache.observeLiveInboxSnapshot(account).first { it != null } }
        assertEquals(listOf("old"), first!!.items.map(InboxItem::id))

        val updated = async(Dispatchers.IO) {
            withTimeout(5_000) {
                cache.observeLiveInboxSnapshot(account).first { snapshot ->
                    snapshot?.items?.any { it.id == "new" } == true
                }
            }
        }
        // The background sync's write, through the same store.
        cache.refreshLiveInboxHead(account, listOf(item("new"), item("old")), nextCursor = null, completeSinceEpochSeconds = null)

        assertEquals(listOf("new", "old"), updated.await()!!.items.map(InboxItem::id))
    }

    @Test
    fun theObserverReadsWithoutWritingSoItDoesNotWakeItself() = runBlocking {
        cache.replaceLiveInboxSnapshot(account, listOf(item("a")), nextCursor = null)
        val emissions = mutableListOf<CachedLiveInboxSnapshot?>()
        val tableWrites = java.util.concurrent.atomic.AtomicInteger()
        val writes = launch(Dispatchers.IO) {
            database.invalidationTracker
                .createFlow("cached_live_inbox_lists", emitInitialState = false)
                .collect { tableWrites.incrementAndGet() }
        }
        Thread.sleep(300)
        val job = launch(Dispatchers.IO) {
            cache.observeLiveInboxSnapshot(account).collect { emissions += it }
        }
        Thread.sleep(1_000)
        job.cancel()
        writes.cancel()
        // A read that touched the last access would invalidate the table it
        // observes and read again, without end (equal snapshots hide it).
        assertEquals(0, tableWrites.get())
        assertEquals(1, emissions.size)
    }

    @Test
    fun aRefreshKeepsThePagesTheReaderAlreadyScrolledTo() {
        cache.replaceLiveInboxSnapshot(account, listOf(item("3", 30), item("2", 20)), nextCursor = "page2")
        cache.appendLiveInboxPage(account, expectedCursor = "page2", items = listOf(item("1", 10), item("0", 5)), nextCursor = "page3")

        val stored = cache.refreshLiveInboxHead(
            account,
            listOf(item("4", 40), item("3", 30)),
            nextCursor = "fresh2",
            completeSinceEpochSeconds = 30,
        )!!

        assertEquals(listOf("4", "3", "2", "1", "0"), stored.items.map(InboxItem::id))
        // The cursor still points past the end of the stored pages.
        assertEquals("page3", stored.nextCursor)
    }

    // INBOXFAST01_A_REFRESH_IS_COMPLETE_ONLY_SINCE_ITS_OLDER_EDGE_V1: the
    // owner's inbox lost 53 items this way.
    @Test
    fun anOldSentOnlyThreadOnTheFreshPageDoesNotCutTheInboxBelowIt() {
        val (items, cursor) = refreshedLiveInboxHead(
            storedItems = listOf(item("i3", 300), item("i2", 200), item("i1", 100), item("s0", 50)),
            storedCursor = "inbox-page2",
            // Inbox page 1 reaches back to 300; the whole sent page reaches
            // back to the old thread s0.
            freshItems = listOf(item("i4", 400), item("i3", 300), item("s0", 50)),
            freshCursor = "inbox-page1",
            completeSinceEpochSeconds = 300,
        )
        assertEquals(listOf("i4", "i3", "i2", "i1", "s0"), items.map(InboxItem::id))
        assertEquals("inbox-page2", cursor)
    }

    @Test
    fun aStoredItemInsideTheCompleteWindowButNotOnThePageIsGone() {
        val (items, _) = refreshedLiveInboxHead(
            storedItems = listOf(item("deleted", 350), item("i3", 300), item("i2", 200)),
            storedCursor = "old",
            freshItems = listOf(item("i4", 400), item("i3", 300)),
            freshCursor = "fresh",
            completeSinceEpochSeconds = 300,
        )
        assertEquals(listOf("i4", "i3", "i2"), items.map(InboxItem::id))
    }

    @Test
    fun aThreadMovedToTheTopDoesNotCutTheList() {
        val (items, cursor) = refreshedLiveInboxHead(
            storedItems = listOf(item("e", 50), item("d", 40), item("c", 30), item("b", 20), item("a", 10)),
            storedCursor = "old",
            // "a" got a new message: it is first on the fresh page.
            freshItems = listOf(item("a", 60), item("f", 55), item("e", 50)),
            freshCursor = "fresh",
            completeSinceEpochSeconds = 50,
        )
        assertEquals(listOf("a", "f", "e", "d", "c", "b"), items.map(InboxItem::id))
        assertEquals("old", cursor)
    }

    @Test
    fun aFreshPageThatMeetsNothingStoredStandsAlone() {
        val (items, cursor) = refreshedLiveInboxHead(
            storedItems = listOf(item("b", 20), item("a", 10)),
            storedCursor = "old",
            freshItems = listOf(item("d", 40), item("c", 30)),
            freshCursor = "fresh",
            completeSinceEpochSeconds = 30,
        )
        assertEquals(listOf("d", "c"), items.map(InboxItem::id))
        assertEquals("fresh", cursor)
    }

    @Test
    fun anUnchangedFirstPageKeepsItsOwnCursor() {
        val (items, cursor) = refreshedLiveInboxHead(
            storedItems = listOf(item("b", 20), item("a", 10)),
            storedCursor = "old",
            freshItems = listOf(item("c", 30), item("b", 20)),
            freshCursor = "fresh",
            completeSinceEpochSeconds = 20,
        )
        // "a" lies below the fresh page's edge and stays with the old cursor.
        assertEquals(listOf("c", "b", "a"), items.map(InboxItem::id))
        assertEquals("old", cursor)

        val (same, sameCursor) = refreshedLiveInboxHead(
            storedItems = listOf(item("b", 20), item("a", 10)),
            storedCursor = "old",
            freshItems = listOf(item("b", 20), item("a", 10)),
            freshCursor = "fresh",
            completeSinceEpochSeconds = 10,
        )
        assertEquals(listOf("b", "a"), same.map(InboxItem::id))
        assertEquals("fresh", sameCursor)
        assertTrue(same.isNotEmpty())
    }

    private fun item(id: String, at: Long? = null) = InboxItem(
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
