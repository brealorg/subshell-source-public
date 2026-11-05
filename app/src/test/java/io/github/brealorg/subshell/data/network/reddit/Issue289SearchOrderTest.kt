package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.RedditFeedSort
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditMainFeed
import io.github.brealorg.subshell.data.RedditSearchOrder
import io.github.brealorg.subshell.data.RedditSearchSort
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFeedTarget
import io.github.brealorg.subshell.data.auth.authenticatedRedditFeedQueryKey
import io.github.brealorg.subshell.data.liveSearchPostFeedQueryKey
import io.github.brealorg.subshell.data.network.JsonGetRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #289 (reader): sort the posts of a search. ISSUE289_SEARCH_RESULTS_CAN_BE_SORTED_V1.
 */
class Issue289SearchOrderTest {
    private fun param(request: JsonGetRequest, name: String): String? =
        request.queryParameters.firstOrNull { it.first == name }?.second

    @Test
    fun theDefaultSearchIsStillRelevanceOverAllTime() {
        val request = redditSearchJsonRequest("cats")

        assertEquals("relevance", param(request, "sort"))
        assertEquals("all", param(request, "t"))
    }

    @Test
    fun aSortAndATimeRangeGoToReddit() {
        val top = redditSearchJsonRequest("cats", order = RedditSearchOrder(RedditSearchSort.TOP, RedditListingTimePeriod.WEEK))
        val comments = redditSearchJsonRequest("cats", order = RedditSearchOrder(RedditSearchSort.COMMENTS, RedditListingTimePeriod.DAY))

        assertEquals("top", param(top, "sort"))
        assertEquals("week", param(top, "t"))
        assertEquals("comments", param(comments, "sort"))
        assertEquals("day", param(comments, "t"))
    }

    @Test
    fun hotAndNewIgnoreTheTimeRange() {
        val hot = redditSearchJsonRequest("cats", order = RedditSearchOrder(RedditSearchSort.HOT, RedditListingTimePeriod.WEEK))
        val new = redditSearchJsonRequest("cats", order = RedditSearchOrder(RedditSearchSort.NEW, RedditListingTimePeriod.YEAR))

        assertEquals("hot", param(hot, "sort"))
        assertEquals("all", param(hot, "t"))
        assertEquals("new", param(new, "sort"))
        assertEquals("all", param(new, "t"))
    }

    @Test
    fun theDefaultOrderKeepsTheCacheKeyEveryEarlierVersionStored() {
        val key = liveSearchPostFeedQueryKey("account", "cats").value

        assertTrue(key, !key.contains("|order|"))
        assertEquals(key, liveSearchPostFeedQueryKey("account", "cats", order = RedditSearchOrder.DEFAULT).value)
        // A sort that ignores the range is the same order whatever range was picked before.
        assertEquals(
            liveSearchPostFeedQueryKey("account", "cats", order = RedditSearchOrder(RedditSearchSort.NEW)).value,
            liveSearchPostFeedQueryKey(
                "account",
                "cats",
                order = RedditSearchOrder(RedditSearchSort.NEW, RedditListingTimePeriod.YEAR),
            ).value,
        )
    }

    @Test
    fun eachOrderHasItsOwnStoredResults() {
        val relevance = liveSearchPostFeedQueryKey("account", "cats").value
        val newest = liveSearchPostFeedQueryKey("account", "cats", order = RedditSearchOrder(RedditSearchSort.NEW)).value
        val topWeek = liveSearchPostFeedQueryKey("account", "cats", order = RedditSearchOrder(RedditSearchSort.TOP, RedditListingTimePeriod.WEEK)).value
        val topYear = liveSearchPostFeedQueryKey("account", "cats", order = RedditSearchOrder(RedditSearchSort.TOP, RedditListingTimePeriod.YEAR)).value

        assertEquals(4, setOf(relevance, newest, topWeek, topYear).size)
        assertNotEquals(relevance, newest)
    }

    @Test
    fun theSearchTargetCarriesItsOrderIntoTheKey() {
        val order = RedditSearchOrder(RedditSearchSort.TOP, RedditListingTimePeriod.MONTH)
        val target = AuthenticatedRedditFeedTarget.search("cats", order = order)

        assertEquals(order, target.searchOrder)
        assertEquals(
            liveSearchPostFeedQueryKey("account", "cats", order = order),
            authenticatedRedditFeedQueryKey("account", target),
        )
    }

    @Test
    fun onlyASearchHasASearchOrder() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedRedditFeedTarget(
                mainFeed = RedditMainFeed.HOME,
                sort = RedditFeedSort.BEST,
                searchOrder = RedditSearchOrder(RedditSearchSort.NEW),
            )
        }
    }
}
