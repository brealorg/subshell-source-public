package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMMENTSSOT01 (#244): the thread on screen follows the stored thread, local
 * edits and deletes are written to it, an evicted (empty) store never blanks
 * the screen, and a comment-link view is not replaced by the stored thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommentSsot01FollowsTheStoreTest {
    private val stored = MutableSharedFlow<List<Comment>>(extraBufferCapacity = 8)
    private val storedEdits = mutableListOf<List<Comment>?>()
    private var storeContent = listOf(comment("t1_a", "first"))
    private val networkAnswer = CompletableDeferred<AuthenticatedRedditCommentResult>()

    private val loader = object : AuthenticatedRedditCommentLoader {
        override fun observeStoredThread(accountStableId: String, postId: String, sort: RedditCommentSort): Flow<List<Comment>> =
            stored
        override suspend fun updateStoredThread(
            accountStableId: String,
            postId: String,
            transform: (List<Comment>) -> List<Comment>?,
        ) {
            storedEdits += transform(storeContent)
        }
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = emptyList(), fromCache = true)
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) =
            networkAnswer.await()
        override suspend fun synchronizeFocused(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            focusedCommentId: String,
            context: Int,
        ) = AuthenticatedRedditCommentResult.Loaded(
            comments = listOf(comment("t1_a", "first"), comment("t1_linked", "the linked comment")),
            fromCache = false,
        )
        override suspend fun loadMore(accountStableId: String, postId: String, sort: RedditCommentSort, moreNodeId: String) =
            error("unused")
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ) = error("unused")
    }

    private val coordinator = CommentThreadCoordinator(commentLoader = loader, scope = CoroutineScope(Dispatchers.Unconfined))

    private fun loaded() {
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)
        networkAnswer.complete(AuthenticatedRedditCommentResult.Loaded(comments = storeContent, fromCache = false))
    }

    private fun shownBodies() = coordinator.redditCommentsUiState.value.comments.map(Comment::body)

    @Test
    fun whatReachesTheStoredThreadIsShown() {
        loaded()

        // Another writer (a reply, "load more", another screen) stored more.
        stored.tryEmit(listOf(comment("t1_a", "first"), comment("t1_b", "second")))

        assertEquals(listOf("first", "second"), shownBodies())
    }

    @Test
    fun anEvictedThreadDoesNotBlankTheScreen() {
        loaded()

        stored.tryEmit(emptyList())

        assertEquals(listOf("first"), shownBodies())
    }

    @Test
    fun anEditAndADeleteAreWrittenToTheStoredThread() {
        loaded()

        coordinator.applyEditedComment(
            CommentEditRequest(
                accountStableId = "acc",
                postId = "t3_post",
                commentFullname = "t1_a",
                commentAuthor = "me",
                text = "edited",
                sort = RedditCommentSort.BEST,
            ),
        )
        coordinator.applyDeletedComment(
            CommentDeleteRequest(
                accountStableId = "acc",
                postId = "t3_post",
                commentFullname = "t1_a",
                commentAuthor = "me",
                sort = RedditCommentSort.BEST,
            ),
        )

        assertEquals(listOf("edited"), storedEdits[0]!!.map(Comment::body))
        assertEquals(listOf("[deleted]"), storedEdits[1]!!.map(Comment::body))
    }

    @Test
    fun aModeratorRemovalIsWrittenToTheStoredThread() {
        loaded()

        coordinator.applyModeratedComment("acc", "t1_a", removed = true)

        assertTrue(storedEdits.single()!!.single().removedByModerator)
    }

    @Test
    fun anEditOfAThreadNotOnScreenStillReachesTheStore() {
        coordinator.applyEditedComment(
            CommentEditRequest(
                accountStableId = "acc",
                postId = "t3_elsewhere",
                commentFullname = "t1_a",
                commentAuthor = "me",
                text = "edited",
                sort = RedditCommentSort.BEST,
            ),
        )

        assertEquals(listOf("edited"), storedEdits.single()!!.map(Comment::body))
    }

    @Test
    fun aCommentLinkViewIsNotReplacedByTheStoredThread() {
        loaded()
        coordinator.loadFocusedRedditComment("acc", "t3_post", RedditCommentSort.BEST, "t1_linked")
        assertTrue(shownBodies().toString(), "the linked comment" in shownBodies())

        stored.tryEmit(listOf(comment("t1_a", "first, stored again")))

        assertTrue(shownBodies().toString(), "the linked comment" in shownBodies())
    }

    private fun comment(id: String, body: String) = Comment(
        id = id,
        author = "me",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0L,
    )
}
