package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CONC02 (OCB-582): a thread load in flight when a comment edit landed could
 * finish afterwards and put back the body it had fetched before the edit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Conc02LocalWriteBeatsStaleLoadTest {
    @Test
    fun anEditMadeDuringALoadIsNotOverwrittenByThatLoad() = runBlocking {
        val networkAnswer = CompletableDeferred<Unit>()
        val coordinator = CommentThreadCoordinator(
            commentLoader = object : AuthenticatedRedditCommentLoader {
                override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
                    AuthenticatedRedditCommentResult.Loaded(comments = listOf(comment("old body")), fromCache = true)
                override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort): AuthenticatedRedditCommentResult {
                    networkAnswer.await()
                    return AuthenticatedRedditCommentResult.Loaded(comments = listOf(comment("old body")), fromCache = false)
                }
                override suspend fun loadMore(accountStableId: String, postId: String, sort: RedditCommentSort, moreNodeId: String) =
                    error("unused")
                override suspend fun cacheSubmittedComment(
                    accountStableId: String,
                    postId: String,
                    sort: RedditCommentSort,
                    parentFullname: String,
                    comment: Comment,
                ) = error("unused")
            },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)
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
        networkAnswer.complete(Unit)

        assertEquals("edited body", coordinator.redditCommentsUiState.value.comments.single().body)
    }

    private fun comment(body: String) = Comment(
        id = "t1_c1",
        author = "me",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0L,
    )
}
