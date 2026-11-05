package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.CommentWritePhase
import io.github.brealorg.subshell.data.account.CommentWriteRequest
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentSubmitResult
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.model.Comment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMMENTCACHE01 (#138): the comment writer hands the loader the thread the
 * reader sees, so the posted comment is merged into it instead of into the
 * cached thread.
 */
class CommentCache01DisplayedThreadTest {
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
    private fun comment(id: String, body: String) = Comment(
        id = id, author = "alice", age = "now", body = body, score = 1, depth = 0, accent = 0xFF000000,
    )
    private val displayed = listOf(comment("t1_x", "edited on screen"))

    private var mergedOnto: List<Comment>? = null
    private val loader = object : AuthenticatedRedditCommentLoader {
        private fun loaded(comments: List<Comment> = emptyList()) =
            AuthenticatedRedditCommentResult.Loaded(comments = comments, fromCache = false)
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) = loaded()
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) = loaded()
        override suspend fun loadMore(accountStableId: String, postId: String, sort: RedditCommentSort, moreNodeId: String) = loaded()
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ) = loaded(listOf(comment, comment("t1_x", "stale cached text")))
        override suspend fun cacheSubmittedCommentOnto(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
            currentComments: List<Comment>?,
        ): AuthenticatedRedditCommentResult {
            mergedOnto = currentComments
            return loaded(listOf(comment) + currentComments.orEmpty())
        }
    }

    @Test
    fun aPostedCommentIsMergedIntoTheDisplayedThread() = runBlocking {
        var shown: List<Comment>? = null
        val writes = CommentWriteCoordinator(
            capability = null,
            submitter = { _ ->
                AuthenticatedCommentSubmitResult.Submitted(
                    accountStableId = account.stableId,
                    comment = comment("t1_new", "Hello thread"),
                    metadata = NetworkResponseMetadata(
                        statusCode = 200,
                        receivedAtEpochMs = 0L,
                        rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
                        retryAfterMs = null,
                    ),
                )
            },
            commentLoader = loader,
            accountDirectory = directory,
            clientMode = null,
            onThreadUpdated = { _, comments -> shown = comments },
            displayedThread = { displayed },
        )

        writes.submit(request)

        assertEquals(CommentWritePhase.SUCCEEDED, writes.uiState.value.phase)
        assertEquals(displayed, mergedOnto)
        assertEquals(listOf("Hello thread", "edited on screen"), shown?.map { it.body })
    }
}
