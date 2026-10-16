package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsSnapshot
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * STEP3 (profile01a6a2): a profile's comments show the stored copy first and
 * then Reddit's answer with its next-page cursor; the next page asks with
 * that cursor and is added below; a profile without a valid name reads
 * nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Step3AccountHistoryCommentsCoordinatorTest {
    private val account = "reddit:me"
    private val user = "someone"
    private val calls = mutableListOf<String>()

    private fun comment(id: String) = ProfileCommentFixture(
        id = id,
        author = user,
        postId = "t3_abc",
        age = "1h",
        body = "comment $id",
        score = 1,
        placement = ThreadPlacement.TOP,
    )

    private fun loaded(vararg ids: String, next: String?, fromCache: Boolean = false) =
        AuthenticatedRedditProfileCommentsResult.Loaded(
            AuthenticatedRedditProfileCommentsSnapshot(ids.map(::comment), next),
            fromCache = fromCache,
        )

    private inner class Loader(
        private val fresh: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : AuthenticatedRedditAccountHistoryCommentLoader {
        override suspend fun cached(accountStableId: String, username: String, kind: RedditAccountHistoryKind) =
            loaded("t1_stored", next = null, fromCache = true).also { calls += "cached" }

        override suspend fun synchronize(accountStableId: String, username: String, kind: RedditAccountHistoryKind): AuthenticatedRedditProfileCommentsResult {
            calls += "synchronize"
            fresh.await()
            return loaded("t1_a", "t1_b", next = "t1_b")
        }

        override suspend fun append(accountStableId: String, username: String, kind: RedditAccountHistoryKind, after: String): AuthenticatedRedditProfileCommentsResult {
            calls += "append:$after"
            return loaded("t1_a", "t1_b", "t1_c", next = null)
        }

        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditProfileCommentTargetResult.AuthorizationRequired
    }

    @Test
    fun theStoredCopyComesFirstThenRedditsAnswerAndItsNextPage() = runTest(StandardTestDispatcher()) {
        val fresh = CompletableDeferred<Unit>()
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = null, commentLoader = Loader(fresh))

        coordinator.update(account, user, RedditAccountHistoryKind.COMMENTS, RepositoryLoadOperation.REFRESH)
        runCurrent()
        assertEquals(listOf("t1_stored"), coordinator.uiState.value.comments.map(ProfileCommentFixture::id))

        fresh.complete(Unit)
        advanceUntilIdle()
        val refreshed = coordinator.uiState.value
        assertEquals(listOf("t1_a", "t1_b"), refreshed.comments.map(ProfileCommentFixture::id))
        assertEquals("t1_b", refreshed.nextCursor)

        coordinator.update(account, user, RedditAccountHistoryKind.COMMENTS, RepositoryLoadOperation.APPEND)
        advanceUntilIdle()

        assertEquals(listOf("cached", "synchronize", "append:t1_b"), calls)
        assertEquals(listOf("t1_a", "t1_b", "t1_c"), coordinator.uiState.value.comments.map(ProfileCommentFixture::id))
        assertEquals(null, coordinator.uiState.value.nextCursor)
    }

    @Test
    fun aProfileWithoutAValidNameReadsNothing() = runTest(StandardTestDispatcher()) {
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = null, commentLoader = Loader())

        coordinator.update(account, " ", RedditAccountHistoryKind.COMMENTS, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        assertEquals(RedditAccountHistoryUiFailureKind.CLIENT_CONFIGURATION, coordinator.uiState.value.failureKind)
        assertEquals(emptyList<String>(), calls)
    }
}
