package app.subshell.prototype.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.SampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CACHE06 (#204): CACHE02 made an unreadable post a cache miss. Every other
 * cached type (communities, subscriptions, discovery, users, inbox, live
 * inbox, comments, profile comments) still threw out of the read, so an
 * older build installed over a newer one could crash on every start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Cache06UnreadableRowsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
    private val page = RepositoryPageRequest(limit = 25)
    private val account = "reddit:me"

    @After
    fun close() {
        cache.close()
    }

    /** Three more positional fields, as a newer build would write. */
    private fun damage(table: String) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE $table SET payload = payload || '1:x1:y1:z'",
        )
    }

    @Test
    fun unreadableCommunitiesAreACacheMiss() {
        val communities = SampleData.communities.take(2)
        cache.cacheCommunities(communities)
        cache.replaceSubscriptionSnapshot(account, communities)
        damage("cached_communities")
        damage("cached_subscriptions")

        assertNull(cache.community(communities.first().name))
        assertTrue(cache.communities(page).isEmpty())
        assertNull(cache.subscriptionSnapshot(account))
    }

    @Test
    fun unreadableUsersAreACacheMiss() {
        val user = SampleData.users.first()
        cache.cacheUsers(listOf(user))
        damage("cached_users")

        assertNull(cache.user(user.username))
        assertTrue(cache.users(page).isEmpty())
    }

    @Test
    fun unreadableInboxRowsAreACacheMissAndMarkReadLeavesThem() {
        val items = SampleData.inbox.take(2).map { it.copy(unread = true) }
        cache.cacheInbox(items)
        cache.replaceLiveInboxSnapshot(account, items, nextCursor = null)
        damage("cached_inbox")
        damage("cached_live_inbox_entries")

        assertNull(cache.inboxItem(items.first().id))
        assertTrue(cache.inbox(page).isEmpty())
        assertNull(cache.liveInboxSnapshot(account))
        // Neither throws; the unreadable rows stay until the next refresh.
        cache.markLiveInboxItemServerRead(account, items.first().id)
        cache.markAllLiveInboxServerRead(account)
        assertNull(cache.liveInboxSnapshot(account))
    }

    @Test
    fun unreadableCommentsAreACacheMiss() {
        val comments = SampleData.comments.take(2)
        cache.cacheComments("t3_post", comments)
        cache.cacheProfileComments("someone", SampleData.profileComments.take(2))
        damage("cached_comments")
        damage("cached_profile_comments")

        assertTrue(cache.comments("t3_post", page).isEmpty())
        assertTrue(cache.profileComments("someone", page).isEmpty())
    }

    @Test
    fun readableRowsStillRead() {
        val communities = SampleData.communities.take(2)
        cache.cacheCommunities(communities)
        cache.replaceSubscriptionSnapshot(account, communities)

        assertEquals(communities.first().name, cache.community(communities.first().name)?.name)
        assertEquals(2, cache.subscriptionSnapshot(account)?.communities?.size)
    }
}
