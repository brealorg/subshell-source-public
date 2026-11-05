package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.RedditFeedSort
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditMainFeed
import io.github.brealorg.subshell.data.RedditProfilePostSort
import io.github.brealorg.subshell.data.RedditProfilePostsOrder
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFeedTarget
import io.github.brealorg.subshell.data.auth.authenticatedRedditFeedQueryKey
import io.github.brealorg.subshell.data.network.JsonGetRequest
import io.github.brealorg.subshell.data.redditUserSubmittedPostFeedQueryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** #289: sort the posts on a profile. ISSUE289_PROFILE_POSTS_CAN_BE_SORTED_V1. */
class Issue289ProfilePostsOrderTest {
    private fun param(request: JsonGetRequest, name: String): String? =
        request.queryParameters.firstOrNull { it.first == name }?.second

    @Test
    fun theDefaultProfileRequestIsStillNewWithNoTimeRange() {
        val request = redditUserSubmittedJsonRequest("someone")

        assertEquals("new", param(request, "sort"))
        assertNull(param(request, "t"))
    }

    @Test
    fun aSortAndATimeRangeGoToReddit() {
        val top = redditUserSubmittedJsonRequest("someone", order = RedditProfilePostsOrder(RedditProfilePostSort.TOP, RedditListingTimePeriod.WEEK))
        val controversial = redditUserSubmittedJsonRequest(
            "someone",
            order = RedditProfilePostsOrder(RedditProfilePostSort.CONTROVERSIAL, RedditListingTimePeriod.DAY),
        )

        assertEquals("top", param(top, "sort"))
        assertEquals("week", param(top, "t"))
        assertEquals("controversial", param(controversial, "sort"))
        assertEquals("day", param(controversial, "t"))
    }

    @Test
    fun hotSendsNoTimeRange() {
        val hot = redditUserSubmittedJsonRequest("someone", order = RedditProfilePostsOrder(RedditProfilePostSort.HOT, RedditListingTimePeriod.YEAR))

        assertEquals("hot", param(hot, "sort"))
        assertNull(param(hot, "t"))
    }

    @Test
    fun newKeepsTheKeyEveryEarlierVersionStoredAndEachOtherOrderHasItsOwn() {
        val newKey = redditUserSubmittedPostFeedQueryKey("account", "someone").value
        assertTrue(newKey, newKey.endsWith("|submitted"))

        val keys = listOf(
            RedditProfilePostsOrder(RedditProfilePostSort.NEW, RedditListingTimePeriod.YEAR),
            RedditProfilePostsOrder(RedditProfilePostSort.HOT),
            RedditProfilePostsOrder(RedditProfilePostSort.TOP, RedditListingTimePeriod.WEEK),
            RedditProfilePostsOrder(RedditProfilePostSort.TOP, RedditListingTimePeriod.YEAR),
            RedditProfilePostsOrder(RedditProfilePostSort.CONTROVERSIAL),
        ).map { redditUserSubmittedPostFeedQueryKey("account", "someone", it).value }

        // New with a stale range is still New, the first key; the other four each differ from it
        // and from each other.
        assertEquals(newKey, keys.first())
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun theUserTargetCarriesItsOrderIntoTheKey() {
        val order = RedditProfilePostsOrder(RedditProfilePostSort.TOP, RedditListingTimePeriod.MONTH)
        val target = AuthenticatedRedditFeedTarget.user("someone", order)

        assertEquals(order, target.userPostsOrder)
        assertEquals(
            redditUserSubmittedPostFeedQueryKey("account", "someone", order),
            authenticatedRedditFeedQueryKey("account", target),
        )
    }

    @Test
    fun onlyAUserTargetHasAPostsOrder() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedRedditFeedTarget(
                mainFeed = RedditMainFeed.HOME,
                sort = RedditFeedSort.BEST,
                userPostsOrder = RedditProfilePostsOrder(RedditProfilePostSort.HOT),
            )
        }
    }
}
