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
 * ISSUE70_POST_LOOKUP_STAYS_IN_ITS_ACCOUNT_V1: the post lookup behind route
 * restores and links fell back to the most recently used account-scoped row
 * of any account, so B could see A's vote on a post after a restore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue70PostLookupPerAccountTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000L)
    private val cache = RoomSubshellCacheStore(database, clock = clock)
    private val homeA = RepositoryQueryKey("account|authenticated|reddit:a|live02|home|best")
    private val homeB = RepositoryQueryKey("account|authenticated|reddit:b|live02|home|best")
    private val popular = RepositoryQueryKey("popular|hot")

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

    private fun write(key: RepositoryQueryKey, posts: List<Post>) =
        cache.writeNetworkPostPage(
            RepositoryPageRequest(limit = 25, queryKey = key, operation = RepositoryLoadOperation.INITIAL),
            NetworkPage(posts, "c1"),
            receivedAtEpochMs = clock.epochMs,
        )

    @Test
    fun eachAccountReadsItsOwnRow() {
        write(homeA, listOf(post("t3_x", vote = 1, saved = true)))
        write(homeB, listOf(post("t3_x")))

        assertEquals(1, cache.postForAccount("t3_x", "reddit:a")?.vote)
        assertEquals(0, cache.postForAccount("t3_x", "reddit:b")?.vote)
    }

    @Test
    fun anotherAccountsRowIsNeverUsed() {
        write(homeA, listOf(post("t3_x", vote = 1, saved = true)))

        assertNull(cache.postForAccount("t3_x", "reddit:b"))
        // The account-less lookup still finds it (existence/kind checks).
        assertEquals(1, cache.post("t3_x")?.vote)
    }

    @Test
    fun withoutItsOwnRowAnAccountReadsTheSharedRow() {
        write(popular, listOf(post("t3_y")))
        write(homeA, listOf(post("t3_y", vote = 1)))

        assertEquals(0, cache.postForAccount("t3_y", "reddit:b")?.vote)
        assertEquals(1, cache.postForAccount("t3_y", "reddit:a")?.vote)
        assertEquals(0, cache.postForAccount("t3_y", null)?.vote)
    }
}
