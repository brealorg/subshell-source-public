package io.github.brealorg.subshell.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.SampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATECACHE01_PRIVATE_ROWS_ARE_SEALED_V1: the inbox and the joined-community list are stored
 * under the cache key; public rows are not; a row that cannot be opened is a cache miss; rows an
 * older build wrote in plain text are read, then sealed once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivateCache01SealedRowsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val page = RepositoryPageRequest(limit = 25)
    private val account = "reddit:me"
    private val items = SampleData.inbox.take(2).map { it.copy(unread = true) }
    private val communities = SampleData.communities.take(2)
    private val stores = mutableListOf<RoomSubshellCacheStore>()

    private fun store(seal: CachePayloadSeal): RoomSubshellCacheStore {
        database.payloadSeal = seal
        return RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L), ownsDatabase = false)
            .also(stores::add)
    }

    @After
    fun close() {
        stores.forEach(RoomSubshellCacheStore::close)
        database.close()
    }

    private fun payloads(table: String): List<String> =
        database.openHelper.readableDatabase.query("SELECT payload FROM $table ORDER BY 1").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun realSeal(keyByte: Int) = CipherCachePayloadSeal(SoftwareAesCipher(keyByte))

    @Test
    fun inboxAndSubscriptionRowsAreStoredSealedAndStillRead() {
        val cache = store(realSeal(1))

        cache.cacheInbox(items)
        cache.replaceLiveInboxSnapshot(account, items, nextCursor = null)
        cache.replaceSubscriptionSnapshot(account, communities)

        for (table in listOf("cached_inbox", "cached_live_inbox_entries", "cached_subscriptions")) {
            val rows = payloads(table)
            assertTrue("$table has rows", rows.isNotEmpty())
            rows.forEach { assertTrue("$table row is sealed", it.startsWith(CachePayloadSeal.SEALED_PREFIX)) }
        }
        val plainInbox = SubshellCacheCodec.encodeInboxItem(items.first())
        // The needle is the title, not the two-character id: a sealed row is Base64 with a random
        // IV, and "i1" turned up by chance in a few runs out of a hundred.
        assertFalse(payloads("cached_inbox").any { it.contains(items.first().title) || it == plainInbox })
        assertEquals(items.map { it.id }.toSet(), cache.inbox(page).map { it.id }.toSet())
        assertEquals(items.first().id, cache.inboxItem(items.first().id)?.id)
        assertEquals(items.map { it.id }, cache.liveInboxSnapshot(account)?.items?.map { it.id })
        assertEquals(communities.map { it.id }, cache.subscriptionSnapshot(account)?.communities?.map { it.id })
    }

    @Test
    fun publicRowsStayPlain() {
        val cache = store(realSeal(1))

        cache.cacheCommunities(communities)

        payloads("cached_communities").forEach {
            assertFalse(it.startsWith(CachePayloadSeal.SEALED_PREFIX))
        }
        assertEquals(communities.size, cache.communities(page).size)
    }

    @Test
    fun anotherKeyMakesTheRowsACacheMissAndNothingThrows() {
        val writer = store(realSeal(1))
        writer.cacheInbox(items)
        writer.replaceLiveInboxSnapshot(account, items, nextCursor = null)
        writer.replaceSubscriptionSnapshot(account, communities)

        val reader = store(realSeal(2))

        assertNull(reader.inboxItem(items.first().id))
        assertTrue(reader.inbox(page).isEmpty())
        assertNull(reader.liveInboxSnapshot(account))
        assertNull(reader.subscriptionSnapshot(account))
        // A write over the unreadable rows works, and reads back with the new key.
        reader.replaceSubscriptionSnapshot(account, communities)
        assertEquals(communities.size, reader.subscriptionSnapshot(account)?.communities?.size)
    }

    @Test
    fun aSealThatCannotSealNeverWritesPlainText() {
        val cache = store(CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 1, failToSeal = true)))

        cache.cacheInbox(items)
        cache.replaceSubscriptionSnapshot(account, communities)

        (payloads("cached_inbox") + payloads("cached_subscriptions")).forEach {
            assertEquals(CachePayloadSeal.UNAVAILABLE_PREFIX, it)
        }
        assertNull(cache.inboxItem(items.first().id))
        assertNull(cache.subscriptionSnapshot(account))
    }

    @Test
    fun rowsAnOlderBuildWroteInPlainTextAreReadAndSealedOnce() {
        // The store starts plain, so the start-up sweep does not run; the seal is set after.
        val cache = store(CachePayloadSeal.Plain)
        cache.cacheInbox(items)
        cache.replaceLiveInboxSnapshot(account, items, nextCursor = null)
        cache.replaceSubscriptionSnapshot(account, communities)
        database.payloadSeal = realSeal(1)
        assertTrue(payloads("cached_inbox").none { it.startsWith(CachePayloadSeal.SEALED_PREFIX) })

        // Plain rows still read before they are sealed.
        assertEquals(items.first().id, cache.inboxItem(items.first().id)?.id)
        assertNotNull(cache.subscriptionSnapshot(account))

        val sealed = cache.sealLegacyPayloads()

        assertTrue("rows were sealed", sealed >= items.size + communities.size)
        for (table in listOf("cached_inbox", "cached_live_inbox_entries", "cached_subscriptions")) {
            payloads(table).forEach { assertTrue(it.startsWith(CachePayloadSeal.SEALED_PREFIX)) }
        }
        assertEquals(items.first().id, cache.inboxItem(items.first().id)?.id)
        assertEquals(communities.size, cache.subscriptionSnapshot(account)?.communities?.size)
        assertEquals(0, cache.sealLegacyPayloads())
    }

    @Test
    fun aStoreThatStartsOverPlainRowsSealsThemWithoutBeingAsked() {
        val older = store(CachePayloadSeal.Plain)
        older.cacheInbox(items)
        older.close()
        stores.remove(older)

        val cache = store(realSeal(1))
        // The start-up sweep runs on the store's own thread, ahead of anything asked of it.
        cache.inbox(page)

        payloads("cached_inbox").forEach { assertTrue(it.startsWith(CachePayloadSeal.SEALED_PREFIX)) }
    }
}
