package app.subshell.prototype.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PERF01 (#69): an account-scoped feed loads its whole cached snapshot whatever
 * the cursor, so the extra append in synchronizeAfterRemotePageWrite decoded the
 * whole feed a second time on the main thread after every Reddit page, without
 * ever growing it. The sync now reads it once; other feeds still page.
 */
class Perf01AccountFeedSyncTest {
    @Test
    fun anAccountFeedSyncReadsTheWholeSnapshotOnce() {
        var reads = 0
        var feed = (1..30).map { "t3_$it" }
        val pager = RepositoryPager(
            queryKey = RepositoryQueryKey("account|authenticated|reddit:me|live02|home|best"),
            pageSize = 12,
            stableId = { value: String -> value },
            loadPage = { _ ->
                reads += 1
                RepositoryResult.Content(value = feed, nextCursor = "remote-cursor")
            },
        )
        pager.loadInitial()
        feed = feed + (31..35).map { "t3_$it" }
        reads = 0

        pager.synchronizeAfterRemotePageWrite()

        assertEquals(1, reads)
        assertEquals(35, pager.snapshot.items.size)
    }

    @Test
    fun anOrdinaryFeedStillPagesUpToItsWindowOnSync() {
        val pages = mapOf(
            null to RepositoryResult.Content(listOf("a", "b"), nextCursor = "2"),
            "2" to RepositoryResult.Content(listOf("c", "d"), nextCursor = "3"),
            "3" to RepositoryResult.Content(listOf("e", "f"), nextCursor = null),
        )
        var reads = 0
        val pager = RepositoryPager(
            queryKey = RepositoryQueryKey("community|subshell|hot"),
            pageSize = 2,
            stableId = { value: String -> value },
            loadPage = { request -> reads += 1; pages.getValue(request.cursor) },
        )
        pager.loadInitial()
        reads = 0

        pager.synchronizeAfterRemotePageWrite()

        assertEquals(2, reads)
        assertEquals(listOf("a", "b", "c", "d"), pager.snapshot.items)
    }
}
