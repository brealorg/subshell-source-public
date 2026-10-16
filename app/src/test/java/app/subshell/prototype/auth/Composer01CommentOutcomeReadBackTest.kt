package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentWriteFailureKind
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitResult
import app.subshell.prototype.data.network.reddit.RedditAccountCommentHistory
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMPOSER01_COMMENT_OUTCOME_READ_BACK_V1 (ui review round 3 C2): a text
 * comment whose response was lost is read back from the thread, and the same
 * comment is never dispatched again until a read-back shows it is missing.
 */
class Composer01CommentOutcomeReadBackTest {
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
        text = "Hello thread",
        sort = RedditCommentSort.BEST,
    )

    private var thread: List<Comment> = emptyList()

    // COMMENTREADBACK01 (#123): "not posted" needs the account's own history
    // to prove absence; here it is complete and empty.
    private var history: RedditAccountCommentHistory? =
        RedditAccountCommentHistory(candidates = emptyList(), reachesEnd = true)
    private var dispatches = 0

    private val loader = object : AuthenticatedRedditCommentLoader {
        private fun loaded() = AuthenticatedRedditCommentResult.Loaded(comments = thread, fromCache = false)
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) = loaded()
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) = loaded()
        override suspend fun loadMore(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
        ) = loaded()
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ) = loaded()
        override suspend fun readAccountRecentComments(accountStableId: String, username: String) = history
    }

    private fun coordinator() = CommentWriteCoordinator(
        capability = null,
        submitter = { _ ->
            dispatches += 1
            AuthenticatedCommentSubmitResult.Failed(kind = AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN)
        },
        commentLoader = loader,
        accountDirectory = directory,
        clientMode = null,
    )

    private fun postedComment(body: String = "Hello thread") = Comment(
        id = "c1",
        author = "alice",
        age = "now",
        body = body,
        score = 1,
        depth = 0,
        accent = 0xFF000000,
        createdEpochSeconds = System.currentTimeMillis() / 1_000L,
        parentId = "t3_abc",
    )

    @Test
    fun aLostResponseForAPostedCommentBecomesSuccessWithoutASecondDispatch() = runBlocking {
        val writes = coordinator()
        writes.submit(request)
        assertEquals(CommentWritePhase.OUTCOME_UNKNOWN, writes.uiState.value.phase)

        thread = listOf(postedComment())
        writes.submit(request) // Check again

        assertEquals(CommentWritePhase.SUCCEEDED, writes.uiState.value.phase)
        assertEquals(1, dispatches)
    }

    @Test
    fun theSameCommentIsOnlySentAgainAfterAReadBackShowsItMissing() = runBlocking {
        val writes = coordinator()
        writes.submit(request)
        writes.reset() // Dismiss
        writes.submit(request) // Send again: checks instead

        assertEquals(1, dispatches)
        assertEquals(CommentWritePhase.FAILED, writes.uiState.value.phase)
        assertEquals(CommentWriteFailureKind.NOT_POSTED, writes.uiState.value.failureKind)

        writes.submit(request) // Try again after "not posted"
        assertEquals(2, dispatches)
    }

    @Test
    fun aCommentFoundRightAwayIsSuccess() = runBlocking {
        thread = listOf(postedComment())
        val writes = coordinator()
        writes.submit(request)

        assertEquals(CommentWritePhase.SUCCEEDED, writes.uiState.value.phase)
    }

    @Test
    fun aDifferentCommentIsSentNormally() = runBlocking {
        val writes = coordinator()
        writes.submit(request)
        writes.submit(request.copy(text = "Something else"))

        assertEquals(2, dispatches)
    }

    // LOW10_COMMENT_UNKNOWN_OUTCOMES_KEPT_PER_COMMENT_V1 (OCB-583)
    @Test
    fun anotherCommentDoesNotForgetTheUnconfirmedOne() = runBlocking {
        val writes = coordinator()
        writes.submit(request)
        writes.submit(request.copy(text = "Something else"))
        writes.submit(request) // the first comment again: read back, not sent

        assertEquals(2, dispatches)
        assertEquals(CommentWriteFailureKind.NOT_POSTED, writes.uiState.value.failureKind)
    }

    // LOW10_COMMENT_READ_BACK_ALLOWS_CLOCK_SKEW_V1 (OCB-409)
    @Test
    fun aCommentIsFoundWhenThePhoneClockRunsMinutesAhead() = runBlocking {
        thread = listOf(postedComment().copy(createdEpochSeconds = System.currentTimeMillis() / 1_000L - 300))
        val writes = coordinator()
        writes.submit(request)

        assertEquals(CommentWritePhase.SUCCEEDED, writes.uiState.value.phase)
    }

    @Test
    fun readBackMatchesTheAccountParentTimeAndText() {
        val earliest = System.currentTimeMillis() / 1_000L - 5
        fun find(comment: Comment) = findAuthoritativeTextCommentAfterUnknownOutcome(
            comments = listOf(comment),
            username = "alice",
            parentFullname = "t3_abc",
            submittedText = "Hello thread\r\n",
            earliestCreatedEpochSeconds = earliest,
        )
        assertEquals("c1", find(postedComment())?.id)
        assertEquals(null, find(postedComment(body = "Other text")))
        assertEquals(null, find(postedComment().copy(author = "bob")))
        assertEquals(null, find(postedComment().copy(parentId = "t1_other")))
        assertEquals(null, find(postedComment().copy(createdEpochSeconds = earliest - 60)))
    }
}
