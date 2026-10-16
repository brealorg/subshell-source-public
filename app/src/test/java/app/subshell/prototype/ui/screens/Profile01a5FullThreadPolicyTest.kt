package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.beginSortLoad
import app.subshell.prototype.data.network.reddit.mergeFocusedCommentContext
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a5FullThreadPolicyTest {
    @Test
    fun canonicalDiscussionReadinessSurvivesOnlyTheSameCommentQuery() {
        val loaded = RedditCommentsUiState(
            accountStableId = "reddit:account",
            postId = "t3_post",
            sort = RedditCommentSort.BEST,
            comments = listOf(comment("t1_cached")),
            fullDiscussionLoaded = true,
            contentRevision = 4L,
        )

        val sameQuery = loaded.beginSortLoad(
            accountStableId = "reddit:account",
            postId = "t3_post",
            sort = RedditCommentSort.BEST,
        )
        val differentSort = loaded.beginSortLoad(
            accountStableId = "reddit:account",
            postId = "t3_post",
            sort = RedditCommentSort.TOP,
        )

        assertTrue(sameQuery.fullDiscussionLoaded)
        assertTrue(sameQuery.loading)
        assertTrue(!differentSort.fullDiscussionLoaded)
        assertTrue(!sameQuery.fullDiscussionLoading)
    }

    @Test
    fun focusedContextMergesIntoFullDiscussionWithoutDroppingSiblings() {
        val current = listOf(
            comment("t1_before"),
            comment(
                id = "t1_root",
                children = listOf(comment("t1_existing")),
            ),
            comment("t1_after"),
        )
        val focused = listOf(
            comment(
                id = "t1_root",
                children = listOf(comment("t1_target")),
            ),
        )

        val merged = mergeFocusedCommentContext(current, focused)

        assertEquals(listOf("t1_before", "t1_root", "t1_after"), merged.map(Comment::id))
        val root = merged.single { it.id == "t1_root" }
        assertEquals(listOf("t1_existing", "t1_target"), root.children.map(Comment::id))
    }

    @Test
    fun exactCommentNavigationSelectsOnlyMoreNodeContainingTarget() {
        val unrelated = more(
            id = "more:t1_root:unrelated",
            childIds = listOf("unrelated"),
        )
        val target = more(
            id = "more:t1_root:target",
            childIds = listOf("target", "other"),
        )
        val comments = listOf(
            comment(
                "t1_root",
                children = listOf(unrelated, target),
            ),
        )

        assertEquals(
            target.id,
            targetPendingMoreCommentNode(
                comments = comments,
                focusedCommentId = "t1_target",
                loadingMoreNodeId = null,
                blockedMoreNodeIds = emptySet(),
            )?.id,
        )
    }

    @Test
    fun exactCommentNavigationSkipsBlockedTargetMoreNode() {
        val target = more(
            id = "more:t1_root:target",
            childIds = listOf("target"),
        )
        val comments = listOf(comment("t1_root", children = listOf(target)))

        assertNull(
            targetPendingMoreCommentNode(
                comments = comments,
                focusedCommentId = "t1_target",
                loadingMoreNodeId = null,
                blockedMoreNodeIds = setOf(target.id),
            ),
        )
    }

    @Test
    fun focusedCommentLazyListIndexIncludesDynamicPrefix() {
        val comments = listOf(
            comment("t1_first"),
            comment(
                "t1_second",
                children = listOf(comment("t1_target")),
            ),
        )
        val rows = visibleCommentRows(comments, collapsedCommentIds = emptySet())

        assertEquals(
            5,
            focusedCommentLazyListIndex(
                rows = rows,
                focusedCommentId = "t1_target",
                addCommentVisible = false,
                liveStatusVisible = false,
            ),
        )
        assertEquals(
            6,
            focusedCommentLazyListIndex(
                rows = rows,
                focusedCommentId = "t1_target",
                addCommentVisible = true,
                liveStatusVisible = false,
            ),
        )
        assertEquals(
            7,
            focusedCommentLazyListIndex(
                rows = rows,
                focusedCommentId = "t1_target",
                addCommentVisible = true,
                liveStatusVisible = true,
            ),
        )
    }

    @Test
    fun focusedMergeDoesNotDuplicateAnAlreadyPresentTarget() {
        val target = comment("t1_target")
        val current = listOf(comment("t1_root", children = listOf(target)))
        val focused = listOf(comment("t1_root", children = listOf(target)))

        val merged = mergeFocusedCommentContext(current, focused)
        val children = merged.single().children

        assertEquals(1, children.count { it.id == target.id })
        assertTrue(children.single().id == target.id)
    }

    private fun more(
        id: String,
        childIds: List<String>,
    ): Comment = Comment(
        id = id,
        author = "More replies",
        age = "",
        body = "",
        score = 0,
        depth = 1,
        accent = 0L,
        parentId = "t1_root",
        moreChildIds = childIds,
        moreCount = childIds.size,
    )

    private fun comment(
        id: String,
        children: List<Comment> = emptyList(),
    ): Comment = Comment(
        id = id,
        author = "author",
        age = "1m",
        body = id,
        score = 1,
        depth = 0,
        accent = 0L,
        children = children,
    )
}
