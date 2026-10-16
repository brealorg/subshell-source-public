package app.subshell.prototype.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01A7A4PagingWindowSyncPolicyTest {
    @Test
    fun remotePageWriteSyncPublishesExpandedWindowWithoutFirstPageCollapse() {
        val publishedSizes = mutableListOf<Int>()
        val pager = RepositoryPager(
            queryKey = RepositoryQueryKey("profile01a7a4|posts"),
            pageSize = 2,
            stableId = { value: String -> value },
            loadPage = { request ->
                when (request.cursor) {
                    null -> RepositoryResult.Content(
                        value = listOf("a", "b"),
                        nextCursor = "page-2",
                    )
                    "page-2" -> RepositoryResult.Content(
                        value = listOf("c", "d"),
                        nextCursor = "page-3",
                    )
                    "page-3" -> RepositoryResult.Content(
                        value = listOf("e", "f"),
                        nextCursor = null,
                    )
                    else -> RepositoryResult.Empty()
                }
            },
            onSnapshot = { snapshot -> publishedSizes += snapshot.items.size },
        )

        pager.loadInitial()
        pager.append()
        assertEquals(4, pager.snapshot.items.size)

        publishedSizes.clear()
        pager.synchronizeAfterRemotePageWrite()

        assertEquals(listOf(6), publishedSizes)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), pager.snapshot.items)
    }

    @Test
    fun failedSyncKeepsTheExistingWindow() {
        var failReads = false
        val pager = RepositoryPager(
            queryKey = RepositoryQueryKey("profile01a7a4|failure"),
            pageSize = 2,
            stableId = { value: String -> value },
            loadPage = { request ->
                if (failReads) {
                    RepositoryResult.Error("synthetic")
                } else {
                    when (request.cursor) {
                        null -> RepositoryResult.Content(
                            value = listOf("a", "b"),
                            nextCursor = "page-2",
                        )
                        "page-2" -> RepositoryResult.Content(
                            value = listOf("c", "d"),
                            nextCursor = null,
                        )
                        else -> RepositoryResult.Empty()
                    }
                }
            },
        )

        pager.loadInitial()
        pager.append()
        failReads = true

        val result = pager.synchronizeAfterRemotePageWrite()

        assertEquals(4, result.items.size)
        assertTrue(result.items.containsAll(listOf("a", "b", "c", "d")))
    }
}
