package app.subshell.prototype.data.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MED4B: OCB-526 (comment caches are capped), OCB-578/619 (one runtime cache store per process). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Med4bCacheBoundsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableCacheClock(1_000L)
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(
        database,
        clock = clock,
        maxCommentThreads = 2,
        maxProfileCommentLists = 2,
    )

    @After
    fun close() = cache.close()

    @Test
    fun onlyTheMostRecentlyWrittenCommentThreadsAreKept() {
        for ((step, key) in listOf("thread-a", "thread-b", "thread-c").withIndex()) {
            clock.epochMs = 1_000L + step
            cache.cacheComments(key, listOf(comment()))
        }

        assertTrue(cache.allComments("thread-a").isEmpty())
        assertEquals(1, cache.allComments("thread-b").size)
        assertEquals(1, cache.allComments("thread-c").size)
    }

    @Test
    fun onlyTheMostRecentlyWrittenProfileCommentListsAreKept() {
        for ((step, key) in listOf("user-a", "user-b", "user-c").withIndex()) {
            clock.epochMs = 1_000L + step
            cache.cacheProfileComments(key, listOf(profileComment()))
        }
        val page = RepositoryPageRequest(limit = 25)

        assertTrue(cache.profileComments("user-a", page).isEmpty())
        assertEquals(1, cache.profileComments("user-c", page).size)
    }

    @Test
    fun theRuntimeCacheStoreIsOnePerProcess() {
        assertSame(processRuntimeCacheStore(context), processRuntimeCacheStore(context))
    }

    private fun comment() = Comment(id = "t1_c1", author = "me", age = "1h", body = "hi", score = 1, depth = 0, accent = 0L)

    private fun profileComment() = ProfileCommentFixture(
        id = "t1_p1",
        author = "someone",
        postId = "t3_post",
        age = "1h",
        body = "hi",
        score = 1,
        placement = ThreadPlacement.TOP,
    )
}
