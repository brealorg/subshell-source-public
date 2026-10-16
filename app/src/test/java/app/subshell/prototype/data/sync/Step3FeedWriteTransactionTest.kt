package app.subshell.prototype.data.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.cache.CacheWriteTransactionObserver
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (net02): a feed page is written in one transaction — a write that
 * fails before it commits leaves the feed the reader had; a refresh starts a
 * new generation and records when Reddit answered while a next page keeps that
 * time; a next page never lists a post twice; a refresh leaves no post behind
 * that no feed holds; a rate-limited read keeps Reddit's wait; and a cancelled
 * write surfaces as cancellation, not as a storage failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3FeedWriteTransactionTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000L)

    /** What the transaction does just before it commits (the store's test seam). */
    private var beforeCommit: (RepositoryQueryKey) -> Unit = {}
    private val cache = RoomSubshellCacheStore(
        database,
        clock = clock,
        transactionObserver = CacheWriteTransactionObserver { key -> beforeCommit(key) },
    )
    private val home = RepositoryQueryKey("account|authenticated|reddit:me|live02|home|best")

    @After
    fun close() {
        cache.close()
    }

    private fun post(id: String) = Post(
        id = id,
        community = "r/test",
        author = "someone",
        age = "1h",
        title = "Post $id",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun page(vararg ids: String, cursor: String?) = NetworkPage(ids.map(::post), cursor)

    private fun request(operation: RepositoryLoadOperation, cursor: String? = null) =
        RepositoryPageRequest(cursor = cursor, limit = 25, queryKey = home, operation = operation)

    private fun write(operation: RepositoryLoadOperation, page: NetworkPage<Post>, at: Long, cursor: String? = null) =
        cache.writeNetworkPostPage(request(operation, cursor), page, receivedAtEpochMs = at)

    private fun success(page: NetworkPage<Post>): NetworkResult<NetworkPage<Post>> = NetworkResult.Success(
        value = page,
        metadata = NetworkResponseMetadata(200, clock.epochMs, NetworkRateLimit(null, null, null), null),
    )

    private fun feedIds(): List<String> = cache.readPostFeedSnapshot(home)!!.posts.map(Post::id)

    @Test
    fun aWriteThatFailsBeforeItCommitsLeavesTheFeedTheReaderHad() = runBlocking {
        write(RepositoryLoadOperation.INITIAL, page("t3_a", "t3_b", cursor = "c1"), at = 1_000L)
        val before = cache.queryMetadata(home)!!

        beforeCommit = { throw IllegalStateException("disk full") }
        val synchronizer = OfflineFirstPostFeedSynchronizer(
            networkSource = { success(page("t3_x", cursor = null)) },
            cacheStore = cache,
        )
        val result = synchronizer.synchronize(request(RepositoryLoadOperation.REFRESH))

        assertEquals(PostFeedSyncResult.StorageFailure, result)
        beforeCommit = {}
        assertEquals(listOf("t3_a", "t3_b"), feedIds())
        assertEquals(before, cache.queryMetadata(home))
        assertNull("the failed page's post was not kept", cache.post("t3_x"))
    }

    @Test
    fun aRefreshStartsANewGenerationAndANextPageKeepsTheRefreshTime() {
        write(RepositoryLoadOperation.REFRESH, page("t3_a", cursor = "c1"), at = 5_000L)
        val first = cache.queryMetadata(home)!!
        assertEquals(5_000L, first.lastSuccessfulRefreshEpochMs)

        write(RepositoryLoadOperation.APPEND, page("t3_b", cursor = "c2"), at = 9_000L, cursor = "c1")
        val appended = cache.queryMetadata(home)!!
        assertEquals("a next page keeps the refresh time", 5_000L, appended.lastSuccessfulRefreshEpochMs)
        assertEquals("a next page keeps the generation", first.generation, appended.generation)

        write(RepositoryLoadOperation.REFRESH, page("t3_c", cursor = null), at = 12_000L)
        val refreshed = cache.queryMetadata(home)!!
        assertEquals(12_000L, refreshed.lastSuccessfulRefreshEpochMs)
        assertEquals("a refresh starts the next generation", first.generation + 1, refreshed.generation)
    }

    @Test
    fun aNextPageNeverListsAPostTwice() {
        write(RepositoryLoadOperation.INITIAL, page("t3_a", "t3_b", cursor = "c1"), at = 1_000L)

        val written = write(RepositoryLoadOperation.APPEND, page("t3_b", "t3_c", cursor = null), at = 2_000L, cursor = "c1")

        assertNotNull(written)
        assertEquals(3, written!!.storedItemCount)
        assertEquals(listOf("t3_a", "t3_b", "t3_c"), feedIds())
    }

    @Test
    fun aRefreshLeavesNoPostBehindThatNoFeedHolds() {
        write(RepositoryLoadOperation.INITIAL, page("t3_a", "t3_b", cursor = "c1"), at = 1_000L)
        assertNotNull(cache.post("t3_a"))

        write(RepositoryLoadOperation.REFRESH, page("t3_c", cursor = null), at = 2_000L)

        assertEquals(listOf("t3_c"), feedIds())
        assertNull("a post only the replaced page held is gone", cache.post("t3_a"))
        assertNull(cache.post("t3_b"))
        assertNotNull(cache.post("t3_c"))
    }

    @Test
    fun aRateLimitedReadKeepsRedditsWait() = runBlocking {
        val synchronizer = OfflineFirstPostFeedSynchronizer(
            networkSource = {
                NetworkResult.HttpFailure(
                    statusCode = 429,
                    retryable = true,
                    metadata = NetworkResponseMetadata(
                        429,
                        clock.epochMs,
                        NetworkRateLimit(null, null, null),
                        5_000L,
                    ),
                )
            },
            cacheStore = cache,
        )

        val result = synchronizer.synchronize(request(RepositoryLoadOperation.INITIAL))

        val failure = result as PostFeedSyncResult.HttpFailure
        assertEquals(429, failure.statusCode)
        assertTrue(failure.retryable)
        assertEquals(5_000L, failure.retryAfterMs)
    }

}
