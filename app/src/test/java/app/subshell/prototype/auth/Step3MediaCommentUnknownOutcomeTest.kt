package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentWriteMediaAttachment
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitResult
import app.subshell.prototype.model.Comment
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (messages02c4r3): when Reddit's answer to an image/GIF comment is
 * lost, the app reads the thread back once instead of sending again. Only a
 * fresh comment by the same account under the same parent counts as the sent
 * one; anything else, or a failed read, stays "may have been sent".
 */
class Step3MediaCommentUnknownOutcomeTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val directory = object : AccountSessionDirectory {
        override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
        override fun selectAccount(stableId: String) = snapshot()
    }
    private val request = CommentWriteRequest(
        accountStableId = account.stableId,
        postId = "abc",
        targetStableKey = "post:abc",
        parentFullname = "t3_abc",
        text = "",
        sort = RedditCommentSort.BEST,
        media = CommentWriteMediaAttachment("content://media/1", "image/gif"),
    )

    private var submits = 0
    private var synchronizes = 0
    private var readBack: () -> List<Comment> = { emptyList() }
    private val shownThreads = mutableListOf<List<Comment>>()

    private val loader = object : AuthenticatedRedditCommentLoader {
        private fun loaded(comments: List<Comment>) =
            AuthenticatedRedditCommentResult.Loaded(comments = comments, fromCache = false)
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            loaded(emptyList())
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort): AuthenticatedRedditCommentResult {
            synchronizes += 1
            return loaded(readBack())
        }
        override suspend fun loadMore(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
        ) = loaded(emptyList())
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ) = loaded(listOf(comment))
        override suspend fun readCommentBody(accountStableId: String, postId: String, commentFullname: String): String? = null
    }

    private fun coordinator() = CommentWriteCoordinator(
        capability = null,
        submitter = { _ ->
            submits += 1
            AuthenticatedCommentSubmitResult.Failed(kind = AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN)
        },
        commentLoader = loader,
        accountDirectory = directory,
        clientMode = null,
        onThreadUpdated = { _, comments -> shownThreads += comments },
    )

    private fun now() = System.currentTimeMillis() / 1_000L

    private fun comment(id: String, author: String, parentId: String, created: Long) = Comment(
        id = id,
        author = author,
        age = "0m",
        body = "*Processing img k2x9...*",
        score = 1,
        depth = 0,
        accent = 0xFF000000,
        parentId = parentId,
        createdEpochSeconds = created,
    )

    @Test
    fun aFreshMatchingCommentFoundByTheReadBackIsTheSentOne() = runBlocking {
        readBack = { listOf(comment("t1_mine", "alice", "t3_abc", now())) }
        val writes = coordinator()

        writes.submit(request)

        val state = writes.uiState.value
        assertEquals(CommentWritePhase.SUCCEEDED, state.phase)
        assertEquals("t1_mine", state.submittedComment?.id)
        assertTrue(state.threadUpdated)
        assertEquals(listOf("t1_mine"), shownThreads.last().map { it.id })
        assertEquals(1, submits)
        assertEquals(1, synchronizes)
    }

    @Test
    fun anOlderOrSomeoneElsesCommentIsNotTakenAndNothingIsSentAgain() = runBlocking {
        readBack = {
            listOf(
                comment("t1_old", "alice", "t3_abc", now() - 3_600L),
                comment("t1_other", "bob", "t3_abc", now()),
                comment("t1_elsewhere", "alice", "t3_other", now()),
            )
        }
        val writes = coordinator()

        writes.submit(request)

        assertEquals(CommentWritePhase.OUTCOME_UNKNOWN, writes.uiState.value.phase)
        assertEquals(1, submits)
        assertEquals(1, synchronizes)
    }

    @Test
    fun aFailedReadBackStaysMayHaveBeenSent() = runBlocking {
        readBack = { throw IOException("offline") }
        val writes = coordinator()

        writes.submit(request)

        assertEquals(CommentWritePhase.OUTCOME_UNKNOWN, writes.uiState.value.phase)
        assertEquals(1, submits)
    }
}
