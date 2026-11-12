package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentSnapshotLoader
import io.github.brealorg.subshell.model.Comment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AUDIT298C_SMALL_NODES_LOAD_TOGETHER_V1: several small more nodes, one round, one publish. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Audit298cSmallNodesTogetherTest {
    private fun c(id: String, children: List<Comment> = emptyList()) =
        Comment(id = id, author = "a", age = "1h", body = id, score = 1, depth = 0, accent = 0L, children = children)

    private fun node(id: String) = Comment(
        id = id, author = "", age = "", body = "", score = 0, depth = 1, accent = 0L,
        parentId = "t1_top", moreChildIds = listOf("x"), moreCount = 1,
    )

    private val thread = listOf(c("t1_a", listOf(node("more:a"))), c("t1_b", listOf(node("more:b"))))

    private inner class Loader(private val failing: Set<String> = emptySet()) :
        AuthenticatedRedditCommentLoader, AuthenticatedRedditCommentSnapshotLoader {
        val asked = mutableListOf<String>()
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = thread, fromCache = true)
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = thread, fromCache = false)
        override suspend fun loadMore(accountStableId: String, postId: String, sort: RedditCommentSort, moreNodeId: String) =
            error("snapshot loader is used")
        override suspend fun cacheSubmittedComment(
            accountStableId: String, postId: String, sort: RedditCommentSort, parentFullname: String, comment: Comment,
        ) = error("unused")
        override suspend fun loadMoreFromSnapshot(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
            currentComments: List<Comment>,
        ): AuthenticatedRedditCommentResult {
            asked += moreNodeId
            if (moreNodeId in failing) {
                return AuthenticatedRedditCommentResult.Failed(AuthenticatedRedditCommentFailureKind.TRANSPORT, retryable = true)
            }
            // The node's reply in place of the node.
            return AuthenticatedRedditCommentResult.Loaded(
                comments = currentComments.map { top ->
                    top.copy(children = top.children.map { child -> if (child.id == moreNodeId) c("t1_r_${moreNodeId.removePrefix("more:")}") else child })
                },
                fromCache = false,
            )
        }
    }

    private fun ids(comments: List<Comment>): List<String> = comments.flatMap { listOf(it.id) + ids(it.children) }

    @Test
    fun twoNodesLoadInOneRoundAndArePublishedOnce() = runBlocking {
        val loader = Loader()
        val coordinator = CommentThreadCoordinator(commentLoader = loader, scope = CoroutineScope(Dispatchers.Unconfined))
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)
        val before = coordinator.redditCommentsUiState.value.contentRevision

        coordinator.loadMoreRedditComments("acc", "t3_post", RedditCommentSort.BEST, "more:a", alsoNodeIds = listOf("more:b"))

        val state = coordinator.redditCommentsUiState.value
        assertEquals(listOf("more:a", "more:b"), loader.asked)
        assertEquals(listOf("t1_a", "t1_r_a", "t1_b", "t1_r_b"), ids(state.comments))
        assertEquals("one publish", before + 1, state.contentRevision)
        assertNull(state.loadingMoreNodeId)
    }

    @Test
    fun aFailingSecondNodeKeepsTheFirst() = runBlocking {
        val loader = Loader(failing = setOf("more:b"))
        val coordinator = CommentThreadCoordinator(commentLoader = loader, scope = CoroutineScope(Dispatchers.Unconfined))
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)

        coordinator.loadMoreRedditComments("acc", "t3_post", RedditCommentSort.BEST, "more:a", alsoNodeIds = listOf("more:b"))

        val state = coordinator.redditCommentsUiState.value
        assertEquals(listOf("t1_a", "t1_r_a", "t1_b", "more:b"), ids(state.comments))
        assertNull("the second node is not marked failed; it loads again by itself", state.failedMoreNodeId)
    }
}
