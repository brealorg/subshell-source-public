package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteResult
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMMENTSSOT01_SETTLED_COMMENT_ACTIONS_REACH_THE_THREAD_V1 (#244): a comment
 * vote or save Reddit took is written to the stored thread on screen (a reply
 * too, the score moved with the vote); a comment of another thread or account
 * is left; the vote and save coordinators say what Reddit took and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenPostSsot01CommentActionsTest {
    private val reply = comment("t1_reply", score = 5, vote = -1)
    private val storeContent = listOf(comment("t1_top", score = 1, vote = 0).copy(children = listOf(reply)))
    private val storedEdits = mutableListOf<Pair<String, List<Comment>?>>()

    private val loader = object : AuthenticatedRedditCommentLoader {
        override fun observeStoredThread(accountStableId: String, postId: String, sort: RedditCommentSort): Flow<List<Comment>> =
            emptyFlow()
        override suspend fun updateStoredThread(
            accountStableId: String,
            postId: String,
            transform: (List<Comment>) -> List<Comment>?,
        ) {
            storedEdits += postId to transform(storeContent)
        }
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = emptyList(), fromCache = true)
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = storeContent, fromCache = false)
        override suspend fun synchronizeFocused(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            focusedCommentId: String,
            context: Int,
        ) = error("unused")
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

    private fun stored(): Comment = storedEdits.single().second!!.single().children.single()

    @Test
    fun aVotedReplyIsWrittenToTheStoredThreadWithItsScore() {
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)

        coordinator.applySettledCommentAction("acc", "t1_reply") { comment ->
            comment.copy(score = comment.score + (1 - comment.vote), vote = 1)
        }

        assertEquals("t3_post", storedEdits.single().first)
        assertEquals(1, stored().vote)
        assertEquals(7, stored().score)
    }

    @Test
    fun aCommentOfAnotherThreadOrAccountIsLeft() {
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)

        coordinator.applySettledCommentAction("acc", "t1_elsewhere") { comment -> comment.copy(vote = 1) }
        coordinator.applySettledCommentAction("other", "t1_reply") { comment -> comment.copy(vote = 1) }

        assertTrue(storedEdits.isEmpty())
    }

    @Test
    fun theCoordinatorsSayWhatRedditTook() = runBlocking {
        val voted = mutableListOf<PostVoteRequest>()
        val saved = mutableListOf<PostSaveRequest>()
        val votes = PostVoteCoordinator(
            capability = null,
            writer = { request ->
                if (request.direction == -1) {
                    AuthenticatedPostVoteResult.Failed(kind = app.subshell.prototype.data.account.PostVoteFailureKind.TRANSPORT, retryable = false)
                } else {
                    AuthenticatedPostVoteResult.Voted(request)
                }
            },
            clientMode = null,
            activeAccountStableId = { "acc" },
            onVoted = { request -> voted += request },
        )
        val saves = PostSaveCoordinator(
            capability = null,
            writer = { request -> AuthenticatedPostSaveResult.Updated(request) },
            clientMode = null,
            activeAccountStableId = { "acc" },
            onSaved = { request -> saved += request },
        )

        votes.vote(PostVoteRequest(accountStableId = "acc", postFullname = "t1_reply", direction = 1))
        votes.vote(PostVoteRequest(accountStableId = "acc", postFullname = "t1_failed", direction = -1))
        saves.setSaved(PostSaveRequest(accountStableId = "acc", postFullname = "t1_reply", saved = true))

        assertEquals(listOf("t1_reply"), voted.map(PostVoteRequest::postFullname))
        assertEquals(listOf(true), saved.map(PostSaveRequest::saved))
    }

    private fun comment(id: String, score: Int, vote: Int) = Comment(
        id = id,
        author = "me",
        age = "1h",
        body = "b",
        score = score,
        depth = 0,
        accent = 0L,
        vote = vote,
    )
}
