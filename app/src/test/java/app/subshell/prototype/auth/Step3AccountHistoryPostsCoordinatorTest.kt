package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (profile01a6): a profile's post history (Upvotes, Downvotes, Hidden)
 * asks the account-history updater for that account, user and kind; the
 * answer for the latest request is the one shown; a missing grant asks to
 * authorize; a profile without a valid name reads nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Step3AccountHistoryPostsCoordinatorTest {
    private val account = "reddit:me"
    private val user = "someone"

    private data class Call(
        val account: String,
        val username: String,
        val kind: RedditAccountHistoryKind,
        val operation: RepositoryLoadOperation,
    )

    private class Updater(
        private val answer: (RedditAccountHistoryKind) -> AuthenticatedRedditAccountHistoryUpdateResult = { kind ->
            applied(kind)
        },
    ) : AuthenticatedRedditAccountHistoryUpdater {
        val calls = mutableListOf<Call>()
        val gates = mutableMapOf<RedditAccountHistoryKind, CompletableDeferred<Unit>>()

        override suspend fun synchronize(
            accountStableId: String,
            username: String,
            kind: RedditAccountHistoryKind,
            operation: RepositoryLoadOperation,
        ): AuthenticatedRedditAccountHistoryUpdateResult {
            calls += Call(accountStableId, username, kind, operation)
            gates[kind]?.await()
            return answer(kind)
        }

        companion object {
            fun applied(kind: RedditAccountHistoryKind) = AuthenticatedRedditAccountHistoryUpdateResult.Applied(
                queryKey = redditAccountHistoryQueryKey("reddit:me", "someone", kind),
                operation = RepositoryLoadOperation.REFRESH,
                networkItemCount = 1,
                storedItemCount = 1,
                endReached = false,
            )
        }
    }

    @Test
    fun aRefreshAsksTheUpdaterForThatAccountUserAndKind() = runTest(StandardTestDispatcher()) {
        val updater = Updater()
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = updater)

        coordinator.update(account, user, RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        assertEquals(
            listOf(Call(account, user, RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)),
            updater.calls,
        )
        val state = coordinator.uiState.value
        assertEquals(RedditAccountHistoryKind.UPVOTED, state.kind)
        assertEquals(1L, state.contentRevision)
        assertFalse(state.inProgress)
        assertNull(state.failureKind)
        assertEquals(redditAccountHistoryQueryKey(account, user, RedditAccountHistoryKind.UPVOTED), state.queryKey)
    }

    @Test
    fun theLatestRequestWinsOverALateAnswer() = runTest(StandardTestDispatcher()) {
        val updater = Updater()
        val upvotedGate = CompletableDeferred<Unit>()
        updater.gates[RedditAccountHistoryKind.UPVOTED] = upvotedGate
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = updater)

        coordinator.update(account, user, RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)
        runCurrent()
        coordinator.update(account, user, RedditAccountHistoryKind.HIDDEN, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()
        upvotedGate.complete(Unit)
        advanceUntilIdle()

        val state = coordinator.uiState.value
        assertEquals(RedditAccountHistoryKind.HIDDEN, state.kind)
        assertEquals(1L, state.contentRevision)
        assertEquals(redditAccountHistoryQueryKey(account, user, RedditAccountHistoryKind.HIDDEN), state.queryKey)
    }

    @Test
    fun aMissingGrantAsksToAuthorize() = runTest(StandardTestDispatcher()) {
        val updater = Updater { AuthenticatedRedditAccountHistoryUpdateResult.AuthorizationRequired }
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = updater)

        coordinator.update(account, user, RedditAccountHistoryKind.DOWNVOTED, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        val state = coordinator.uiState.value
        assertTrue(state.authorizationRequired)
        assertNull(state.failureKind)
        assertEquals(RepositoryLoadOperation.REFRESH, state.failedOperation)
    }

    @Test
    fun aProfileWithoutAValidNameAsksNothing() = runTest(StandardTestDispatcher()) {
        val updater = Updater()
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = updater)

        coordinator.update(account, " ", RedditAccountHistoryKind.UPVOTED, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        assertEquals(RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION, coordinator.uiState.value.failureKind)
        assertEquals(emptyList<Call>(), updater.calls)
    }
}
