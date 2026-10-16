package app.subshell.prototype.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PAGING01 (#203): canAppend required refreshLoadState == Idle, and only a
 * later successful refresh reset it. After one failed pull-to-refresh
 * (offline), community, library, search and profile lists never loaded more
 * although the items and their cursor were kept.
 */
class Paging01RefreshFailureAppendTest {
    private var offline = false

    private val pager = RepositoryPager<String>(
        queryKey = RepositoryQueryKey("paging01"),
        pageSize = 2,
        stableId = { it },
        loadPage = { request ->
            if (offline) {
                RepositoryResult.Offline(nextCursor = request.cursor)
            } else {
                val start = request.cursor?.toInt() ?: 0
                RepositoryResult.Content(
                    value = listOf("item$start", "item${start + 1}"),
                    nextCursor = (start + 2).toString(),
                )
            }
        },
    )

    @Test
    fun aFailedRefreshStillLetsTheListLoadMore() {
        pager.loadInitial()
        offline = true
        pager.refresh()
        assertTrue(pager.snapshot.refreshLoadState is RepositoryLoadState.Offline)

        assertTrue(pager.snapshot.canAppend)
        offline = false
        pager.append()

        assertEquals(listOf("item0", "item1", "item2", "item3"), pager.snapshot.items)
    }

    @Test
    fun aPageThatLoadsSettlesTheRefreshFailure() {
        pager.loadInitial()
        offline = true
        pager.refresh()
        offline = false

        pager.append()

        assertEquals(RepositoryLoadState.Idle, pager.snapshot.refreshLoadState)
        assertFalse(pager.snapshot.hasFailure)
    }

    @Test
    fun aRefreshInFlightStillBlocksAppend() {
        val refreshing = RepositoryPagingSnapshot(
            queryKey = RepositoryQueryKey("paging01"),
            items = listOf("item0"),
            nextCursor = "1",
            refreshLoadState = RepositoryLoadState.Loading,
        )

        assertFalse(refreshing.canAppend)
    }
}
