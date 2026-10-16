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
 * OCB102_DELETED_POST_LEAVES_THE_CACHE_V1: a deleted post's rows (unscoped
 * and per account) and its places in cached lists go; the rest stays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Ocb102DeletedPostCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000L)
    private val cache = RoomSubshellCacheStore(database, clock = clock)
    private val homeA = RepositoryQueryKey("account|authenticated|reddit:a|live02|home|best")
    private val popular = RepositoryQueryKey("popular|hot")

    @After
    fun close() {
        cache.close()
    }

    private fun post(id: String) = Post(
        id = id,
        community = "r/test",
        author = "me",
        age = "1h",
        title = "Post $id",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun write(key: RepositoryQueryKey, posts: List<Post>) =
        cache.writeNetworkPostPage(
            RepositoryPageRequest(limit = 25, queryKey = key, operation = RepositoryLoadOperation.INITIAL),
            NetworkPage(posts, "c1"),
            receivedAtEpochMs = clock.epochMs,
        )

    @Test
    fun aDeletedPostLeavesEveryCachedListAndRow() {
        write(homeA, listOf(post("t3_keep"), post("t3_gone")))
        write(popular, listOf(post("t3_gone"), post("t3_other")))

        cache.removeDeletedPost("t3_gone")

        assertEquals(listOf("t3_keep"), cache.readPostFeedSnapshot(homeA)!!.posts.map(Post::id))
        assertEquals(
            listOf("t3_other"),
            cache.readPostPage(RepositoryPageRequest(limit = 25, queryKey = popular))!!.posts.map(Post::id),
        )
        assertNull(cache.post("t3_gone"))
    }
}
