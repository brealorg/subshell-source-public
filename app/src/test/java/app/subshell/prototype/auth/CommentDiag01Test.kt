package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMMENTDIAG01_FAILURE_DETAILS_REACH_THE_REPORT_V1 (owner 2026-10-18): a
 * failed comment load keeps the loader's failure details, so the diagnostic
 * report says what failed (a report showed a comments error with none).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommentDiag01Test {
    private val details = FailureDetails(retryable = true, transportKind = NetworkTransportFailureKind.TIMEOUT)

    private val failing = object : AuthenticatedRedditCommentLoader {
        private val failed = AuthenticatedRedditCommentResult.Failed(
            kind = AuthenticatedRedditCommentFailureKind.TRANSPORT,
            retryable = true,
            details = details,
        )
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) = failed
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) = failed
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

    private fun coordinator() = CommentThreadCoordinator(
        commentLoader = failing,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun aFailedLoadKeepsItsDetails() {
        val coordinator = coordinator()
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)
        assertEquals(details, coordinator.redditCommentsUiState.value.failureDetails)
    }

    @Test
    fun aFailedCachedLoadKeepsItsDetails() {
        val coordinator = coordinator()
        coordinator.loadCachedRedditComments("acc", "t3_post", RedditCommentSort.BEST)
        assertEquals(details, coordinator.redditCommentsUiState.value.failureDetails)
    }

    @Test
    fun aFailedRefreshAroundAFocusedCommentKeepsItsDetails() {
        val coordinator = coordinator()
        coordinator.refreshRedditCommentsPreservingFocusedTarget("acc", "t3_post", RedditCommentSort.BEST, "t1_focus")
        assertEquals(details, coordinator.redditCommentsUiState.value.failureDetails)
    }
}
