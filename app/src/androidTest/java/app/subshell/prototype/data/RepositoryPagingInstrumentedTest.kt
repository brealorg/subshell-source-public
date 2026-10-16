package app.subshell.prototype.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.FeedSort
import app.subshell.prototype.ui.FeedSource
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.mainFeedScrollKey
import app.subshell.prototype.ui.mainPostFeedQueryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val DATA02_LOG_TAG = "subshell-data02"

@RunWith(AndroidJUnit4::class)
class RepositoryPagingInstrumentedTest {
    @Test
    fun keyedPagingRefreshRetryDedupAndAnchorPolicySurviveOnDevice() {
        val repository = FakeSubshellRepository().apply {
            repeatPreviousItemOnAppend = true
        }
        val state = SubshellState(
            repository = repository,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        repository.clearPageRequestHistory()
        val account = localFixtureAccountSummaries(listOf("root")).single()
        val homeKey = mainPostFeedQueryKey(account, FeedSource.HOME, FeedSort.BEST)
        val popularKey = mainPostFeedQueryKey(account, FeedSource.POPULAR, FeedSort.NEW)

        state.ensurePostPaging(homeKey)
        val initial = state.postPagingSnapshot(homeKey)
        assertEquals(12, initial.items.size)
        assertEquals("12", initial.nextCursor)

        val anchoredId = initial.items[4].id
        val scrollKey = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)
        state.updateFeedScrollAnchor(
            key = scrollKey,
            itemKey = anchoredId,
            fallbackIndex = 4,
            pixelOffset = 37,
        )

        state.appendPostPaging(homeKey)
        val appended = state.postPagingSnapshot(homeKey)
        assertEquals(24, appended.items.size)
        assertEquals(24, appended.items.map(Post::id).distinct().size)
        assertEquals(
            anchoredId,
            appended.items[state.feedScrollAnchor(scrollKey).resolveIndex(appended.items.map(Post::id))].id,
        )
        assertEquals(37, state.feedScrollAnchor(scrollKey).pixelOffset)

        repository.setPageMode(
            homeKey,
            RepositoryLoadOperation.REFRESH,
            FakeRepositoryMode.ERROR,
        )
        state.refreshPostPaging(homeKey)
        val failedRefresh = state.postPagingSnapshot(homeKey)
        assertTrue(failedRefresh.stale)
        assertEquals(24, failedRefresh.items.size)
        assertTrue(failedRefresh.refreshLoadState is RepositoryLoadState.Error)

        repository.clearPageMode(homeKey, RepositoryLoadOperation.REFRESH)
        state.retryPostPaging(homeKey)
        val refreshed = state.postPagingSnapshot(homeKey)
        assertEquals(12, refreshed.items.size)
        assertFalse(refreshed.stale)
        assertEquals(RepositoryLoadState.Idle, refreshed.refreshLoadState)

        repository.setPageMode(
            homeKey,
            RepositoryLoadOperation.APPEND,
            FakeRepositoryMode.OFFLINE,
        )
        state.appendPostPaging(homeKey)
        val failedAppend = state.postPagingSnapshot(homeKey)
        assertTrue(failedAppend.stale)
        assertEquals(12, failedAppend.items.size)
        assertTrue(failedAppend.appendLoadState is RepositoryLoadState.Offline)

        repository.clearPageMode(homeKey, RepositoryLoadOperation.APPEND)
        state.retryPostPaging(homeKey)
        val appendRetry = state.postPagingSnapshot(homeKey)
        assertEquals(24, appendRetry.items.size)
        assertFalse(appendRetry.stale)

        state.ensurePostPaging(popularKey)
        val isolated = state.postPagingSnapshot(popularKey)
        assertEquals(12, isolated.items.size)
        assertEquals(24, state.postPagingSnapshot(homeKey).items.size)
        assertTrue(
            repository.pageRequestSnapshot().any { request ->
                request.queryKey == homeKey && request.operation == RepositoryLoadOperation.APPEND
            },
        )
        assertTrue(
            repository.pageRequestSnapshot().any { request ->
                request.queryKey == popularKey && request.operation == RepositoryLoadOperation.INITIAL
            },
        )

        Log.i(
            DATA02_LOG_TAG,
            "DATA02_KEYED_PAGING=true " +
                "INITIAL_COUNT=${initial.items.size} " +
                "APPEND_COUNT=${appended.items.size} " +
                "REFRESH_GENERATION=${refreshed.generation} " +
                "QUERY_COUNT=${repository.pageRequestSnapshot().map { it.queryKey }.distinct().size} " +
                "ANCHOR_ID=$anchoredId",
        )
        Log.i(DATA02_LOG_TAG, "RESULT=SUBSHELL_DATA02_PAGING_RUNTIME_PASS")
    }
}
