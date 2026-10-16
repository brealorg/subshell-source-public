package app.subshell.prototype.auth

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.account.RedditSavedHistoryUiItem
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistorySnapshot
import app.subshell.prototype.data.network.reddit.RedditSavedHistoryItem
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (savedcomments01a2): the Saved list shows the stored copy first, then
 * Reddit's answer with its next-page cursor; the next page asks with that
 * cursor and is added below; the latest request wins; a profile without a
 * valid name reads nothing; another account never keeps the first one's items.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Step3SavedHistoryCoordinatorTest {
    private val account = "reddit:me"
    private val user = "me_user"

    private fun item(id: String) = RedditSavedHistoryItem.CommentItem(
        ProfileCommentFixture(
            id = id,
            author = user,
            postId = "t3_abc",
            age = "1h",
            body = "comment $id",
            score = 1,
            placement = ThreadPlacement.TOP,
        ),
    )

    private fun loaded(vararg ids: String, next: String?, fromCache: Boolean = false) =
        AuthenticatedRedditSavedHistoryResult.Loaded(
            AuthenticatedRedditSavedHistorySnapshot(ids.map(::item), next),
            fromCache = fromCache,
        )

    private class Loader(
        private val cachedFor: (String, String) -> AuthenticatedRedditSavedHistoryResult,
        private val freshFor: (String, String) -> AuthenticatedRedditSavedHistoryResult,
        private val appendFor: (String) -> AuthenticatedRedditSavedHistoryResult,
    ) : AuthenticatedRedditSavedHistoryLoader {
        val calls = mutableListOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

        override suspend fun cached(accountStableId: String, username: String): AuthenticatedRedditSavedHistoryResult {
            calls += "cached:$accountStableId:$username"
            return cachedFor(accountStableId, username)
        }

        override suspend fun synchronize(accountStableId: String, username: String): AuthenticatedRedditSavedHistoryResult {
            calls += "synchronize:$accountStableId:$username"
            gates[username]?.await()
            return freshFor(accountStableId, username)
        }

        override suspend fun append(accountStableId: String, username: String, after: String): AuthenticatedRedditSavedHistoryResult {
            calls += "append:$after"
            return appendFor(after)
        }
    }

    private fun ids(state: app.subshell.prototype.data.account.RedditSavedHistoryUiState) =
        state.items.map(RedditSavedHistoryUiItem::fullname)

    private fun loader() = Loader(
        cachedFor = { _, name -> loaded("t1_stored_$name", next = null, fromCache = true) },
        freshFor = { _, name -> loaded("t1_${name}_a", "t1_${name}_b", next = "t1_${name}_b") },
        appendFor = { loaded("t1_me_user_a", "t1_me_user_b", "t1_me_user_c", next = null) },
    )

    @Test
    fun theStoredCopyComesFirstThenRedditsAnswer() = runTest(StandardTestDispatcher()) {
        val loader = loader()
        val gate = CompletableDeferred<Unit>()
        loader.gates[user] = gate
        val coordinator = RedditSavedHistoryRequestCoordinator(scope = this, loader = loader)

        coordinator.update(account, user, RepositoryLoadOperation.REFRESH)
        runCurrent()
        assertEquals(listOf("t1_stored_me_user"), ids(coordinator.uiState.value))
        assertTrue(coordinator.uiState.value.fromCache)

        gate.complete(Unit)
        advanceUntilIdle()
        val state = coordinator.uiState.value
        assertEquals(listOf("t1_me_user_a", "t1_me_user_b"), ids(state))
        assertEquals("t1_me_user_b", state.nextCursor)
        assertFalse(state.fromCache)
    }

    @Test
    fun theNextPageAsksWithTheCursorAndStopsAtTheEnd() = runTest(StandardTestDispatcher()) {
        val loader = loader()
        val coordinator = RedditSavedHistoryRequestCoordinator(scope = this, loader = loader)
        coordinator.update(account, user, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        coordinator.update(account, user, RepositoryLoadOperation.APPEND)
        advanceUntilIdle()
        assertEquals(listOf("t1_me_user_a", "t1_me_user_b", "t1_me_user_c"), ids(coordinator.uiState.value))
        assertEquals(null, coordinator.uiState.value.nextCursor)
        val finished = coordinator.uiState.value

        coordinator.update(account, user, RepositoryLoadOperation.APPEND)
        advanceUntilIdle()
        assertEquals("a finished list stays as it is", finished, coordinator.uiState.value)
        assertEquals(1, loader.calls.count { it.startsWith("append:") })
        assertEquals("append:t1_me_user_b", loader.calls.last())
    }

    @Test
    fun theLatestRequestWins() = runTest(StandardTestDispatcher()) {
        val loader = loader()
        val aliceGate = CompletableDeferred<Unit>()
        loader.gates["alice"] = aliceGate
        val coordinator = RedditSavedHistoryRequestCoordinator(scope = this, loader = loader)

        coordinator.update(account, "alice", RepositoryLoadOperation.REFRESH)
        runCurrent()
        coordinator.update(account, "bob", RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()
        aliceGate.complete(Unit)
        advanceUntilIdle()

        val state = coordinator.uiState.value
        assertEquals("bob", state.username)
        assertEquals(listOf("t1_bob_a", "t1_bob_b"), ids(state))
    }

    @Test
    fun aProfileWithoutAValidNameReadsNothing() = runTest(StandardTestDispatcher()) {
        val loader = loader()
        val coordinator = RedditSavedHistoryRequestCoordinator(scope = this, loader = loader)

        coordinator.update(account, " ", RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        assertEquals(RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION, coordinator.uiState.value.failureKind)
        assertEquals(emptyList<String>(), loader.calls)
    }

    @Test
    fun anotherAccountDoesNotKeepTheFirstAccountsItems() = runTest(StandardTestDispatcher()) {
        val loader = Loader(
            cachedFor = { _, _ -> AuthenticatedRedditSavedHistoryResult.NotCached },
            freshFor = { accountId, _ ->
                if (accountId == account) loaded("t1_mine", next = null) else loaded("t1_theirs", next = null)
            },
            appendFor = { error("unused") },
        )
        val coordinator = RedditSavedHistoryRequestCoordinator(scope = this, loader = loader)
        coordinator.update(account, user, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()
        assertEquals(listOf("t1_mine"), ids(coordinator.uiState.value))

        coordinator.update("reddit:other", user, RepositoryLoadOperation.REFRESH)
        assertEquals("reddit:other", coordinator.uiState.value.accountStableId)
        assertEquals(emptyList<String>(), ids(coordinator.uiState.value))

        advanceUntilIdle()
        assertEquals(listOf("t1_theirs"), ids(coordinator.uiState.value))
        assertTrue(loader.calls.contains("cached:reddit:other:$user"))
    }
}
