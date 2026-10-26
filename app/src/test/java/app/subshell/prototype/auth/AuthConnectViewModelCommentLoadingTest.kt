package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentSnapshotLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditFocusedCommentContinuityLoader
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Behaviour tests for the comment-loading guarantees that
 * check-profile01a5-contract.sh pins as AuthConnectViewModel text: the
 * canonical discussion owns its job and "load more" owns a separate one
 * (a more-load never cancels the discussion; a new discussion load
 * cancels a pending more-load; more-loads are refused while the
 * discussion is loading or another more-load is in flight), snapshot
 * loaders receive the current comments, and the focused-target refresh
 * hands continuity to a continuity loader with the current comments.
 * Runs under Robolectric so viewModelScope is driven by the main looper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelCommentLoadingTest {
    private val account = "reddit:test"
    private val post = "abc123"
    private val sort = RedditCommentSort.BEST

    @Test
    fun moreLoadsWaitForTheDiscussionAndNeverCancelIt() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)

        viewModel.reading().loadRedditComments(account, post, sort)
        idle()
        var state = viewModel.reading().redditCommentsUiState.value
        assertEquals(listOf("c1"), state.comments.map { it.id })
        assertTrue(state.fullDiscussionLoaded)
        assertTrue(state.fullDiscussionLoading)

        // While the canonical discussion is still synchronising, "load more"
        // is refused and the discussion job is untouched. OCB581A: the caller
        // learns it was refused.
        assertFalse(viewModel.reading().loadMoreRedditComments(account, post, sort, "more-1"))
        idle()
        assertNull(viewModel.reading().redditCommentsUiState.value.loadingMoreNodeId)
        assertEquals(0, loader.moreCalls)

        loader.syncGate.complete(loaded("c1", "c2"))
        idle()
        state = viewModel.reading().redditCommentsUiState.value
        assertFalse(state.fullDiscussionLoading)
        assertFalse(state.loading)
        assertEquals(listOf("c1", "c2"), state.comments.map { it.id })

        // Now a more-load runs on its own job; a second one is refused while
        // it is in flight.
        assertTrue(viewModel.reading().loadMoreRedditComments(account, post, sort, "more-1"))
        idle()
        assertEquals("more-1", viewModel.reading().redditCommentsUiState.value.loadingMoreNodeId)
        assertFalse(viewModel.reading().loadMoreRedditComments(account, post, sort, "more-2"))
        idle()
        assertEquals(1, loader.moreCalls)
        assertEquals(listOf("c1", "c2"), loader.lastMoreCurrentComments.map { it.id })

        loader.moreGate.complete(loaded("c1", "c2", "c3"))
        idle()
        state = viewModel.reading().redditCommentsUiState.value
        assertNull(state.loadingMoreNodeId)
        assertEquals(listOf("c1", "c2", "c3"), state.comments.map { it.id })
    }

    @Test
    fun aNewDiscussionLoadCancelsAPendingMoreLoad() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)
        viewModel.reading().loadRedditComments(account, post, sort)
        idle()
        loader.syncGate.complete(loaded("c1"))
        idle()

        viewModel.reading().loadMoreRedditComments(account, post, sort, "more-1")
        idle()
        assertEquals("more-1", viewModel.reading().redditCommentsUiState.value.loadingMoreNodeId)

        loader.syncGate = CompletableDeferred()
        viewModel.reading().loadRedditComments(account, post, RedditCommentSort.NEW)
        idle()
        loader.moreGate.complete(loaded("stale"))
        idle()
        loader.syncGate.complete(loaded("n1"))
        idle()
        val state = viewModel.reading().redditCommentsUiState.value
        assertEquals(RedditCommentSort.NEW, state.sort)
        assertEquals(listOf("n1"), state.comments.map { it.id })
        assertNull(state.loadingMoreNodeId)
    }

    @Test
    fun focusedRefreshHandsContinuityToTheLoaderWithTheCurrentComments() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)
        viewModel.reading().loadRedditComments(account, post, sort)
        idle()
        loader.syncGate.complete(loaded("c1", "c2"))
        idle()

        viewModel.reading().refreshRedditCommentsPreservingFocusedTarget(account, post, sort, " c2 ")
        idle()
        assertTrue(viewModel.reading().redditCommentsUiState.value.fullDiscussionLoading)
        assertEquals("c2", loader.lastFocusedCommentId)
        assertEquals(listOf("c1", "c2"), loader.lastContinuityCurrentComments.map { it.id })

        loader.continuityGate.complete(loaded("c2", "c9"))
        idle()
        val state = viewModel.reading().redditCommentsUiState.value
        assertFalse(state.fullDiscussionLoading)
        assertEquals(listOf("c2", "c9"), state.comments.map { it.id })

        // A blank target is ignored outright.
        viewModel.reading().refreshRedditCommentsPreservingFocusedTarget(account, post, sort, "  ")
        idle()
        assertEquals(1, loader.continuityCalls)
    }

    private fun viewModel(loader: AuthenticatedRedditCommentLoader): AuthConnectViewModel =
        AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(redditCommentLoader = loader),
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun loaded(vararg ids: String) = AuthenticatedRedditCommentResult.Loaded(
        comments = ids.map { comment(it) },
        fromCache = false,
    )

    private fun comment(id: String) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "body $id",
        score = 1,
        depth = 0,
        accent = 0L,
    )

    /** Loader whose network-shaped calls suspend until a test completes their gate. */
    private class GatedLoader :
        AuthenticatedRedditCommentLoader,
        AuthenticatedRedditCommentSnapshotLoader,
        AuthenticatedRedditFocusedCommentContinuityLoader {
        var syncGate = CompletableDeferred<AuthenticatedRedditCommentResult>()
        var moreGate = CompletableDeferred<AuthenticatedRedditCommentResult>()
        var continuityGate = CompletableDeferred<AuthenticatedRedditCommentResult>()
        var moreCalls = 0
        var continuityCalls = 0
        var lastMoreCurrentComments: List<Comment> = emptyList()
        var lastContinuityCurrentComments: List<Comment> = emptyList()
        var lastFocusedCommentId: String? = null

        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(
                comments = listOf(
                    Comment(id = "c1", author = "someone", age = "1h", body = "cached", score = 1, depth = 0, accent = 0L),
                ),
                fromCache = true,
            )

        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) =
            syncGate.await()

        override suspend fun loadMore(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
        ): AuthenticatedRedditCommentResult = error("snapshot path expected")

        override suspend fun loadMoreFromSnapshot(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
            currentComments: List<Comment>,
        ): AuthenticatedRedditCommentResult {
            moreCalls += 1
            lastMoreCurrentComments = currentComments
            return moreGate.await()
        }

        override suspend fun synchronizePreservingFocusedTarget(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            focusedCommentId: String,
            currentComments: List<Comment>,
        ): AuthenticatedRedditCommentResult {
            continuityCalls += 1
            lastFocusedCommentId = focusedCommentId
            lastContinuityCurrentComments = currentComments
            return continuityGate.await()
        }

        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ): AuthenticatedRedditCommentResult = error("not used")
    }
}
