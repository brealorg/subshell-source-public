package app.subshell.prototype.data

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPagingPolicyTest {
    @Test
    fun accountScopedKeysSeparateAuthenticatedAndFixtureFeeds() {
        val base = RepositoryQueryKey("main|home|best")
        val authenticated = base.scopedToAccount(
            stableId = "reddit:redreader_compatibility:t2_live",
            source = AccountSessionSource.AUTHENTICATED,
        )
        val local = base.scopedToAccount(
            stableId = "local:root",
            source = AccountSessionSource.LOCAL_FIXTURE,
        )

        assertTrue(authenticated.isAuthenticatedAccountScoped)
        assertFalse(local.isAuthenticatedAccountScoped)
        assertNotEquals(authenticated, local)
        assertEquals(
            "account|authenticated|reddit:redreader_compatibility:t2_live|main|home|best",
            authenticated.value,
        )
    }

    @Test
    fun keyedPagersKeepIndependentCachesAndRequestIdentity() {
        val repository = FakeSubshellRepository()
        val homeKey = RepositoryQueryKey("main|home|best")
        val popularKey = RepositoryQueryKey("main|popular|best")
        val home = pager(repository, homeKey, pageSize = 6)
        val popular = pager(repository, popularKey, pageSize = 4)

        home.loadInitial()
        popular.loadInitial()
        home.append()

        assertEquals(12, home.snapshot.items.size)
        assertEquals(4, popular.snapshot.items.size)
        assertNotEquals(home.snapshot.nextCursor, popular.snapshot.nextCursor)
        assertEquals(
            listOf(homeKey, popularKey, homeKey),
            repository.pageRequestSnapshot().map(FakeRepositoryPageRequestSnapshot::queryKey),
        )
    }

    @Test
    fun appendMergesByStableIdAndPreservesFirstSeenOrder() {
        val repository = FakeSubshellRepository().apply {
            repeatPreviousItemOnAppend = true
        }
        val pager = pager(repository, RepositoryQueryKey("main|all|new"), pageSize = 5)

        pager.loadInitial()
        val firstIds = pager.snapshot.items.map(Post::id)
        pager.append()
        val mergedIds = pager.snapshot.items.map(Post::id)

        assertEquals(10, mergedIds.size)
        assertEquals(mergedIds.size, mergedIds.distinct().size)
        assertEquals(firstIds, mergedIds.take(firstIds.size))
        assertEquals("10", pager.snapshot.nextCursor)
    }

    @Test
    fun refreshReplacesGenerationAndResetsTheCursor() {
        val repository = FakeSubshellRepository()
        val pager = pager(repository, RepositoryQueryKey("community|android|best"), pageSize = 7)

        pager.loadInitial()
        pager.append()
        assertEquals(14, pager.snapshot.items.size)
        assertEquals("14", pager.snapshot.nextCursor)

        pager.refresh()

        assertEquals(1, pager.snapshot.generation)
        assertEquals(7, pager.snapshot.items.size)
        assertEquals("7", pager.snapshot.nextCursor)
        assertFalse(pager.snapshot.stale)
        assertEquals(RepositoryLoadState.Idle, pager.snapshot.refreshLoadState)
    }

    @Test
    fun failedRefreshKeepsStaleContentAndRetryReplacesIt() {
        val repository = FakeSubshellRepository()
        val key = RepositoryQueryKey("search|home||cache|posts|all_subshell")
        val pager = pager(repository, key, pageSize = 8)
        pager.loadInitial()
        val cachedIds = pager.snapshot.items.map(Post::id)

        repository.setPageMode(key, RepositoryLoadOperation.REFRESH, FakeRepositoryMode.ERROR)
        pager.refresh()

        assertEquals(cachedIds, pager.snapshot.items.map(Post::id))
        assertTrue(pager.snapshot.stale)
        assertTrue(pager.snapshot.refreshLoadState is RepositoryLoadState.Error)
        assertEquals(RepositoryLoadOperation.REFRESH, pager.snapshot.lastFailedOperation)

        repository.clearPageMode(key, RepositoryLoadOperation.REFRESH)
        pager.retry()

        assertEquals(cachedIds, pager.snapshot.items.map(Post::id))
        assertFalse(pager.snapshot.stale)
        assertEquals(RepositoryLoadState.Idle, pager.snapshot.refreshLoadState)
        assertEquals(null, pager.snapshot.lastFailedOperation)
    }

    @Test
    fun failedAppendKeepsCursorAndRetryContinuesWithoutDuplicates() {
        val repository = FakeSubshellRepository().apply {
            repeatPreviousItemOnAppend = true
        }
        val key = RepositoryQueryKey("user|root|0|0")
        val pager = pager(repository, key, pageSize = 6)
        pager.loadInitial()
        val cursorBeforeFailure = pager.snapshot.nextCursor

        repository.setPageMode(key, RepositoryLoadOperation.APPEND, FakeRepositoryMode.OFFLINE)
        pager.append()

        assertTrue(pager.snapshot.stale)
        assertTrue(pager.snapshot.appendLoadState is RepositoryLoadState.Offline)
        assertEquals(cursorBeforeFailure, pager.snapshot.nextCursor)
        assertEquals(RepositoryLoadOperation.APPEND, pager.snapshot.lastFailedOperation)

        repository.clearPageMode(key, RepositoryLoadOperation.APPEND)
        pager.retry()

        assertEquals(12, pager.snapshot.items.size)
        assertEquals(12, pager.snapshot.items.map(Post::id).distinct().size)
        assertFalse(pager.snapshot.stale)
        assertEquals(RepositoryLoadState.Idle, pager.snapshot.appendLoadState)
    }

    @Test
    fun endReachedBlocksFurtherAppendRequests() {
        val repository = FakeSubshellRepository()
        val key = RepositoryQueryKey("library|1")
        val pager = pager(repository, key, pageSize = RepositoryPageRequest.MAX_LIMIT)

        pager.loadInitial()
        val requestsAtEnd = repository.pageRequestSnapshot().size
        assertTrue(pager.snapshot.endReached)

        pager.append()

        assertEquals(requestsAtEnd, repository.pageRequestSnapshot().size)
        assertEquals(repository.posts().itemsOrEmpty().size, pager.snapshot.items.size)
    }

    @Test
    fun initialOfflineResultCanExposeStaleCachedPage() {
        val repository = FakeSubshellRepository()
        val key = RepositoryQueryKey("you|1")
        repository.setPageMode(key, RepositoryLoadOperation.INITIAL, FakeRepositoryMode.OFFLINE)
        val pager = pager(repository, key, pageSize = 5)

        pager.loadInitial()

        assertEquals(5, pager.snapshot.items.size)
        assertTrue(pager.snapshot.stale)
        assertTrue(pager.snapshot.initialLoadState is RepositoryLoadState.Offline)
        assertEquals(RepositoryLoadOperation.INITIAL, pager.snapshot.lastFailedOperation)
        assertEquals("5", pager.snapshot.nextCursor)
    }

    @Test
    fun persistedGenerationAndStaleStateHydrateIntoPagerSnapshot() {
        val key = RepositoryQueryKey("data03|cold-start|generation")
        val posts = SampleData.posts.take(3)
        val pager = RepositoryPager(
            queryKey = key,
            pageSize = 3,
            stableId = Post::id,
            loadPage = {
                RepositoryResult.Content(
                    value = posts,
                    nextCursor = "3",
                    generation = 7,
                    stale = true,
                )
            },
        )

        pager.loadInitial()

        assertEquals(7, pager.snapshot.generation)
        assertEquals("3", pager.snapshot.nextCursor)
        assertEquals(posts.map(Post::id), pager.snapshot.items.map(Post::id))
        assertTrue(pager.snapshot.stale)
    }

    private fun pager(
        repository: FakeSubshellRepository,
        key: RepositoryQueryKey,
        pageSize: Int,
    ): RepositoryPager<Post> = RepositoryPager(
        queryKey = key,
        pageSize = pageSize,
        stableId = Post::id,
        loadPage = repository::posts,
    )
}
