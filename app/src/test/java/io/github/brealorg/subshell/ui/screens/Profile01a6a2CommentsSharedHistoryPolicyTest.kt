package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.account.RedditAccountHistoryUiState
import io.github.brealorg.subshell.data.redditAccountHistoryQueryKey
import io.github.brealorg.subshell.model.ProfileCommentFixture
import io.github.brealorg.subshell.model.ThreadPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a6a2CommentsSharedHistoryPolicyTest {
    @Test
    fun commentsAreRepresentedByTheSharedAccountHistoryState() {
        val queryKey = redditAccountHistoryQueryKey(
            accountStableId = "reddit:account",
            username = "BrealOrg",
            kind = RedditAccountHistoryKind.COMMENTS,
        )
        val comment = ProfileCommentFixture(
            id = "t1_example",
            author = "brealorg",
            postId = "t3_example",
            age = "1m",
            body = "Shared history comment",
            score = 2,
            placement = ThreadPlacement.MIDDLE,
            community = "subshell",
            postTitle = "Account history",
        )
        val state = RedditAccountHistoryUiState(
            accountStableId = "reddit:account",
            username = "brealorg",
            kind = RedditAccountHistoryKind.COMMENTS,
            queryKey = queryKey,
            comments = listOf(comment),
            nextCursor = "t1_after",
            fromCache = true,
            loaded = true,
            contentRevision = 4L,
        )

        assertTrue(
            state.matches(
                accountStableId = "reddit:account",
                username = "u/BREALORG",
                kind = RedditAccountHistoryKind.COMMENTS,
            ),
        )
        assertEquals(listOf(comment), state.comments)
        assertEquals("t1_after", state.nextCursor)
        assertTrue(state.fromCache)
        assertTrue(state.loaded)
    }

    @Test
    fun commentsAndSavedCommentsRemainCommentHistoryKinds() {
        assertFalse(RedditAccountHistoryKind.COMMENTS.isPostHistory)
        assertFalse(RedditAccountHistoryKind.SAVED_COMMENTS.isPostHistory)
        assertTrue(RedditAccountHistoryKind.UPVOTED.isPostHistory)
    }
}
