package app.subshell.prototype.data.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.auth.live03CommentCacheKey
import app.subshell.prototype.data.auth.reddit.redditProfileCommentsCacheKey
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MED4A: OCB-500 (seen marks survive a refresh), OCB-527 (logout removes the account's cache). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Med4aAccountCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
    private val accountA = "reddit:redreader_compatibility:t2_a_x"
    private val accountB = "reddit:redreader_compatibility:t2_b"

    @After
    fun close() = cache.close()

    @Test
    fun aRefreshKeepsSeenMarksOfMessagesStillUnreadOnReddit() {
        cache.replaceLiveInboxSnapshot(accountA, listOf(item("t4_1", unread = true), item("t4_2", unread = true)), null)
        cache.markLiveInboxSeen(accountA, setOf("t4_1", "t4_2"))

        // Reddit still reports t4_1 unread (the server read failed); t4_2 is now read.
        cache.replaceLiveInboxSnapshot(accountA, listOf(item("t4_1", unread = true), item("t4_2", unread = false)), null)

        assertEquals(setOf("t4_1"), cache.liveInboxSeenIds(accountA))
    }

    @Test
    fun loggingOutRemovesOnlyThatAccountsCachedData() {
        for (account in listOf(accountA, accountB)) {
            cache.replaceLiveInboxSnapshot(account, listOf(item("t4_$account", unread = true)), null)
            cache.markLiveInboxSeen(account, "t4_$account")
            cache.replaceSubscriptionSnapshot(account, listOf(community("kotlin")))
            cache.cacheComments(live03CommentCacheKey(account, "t3_post", RedditCommentSort.BEST), listOf(comment()))
            cache.cacheProfileComments(redditProfileCommentsCacheKey(account, "someone"), listOf(profileComment()))
        }

        cache.removeAccount(accountA)

        assertNull(cache.liveInboxSnapshot(accountA))
        assertTrue(cache.liveInboxSeenIds(accountA).isEmpty())
        assertNull(cache.subscriptionSnapshot(accountA))
        assertTrue(cache.allComments(live03CommentCacheKey(accountA, "t3_post", RedditCommentSort.BEST)).isEmpty())
        assertTrue(cache.profileComments(redditProfileCommentsCacheKey(accountA, "someone"), RepositoryPageRequest(limit = 25)).isEmpty())

        assertNotNull(cache.liveInboxSnapshot(accountB))
        assertEquals(setOf("t4_$accountB"), cache.liveInboxSeenIds(accountB))
        assertNotNull(cache.subscriptionSnapshot(accountB))
        assertEquals(1, cache.allComments(live03CommentCacheKey(accountB, "t3_post", RedditCommentSort.BEST)).size)
        assertEquals(1, cache.profileComments(redditProfileCommentsCacheKey(accountB, "someone"), RepositoryPageRequest(limit = 25)).size)
    }

    private fun item(id: String, unread: Boolean) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "t",
        sender = "s",
        age = "1h",
        preview = "p",
        unread = unread,
    )

    private fun community(name: String) = Community(id = "t5_$name", name = name, description = "", members = "1", accent = 0L, joined = true)

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
