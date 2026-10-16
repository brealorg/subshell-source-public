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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CACHE02: a cached post this build cannot read (a newer build's payload
 * after installing an older one over it, or a damaged row) is a cache miss,
 * not an exception out of every feed read; the next network page heals it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Cache02UnreadablePostTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
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

    private fun writeInitial(vararg ids: String) = cache.writeNetworkPostPage(
        RepositoryPageRequest(limit = 25, queryKey = home, operation = RepositoryLoadOperation.INITIAL),
        NetworkPage(ids.map(::post), nextCursor = "c1"),
        receivedAtEpochMs = 1_000L,
    )

    private fun damage(id: String) {
        // Three more positional fields, as a newer build would write.
        val payload = SubshellCacheCodec.encodePost(post(id)) + "1:x1:y1:z"
        // CACHE05: account feeds store their rows under an account-scoped key.
        database.cacheDao().upsertPosts(listOf(CachedPostEntity(postId = cachedPostRowKey(home, id), payload = payload, lastAccessEpochMs = 1_000L)))
    }

    @Test
    fun anUnreadableRowMakesTheFeedACacheMissInsteadOfThrowing() {
        writeInitial("t3_a", "t3_b")
        damage("t3_b")

        assertNull(cache.readPostFeedSnapshot(home))
        assertNull(cache.readPostPage(RepositoryPageRequest(limit = 25, queryKey = home)))
        assertNull(cache.post("t3_b"))
        assertEquals("t3_a", cache.post("t3_a")?.id)
    }

    @Test
    fun theNextNetworkPageReplacesTheUnreadableRow() {
        writeInitial("t3_a", "t3_b")
        damage("t3_b")

        writeInitial("t3_b", "t3_c")

        assertEquals(listOf("t3_b", "t3_c"), cache.readPostFeedSnapshot(home)!!.posts.map(Post::id))
    }
}
