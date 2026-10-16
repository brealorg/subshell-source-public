package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SUBMITREADBACK01 (#141): a post sent without NSFW/spoiler and with one flair
 * may come back marked NSFW (an NSFW community), spoilered, or re-flaired by
 * AutoModerator. The read-back still recognises it, so the attempt resolves
 * instead of blocking the account's later posts for good.
 */
class SubmitReadback01MutableMarksTest {
    @Test
    fun aPostRedditMarkedOrReflairedIsStillRecognised() {
        listOf(
            candidate(nsfw = true),
            candidate(spoiler = true),
            candidate(flairText = "Removed: rule 2"),
            candidate(flairText = null),
            candidate(nsfw = true, spoiler = true, flairText = "Other"),
        ).forEach { candidate ->
            assertEquals("$candidate", "abc123", select(candidate)?.id)
        }
    }

    @Test
    fun anotherBodyOrTitleIsStillNotTheAttempt() {
        assertNull(select(candidate(nsfw = true).copy(text = "A different body")))
        assertNull(select(candidate(nsfw = true).copy(title = "A different title")))
    }

    private fun select(candidate: RedditSubmitReconciliationCandidate) =
        selectRedditSubmitReconciliationCandidate(
            request = AuthenticatedTextPostSubmitRequest(
                accountStableId = "reddit:test:t2_user",
                community = "subshell",
                title = "Composer09 proof",
                text = "Outcome reconciliation body",
                flairText = "News",
                nsfw = false,
                spoiler = false,
                requestKey = "request-key",
            ),
            username = "brealorg",
            dispatchedAtEpochMs = 1_000_000L,
            candidates = listOf(candidate),
        )

    private fun candidate(
        nsfw: Boolean = false,
        spoiler: Boolean = false,
        flairText: String? = "News",
    ) = RedditSubmitReconciliationCandidate(
        fullname = "t3_abc123",
        id = "abc123",
        community = "subshell",
        author = "brealorg",
        title = "Composer09 proof",
        text = "Outcome reconciliation body",
        createdUtcEpochSeconds = 1_005L,
        permalink = "/r/subshell/comments/abc123/",
        destinationUrl = null,
        kind = RedditSubmitReconciliationKind.TEXT,
        flairText = flairText,
        nsfw = nsfw,
        spoiler = spoiler,
    )
}
