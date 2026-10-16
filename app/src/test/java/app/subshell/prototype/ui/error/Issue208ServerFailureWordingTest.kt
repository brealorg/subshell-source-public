package app.subshell.prototype.ui.error

import app.subshell.prototype.data.account.CustomFeedPostsFailureKind
import app.subshell.prototype.data.account.RedditCommentUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditInboxUiFailureKind
import app.subshell.prototype.data.error.FailureDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1 (#208): a read that fails
 * with HTTP 5xx says Reddit had a server problem, keeps Retry, and a 4xx
 * still reads as a rejection.
 */
class Issue208ServerFailureWordingTest {
    private fun details(status: Int) = FailureDetails(retryable = status >= 500, httpStatusCode = status)

    @Test
    fun aServerErrorIsAServerProblemWithRetry() {
        val feed = redditFeedErrorPresentation(RedditFeedUiFailureKind.HTTP, details(500), true, false)
        assertEquals(REDDIT_READ_SERVER_PROBLEM_REASON, feed.message)
        assertNotNull(feed.primaryAction)

        val comments = redditCommentsErrorPresentation(RedditCommentUiFailureKind.HTTP, details(503), true, true)
        assertTrue(comments.message, comments.message.startsWith(REDDIT_READ_SERVER_PROBLEM_REASON))

        val inbox = redditInboxErrorPresentation(RedditInboxUiFailureKind.HTTP, details(502), true, false)
        assertEquals(REDDIT_READ_SERVER_PROBLEM_REASON, inbox.message)

        val customFeed = customFeedPostsErrorPresentation(CustomFeedPostsFailureKind.HTTP, 504, true, append = true)
        assertTrue(customFeed.message, customFeed.message.startsWith(REDDIT_READ_SERVER_PROBLEM_REASON))
    }

    @Test
    fun aClientErrorIsStillARejectionAndALimitSaysSo() {
        assertEquals(
            "Reddit rejected the feed request.",
            redditFeedErrorPresentation(RedditFeedUiFailureKind.HTTP, details(403), false, false).message,
        )
        assertEquals(
            "Reddit returned HTTP 403. Loaded posts remain visible.",
            customFeedPostsErrorPresentation(CustomFeedPostsFailureKind.HTTP, 403, false, append = true).message,
        )
        assertEquals(REDDIT_READ_RATE_LIMIT_REASON, redditReadHttpReason(429, "rejected"))
        assertEquals("rejected", redditReadHttpReason(null, "rejected"))
    }
}
