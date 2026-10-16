package app.subshell.prototype.data

import app.subshell.prototype.model.Post
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryPagingTerminalStateTest {
    @Test
    fun initialContentAndEmptyResultsLeaveThePagerIdle() {
        val contentPager = RepositoryPager(
            queryKey = RepositoryQueryKey("terminal|content"),
            stableId = Post::id,
            loadPage = {
                RepositoryResult.Content(
                    value = SampleData.posts.take(2),
                    nextCursor = null,
                )
            },
        )
        contentPager.loadInitial()
        assertEquals(RepositoryLoadState.Idle, contentPager.snapshot.initialLoadState)

        val emptyPager = RepositoryPager<Post>(
            queryKey = RepositoryQueryKey("terminal|empty"),
            stableId = Post::id,
            loadPage = { RepositoryResult.Empty("No posts") },
        )
        emptyPager.loadInitial()
        assertEquals(RepositoryLoadState.Idle, emptyPager.snapshot.initialLoadState)
    }
}
