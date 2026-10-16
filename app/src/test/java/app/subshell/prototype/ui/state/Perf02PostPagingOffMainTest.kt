package app.subshell.prototype.ui.state

import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.createFakeSubshellRepository
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.FeedScrollAnchorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF02 (#69): post paging read and decoded the Room cache on the main thread,
 * at every feed entry and after every Reddit page. With a background executor
 * the caller only queues the work; the Room read runs on the worker, the
 * snapshot is published afterwards, a feed is not loaded twice while its first
 * load is queued, and results from before an invalidation are dropped.
 */
class Perf02PostPagingOffMainTest {
    private class HeldExecutor : PostPagingExecutor {
        val work = ArrayDeque<() -> Unit>()
        val published = ArrayDeque<() -> Unit>()
        override fun execute(work: () -> Unit) { this.work += work }
        override fun publish(update: () -> Unit) { published += update }
        fun runWorker() { while (work.isNotEmpty()) work.removeFirst()() }
        fun runMain() { while (published.isNotEmpty()) published.removeFirst()() }
    }

    private val base = createFakeSubshellRepository()
    private val reads = mutableListOf<String?>()
    private val repository = object : SubshellRepository by base {
        override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
            reads += request.cursor
            val page = base.posts(RepositoryPageRequest(limit = 2))
            return when (page) {
                is RepositoryResult.Content -> RepositoryResult.Content(
                    value = page.value.take(2),
                    nextCursor = if (request.cursor == null) "page-2" else null,
                )
                else -> page
            }
        }
    }
    private val key = RepositoryQueryKey("community|subshell|hot")

    private fun state(executor: PostPagingExecutor) =
        PostPagingState(repository, FeedScrollAnchorStore(), isPostDeleted = { false }, executor = executor)

    @Test
    fun theCallerOnlyQueuesTheRoomReadAndTheSnapshotArrivesAfterwards() {
        val executor = HeldExecutor()
        val paging = state(executor)

        paging.ensurePostPaging(key)
        paging.ensurePostPaging(key)
        assertEquals(emptyList<String?>(), reads)
        assertFalse(key in paging.postPagingSnapshots)

        executor.runWorker()
        assertEquals(listOf<String?>(null), reads)
        assertFalse(key in paging.postPagingSnapshots)

        executor.runMain()
        assertTrue(key in paging.postPagingSnapshots)
    }

    @Test
    fun anAppendQueuedBehindTheFirstLoadPagesOnInOrder() {
        val executor = HeldExecutor()
        val paging = state(executor)

        paging.ensurePostPaging(key)
        paging.appendPostPaging(key)
        executor.runWorker()

        assertEquals(listOf(null, "page-2"), reads)
    }

    @Test
    fun aResultFromBeforeAnInvalidationIsDropped() {
        val executor = HeldExecutor()
        val paging = state(executor)

        paging.ensurePostPaging(key)
        executor.runWorker()
        paging.invalidatePostPaging()
        executor.runMain()

        assertFalse(key in paging.postPagingSnapshots)
    }

    @Test
    fun inlineKeepsTheFormerSynchronousBehaviour() {
        val paging = state(PostPagingExecutor.Inline)

        paging.ensurePostPaging(key)

        assertEquals(listOf<String?>(null), reads)
        assertTrue(key in paging.postPagingSnapshots)
    }
}
