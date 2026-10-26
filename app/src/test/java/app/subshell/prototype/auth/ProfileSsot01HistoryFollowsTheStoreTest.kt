package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsSnapshot
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PROFILESSOT01 (#244): the Comments tab follows the stored comment history,
 * an empty (not stored, evicted) history never blanks it, and a confirmed
 * edit or delete is written to the stored histories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSsot01HistoryFollowsTheStoreTest {
    private val account = "reddit:me"
    private val user = "me_user"
    private val stored = MutableSharedFlow<List<ProfileCommentFixture>>(extraBufferCapacity = 8)
    private val storedChanges = mutableListOf<ProfileCommentFixture?>()

    private fun comment(id: String, body: String = "comment $id") = ProfileCommentFixture(
        id = id,
        author = user,
        postId = "t3_abc",
        age = "1h",
        body = body,
        score = 1,
        placement = ThreadPlacement.TOP,
    )

    private val loader = object : AuthenticatedRedditAccountHistoryCommentLoader {
        override fun observeStoredHistory(accountStableId: String, username: String, kind: RedditAccountHistoryKind): Flow<List<ProfileCommentFixture>> =
            stored
        override suspend fun updateStoredHistoryComment(
            commentFullname: String,
            transform: (ProfileCommentFixture) -> ProfileCommentFixture?,
        ) {
            storedChanges += transform(comment(commentFullname, "old body"))
        }
        override suspend fun cached(accountStableId: String, username: String, kind: RedditAccountHistoryKind) =
            AuthenticatedRedditProfileCommentsResult.NotCached
        override suspend fun synchronize(accountStableId: String, username: String, kind: RedditAccountHistoryKind) =
            AuthenticatedRedditProfileCommentsResult.Loaded(
                AuthenticatedRedditProfileCommentsSnapshot(listOf(comment("t1_a")), nextCursor = "t1_a"),
                fromCache = false,
            )
        override suspend fun append(accountStableId: String, username: String, kind: RedditAccountHistoryKind, after: String) =
            error("unused")
        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditProfileCommentTargetResult.AuthorizationRequired
    }

    @Test
    fun anEditStoredElsewhereIsShownAndTheCursorIsKept() = runTest(StandardTestDispatcher()) {
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = null, commentLoader = loader)
        coordinator.update(account, user, RedditAccountHistoryKind.COMMENTS, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        stored.tryEmit(listOf(comment("t1_a", "edited in the thread")))
        advanceUntilIdle()

        assertEquals(listOf("edited in the thread"), coordinator.uiState.value.comments.map(ProfileCommentFixture::body))
        assertEquals("t1_a", coordinator.uiState.value.nextCursor)
        coordinator.cancelPending()
    }

    @Test
    fun anEmptyStoreDoesNotBlankTheTab() = runTest(StandardTestDispatcher()) {
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = null, commentLoader = loader)
        coordinator.update(account, user, RedditAccountHistoryKind.COMMENTS, RepositoryLoadOperation.REFRESH)
        advanceUntilIdle()

        stored.tryEmit(emptyList())
        advanceUntilIdle()

        assertEquals(listOf("t1_a"), coordinator.uiState.value.comments.map(ProfileCommentFixture::id))
        coordinator.cancelPending()
    }

    @Test
    fun anEditAndADeleteAreWrittenToTheStoredHistories() = runTest(StandardTestDispatcher()) {
        val coordinator = RedditAccountHistoryRequestCoordinator(scope = this, updater = null, commentLoader = loader)

        coordinator.applyEditedComment("t1_a", "  new text  ")
        coordinator.applyDeletedComment("t1_a")
        advanceUntilIdle()

        assertEquals("new text", storedChanges[0]?.body)
        assertNull(storedChanges[1])
    }
}
