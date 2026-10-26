package app.subshell.prototype.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.PostStoreChanges
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.model.Post
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FEEDSSOT01_FEEDS_FOLLOW_THE_STORE_V1 (#244): a committed change to the stored
 * posts makes every loaded feed read the store again (no hand-made push), a
 * feed that was never opened is not loaded, and the store signals post writes
 * but not its own feed reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedSsot01FeedsFollowTheStoreTest {
    /** A repository whose stored posts a test changes, signalling each change. */
    private class StoreRepository(
        private val fake: SubshellRepository = FakeSubshellRepository(),
    ) : SubshellRepository by fake, PostStoreChanges {
        @Volatile var stored: List<Post> = SampleData.posts.take(2)
        val reads = mutableMapOf<RepositoryQueryKey, Int>()
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

        override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
            reads[request.queryKey] = (reads[request.queryKey] ?: 0) + 1
            return RepositoryResult.Content(stored)
        }

        override fun postStoreChanges(): Flow<Unit> = changes
    }

    @Test
    fun aStoreChangeReachesALoadedFeedAndLeavesUnopenedFeedsAlone() = runBlocking {
        val repository = StoreRepository()
        val state = SubshellState(repository = repository)
        val home = RepositoryQueryKey("feed:home")
        val unopened = RepositoryQueryKey("feed:unopened")
        state.ensurePostPaging(home)
        assertEquals(2, state.postPagingSnapshot(home).items.size)
        val following = launch(start = CoroutineStart.UNDISPATCHED) { state.followPostStore() }
        while (repository.changes.subscriptionCount.value == 0) yield()

        // Another writer (a feed sync, a vote) committed a change.
        repository.stored = SampleData.posts.take(3)
        repository.changes.emit(Unit)
        repeat(3) { yield() }

        assertEquals(3, state.postPagingSnapshot(home).items.size)
        assertFalse(unopened in repository.reads)
        following.cancel()
    }

    @Test
    fun theStoreSignalsAPostWriteButNotItsOwnFeedRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cache = RoomSubshellCacheStore(database)
        val signals = AtomicInteger()
        val listening = CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            listening.countDown()
            cache.postStoreChanges().collect { signals.incrementAndGet() }
        }
        assertTrue(listening.await(5, TimeUnit.SECONDS))
        Thread.sleep(300)
        val request = RepositoryPageRequest(
            cursor = null,
            limit = 10,
            queryKey = RepositoryQueryKey("feed:written"),
            operation = RepositoryLoadOperation.REFRESH,
        )

        cache.writePostResult(request, RepositoryResult.Content(SampleData.posts.take(2)))
        Thread.sleep(500)
        val afterWrite = signals.get()
        // A read touches only the query's last access.
        cache.readPostPage(request.copy(operation = RepositoryLoadOperation.INITIAL))
        Thread.sleep(500)

        assertTrue("a write signals", afterWrite >= 1)
        assertEquals(afterWrite, signals.get())
        job.cancel()
        cache.close()
    }
}
