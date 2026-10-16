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
import app.subshell.prototype.data.network.reddit.RedditAccountCommentCandidate
import app.subshell.prototype.data.network.reddit.RedditAccountCommentHistory
import app.subshell.prototype.data.network.reddit.RedditAccountCommentHistoryDataDto
import app.subshell.prototype.data.network.reddit.RedditAccountCommentHistoryDto
import app.subshell.prototype.data.network.reddit.toAccountCommentHistory
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COMMENTREADBACK01 (#123): after a lost response, a comment missing from the
 * truncated thread listing (100 comments, depth 8, the reader's sort) was
 * declared "not posted", so Retry could publish a duplicate. Absence now needs
 * the account's own newest comments to reach back before the attempt with no
 * comment under the same parent since then.
 */
class CommentReadback01AccountHistoryTest {
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
        parentFullname = "t1_deep",
        text = "Hello thread",
        sort = RedditCommentSort.TOP,
    )
    private val now = System.currentTimeMillis() / 1_000L

    // The thread listing never shows the reply (it sits past the listing's limits).
    private var history: RedditAccountCommentHistory? = null
    private var dispatches = 0

    private val loader = object : AuthenticatedRedditCommentLoader {
        private fun loaded() = AuthenticatedRedditCommentResult.Loaded(comments = emptyList<Comment>(), fromCache = false)
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

    private fun candidate(parent: String, created: Long) =
        RedditAccountCommentCandidate(author = "alice", parentFullname = parent, createdUtcEpochSeconds = created)

    private suspend fun checkAgainAndRetry(): CommentWriteCoordinator {
        val writes = coordinator()
        writes.submit(request)
        writes.submit(request) // Check again
        return writes
    }

    @Test
    fun aReplyUnderTheSameParentInTheAccountHistoryKeepsTheOutcomeUnknown() = runBlocking {
        history = RedditAccountCommentHistory(
            candidates = listOf(candidate("t1_deep", now), candidate("t3_old", now - 3_600)),
            reachesEnd = false,
        )
        val writes = checkAgainAndRetry()

        assertEquals(CommentWritePhase.OUTCOME_UNKNOWN, writes.uiState.value.phase)
        writes.submit(request) // Retry: still only checks
        assertEquals(1, dispatches)
    }

    @Test
    fun aHistoryThatDoesNotReachBackBeforeTheAttemptProvesNothing() = runBlocking {
        history = RedditAccountCommentHistory(
            candidates = listOf(candidate("t3_other", now)),
            reachesEnd = false,
        )
        val writes = checkAgainAndRetry()

        assertEquals(CommentWritePhase.OUTCOME_UNKNOWN, writes.uiState.value.phase)
        assertEquals(1, dispatches)
    }

    @Test
    fun anUnreadableHistoryKeepsTheOutcomeUnknown() = runBlocking {
        history = null
        val writes = checkAgainAndRetry()

        assertEquals(CommentWritePhase.OUTCOME_UNKNOWN, writes.uiState.value.phase)
        assertEquals(1, dispatches)
    }

    @Test
    fun aCoveringHistoryWithoutTheReplyAllowsARetry() = runBlocking {
        history = RedditAccountCommentHistory(
            candidates = listOf(candidate("t1_deep", now - 3_600)),
            reachesEnd = false,
        )
        val writes = checkAgainAndRetry()

        assertEquals(CommentWritePhase.FAILED, writes.uiState.value.phase)
        assertEquals(CommentWriteFailureKind.NOT_POSTED, writes.uiState.value.failureKind)
        writes.submit(request)
        assertEquals(2, dispatches)
    }

    @Test
    fun theListingSaysWhetherItReachesTheEnd() {
        val more = RedditAccountCommentHistoryDto(RedditAccountCommentHistoryDataDto(after = "t1_x"))
        val last = RedditAccountCommentHistoryDto(RedditAccountCommentHistoryDataDto(after = null))
        assertFalse(more.toAccountCommentHistory().reachesEnd)
        assertTrue(last.toAccountCommentHistory().reachesEnd)
    }
}
