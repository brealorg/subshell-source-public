package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.account.CommentEditRequest
import io.github.brealorg.subshell.data.account.CommentWriteRequest
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentSnapshotLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFocusedCommentContinuityLoader
import io.github.brealorg.subshell.model.Comment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit 2026-11-12 (#298), the coordinator:
 * - AUDIT298A_LOCAL_WRITES_AND_REFRESHES_STOP_MORE_LOADS_V1: a more load
 *   merges into the thread as it was when it started; answering after a
 *   write or a refresh it put that older thread back.
 * - AUDIT298A_FOCUSED_REFRESH_DOES_NOT_SHOW_THE_STORED_THREAD_V1: the
 *   landing's refresh followed the stored thread at once, which Room emits
 *   at once, and the linked comment left the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Audit298aMoreLoadsAndRefreshesTest {
    private fun c(id: String, body: String = id, children: List<Comment> = emptyList(), more: List<String> = emptyList()) = Comment(
        id = id,
        author = "me",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0L,
        moreChildIds = more,
        moreCount = more.size,
        parentId = "t3_post".takeIf { more.isNotEmpty() },
    )

    private val moreNode = c("more:t3_post:x", more = listOf("x"))
    private val thread = listOf(c("t1_c1", body = "old body"), moreNode)

    /** Answers loads with the thread as it was when the load started, after [moreAnswer]. */
    private inner class Loader(
        private val stored: List<Comment> = emptyList(),
    ) : AuthenticatedRedditCommentLoader, AuthenticatedRedditCommentSnapshotLoader,
        AuthenticatedRedditFocusedCommentContinuityLoader {
        val storedFlow = MutableStateFlow(stored)
        val moreAnswer = CompletableDeferred<Unit>()
        val refreshAnswer = CompletableDeferred<Unit>()
        var focusedThread: List<Comment> = thread

        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = thread, fromCache = true)

        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = thread, fromCache = false)

        override suspend fun synchronizeFocused(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            focusedCommentId: String,
            context: Int,
        ) = AuthenticatedRedditCommentResult.Loaded(comments = focusedThread, fromCache = false)

        override suspend fun loadMore(accountStableId: String, postId: String, sort: RedditCommentSort, moreNodeId: String) =
            error("snapshot loader is used")

        override suspend fun loadMoreFromSnapshot(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
            currentComments: List<Comment>,
        ): AuthenticatedRedditCommentResult {
            moreAnswer.await()
            // The node's comment in place of the node, merged into the old snapshot.
            return AuthenticatedRedditCommentResult.Loaded(
                comments = currentComments.filterNot { it.id == moreNodeId } + c("t1_x"),
                fromCache = false,
            )
        }

        override suspend fun synchronizePreservingFocusedTarget(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            focusedCommentId: String,
            currentComments: List<Comment>,
        ): AuthenticatedRedditCommentResult {
            refreshAnswer.await()
            // As the real loader: the thread with the linked comment is stored.
            storedFlow.value = focusedThread
            return AuthenticatedRedditCommentResult.Loaded(comments = focusedThread, fromCache = false)
        }

        override fun observeStoredThread(accountStableId: String, postId: String, sort: RedditCommentSort): Flow<List<Comment>> =
            storedFlow

        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ) = error("unused")
    }

    private fun coordinator(loader: Loader) =
        CommentThreadCoordinator(commentLoader = loader, scope = CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun anEditMadeWhileMoreRepliesLoadIsNotUndoneByThatLoad() = runBlocking {
        val loader = Loader()
        val coordinator = coordinator(loader)
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)
        assertTrue(coordinator.loadMoreRedditComments("acc", "t3_post", RedditCommentSort.BEST, moreNode.id))

        coordinator.applyEditedComment(
            CommentEditRequest(
                accountStableId = "acc",
                postId = "t3_post",
                commentFullname = "t1_c1",
                commentAuthor = "me",
                text = "edited body",
                sort = RedditCommentSort.BEST,
            ),
        )
        loader.moreAnswer.complete(Unit)

        val state = coordinator.redditCommentsUiState.value
        assertEquals("edited body", state.comments.first { it.id == "t1_c1" }.body)
        assertNull("the stopped load does not stay 'loading'", state.loadingMoreNodeId)
        assertTrue(
            "the node can load again",
            coordinator.loadMoreRedditComments("acc", "t3_post", RedditCommentSort.BEST, moreNode.id),
        )
    }

    @Test
    fun aReplyPostedWhileMoreRepliesLoadIsNotUndoneByThatLoad() = runBlocking {
        val loader = Loader()
        val coordinator = coordinator(loader)
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)
        coordinator.loadMoreRedditComments("acc", "t3_post", RedditCommentSort.BEST, moreNode.id)

        val withReply = thread + c("t1_reply", body = "my reply")
        coordinator.applySubmittedCommentThread(
            CommentWriteRequest(
                accountStableId = "acc",
                postId = "t3_post",
                targetStableKey = "post:t3_post",
                parentFullname = "t3_post",
                text = "my reply",
                sort = RedditCommentSort.BEST,
            ),
            withReply,
        )
        loader.moreAnswer.complete(Unit)

        val state = coordinator.redditCommentsUiState.value
        assertTrue(state.comments.any { it.id == "t1_reply" })
        assertNull(state.loadingMoreNodeId)
    }

    @Test
    fun theLandingsRefreshNeitherShowsTheStoredThreadNorLetsAnOlderMoreLoadWin() = runBlocking {
        // The stored thread has no linked comment; the focused thread has it.
        val loader = Loader(stored = listOf(c("t1_c1", body = "stored, no link target")))
        loader.focusedThread = thread + c("t1_linked")
        val coordinator = coordinator(loader)
        coordinator.loadFocusedRedditComment("acc", "t3_post", RedditCommentSort.BEST, "t1_linked")
        assertTrue("landed", coordinator.redditCommentsUiState.value.comments.any { it.id == "t1_linked" })
        coordinator.loadMoreRedditComments("acc", "t3_post", RedditCommentSort.BEST, moreNode.id)

        coordinator.refreshRedditCommentsPreservingFocusedTarget("acc", "t3_post", RedditCommentSort.BEST, "t1_linked")
        assertTrue(
            "the linked comment stays on screen while the refresh runs",
            coordinator.redditCommentsUiState.value.comments.any { it.id == "t1_linked" },
        )
        loader.moreAnswer.complete(Unit)
        loader.refreshAnswer.complete(Unit)

        val state = coordinator.redditCommentsUiState.value
        assertTrue("after the refresh", state.comments.any { it.id == "t1_linked" })
        assertTrue("the older more load did not win", state.comments.none { it.id == "t1_x" })
    }
}
