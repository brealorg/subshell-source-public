package app.subshell.prototype.data.cache

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.authenticatedRedditFeedQueryKey
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Feed03CacheIsolationInstrumentedTest {
    @Test
    fun accountFeedSortAndTimeCachesStayIsolatedAcrossAppendAndDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val keys = ACCOUNTS.flatMap { account ->
            TARGETS.map { target -> authenticatedRedditFeedQueryKey(account, target) }
        }
        assertEquals(keys.size, keys.distinct().size)

        val firstStore = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context, DATABASE_NAME),
            clock = MutableCacheClock(1_000L),
            maxQueryCount = 64,
            freshForMs = 1_000_000L,
        )
        keys.forEachIndexed { index, key ->
            val refreshPosts = refreshPosts(index)
            firstStore.writeNetworkPostPage(
                request = RepositoryPageRequest(
                    limit = 2,
                    queryKey = key,
                    operation = RepositoryLoadOperation.REFRESH,
                ),
                page = NetworkPage(
                    items = refreshPosts,
                    nextCursor = "cursor-$index",
                ),
                receivedAtEpochMs = 1_000L + index,
            )
            firstStore.writeNetworkPostPage(
                request = RepositoryPageRequest(
                    cursor = "cursor-$index",
                    limit = 2,
                    queryKey = key,
                    operation = RepositoryLoadOperation.APPEND,
                ),
                page = NetworkPage(
                    items = listOf(refreshPosts.last(), appendPost(index)),
                    nextCursor = null,
                ),
                receivedAtEpochMs = 2_000L + index,
            )
        }

        keys.forEachIndexed { index, key ->
            assertSnapshot(firstStore, key, index)
        }
        assertEquals(keys.size, firstStore.diagnostics().queryCount)
        firstStore.close()

        val reopenedStore = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context, DATABASE_NAME),
            clock = MutableCacheClock(2_100L),
            maxQueryCount = 64,
            freshForMs = 1_000_000L,
        )
        keys.forEachIndexed { index, key ->
            assertSnapshot(reopenedStore, key, index)
        }
        assertEquals(keys.size, reopenedStore.diagnostics().queryCount)
        reopenedStore.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun assertSnapshot(
        store: RoomSubshellCacheStore,
        key: app.subshell.prototype.data.RepositoryQueryKey,
        index: Int,
    ) {
        val snapshot = requireNotNull(store.readPostFeedSnapshot(key))
        assertEquals(
            listOf(
                "t3_feed03_${index}_a",
                "t3_feed03_${index}_b",
                "t3_feed03_${index}_c",
            ),
            snapshot.posts.map(Post::id),
        )
        assertEquals(1, snapshot.generation)
        assertEquals(null, snapshot.remoteNextCursor)
        assertFalse(snapshot.stale)
    }

    private fun refreshPosts(index: Int): List<Post> = listOf(
        SampleData.posts[index % SampleData.posts.size].copy(
            id = "t3_feed03_${index}_a",
            title = "FEED03 $index A",
        ),
        SampleData.posts[(index + 1) % SampleData.posts.size].copy(
            id = "t3_feed03_${index}_b",
            title = "FEED03 $index B",
        ),
    )

    private fun appendPost(index: Int): Post =
        SampleData.posts[(index + 2) % SampleData.posts.size].copy(
            id = "t3_feed03_${index}_c",
            title = "FEED03 $index C",
        )

    private companion object {
        const val DATABASE_NAME = "subshell-feed03-cache-isolation.db"
        val ACCOUNTS = listOf(
            "reddit:redreader_compatibility:t2_feed03_one",
            "reddit:redreader_compatibility:t2_feed03_two",
        )
        val TARGETS = listOf(
            AuthenticatedRedditFeedTarget.home(RedditFeedSort.BEST),
            AuthenticatedRedditFeedTarget.home(RedditFeedSort.HOT),
            AuthenticatedRedditFeedTarget.main(
                RedditMainFeed.POPULAR,
                RedditFeedSort.NEW,
            ),
            AuthenticatedRedditFeedTarget.main(
                RedditMainFeed.POPULAR,
                RedditFeedSort.CONTROVERSIAL,
                RedditListingTimePeriod.WEEK,
            ),
            AuthenticatedRedditFeedTarget.main(
                RedditMainFeed.ALL,
                RedditFeedSort.TOP,
                RedditListingTimePeriod.MONTH,
            ),
            AuthenticatedRedditFeedTarget.main(
                RedditMainFeed.ALL,
                RedditFeedSort.NEW,
            ),
        )
    }
}
