package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Polls01A1ExternalPollReboundPolicyTest {
    @Test
    fun samePostCommentsRedirectWithinWindowIsConsumed() {
        assertTrue(
            shouldConsumeRedditExternalPollRebound(
                pendingPostId = "t3_1w1ka8j",
                pendingStartedAtEpochMillis = 1_000L,
                rawUri = "https://www.reddit.com/r/polls/comments/1w1ka8j/title/",
                nowEpochMillis = 30_000L,
            ),
        )
    }

    @Test
    fun canonicalCommentsShapeForSamePostIsConsumed() {
        assertTrue(
            shouldConsumeRedditExternalPollRebound(
                pendingPostId = "1w1ka8j",
                pendingStartedAtEpochMillis = 10_000L,
                rawUri = "https://www.reddit.com/comments/1w1ka8j/title/",
                nowEpochMillis = 20_000L,
            ),
        )
    }

    @Test
    fun differentPostIsNeverConsumed() {
        assertFalse(
            shouldConsumeRedditExternalPollRebound(
                pendingPostId = "t3_1w1ka8j",
                pendingStartedAtEpochMillis = 1_000L,
                rawUri = "https://www.reddit.com/r/polls/comments/abcdef/other/",
                nowEpochMillis = 2_000L,
            ),
        )
    }

    @Test
    fun expiredMarkerIsNeverConsumed() {
        assertFalse(
            shouldConsumeRedditExternalPollRebound(
                pendingPostId = "t3_1w1ka8j",
                pendingStartedAtEpochMillis = 1_000L,
                rawUri = "https://www.reddit.com/comments/1w1ka8j/title/",
                nowEpochMillis = 61_001L,
            ),
        )
    }

    @Test
    fun futureClockMarkerIsRejected() {
        assertFalse(
            shouldConsumeRedditExternalPollRebound(
                pendingPostId = "t3_1w1ka8j",
                pendingStartedAtEpochMillis = 20_000L,
                rawUri = "https://www.reddit.com/comments/1w1ka8j/title/",
                nowEpochMillis = 19_999L,
            ),
        )
    }

    @Test
    fun communityAndUserLinksAreNeverConsumed() {
        assertFalse(
            shouldConsumeRedditExternalPollRebound(
                pendingPostId = "t3_1w1ka8j",
                pendingStartedAtEpochMillis = 1_000L,
                rawUri = "https://www.reddit.com/r/polls/",
                nowEpochMillis = 2_000L,
            ),
        )
        assertFalse(
            shouldConsumeRedditExternalPollRebound(
                pendingPostId = "t3_1w1ka8j",
                pendingStartedAtEpochMillis = 1_000L,
                rawUri = "https://www.reddit.com/user/example/",
                nowEpochMillis = 2_000L,
            ),
        )
    }
}
