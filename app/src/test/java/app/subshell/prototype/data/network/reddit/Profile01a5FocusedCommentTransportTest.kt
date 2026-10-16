package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.RedditCommentSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a5FocusedCommentTransportTest {
    @Test
    fun focusedCommentRequestUsesId36AndEightLevelsOfContext() {
        val request = redditCommentsJsonRequest(
            postId = "abc123",
            sort = RedditCommentSort.BEST,
            focusedCommentId = "t1_def456",
            context = 8,
        )

        val query =
            request.queryParameters.toMap()

        assertEquals(
            "def456",
            query["comment"],
        )
        assertEquals(
            "8",
            query["context"],
        )
    }

    @Test
    fun ordinaryCommentRequestDoesNotBecomePartial() {
        val request = redditCommentsJsonRequest(
            postId = "abc123",
            sort = RedditCommentSort.BEST,
        )

        val query =
            request.queryParameters.toMap()

        assertFalse(
            query.containsKey("comment"),
        )
        assertFalse(
            query.containsKey("context"),
        )
    }

    @Test
    fun focusedCommentIdNormalizationAcceptsIdAndFullname() {
        assertEquals(
            "abc123",
            normalizeRedditFocusedCommentId(
                "abc123",
            ),
        )
        assertEquals(
            "abc123",
            normalizeRedditFocusedCommentId(
                "t1_abc123",
            ),
        )
        assertNull(
            normalizeRedditFocusedCommentId(
                null,
            ),
        )
    }

    @Test
    fun profileCommentMetadataPrefersNamePrefixedCommunity() {
        val data = RedditProfileCommentDataDto(
            subreddit = "fallback",
            subredditNamePrefixed = "r/subshell",
            linkTitle = "Profile A5 target",
        )

        assertEquals(
            "subshell",
            data.profileCommentCommunity(),
        )
        assertEquals(
            "Profile A5 target",
            data.profileCommentPostTitle(),
        )
    }

    @Test
    fun focusedContextRejectsValuesOutsideRedditRange() {
        var failed = false

        try {
            redditCommentsJsonRequest(
                postId = "abc123",
                sort = RedditCommentSort.BEST,
                focusedCommentId = "def456",
                context = 9,
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
    }
}
