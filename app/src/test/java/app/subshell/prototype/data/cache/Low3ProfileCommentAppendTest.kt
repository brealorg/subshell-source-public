package app.subshell.prototype.data.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW3 (OCB-519): a later profile-comment page adds only its new comments after
 * the list's last position; it no longer re-encodes and rewrites the whole list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low3ProfileCommentAppendTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))

    @After
    fun close() = cache.close()

    @Test
    fun aLaterPageAddsOnlyItsNewCommentsAfterTheLastOne() {
        cache.cacheProfileComments("me", listOf(comment("t1_a"), comment("t1_b")))

        val added = cache.appendProfileComments("me", listOf(comment("t1_b", body = "repeated"), comment("t1_c"), comment("t1_d")))

        assertEquals(listOf("t1_c", "t1_d"), added.map { it.id })
        val stored = cache.profileComments("me", RepositoryPageRequest(limit = 25))
        assertEquals(listOf("t1_a", "t1_b", "t1_c", "t1_d"), stored.map { it.id })
        // A comment the list already holds keeps its place and stored copy.
        assertEquals("body", stored[1].body)
    }

    @Test
    fun appendingToAnotherListLeavesThisOneAlone() {
        cache.cacheProfileComments("me", listOf(comment("t1_a")))

        cache.appendProfileComments("other", listOf(comment("t1_a"), comment("t1_x")))

        assertEquals(listOf("t1_a"), cache.profileComments("me", RepositoryPageRequest(limit = 25)).map { it.id })
        assertEquals(listOf("t1_a", "t1_x"), cache.profileComments("other", RepositoryPageRequest(limit = 25)).map { it.id })
    }

    private fun comment(id: String, body: String = "body") = ProfileCommentFixture(
        id = id,
        author = "me",
        postId = "t3_post",
        age = "1h",
        body = body,
        score = 1,
        placement = ThreadPlacement.TOP,
    )
}
