package io.github.brealorg.subshell.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.SampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A cipher that takes [delayMs] per call: a Keystore that is slow on some phones. */
internal class SlowCipher(private val inner: SealCipher, private val delayMs: Long) : SealCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        Thread.sleep(delayMs)
        return inner.encrypt(plaintext, associatedData)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        Thread.sleep(delayMs)
        return inner.decrypt(envelope, associatedData)
    }
}

/**
 * PRIVATECACHE04_LEGACY_ROWS_ARE_SEALED_IN_SMALL_STEPS_V1 (#296): the first start after an update
 * seals every plain row, one Keystore call each. On a slow Keystore that must not hold the cache
 * for the whole sweep (the first screen was blank for ten seconds and more).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivateCache04SlowKeystoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val page = RepositoryPageRequest(limit = 25)
    private val account = "reddit:me"
    private val rows = 60
    private val items = (1..rows).map { index -> SampleData.inbox.first().copy(id = "t4_row$index") }
    private val stores = mutableListOf<RoomSubshellCacheStore>()

    @After
    fun close() {
        stores.forEach(RoomSubshellCacheStore::close)
        database.close()
    }

    private fun store(
        seal: CachePayloadSeal,
        stuckMs: Long = DEFAULT_STUCK_SEAL_OP_MS,
        sweepDelayMs: Long = 0L,
    ): RoomSubshellCacheStore {
        database.payloadSeal = seal
        return RoomSubshellCacheStore(
            database,
            clock = MutableCacheClock(1_000L),
            ownsDatabase = false,
            stuckSealOpMs = stuckMs,
            legacySweepDelayMs = sweepDelayMs,
            legacySweepGapMs = 0L,
        ).also(stores::add)
    }

    private fun slowSeal(delayMs: Long) =
        CipherCachePayloadSeal(SlowCipher(SoftwareAesCipher(keyByte = 1), delayMs))

    private fun sealedCount(): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM cached_inbox WHERE payload LIKE 'e1:%'").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private fun waitUntil(timeoutMs: Long, done: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (done()) return true
            Thread.sleep(25)
        }
        return done()
    }

    @Test
    fun aReadDuringTheSweepWaitsForOneStepNotForEveryRow() {
        store(CachePayloadSeal.Plain).cacheInbox(items)
        // 60 rows at 30 ms: 1.8 s if the sweep were one piece.
        val cache = store(slowSeal(delayMs = 30))

        val startedAt = System.nanoTime()
        val read = cache.inbox(page)
        val waitedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("the plain rows are read as they are, got ${read.size}", read.isNotEmpty())
        assertTrue("the read waited $waitedMs ms", waitedMs < 900)
        assertTrue("the sweep finishes on its own", waitUntil(20_000) { sealedCount() == rows })
        assertEquals(rows, cache.inbox(RepositoryPageRequest(limit = 200)).size)
    }

    // PRIVATECACHE05_THE_SWEEP_WAITS_FOR_AN_IDLE_CACHE_V1: nothing is sealed before the start delay.
    @Test
    fun theSweepWaitsBeforeItsFirstStepAndThenSealsEverything() {
        store(CachePayloadSeal.Plain).cacheInbox(items)

        val cache = store(slowSeal(delayMs = 1), sweepDelayMs = 700)

        Thread.sleep(300)
        assertEquals("nothing is sealed yet", 0, sealedCount())
        assertEquals("the rows read as they are", rows, cache.inbox(RepositoryPageRequest(limit = 200)).size)
        assertTrue("then the sweep runs", waitUntil(20_000) { sealedCount() == rows })
    }

    @Test
    fun aSealCallThatTakesTooLongEndsTheSweepAndLeavesTheRestReadable() {
        store(CachePayloadSeal.Plain).cacheInbox(items)

        val cache = store(slowSeal(delayMs = 80), stuckMs = 40)

        assertTrue("the first call is done", waitUntil(5_000) { sealedCount() >= 1 })
        Thread.sleep(600)
        val after = sealedCount()
        assertTrue("the sweep stopped after the slow call, sealed $after of $rows", after < rows)
        Thread.sleep(400)
        assertEquals("nothing more is sealed", after, sealedCount())
        assertEquals(rows, cache.inbox(RepositoryPageRequest(limit = 200)).size)
    }
}
