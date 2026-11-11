package io.github.brealorg.subshell.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.SampleData
import java.io.File
import java.security.ProviderException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATECACHE06_THE_DATA_KEY_IS_KEPT_IN_MEMORY_V1 (#296, 1.0.1): rows a 1.0.0 build wrote as e1 (a
 * Keystore call each) and rows rc.2 wrote in plain text become e2, a row that is lost for good does not
 * hold the sweep up, and a Keystore that does not answer changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivateCache07MigrationToE2Test {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val rows = 30
    private val items = (1..rows).map { index -> SampleData.inbox.first().copy(id = "t4_row$index") }
    private val stores = mutableListOf<RoomSubshellCacheStore>()
    private val keystore = SoftwareAesCipher(keyByte = 1)

    @After
    fun close() {
        stores.forEach(RoomSubshellCacheStore::close)
        database.close()
    }

    private fun store(seal: CachePayloadSeal): RoomSubshellCacheStore {
        database.payloadSeal = seal
        return RoomSubshellCacheStore(
            database,
            clock = MutableCacheClock(1_000L),
            ownsDatabase = false,
            legacySweepDelayMs = 60_000L,
        ).also(stores::add)
    }

    private fun e2(legacy: SealCipher?, wrapper: SealCipher = keystore, name: String = "key"): CachePayloadSeal =
        EnvelopeCachePayloadSeal(
            SoftwareGcmCipher(DataKeyVault(wrapper, File(folder.root, name))::key),
            legacy,
        )

    private fun payloads(): List<String> =
        database.openHelper.readableDatabase.query("SELECT payload FROM cached_inbox ORDER BY 1").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    @Test
    fun e1AndPlainRowsBecomeE2AndStillRead() {
        store(CachePayloadSeal.Plain).cacheInbox(items.take(10))
        store(CipherCachePayloadSeal(keystore)).cacheInbox(items.drop(10))
        assertEquals(20, payloads().count { it.startsWith(CachePayloadSeal.SEALED_PREFIX) })
        assertEquals(10, payloads().count { CachePayloadSeal.isSealed(it).not() })

        val cache = store(e2(legacy = keystore))
        val sealed = cache.sealLegacyPayloads()

        assertEquals(rows, sealed)
        assertTrue(payloads().all { it.startsWith(CachePayloadSeal.ENVELOPE_PREFIX) })
        assertEquals(rows, cache.inbox(RepositoryPageRequest(limit = 100)).size)
        assertEquals("nothing more to do", 0, cache.sealLegacyPayloads())
    }

    @Test
    fun aRowThatIsLostForGoodIsMarkedAndDoesNotHoldTheRestUp() {
        store(CipherCachePayloadSeal(keystore)).cacheInbox(items.take(10))
        // Ten rows sealed by a Keystore key that is gone now.
        store(CipherCachePayloadSeal(SoftwareAesCipher(keyByte = 9))).cacheInbox(items.drop(20).map { it.copy(id = "lost_" + it.id) })

        val cache = store(e2(legacy = keystore))
        val handled = cache.sealLegacyPayloads()

        val stored = payloads()
        assertEquals(20, handled)
        assertEquals(10, stored.count { it.startsWith(CachePayloadSeal.ENVELOPE_PREFIX) })
        assertEquals(10, stored.count { it.startsWith(CachePayloadSeal.UNAVAILABLE_PREFIX) })
        // CACHE06: a list with one row that cannot be read is a miss as a whole (until the network
        // replaces it), so the rows are read one by one.
        assertEquals(10, items.take(10).count { item -> cache.inboxItem(item.id) != null })
        assertEquals(0, items.drop(20).count { item -> cache.inboxItem("lost_" + item.id) != null })
        assertEquals(0, cache.sealLegacyPayloads())
    }

    @Test
    fun aKeystoreThatDoesNotAnswerChangesNothing() {
        store(CipherCachePayloadSeal(keystore)).cacheInbox(items)
        val before = payloads()

        val silent = CountingCipher(keystore, failWith = ProviderException("The key store did not answer"))
        val cache = store(e2(legacy = silent, wrapper = silent))
        val handled = cache.sealLegacyPayloads()

        assertEquals("nothing is marked as lost", 0, handled)
        assertEquals(before, payloads())

        // The Keystore answers again: everything is still there and is upgraded.
        val back = store(e2(legacy = keystore, name = "key-again"))
        assertEquals(rows, back.sealLegacyPayloads())
        assertEquals(rows, back.inbox(RepositoryPageRequest(limit = 100)).size)
    }
}
