package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Test

class RedditCommentEditNetworkPolicyTest {
    @Test
    fun editUsesRedditEditUserTextWithCanonicalCommentFullname() {
        val request = redditCommentEditFormRequest(
            commentFullname = "T1_AbC123",
            text = "  edited body  ",
        )

        assertEquals(listOf("api", "editusertext"), request.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "raw_json" to "1",
                "thing_id" to "t1_abc123",
                "text" to "edited body",
            ),
            request.fields,
        )
    }

    @Test
    fun redditApiErrorsPreserveCodeMessageAndField() {
        val errors = parseRedditCommentEditApiErrors(
            listOf(
                JsonArray(
                    listOf(
                        JsonPrimitive("TOO_OLD"),
                        JsonPrimitive("This comment can no longer be edited."),
                        JsonPrimitive("thing_id"),
                    ),
                ),
            ),
        )

        assertEquals(1, errors.size)
        assertEquals("TOO_OLD", errors.single().code)
        assertEquals("This comment can no longer be edited.", errors.single().message)
        assertEquals("thing_id", errors.single().field)
    }

    @Test
    fun editRejectsBlankBody() {
        assertThrows(IllegalArgumentException::class.java) {
            redditCommentEditFormRequest(
                commentFullname = "t1_abc123",
                text = "   ",
            )
        }
    }
}
