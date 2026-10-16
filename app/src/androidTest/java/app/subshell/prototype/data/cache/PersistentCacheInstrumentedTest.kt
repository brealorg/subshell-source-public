package app.subshell.prototype.data.cache

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.data.FakeRepositoryMode
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.createPersistentSubshellRepositoryForTest
import app.subshell.prototype.data.itemsOrEmpty
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.SampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentCacheInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun cleanupWhenTestFailsBeforeRuntimeScriptBoundary() {
        // Runtime phases intentionally share the database. The restore phase performs final cleanup.
    }

    @Test
    fun writePersistentPagesAndLocalMutationsBeforeExternalProcessRestart() {
        context.deleteDatabase(RUNTIME_DATABASE)
        context.getSharedPreferences(RUNTIME_PREFERENCES, 0).edit().clear().commit()

        val clock = MutableCacheClock(START_EPOCH_MS)
        val backend = FakeSubshellRepository()
        val repository = createPersistentSubshellRepositoryForTest(
            context = context,
            delegate = backend,
            clock = clock,
            databaseName = RUNTIME_DATABASE,
            maxQueryCount = 4,
            freshForMs = FRESH_FOR_MS,
        )

        val initial = repository.posts(
            RepositoryPageRequest(
                limit = PAGE_SIZE,
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.INITIAL,
            ),
        )
        val initialContent = initial as RepositoryResult.Content
        assertEquals(PAGE_SIZE, initialContent.value.size)
        assertEquals(PAGE_SIZE.toString(), initialContent.nextCursor)

        val append = repository.posts(
            RepositoryPageRequest(
                cursor = initialContent.nextCursor,
                limit = PAGE_SIZE,
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.APPEND,
            ),
        )
        val appendContent = append as RepositoryResult.Content
        assertEquals(PAGE_SIZE, appendContent.value.size)

        val refresh = repository.posts(
            RepositoryPageRequest(
                limit = PAGE_SIZE,
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.REFRESH,
            ),
        ) as RepositoryResult.Content
        assertEquals(PAGE_SIZE, refresh.value.size)
        assertEquals(PAGE_SIZE.toString(), refresh.nextCursor)

        repository.posts(
            RepositoryPageRequest(
                cursor = refresh.nextCursor,
                limit = PAGE_SIZE,
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.APPEND,
            ),
        ) as RepositoryResult.Content

        val queryMetadata = requireNotNull(repository.cacheQueryMetadata(QUERY_KEY))
        assertEquals(1, queryMetadata.generation)
        assertEquals((PAGE_SIZE * 2).toString(), queryMetadata.nextCursor)
        assertFalse(queryMetadata.terminal)

        val created = SampleData.posts.first().copy(
            id = CREATED_POST_ID,
            title = "DATA-03 persisted local post",
            saved = false,
            vote = 0,
        )
        assertTrue(repository.createPost(created) is RepositoryResult.Content)

        val comment = Comment(
            id = CREATED_COMMENT_ID,
            author = repository.localUsername,
            age = "now",
            body = "DATA-03 persisted local comment",
            score = 1,
            depth = 0,
            accent = 0xFFFF8A50,
            isOp = true,
        )
        assertTrue(repository.addComment(created.id, comment) is RepositoryResult.Content)

        val savedTarget = SampleData.posts.first().id
        val voteTarget = SampleData.posts.drop(1).first().id
        val pollTarget = SampleData.posts.first { post -> post.poll != null }
        val pollOption = pollTarget.poll!!.options.first().id
        assertTrue(repository.recordSaved(savedTarget, true) is RepositoryResult.Content)
        assertTrue(repository.recordVote(voteTarget, 1) is RepositoryResult.Content)
        assertTrue(repository.recordPollVote(pollTarget.id, pollOption) is RepositoryResult.Content)

        val diagnostics = repository.cacheDiagnostics()
        assertEquals(1, diagnostics.queryCount)
        assertEquals(PAGE_SIZE * 2, diagnostics.queryEntryCount)
        assertEquals(PAGE_SIZE * 2, diagnostics.postCount)
        assertEquals(5, diagnostics.localMutationCount)

        assertTrue(
            context.getSharedPreferences(RUNTIME_PREFERENCES, 0)
                .edit()
                .putBoolean(PHASE_WRITTEN, true)
                .commit(),
        )
        repository.close()

        Log.i(
            LOG_TAG,
            "DATA03_PHASE=WRITE QUERY_COUNT=${diagnostics.queryCount} " +
                "ENTRY_COUNT=${diagnostics.queryEntryCount} " +
                "GENERATION=${queryMetadata.generation} " +
                "NEXT_CURSOR=${queryMetadata.nextCursor} " +
                "LOCAL_MUTATION_COUNT=${diagnostics.localMutationCount}",
        )
    }

    @Test
    fun restoreColdStartOfflinePagesAndLocalMutationsAfterExternalProcessRestart() {
        assertTrue(
            context.getSharedPreferences(RUNTIME_PREFERENCES, 0)
                .getBoolean(PHASE_WRITTEN, false),
        )

        val freshClock = MutableCacheClock(START_EPOCH_MS + 1_000L)
        val freshBackend = FakeSubshellRepository(initialMode = FakeRepositoryMode.OFFLINE)
        val freshRepository = createPersistentSubshellRepositoryForTest(
            context = context,
            delegate = freshBackend,
            clock = freshClock,
            databaseName = RUNTIME_DATABASE,
            maxQueryCount = 4,
            freshForMs = FRESH_FOR_MS,
        )

        // Constructor replay can legitimately page comments for idempotence checks.
        // Feed-cache proof starts from a clean fake request history.
        freshBackend.clearPageRequestHistory()

        val initial = freshRepository.posts(
            RepositoryPageRequest(
                limit = PAGE_SIZE,
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.INITIAL,
            ),
        ) as RepositoryResult.Content
        assertEquals(PAGE_SIZE, initial.value.size)
        assertEquals(PAGE_SIZE.toString(), initial.nextCursor)
        assertEquals(1, initial.generation)

        val append = freshRepository.posts(
            RepositoryPageRequest(
                cursor = initial.nextCursor,
                limit = PAGE_SIZE,
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.APPEND,
            ),
        ) as RepositoryResult.Content
        assertEquals(PAGE_SIZE, append.value.size)
        assertTrue(freshBackend.pageRequestSnapshot().isEmpty())

        val firstReplay = freshRepository.localMutationReplaySummary()
        assertEquals(5, firstReplay.outcomes.size)
        assertEquals(5, firstReplay.appliedCount)
        assertEquals(0, firstReplay.alreadyAppliedCount)
        assertEquals(0, firstReplay.deferredCount)
        assertEquals(0, firstReplay.failedCount)
        assertEquals(
            listOf(
                "created-post:$CREATED_POST_ID",
                "comment:$CREATED_POST_ID:$CREATED_COMMENT_ID",
            ),
            firstReplay.outcomes.take(2).map { outcome -> outcome.mutationKey },
        )

        val mutationSnapshot = freshBackend.mutationSnapshot()
        assertTrue(CREATED_POST_ID in mutationSnapshot.createdPostIds)
        assertTrue(
            CREATED_COMMENT_ID in mutationSnapshot.commentIdsByPost[CREATED_POST_ID].orEmpty(),
        )
        assertEquals(true, mutationSnapshot.savedByPost[SampleData.posts.first().id])
        assertEquals(1, mutationSnapshot.votesByPost[SampleData.posts.drop(1).first().id])
        assertFalse(mutationSnapshot.pollVotesByPost.isEmpty())

        val secondReplay = freshRepository.replayLocalMutations()
        assertEquals(5, secondReplay.outcomes.size)
        assertEquals(3, secondReplay.appliedCount)
        assertEquals(2, secondReplay.alreadyAppliedCount)
        assertEquals(0, secondReplay.deferredCount)
        assertEquals(0, secondReplay.failedCount)
        val idempotentSnapshot = freshBackend.mutationSnapshot()
        assertEquals(
            1,
            idempotentSnapshot.createdPostIds.count { id -> id == CREATED_POST_ID },
        )
        assertEquals(
            1,
            idempotentSnapshot.commentIdsByPost[CREATED_POST_ID]
                .orEmpty()
                .count { id -> id == CREATED_COMMENT_ID },
        )
        freshRepository.close()

        val staleClock = MutableCacheClock(START_EPOCH_MS + FRESH_FOR_MS + 1L)
        val staleBackend = FakeSubshellRepository(initialMode = FakeRepositoryMode.OFFLINE)
        val staleRepository = createPersistentSubshellRepositoryForTest(
            context = context,
            delegate = staleBackend,
            clock = staleClock,
            databaseName = RUNTIME_DATABASE,
            maxQueryCount = 4,
            freshForMs = FRESH_FOR_MS,
        )

        // Stale fallback observation also excludes constructor replay lookups.
        staleBackend.clearPageRequestHistory()

        val staleInitial = staleRepository.posts(
            RepositoryPageRequest(
                limit = PAGE_SIZE,
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.INITIAL,
            ),
        )
        assertTrue(staleInitial is RepositoryResult.Offline)
        assertEquals(1, (staleInitial as RepositoryResult.Offline).generation)
        assertEquals(
            initial.value.map(Post::id),
            staleInitial.itemsOrEmpty().map(Post::id),
        )
        assertEquals(1, staleBackend.pageRequestSnapshot().size)

        staleRepository.clearRemoteCache()
        val afterClear = staleRepository.cacheDiagnostics()
        assertEquals(0, afterClear.queryCount)
        assertEquals(0, afterClear.queryEntryCount)
        assertEquals(0, afterClear.postCount)
        assertEquals(5, afterClear.localMutationCount)
        assertTrue(staleRepository.createdPosts.any { post -> post.id == CREATED_POST_ID })
        staleRepository.close()

        val lruRepository = createPersistentSubshellRepositoryForTest(
            context = context,
            delegate = FakeSubshellRepository(),
            clock = MutableCacheClock(staleClock.epochMs + 1_000L),
            databaseName = RUNTIME_DATABASE,
            maxQueryCount = 2,
            freshForMs = FRESH_FOR_MS,
        )
        listOf(
            RepositoryQueryKey("data03|lru|one"),
            RepositoryQueryKey("data03|lru|two"),
            RepositoryQueryKey("data03|lru|three"),
        ).forEach { queryKey ->
            lruRepository.posts(
                RepositoryPageRequest(
                    limit = PAGE_SIZE,
                    queryKey = queryKey,
                    operation = RepositoryLoadOperation.INITIAL,
                ),
            )
        }
        val lruDiagnostics = lruRepository.cacheDiagnostics()
        assertEquals(2, lruDiagnostics.queryCount)
        assertEquals(PAGE_SIZE * 2, lruDiagnostics.queryEntryCount)
        assertEquals(5, lruDiagnostics.localMutationCount)
        lruRepository.clearRemoteCache()
        lruRepository.close()

        context.deleteDatabase(RUNTIME_DATABASE)
        context.getSharedPreferences(RUNTIME_PREFERENCES, 0).edit().clear().commit()

        Log.i(
            LOG_TAG,
            "DATA03_PHASE=RESTORE FRESH_COLD_START=true STALE_OFFLINE=true " +
                "LRU_QUERY_COUNT=${lruDiagnostics.queryCount} " +
                "LOCAL_MUTATIONS_SURVIVE_CLEAR=${afterClear.localMutationCount == 5} " +
                "REPLAY_APPLIED=${firstReplay.appliedCount} " +
                "REPLAY_ALREADY_APPLIED=${secondReplay.alreadyAppliedCount} " +
                "REPLAY_DEFERRED=${firstReplay.deferredCount + secondReplay.deferredCount} " +
                "REPLAY_FAILED=${firstReplay.failedCount + secondReplay.failedCount} " +
                "REPLAY_IDEMPOTENT=true",
        )
        Log.i(LOG_TAG, "RESULT=SUBSHELL_DATA03_ROOM_CACHE_RUNTIME_PASS")
    }

    companion object {
        private const val LOG_TAG = "subshell-data03"
        private const val RUNTIME_DATABASE = "subshell-data03-runtime.db"
        private const val RUNTIME_PREFERENCES = "subshell-data03-runtime"
        private const val PHASE_WRITTEN = "phase-written"
        private const val CREATED_POST_ID = "data03-created-post"
        private const val CREATED_COMMENT_ID = "data03-created-comment"
        private const val PAGE_SIZE = 12
        private const val START_EPOCH_MS = 1_000_000L
        private const val FRESH_FOR_MS = 60_000L
        private val QUERY_KEY = RepositoryQueryKey("data03|runtime|home")
    }
}
