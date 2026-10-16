package app.subshell.prototype.ui

import app.subshell.prototype.data.search.TrendingSearchSeed
import app.subshell.prototype.ui.state.CommentThreadState
import app.subshell.prototype.ui.state.MAX_COLLAPSED_COMMENT_IDS
import app.subshell.prototype.ui.state.MAX_DISMISSED_INBOX_ACTIVITY_IDS
import app.subshell.prototype.ui.state.SearchHistoryState
import app.subshell.prototype.ui.state.TRENDING_SEARCH_RETRY_AFTER_MS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LOWUI5: low-severity ui/ performance findings. */
class LowUi5PerformanceTest {
    // LOWUI5_TRENDING_RETRY_WAITS_AFTER_A_FRUITLESS_FETCH_V1 (OCB-290)
    @Test
    fun anEmptyTrendingFetchIsNotRepeatedUntilTheRetryWaitPasses() {
        var now = 1_000_000L
        var loads = 0
        val state = SearchHistoryState(
            loadSearchHistory = { emptyList() },
            persistSearchHistory = { _, _ -> },
            loadSearchIndex = { emptyList() },
            persistSearchIndex = { _, _ -> },
            loadTrendingSearchCache = { "" },
            persistTrendingSearchCache = {},
            currentTimeMillis = { now },
            activeAccountIdProvider = { "local:root" },
            loadTrendingSearchSeeds = {
                loads += 1
                emptyList<TrendingSearchSeed>()
            },
        )

        runBlocking { state.refreshTrendingSearchSeedsIfStale() }
        runBlocking { state.refreshTrendingSearchSeedsIfStale() }
        assertEquals(1, loads)

        now += TRENDING_SEARCH_RETRY_AFTER_MS
        runBlocking { state.refreshTrendingSearchSeedsIfStale() }
        assertEquals(2, loads)
    }

    // LOWUI5_COLLAPSED_COMMENTS_CAPPED_V1 (OCB-287)
    @Test
    fun collapsedCommentsKeepTheNewest() {
        val state = CommentThreadState(initialCommentVisitHistoryToken = "", persistPreference = { _, _ -> })

        repeat(MAX_COLLAPSED_COMMENT_IDS + 5) { index -> state.toggleComment("t1_$index") }

        assertEquals(MAX_COLLAPSED_COMMENT_IDS, state.collapsedCommentIds.size)
        assertFalse("t1_0" in state.collapsedCommentIds)
        assertTrue("t1_${MAX_COLLAPSED_COMMENT_IDS + 4}" in state.collapsedCommentIds)
    }

    // LOWUI5_VISIT_HISTORY_COMPARES_ONE_ENTRY_V1 (OCB-086)
    @Test
    fun aVisitThatAddsNothingIsNotPersistedAgain() {
        var writes = 0
        val state = CommentThreadState(initialCommentVisitHistoryToken = "", persistPreference = { _, _ -> writes += 1 })

        state.recordCommentVisit("reddit:alice", "t3_a", setOf("t1_1", "t1_2"))
        state.recordCommentVisit("reddit:alice", "t3_a", setOf("t1_1"))
        assertEquals(1, writes)

        state.recordCommentVisit("reddit:alice", "t3_a", setOf("t1_3"))
        assertEquals(2, writes)
        assertEquals(setOf("t1_1", "t1_2", "t1_3"), state.commentVisitBaseline("reddit:alice", "t3_a"))
    }

    // LOWUI5_DISMISSED_INBOX_IDS_CACHED_AND_CAPPED_V1 (OCB-220)
    @Test
    fun dismissedInboxIdsAreReadOnceWrittenOncePerBatchAndCapped() {
        var reads = 0
        val writes = mutableListOf<Set<String>>()
        val state = SubshellState(
            loadInboxActivityDismissedIds = { _ ->
                reads += 1
                emptySet()
            },
            persistInboxActivityDismissedIds = { _, ids -> writes += ids },
        )

        repeat(3) { state.dismissedInboxActivityIds("reddit:alice") }
        assertEquals(1, reads)

        state.dismissInboxActivityItems("reddit:alice", setOf("a", "b", "c"))
        assertEquals(1, writes.size)
        assertEquals(setOf("a", "b", "c"), state.dismissedInboxActivityIds("reddit:alice"))

        state.dismissInboxActivityItems("reddit:alice", (0 until MAX_DISMISSED_INBOX_ACTIVITY_IDS).map { "x$it" }.toSet())
        assertEquals(MAX_DISMISSED_INBOX_ACTIVITY_IDS, state.dismissedInboxActivityIds("reddit:alice").size)
        assertFalse("a" in state.dismissedInboxActivityIds("reddit:alice"))
    }
}
