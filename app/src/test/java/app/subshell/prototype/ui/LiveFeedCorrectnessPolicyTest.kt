package app.subshell.prototype.ui

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFeedCorrectnessPolicyTest {
    private val queryKey = RepositoryQueryKey("account|authenticated|test|feed")

    // FEEDS01_SEAMLESS_VIEWPORT_FILL_V1: the predecessor paused after the
    // budget and offered "Load 25 more"; the successor keeps filling, backing
    // off only while pages add no visible post.
    @Test
    fun liveAutoAppendFillsTheViewportThenBacksOffAfterTheBudget() {
        val idle = RedditFeedUiState(
            accountStableId = "reddit:test",
            queryKey = queryKey,
        )

        val appendEnabled = canAppendRedditFeed(
            live = true,
            nextCursor = "t3_after",
            state = idle,
        )

        assertTrue(appendEnabled)
        val governor = LiveFeedAutoAppendGovernor(
            requestBudget = 2,
            backoffMillis = listOf(1_000L, 2_000L),
        )
        fun request(token: String, loaded: Int, visible: Int): LiveFeedAutoAppendDecision.Request {
            val decision = governor.evaluate(
                appendEnabled = appendEnabled,
                contentToken = token,
                loadedItemCount = loaded,
                visibleItemCount = visible,
                viewportUnderfilled = true,
            )
            assertTrue("$token: $decision", decision is LiveFeedAutoAppendDecision.Request)
            governor.markRequested(token, visible)
            return decision as LiveFeedAutoAppendDecision.Request
        }

        val first = request("t3_after_1", loaded = 25, visible = 1)
        assertEquals(LiveFeedAutoAppendReason.VIEWPORT_UNDERFILLED, first.reason)
        assertEquals(0L, first.delayMillis)
        assertEquals(
            LiveFeedAutoAppendDecision.None,
            governor.evaluate(
                appendEnabled = appendEnabled,
                contentToken = "t3_after_1",
                loadedItemCount = 25,
                visibleItemCount = 1,
                viewportUnderfilled = true,
            ),
        )

        val second = request("t3_after_2", loaded = 50, visible = 0)
        assertEquals(LiveFeedAutoAppendReason.FILTERED_EMPTY, second.reason)
        assertEquals(0L, second.delayMillis)
        assertEquals(1_000L, request("t3_after_3", loaded = 75, visible = 0).delayMillis)
        assertEquals(2_000L, request("t3_after_4", loaded = 100, visible = 0).delayMillis)
        assertEquals(2_000L, request("t3_after_5", loaded = 125, visible = 0).delayMillis)
        assertEquals(6, request("t3_after_6", loaded = 150, visible = 0).requestNumber)
    }

    @Test
    fun liveAutoAppendBackoffResetsWhenAPageAddsVisiblePosts() {
        val governor = LiveFeedAutoAppendGovernor(
            requestBudget = 1,
            backoffMillis = listOf(4_000L),
        )
        fun delayFor(token: String, visible: Int): Long {
            val decision = governor.evaluate(
                appendEnabled = true,
                contentToken = token,
                loadedItemCount = 25,
                visibleItemCount = visible,
                viewportUnderfilled = true,
            ) as LiveFeedAutoAppendDecision.Request
            governor.markRequested(token, visible)
            return decision.delayMillis
        }

        assertEquals(0L, delayFor("a", visible = 0))
        assertEquals(4_000L, delayFor("b", visible = 0))
        assertEquals(0L, delayFor("c", visible = 3))
        assertEquals(4_000L, delayFor("d", visible = 3))
    }

    @Test
    fun liveAutoAppendEvaluationIsNotARequestUntilMarked() {
        val governor = LiveFeedAutoAppendGovernor(requestBudget = 1)
        repeat(3) {
            val decision = governor.evaluate(
                appendEnabled = true,
                contentToken = "a",
                loadedItemCount = 25,
                visibleItemCount = 0,
                viewportUnderfilled = true,
            )
            assertTrue(decision is LiveFeedAutoAppendDecision.Request)
            assertEquals(1, (decision as LiveFeedAutoAppendDecision.Request).requestNumber)
        }
        governor.markRequested("a", 0)
        assertEquals(
            LiveFeedAutoAppendDecision.None,
            governor.evaluate(
                appendEnabled = true,
                contentToken = "a",
                loadedItemCount = 25,
                visibleItemCount = 0,
                viewportUnderfilled = true,
            ),
        )
    }

    @Test
    fun viewportFillIsOnlyAutomaticAtTheUnscrolledTop() {
        assertTrue(
            isLiveFeedViewportUnderfilled(
                totalItemsCount = 4,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                canScrollForward = false,
            ),
        )
        assertFalse(
            isLiveFeedViewportUnderfilled(
                totalItemsCount = 4,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 1,
                canScrollForward = false,
            ),
        )
        assertFalse(
            isLiveFeedViewportUnderfilled(
                totalItemsCount = 4,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                canScrollForward = true,
            ),
        )
    }

    @Test
    fun liveAppendStopsWhileLoadingAfterFailureAndAtTheTerminalCursor() {
        val loading = RedditFeedUiState(
            accountStableId = "reddit:test",
            queryKey = queryKey,
            inProgressOperation = RepositoryLoadOperation.APPEND,
        )
        val failed = RedditFeedUiState(
            accountStableId = "reddit:test",
            queryKey = queryKey,
            failureKind = RedditFeedUiFailureKind.TRANSPORT,
        )

        assertFalse(canAppendRedditFeed(true, "t3_after", loading))
        assertFalse(canAppendRedditFeed(true, "t3_after", failed))
        assertFalse(canAppendRedditFeed(true, null, RedditFeedUiState()))
        assertFalse(canAppendRedditFeed(false, "t3_after", RedditFeedUiState()))
    }

    @Test
    fun liveCommunityFeedPreservesRedditListingOrder() {
        val redditOrder = SampleData.posts.take(4).reversed()

        assertEquals(
            redditOrder,
            communityFeedPostsInDisplayOrder(
                posts = redditOrder,
                sort = CommunitySort.HOT,
                authenticatedRedditFeed = true,
            ),
        )
        assertEquals(
            sortCommunityPosts(redditOrder, CommunitySort.TOP),
            communityFeedPostsInDisplayOrder(
                posts = redditOrder,
                sort = CommunitySort.TOP,
                authenticatedRedditFeed = false,
            ),
        )
    }
}
