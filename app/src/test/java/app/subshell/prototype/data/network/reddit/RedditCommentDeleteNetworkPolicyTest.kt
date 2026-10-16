package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditCommentDeleteNetworkPolicyTest {
    @Test
    fun deleteUsesDocumentedRedditEndpointAndCanonicalIdField() {
        val request = redditCommentDeleteFormRequest("t1_abc123")

        assertEquals(listOf("api", "del"), request.pathSegments)
        assertEquals(listOf("id" to "t1_abc123"), request.fields)
    }

    @Test
    fun deleteRejectsNonCommentFullnames() {
        val failure = runCatching {
            redditCommentDeleteFormRequest("t3_abc123")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun readbackUsesTargetedApiInfoLookup() {
        val request = redditCommentDeleteInfoRequest("t1_abc123")

        assertEquals(listOf("api", "info"), request.pathSegments)
        assertEquals(
            listOf(
                "raw_json" to "1",
                "id" to "t1_abc123",
            ),
            request.queryParameters,
        )
    }

    @Test
    fun emptyInfoListingMeansTargetIsAbsent() {
        assertEquals(
            RedditCommentDeleteReadbackState.ABSENT,
            redditCommentDeleteReadbackState(
                listing = RedditCommentListingDto(),
                targetFullname = "t1_abc123",
            ),
        )
    }

    @Test
    fun deletedPlaceholderConfirmsDeletion() {
        val listing = listing(
            fullname = "t1_abc123",
            author = null,
            body = "[deleted]",
        )

        assertEquals(
            RedditCommentDeleteReadbackState.DELETED_PLACEHOLDER,
            redditCommentDeleteReadbackState(
                listing = listing,
                targetFullname = "t1_abc123",
            ),
        )
    }

    @Test
    fun normalCommentMeansDeleteOutcomeIsNotConfirmed() {
        val listing = listing(
            fullname = "t1_abc123",
            author = "brealorg",
            body = "still here",
        )

        assertEquals(
            RedditCommentDeleteReadbackState.PRESENT,
            redditCommentDeleteReadbackState(
                listing = listing,
                targetFullname = "t1_abc123",
            ),
        )
    }

    @Test
    fun unrelatedInfoThingFailsClosed() {
        val listing = listing(
            fullname = "t1_other",
            author = "brealorg",
            body = "other",
        )

        assertEquals(
            RedditCommentDeleteReadbackState.MALFORMED,
            redditCommentDeleteReadbackState(
                listing = listing,
                targetFullname = "t1_abc123",
            ),
        )
    }

    private fun listing(
        fullname: String,
        author: String?,
        body: String,
    ): RedditCommentListingDto =
        RedditCommentListingDto(
            data = RedditCommentListingDataDto(
                children = listOf(
                    RedditCommentThingDto(
                        kind = "t1",
                        data = RedditCommentDataDto(
                            id = fullname.removePrefix("t1_"),
                            name = fullname,
                            author = author,
                            body = body,
                        ),
                    ),
                ),
            ),
        )
}
