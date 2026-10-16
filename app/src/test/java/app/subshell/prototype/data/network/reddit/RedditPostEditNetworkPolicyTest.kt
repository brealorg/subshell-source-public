package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RedditPostEditNetworkPolicyTest {
    @Test
    fun selfPostEditUsesEditusertextWithCanonicalT3ThingId() {
        val request = redditPostEditFormRequest(
            postFullname = "t3_abc123",
            text = "updated body",
        )

        assertEquals(listOf("api", "editusertext"), request.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "raw_json" to "1",
                "thing_id" to "t3_abc123",
                "text" to "updated body",
            ),
            request.fields,
        )
    }

    @Test
    fun postEditRejectsCommentFullname() {
        try {
            redditPostEditFormRequest(
                postFullname = "t1_abc123",
                text = "body",
            )
            fail("Expected comment fullname to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }
}
