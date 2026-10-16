package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SUBMITREADBACK02 (#135): the dispatch time comes from the phone's clock and
 * created_utc from Reddit's. With the phone 2 minutes fast, the real post looked
 * older than the dispatch: the matcher missed it and the absence check said
 * NOT_POSTED, so Retry sent a duplicate. Both now look back a clock-skew margin.
 */
class SubmitReadback02ClockSkewTest {
    // The phone said 1 000 s; Reddit stamped the post 120 s "earlier".
    private val dispatchedAtEpochMs = 1_000_000L
    private val realPost = candidate(id = "real", createdUtc = 880L)
    private val olderPost = candidate(id = "older", createdUtc = 100L, title = "Yesterday's post")

    @Test
    fun aPostStampedBeforeAFastPhoneClockIsStillRecognised() {
        val selected = selectRedditSubmitReconciliationCandidate(
            request = request(),
            username = "brealorg",
            dispatchedAtEpochMs = dispatchedAtEpochMs,
            candidates = listOf(realPost, olderPost),
        )
        assertEquals("real", selected?.id)
    }

    @Test
    fun aPostStampedBeforeAFastPhoneClockIsNeverCalledNotPosted() {
        assertFalse(
            redditSubmitReconciliationShowsNoAttempt(
                request = request(),
                username = "brealorg",
                dispatchedAtEpochMs = dispatchedAtEpochMs,
                listing = RedditSubmitReconciliationListing(listOf(realPost, olderPost), reachesEnd = false),
            ),
        )
        // Without a same-title post near the dispatch, an older post still proves absence.
        assertTrue(
            redditSubmitReconciliationShowsNoAttempt(
                request = request(),
                username = "brealorg",
                dispatchedAtEpochMs = dispatchedAtEpochMs,
                listing = RedditSubmitReconciliationListing(listOf(olderPost), reachesEnd = false),
            ),
        )
    }

    private fun request() = AuthenticatedTextPostSubmitRequest(
        accountStableId = "reddit:test:t2_user",
        community = "subshell",
        title = "Composer09 proof",
        text = "Outcome reconciliation body",
        requestKey = "request-key",
    )

    private fun candidate(id: String, createdUtc: Long, title: String = "Composer09 proof") =
        RedditSubmitReconciliationCandidate(
            fullname = "t3_$id",
            id = id,
            community = "subshell",
            author = "brealorg",
            title = title,
            text = "Outcome reconciliation body",
            createdUtcEpochSeconds = createdUtc,
            permalink = "/r/subshell/comments/$id/",
            destinationUrl = null,
            kind = RedditSubmitReconciliationKind.TEXT,
            flairText = null,
            nsfw = false,
            spoiler = false,
        )
}
