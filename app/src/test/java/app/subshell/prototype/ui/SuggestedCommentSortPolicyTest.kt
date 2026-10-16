package app.subshell.prototype.ui

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDto
import app.subshell.prototype.data.network.reddit.RedditCommentThingDto
import app.subshell.prototype.data.network.reddit.suggestedCommentSort
import app.subshell.prototype.data.redditSuggestedCommentSort
import app.subshell.prototype.data.retainRedditSuggestedCommentSort
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.beginSortLoad
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestedCommentSortPolicyTest {
    @Test
    fun redditSuggestedSortAcceptsOnlyTheFiveSupportedOrders() {
        assertEquals(
            listOf(
                RedditCommentSort.BEST,
                RedditCommentSort.TOP,
                RedditCommentSort.NEW,
                RedditCommentSort.CONTROVERSIAL,
                RedditCommentSort.OLD,
            ),
            listOf("confidence", "top", "new", "controversial", "old")
                .map(::redditSuggestedCommentSort),
        )
        assertNull(redditSuggestedCommentSort("qa"))
        assertNull(redditSuggestedCommentSort("live"))
        assertNull(redditSuggestedCommentSort(null))
    }

    @Test
    fun suggestedSortComesFromThePostListingNotTheCommentListing() {
        val listings = listOf(
            listingWithSuggestedSort("new"),
            listingWithSuggestedSort("top"),
        )

        assertEquals(RedditCommentSort.NEW, listings.suggestedCommentSort())
    }

    @Test
    fun manualChoiceWinsAndDisablingTheSettingFallsBackToBest() {
        assertEquals(
            RedditCommentSort.TOP,
            resolveCommentSort(
                manualSort = RedditCommentSort.TOP,
                useSuggestedSort = true,
                suggestedSort = RedditCommentSort.NEW,
            ),
        )
        assertEquals(
            RedditCommentSort.NEW,
            resolveCommentSort(
                manualSort = null,
                useSuggestedSort = true,
                suggestedSort = RedditCommentSort.NEW,
            ),
        )
        assertEquals(
            RedditCommentSort.BEST,
            resolveCommentSort(
                manualSort = null,
                useSuggestedSort = false,
                suggestedSort = RedditCommentSort.NEW,
            ),
        )
    }

    @Test
    fun missingRefreshMetadataCannotEraseAKnownSuggestedSort() {
        assertEquals(
            RedditCommentSort.NEW,
            retainRedditSuggestedCommentSort(
                previous = RedditCommentSort.NEW,
                refreshed = null,
            ),
        )
        assertEquals(
            RedditCommentSort.TOP,
            retainRedditSuggestedCommentSort(
                previous = RedditCommentSort.NEW,
                refreshed = RedditCommentSort.TOP,
            ),
        )
    }

    @Test
    fun changingSortKeepsCurrentCommentsVisibleWhileTheNewOrderLoads() {
        val current = RedditCommentsUiState(
            accountStableId = "reddit:test",
            postId = "t3_post",
            sort = RedditCommentSort.BEST,
            suggestedSort = RedditCommentSort.NEW,
            comments = listOf(
                Comment(
                    id = "t1_visible",
                    author = "reader",
                    age = "now",
                    body = "Visible",
                    score = 1,
                    depth = 0,
                    accent = 0L,
                ),
            ),
            loading = false,
            contentRevision = 7L,
        )

        val loadingTop = current.beginSortLoad(
            accountStableId = "reddit:test",
            postId = "t3_post",
            sort = RedditCommentSort.TOP,
        )

        assertEquals(RedditCommentSort.TOP, loadingTop.sort)
        assertEquals(RedditCommentSort.NEW, loadingTop.suggestedSort)
        assertEquals(listOf("t1_visible"), loadingTop.comments.map(Comment::id))
        assertEquals(true, loadingTop.loading)
        assertEquals(true, loadingTop.fromCache)
        assertEquals(7L, loadingTop.contentRevision)
    }

    private fun listingWithSuggestedSort(value: String) = RedditCommentListingDto(
        data = RedditCommentListingDataDto(
            children = listOf(
                RedditCommentThingDto(
                    kind = "t3",
                    data = RedditCommentDataDto(suggestedSort = value),
                ),
            ),
        ),
    )
}
