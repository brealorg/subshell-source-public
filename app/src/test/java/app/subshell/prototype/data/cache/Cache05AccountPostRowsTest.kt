package app.subshell.prototype.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CACHE05: a cached post carries per-account state (vote, saved). Two
 * accounts whose feeds hold the same post keep their own copy, the plain
 * post lookup still finds it, and an append over entries written before
 * CACHE05 does not duplicate a post.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Cache05AccountPostRowsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000L)
    private val cache = RoomSubshellCacheStore(database, clock = clock)
    private val homeA = RepositoryQueryKey("account|authenticated|reddit:a|live02|home|best")
    private val homeB = RepositoryQueryKey("account|authenticated|reddit:b|live02|home|best")

    @After
    fun close() {
        cache.close()
    }

    private fun post(id: String, vote: Int = 0, saved: Boolean = false) = Post(
        id = id,
        community = "r/test",
        author = "someone",
        age = "1h",
        title = "Post $id",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        vote = vote,
        saved = saved,
    )

    private fun write(key: RepositoryQueryKey, operation: RepositoryLoadOperation, posts: List<Post>, cursor: String? = null, next: String? = "c1") =
        cache.writeNetworkPostPage(
            RepositoryPageRequest(cursor = cursor, limit = 25, queryKey = key, operation = operation),
            NetworkPage(posts, next),
            receivedAtEpochMs = clock.epochMs,
        )

    @Test
    fun twoAccountsKeepTheirOwnVoteAndSavedStateForTheSamePost() {
        write(homeA, RepositoryLoadOperation.INITIAL, listOf(post("t3_x", vote = 1, saved = true)))
        write(homeB, RepositoryLoadOperation.INITIAL, listOf(post("t3_x")))

        val a = cache.readPostFeedSnapshot(homeA)!!.posts.single()
        val b = cache.readPostFeedSnapshot(homeB)!!.posts.single()
        assertEquals(1, a.vote)
        assertTrue(a.saved)
        assertEquals(0, b.vote)
    }

    @Test
    fun thePlainPostLookupStillFindsAnAccountScopedRow() {
        write(homeA, RepositoryLoadOperation.INITIAL, listOf(post("t3_x", vote = 1)))

        assertEquals("t3_x", cache.post("t3_x")?.id)
    }

    @Test
    fun anAppendOverEntriesWrittenBeforeScopingDoesNotDuplicateAPost() {
        // A feed stored before CACHE05: its entries point at unscoped rows.
        val dao = database.cacheDao()
        dao.upsertPosts(listOf(CachedPostEntity("t3_x", SubshellCacheCodec.encodePost(post("t3_x")), 1_000L)))
        dao.upsertQuery(CachedQueryEntity(homeA.value, 0, "c1", 1_000L, 1_000L, false))
        dao.insertQueryEntries(listOf(CachedQueryEntryEntity(homeA.value, "t3_x", 0, 0)))

        write(homeA, RepositoryLoadOperation.APPEND, listOf(post("t3_x"), post("t3_y")), cursor = "c1", next = null)

        assertEquals(listOf("t3_x", "t3_y"), cache.readPostFeedSnapshot(homeA)!!.posts.map(Post::id))
    }
}
