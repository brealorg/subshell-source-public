package app.subshell.prototype.data.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
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
 * CACHE01: a feed read only moves last access; an APPEND whose cursor is no
 * longer the stored one (the listing was replaced while the page was
 * fetched) writes nothing; the sync result comes from the write itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Cache01FeedMetadataTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000L)
    private val cache = RoomSubshellCacheStore(database, clock = clock, maxQueryCount = 2)
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

    private fun request(operation: RepositoryLoadOperation, cursor: String? = null, key: RepositoryQueryKey = home) =
        RepositoryPageRequest(cursor = cursor, limit = 25, queryKey = key, operation = operation)

    private fun write(operation: RepositoryLoadOperation, page: NetworkPage<Post>, cursor: String? = null, key: RepositoryQueryKey = home) =
        cache.writeNetworkPostPage(request(operation, cursor, key), page, receivedAtEpochMs = clock.epochMs)

    private fun success(page: NetworkPage<Post>): NetworkResult<NetworkPage<Post>> = NetworkResult.Success(
        value = page,
        metadata = NetworkResponseMetadata(200, clock.epochMs, NetworkRateLimit(null, null, null), null),
    )

    @Test
    fun anAppendWithAReplacedCursorWritesNothing() {
        write(RepositoryLoadOperation.INITIAL, page("t3_a", "t3_b", cursor = "c1"))
        write(RepositoryLoadOperation.INITIAL, page("t3_x", "t3_y", cursor = "n1"))

        val stale = write(RepositoryLoadOperation.APPEND, page("t3_c", "t3_d", cursor = "c2"), cursor = "c1")

        assertNull(stale)
        val snapshot = cache.readPostFeedSnapshot(home)!!
        assertEquals(listOf("t3_x", "t3_y"), snapshot.posts.map(Post::id))
        assertEquals("n1", snapshot.remoteNextCursor)
    }

    @Test
    fun aListingReplacedWhileAPageIsFetchedIsReportedAsACursorMismatch() = runBlocking {
        write(RepositoryLoadOperation.INITIAL, page("t3_a", "t3_b", cursor = "c1"))
        val synchronizer = OfflineFirstPostFeedSynchronizer(
            networkSource = {
                // The bootstrap replaces Home while this APPEND is in flight.
                write(RepositoryLoadOperation.INITIAL, page("t3_x", "t3_y", cursor = "n1"))
                success(page("t3_c", "t3_d", cursor = "c2"))
            },
            cacheStore = cache,
        )

        val result = synchronizer.synchronize(request(RepositoryLoadOperation.APPEND, cursor = "c1"))

        val failure = result as PostFeedSyncResult.PreconditionFailure
        assertEquals(PostFeedSyncPreconditionFailureKind.REMOTE_CURSOR_MISMATCH, failure.kind)
        assertEquals("n1", failure.expectedRemoteCursor)
        assertEquals(listOf("t3_x", "t3_y"), cache.readPostFeedSnapshot(home)!!.posts.map(Post::id))
    }

    @Test
    fun theSyncResultCountsTheStoredFeed() = runBlocking {
        val pages = ArrayDeque(listOf(page("t3_a", "t3_b", "t3_c", cursor = "c1"), page("t3_d", "t3_e", cursor = null)))
        val synchronizer = OfflineFirstPostFeedSynchronizer(networkSource = { success(pages.removeFirst()) }, cacheStore = cache)

        synchronizer.synchronize(request(RepositoryLoadOperation.INITIAL))
        val appended = synchronizer.synchronize(request(RepositoryLoadOperation.APPEND, cursor = "c1"))

        val applied = appended as PostFeedSyncResult.Applied
        assertEquals(2, applied.networkItemCount)
        assertEquals(5, applied.storedItemCount)
        assertEquals(null, applied.remoteNextCursor)
        assertTrue(applied.terminal)
    }

    @Test
    fun aReadKeepsItsQueryRecentWithoutRewritingIt() {
        val other = RepositoryQueryKey("account|authenticated|reddit:me|live02|popular|hot")
        val third = RepositoryQueryKey("account|authenticated|reddit:me|live02|all|new")
        write(RepositoryLoadOperation.INITIAL, page("t3_a", cursor = "c1"))
        clock.epochMs = 2_000L
        write(RepositoryLoadOperation.INITIAL, page("t3_b", cursor = "p1"), key = other)
        clock.epochMs = 3_000L
        assertNotNull(cache.readPostFeedSnapshot(home))
        clock.epochMs = 4_000L

        write(RepositoryLoadOperation.INITIAL, page("t3_c", cursor = "q1"), key = third)

        val kept = cache.queryMetadata(home)!!
        assertEquals("c1", kept.nextCursor)
        assertEquals(0, kept.generation)
        assertNull(cache.queryMetadata(other))
    }
}
