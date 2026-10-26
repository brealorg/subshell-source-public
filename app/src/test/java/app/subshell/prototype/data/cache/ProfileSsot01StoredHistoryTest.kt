package app.subshell.prototype.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PROFILESSOT01 (#244) in Room: one comment's edit or delete reaches every
 * stored history that lists it, and an observed history sees the change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileSsot01StoredHistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database)

    @After
    fun close() {
        cache.close()
    }

    private fun comment(id: String, body: String) = ProfileCommentFixture(
        id = id,
        author = "me_user",
        postId = "t3_abc",
        age = "1h",
        body = body,
        score = 1,
        placement = ThreadPlacement.TOP,
    )

    private fun bodies(key: String) =
        cache.profileComments(key, RepositoryPageRequest(cursor = null, limit = 100)).map(ProfileCommentFixture::body)

    @Test
    fun anEditReachesEveryHistoryListingTheComment() {
        cache.cacheProfileComments("account|a|comments|me_user", listOf(comment("t1_x", "old"), comment("t1_y", "other")))
        cache.cacheProfileComments("account|b|comments|me_user", listOf(comment("t1_x", "old")))

        cache.updateProfileComment(listOf("t1_x", "x")) { stored -> stored.copy(body = "edited") }

        assertEquals(listOf("edited", "other"), bodies("account|a|comments|me_user"))
        assertEquals(listOf("edited"), bodies("account|b|comments|me_user"))
    }

    @Test
    fun aDeleteLeavesTheCommentOutOfTheHistory() {
        cache.cacheProfileComments("account|a|comments|me_user", listOf(comment("t1_x", "gone"), comment("t1_y", "kept")))

        cache.updateProfileComment(listOf("t1_x", "x")) { null }

        assertEquals(listOf("kept"), bodies("account|a|comments|me_user"))
    }

    @Test
    fun anObservedHistorySeesTheEdit() = runBlocking {
        val key = "account|a|comments|me_user"
        cache.cacheProfileComments(key, listOf(comment("t1_x", "old")))
        val seen = async(Dispatchers.IO) {
            withTimeout(5_000) { cache.observeProfileComments(key).first { list -> list.singleOrNull()?.body == "edited" } }
        }
        Thread.sleep(200)

        cache.updateProfileComment(listOf("t1_x")) { stored -> stored.copy(body = "edited") }

        assertEquals("edited", seen.await().single().body)
    }
}
