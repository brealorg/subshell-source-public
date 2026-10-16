package app.subshell.prototype.data

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a6AccountHistoryQueryIdentityTest {
    @Test
    fun postHistoryQueryIdentityIsAccountUserAndKindScoped() {
        val upvotedA = redditAccountHistoryPostQueryKey(
            accountStableId = "reddit:a",
            username = "BrealOrg",
            kind = RedditAccountHistoryKind.UPVOTED,
        )
        val downvotedA = redditAccountHistoryPostQueryKey(
            accountStableId = "reddit:a",
            username = "BrealOrg",
            kind = RedditAccountHistoryKind.DOWNVOTED,
        )
        val upvotedB = redditAccountHistoryPostQueryKey(
            accountStableId = "reddit:b",
            username = "BrealOrg",
            kind = RedditAccountHistoryKind.UPVOTED,
        )

        assertTrue(upvotedA.isAuthenticatedAccountScoped)
        assertTrue(downvotedA.isAuthenticatedAccountScoped)
        assertTrue(upvotedB.isAuthenticatedAccountScoped)
        assertNotEquals(upvotedA, downvotedA)
        assertNotEquals(upvotedA, upvotedB)
    }

    @Test
    fun commentHistoryUsesTheSameAccountUserAndKindIdentityFamily() {
        val comments = redditAccountHistoryQueryKey(
            accountStableId = "reddit:a",
            username = "BrealOrg",
            kind = RedditAccountHistoryKind.COMMENTS,
        )
        val savedComments = redditAccountHistoryQueryKey(
            accountStableId = "reddit:a",
            username = "BrealOrg",
            kind = RedditAccountHistoryKind.SAVED_COMMENTS,
        )
        val commentsOtherAccount = redditAccountHistoryQueryKey(
            accountStableId = "reddit:b",
            username = "BrealOrg",
            kind = RedditAccountHistoryKind.COMMENTS,
        )

        assertTrue(comments.isAuthenticatedAccountScoped)
        assertNotEquals(comments, savedComments)
        assertNotEquals(comments, commentsOtherAccount)
        assertThrows(IllegalArgumentException::class.java) {
            redditAccountHistoryPostQueryKey(
                accountStableId = "reddit:a",
                username = "BrealOrg",
                kind = RedditAccountHistoryKind.COMMENTS,
            )
        }
    }

}
