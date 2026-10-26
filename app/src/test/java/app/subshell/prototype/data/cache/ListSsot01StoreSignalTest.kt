package app.subshell.prototype.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LISTSSOT01_LISTS_FOLLOW_THE_STORE_V1 (#244): the store signals once when
 * observing starts and once per change to what the community, user and inbox
 * lists hold; rewriting the same rows (what a list read does) and reading do
 * not signal, so a reader that re-reads on the signal does not wake itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ListSsot01StoreSignalTest {
    @Test
    fun aChangeSignalsAndARewriteOrAReadDoesNot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cache = RoomSubshellCacheStore(database)
        val community = Community(id = "t5_abc", name = "kotlin", description = "", members = "1", accent = 0L)
        val signals = AtomicInteger()
        val started = CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            cache.listStoreChanges().collect {
                signals.incrementAndGet()
                started.countDown()
            }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        Thread.sleep(300)
        assertEquals("the state when observing starts", 1, signals.get())

        cache.cacheCommunities(listOf(community))
        Thread.sleep(500)
        assertEquals("a new community", 2, signals.get())

        cache.cacheCommunities(listOf(community))
        cache.communities(RepositoryPageRequest(cursor = null, limit = 50, queryKey = RepositoryQueryKey("communities"), operation = RepositoryLoadOperation.INITIAL))
        Thread.sleep(500)
        assertEquals("the same row again, and a read", 2, signals.get())

        cache.cacheUsers(listOf(UserFixture(username = "someone", description = "", karma = "1")))
        Thread.sleep(500)
        assertEquals("a new user", 3, signals.get())

        job.cancel()
        cache.close()
    }
}
