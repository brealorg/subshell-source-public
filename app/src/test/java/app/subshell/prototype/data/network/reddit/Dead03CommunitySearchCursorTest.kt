package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.CommunityDiscoverySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DEAD03 (OCB-546): a full community search used to accept a cursor and drop
 * it, so a second page would have repeated the first.
 */
class Dead03CommunitySearchCursorTest {
    @Test
    fun aFullSearchPageAsksForThePageAfterTheCursor() {
        val request = redditCommunityDiscoveryJsonRequest(
            searchQuery = "android",
            searchSort = CommunityDiscoverySort.RELEVANCE,
            autocompleteIncludeOver18 = null,
            cursor = " t5_next ",
            limit = 25,
        )

        assertEquals(listOf("subreddits", "search"), request.pathSegments)
        assertEquals("t5_next", request.queryParameters.toMap()["after"])
    }

    @Test
    fun theFirstSearchPageHasNoCursor() {
        val request = redditCommunityDiscoveryJsonRequest(
            searchQuery = "android",
            searchSort = CommunityDiscoverySort.RELEVANCE,
            autocompleteIncludeOver18 = null,
            cursor = null,
            limit = 25,
        )

        assertNull(request.queryParameters.toMap()["after"])
    }
}
