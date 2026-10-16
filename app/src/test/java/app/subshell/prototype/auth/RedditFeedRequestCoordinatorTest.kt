package app.subshell.prototype.auth

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.data.auth.authenticatedRedditFeedQueryKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditFeedRequestCoordinatorTest {
    @Test
    fun threeHundredRapidSwitchesCompletedInReverseApplyOnlyTheLatestRequest() = runBlocking {
        val updater = ControlledFeedUpdater()
        val appliedNetworkCounts = mutableListOf<Int>()
        val coordinator = RedditFeedRequestCoordinator(
            scope = this,
            updater = updater,
            onApplied = { result -> appliedNetworkCounts += result.networkItemCount },
        )

        repeat(RAPID_SWITCH_COUNT) { index ->
            coordinator.update(
                accountStableId = ACCOUNT_ONE,
                operation = RepositoryLoadOperation.REFRESH,
                target = tortureTarget(index),
            )
            yield()
        }
        assertEquals(RAPID_SWITCH_COUNT, updater.pendingCount)

        updater.pendingIndices.reversed().forEach { index ->
            updater.completeApplied(index)
            yield()
        }

        val finalTarget = tortureTarget(RAPID_SWITCH_COUNT - 1)
        val finalState = coordinator.uiState.value
        assertEquals(
            authenticatedRedditFeedQueryKey(ACCOUNT_ONE, finalTarget),
            finalState.queryKey,
        )
        assertEquals(RepositoryLoadOperation.REFRESH, finalState.lastAppliedOperation)
        assertEquals(1L, finalState.contentRevision)
        assertEquals(listOf(RAPID_SWITCH_COUNT - 1), appliedNetworkCounts)
        assertEquals(RAPID_SWITCH_COUNT, updater.returnedRequestIndices.size)
        assertFalse(finalState.inProgress)
        assertNull(finalState.failureKind)
    }

    @Test
    fun staleAppendCannotOverwriteANewerRefreshAfterLeavingAndReturningToTheSameFeed() =
        runBlocking {
            val updater = ControlledFeedUpdater()
            val appliedOperations = mutableListOf<RepositoryLoadOperation>()
            val coordinator = RedditFeedRequestCoordinator(
                scope = this,
                updater = updater,
                onApplied = { result -> appliedOperations += result.operation },
            )
            val allNew = AuthenticatedRedditFeedTarget.main(
                feed = RedditMainFeed.ALL,
                sort = RedditFeedSort.NEW,
            )
            val popularNew = AuthenticatedRedditFeedTarget.main(
                feed = RedditMainFeed.POPULAR,
                sort = RedditFeedSort.NEW,
            )

            coordinator.update(ACCOUNT_ONE, RepositoryLoadOperation.REFRESH, allNew)
            yield()
            updater.completeApplied(0)
            yield()
            assertEquals(1L, coordinator.uiState.value.contentRevision)

            coordinator.update(ACCOUNT_ONE, RepositoryLoadOperation.APPEND, allNew)
            yield()
            coordinator.update(ACCOUNT_ONE, RepositoryLoadOperation.REFRESH, popularNew)
            yield()
            coordinator.update(ACCOUNT_ONE, RepositoryLoadOperation.REFRESH, allNew)
            yield()

            updater.completeApplied(3)
            updater.completeApplied(2)
            updater.completeApplied(1)
            yield()

            val finalState = coordinator.uiState.value
            assertEquals(authenticatedRedditFeedQueryKey(ACCOUNT_ONE, allNew), finalState.queryKey)
            assertEquals(RepositoryLoadOperation.REFRESH, finalState.lastAppliedOperation)
            assertEquals(2L, finalState.contentRevision)
            assertEquals(
                listOf(RepositoryLoadOperation.REFRESH, RepositoryLoadOperation.REFRESH),
                appliedOperations,
            )
            assertNull(finalState.failureKind)
        }

    @Test
    fun cancelledCoordinatorCannotApplyItsResultAfterRecreate() = runBlocking {
        val updater = ControlledFeedUpdater()
        val oldApplied = mutableListOf<Int>()
        val recreatedApplied = mutableListOf<Int>()
        val target = AuthenticatedRedditFeedTarget.home(RedditFeedSort.BEST)
        val oldCoordinator = RedditFeedRequestCoordinator(
            scope = this,
            updater = updater,
            onApplied = { result -> oldApplied += result.networkItemCount },
        )

        oldCoordinator.update(ACCOUNT_ONE, RepositoryLoadOperation.REFRESH, target)
        yield()
        oldCoordinator.cancelPending()

        val recreated = RedditFeedRequestCoordinator(
            scope = this,
            updater = updater,
            onApplied = { result -> recreatedApplied += result.networkItemCount },
        )
        recreated.update(ACCOUNT_ONE, RepositoryLoadOperation.REFRESH, target)
        yield()

        updater.completeApplied(1)
        updater.completeApplied(0)
        yield()

        assertEquals(emptyList<Int>(), oldApplied)
        assertEquals(listOf(1), recreatedApplied)
        assertEquals(0L, oldCoordinator.uiState.value.contentRevision)
        assertEquals(1L, recreated.uiState.value.contentRevision)
        assertEquals(authenticatedRedditFeedQueryKey(ACCOUNT_ONE, target), recreated.uiState.value.queryKey)
        assertTrue(updater.returnedRequestIndices.containsAll(listOf(0, 1)))
    }

    private fun tortureTarget(index: Int): AuthenticatedRedditFeedTarget = when (index % 6) {
        0 -> AuthenticatedRedditFeedTarget.home(RedditFeedSort.BEST)
        1 -> AuthenticatedRedditFeedTarget.main(
            RedditMainFeed.POPULAR,
            RedditFeedSort.NEW,
        )
        2 -> AuthenticatedRedditFeedTarget.main(
            RedditMainFeed.ALL,
            RedditFeedSort.TOP,
            RedditListingTimePeriod.MONTH,
        )
        3 -> AuthenticatedRedditFeedTarget.main(
            RedditMainFeed.POPULAR,
            RedditFeedSort.CONTROVERSIAL,
            RedditListingTimePeriod.WEEK,
        )
        4 -> AuthenticatedRedditFeedTarget.home(RedditFeedSort.HOT)
        else -> AuthenticatedRedditFeedTarget.main(
            RedditMainFeed.ALL,
            RedditFeedSort.NEW,
        )
    }

    private companion object {
        const val RAPID_SWITCH_COUNT = 300
        const val ACCOUNT_ONE = "reddit:redreader_compatibility:t2_feed03_one"
    }
}

private class ControlledFeedUpdater : AuthenticatedRedditFeedUpdater {
    private data class PendingRequest(
        val accountStableId: String,
        val operation: RepositoryLoadOperation,
        val target: AuthenticatedRedditFeedTarget,
        val continuation: Continuation<AuthenticatedRedditFeedUpdateResult>,
    )

    private val pending = mutableListOf<PendingRequest>()
    val returnedRequestIndices = mutableListOf<Int>()

    val pendingCount: Int
        get() = pending.size

    val pendingIndices: IntRange
        get() = pending.indices

    override suspend fun synchronize(
        accountStableId: String,
        operation: RepositoryLoadOperation,
        target: AuthenticatedRedditFeedTarget,
    ): AuthenticatedRedditFeedUpdateResult {
        val requestIndex = pending.size
        return suspendCoroutine { continuation ->
            pending += PendingRequest(
                accountStableId = accountStableId,
                operation = operation,
                target = target,
                continuation = continuation,
            )
        }.also {
            returnedRequestIndices += requestIndex
        }
    }

    fun completeApplied(index: Int) {
        val request = pending[index]
        request.continuation.resume(
            AuthenticatedRedditFeedUpdateResult.Applied(
                queryKey = authenticatedRedditFeedQueryKey(
                    request.accountStableId,
                    request.target,
                ),
                operation = request.operation,
                networkItemCount = index,
                storedItemCount = index,
                endReached = false,
            ),
        )
    }
}
