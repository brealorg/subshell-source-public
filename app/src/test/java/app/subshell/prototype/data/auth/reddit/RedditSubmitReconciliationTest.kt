package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditSubmitReconciliationTest {
    @Test
    fun uniqueRecentExactMatchIsSelected() {
        val selected = selectRedditSubmitReconciliationCandidate(
            request = request(),
            username = "brealorg",
            dispatchedAtEpochMs = 1_000_000L,
            candidates = listOf(candidate(createdUtc = 1_005L)),
        )

        assertEquals("abc123", selected?.id)
    }

    // DATALAYER11: an older submission the normalisers reject (a title with a
    // newline) is not a match; before, it threw out of the whole readback.
    @Test
    fun aCandidateThatCannotBeNormalisedDoesNotHideTheMatch() {
        val selected = selectRedditSubmitReconciliationCandidate(
            request = request(),
            username = "brealorg",
            dispatchedAtEpochMs = 1_000_000L,
            candidates = listOf(
                candidate(id = "old999", createdUtc = 1_001L).copy(title = "two\nlines"),
                candidate(id = "abc123", createdUtc = 1_005L),
            ),
        )

        assertEquals("abc123", selected?.id)
    }

    @Test
    fun ambiguousRecentMatchesAreNotSelected() {
        val selected = selectRedditSubmitReconciliationCandidate(
            request = request(),
            username = "brealorg",
            dispatchedAtEpochMs = 1_000_000L,
            candidates = listOf(
                candidate(id = "abc123", createdUtc = 1_005L),
                candidate(id = "abc124", createdUtc = 1_010L),
            ),
        )

        assertNull(selected)
    }

    @Test
    fun olderCandidateOutsideDispatchWindowIsRejected() {
        val selected = selectRedditSubmitReconciliationCandidate(
            request = request(),
            username = "brealorg",
            dispatchedAtEpochMs = 1_000_000L,
            // SUBMITREADBACK02: older than the dispatch minus the clock-skew margin (600 s).
            candidates = listOf(candidate(createdUtc = 399L)),
        )

        assertNull(selected)
    }

    @Test
    fun linkDestinationMustMatch() {
        val selected = selectRedditSubmitReconciliationCandidate(
            request = request(linkUrl = "https://example.com/right"),
            username = "brealorg",
            dispatchedAtEpochMs = 1_000_000L,
            candidates = listOf(
                candidate(
                    createdUtc = 1_005L,
                    kind = RedditSubmitReconciliationKind.LINK,
                    destinationUrl = "https://example.com/wrong",
                ),
            ),
        )

        assertNull(selected)
    }

    private fun request(
        linkUrl: String? = null,
    ): AuthenticatedTextPostSubmitRequest = AuthenticatedTextPostSubmitRequest(
        accountStableId = "reddit:test:t2_user",
        community = "subshell",
        title = "Composer09 proof",
        text = "Outcome reconciliation body",
        linkUrl = linkUrl,
        requestKey = "request-key",
    )

    private fun candidate(
        id: String = "abc123",
        createdUtc: Long,
        kind: RedditSubmitReconciliationKind = RedditSubmitReconciliationKind.TEXT,
        destinationUrl: String? = null,
    ): RedditSubmitReconciliationCandidate = RedditSubmitReconciliationCandidate(
        fullname = "t3_$id",
        id = id,
        community = "subshell",
        author = "brealorg",
        title = "Composer09 proof",
        text = "Outcome reconciliation body",
        createdUtcEpochSeconds = createdUtc,
        permalink = "/r/subshell/comments/$id/",
        destinationUrl = destinationUrl,
        kind = kind,
        flairText = null,
        nsfw = false,
        spoiler = false,
    )
}
