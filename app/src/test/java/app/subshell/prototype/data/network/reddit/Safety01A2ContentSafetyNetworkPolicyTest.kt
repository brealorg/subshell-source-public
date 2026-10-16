package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Safety01A2ContentSafetyNetworkPolicyTest {
    @Test
    fun hideUnhideAndReportRetainCanonicalA1RequestShapes() {
        assertEquals(
            listOf("api", "hide"),
            redditPostVisibilityFormRequest(
                RedditPostVisibilityRequest(
                    accountStableId = "acct",
                    postFullname = "t3_post1",
                    hidden = true,
                ),
            ).pathSegments,
        )
        assertEquals(
            listOf("api", "unhide"),
            redditPostVisibilityFormRequest(
                RedditPostVisibilityRequest(
                    accountStableId = "acct",
                    postFullname = "t3_post1",
                    hidden = false,
                ),
            ).pathSegments,
        )
        val report = redditContentReportFormRequest(
            RedditContentReportRequest(
                accountStableId = "acct",
                thingFullname = "t1_comment1",
                reason = "Spam",
            ),
        )
        assertEquals(listOf("api", "report"), report.pathSegments)
        assertEquals("t1_comment1", report.fields.toMap()["id"])
    }

    @Test
    fun reportErrorsAreBoundedAndSanitized() {
        val parsed = parseRedditContentReportApiErrors(
            listOf(
                JsonArray(
                    listOf(
                        JsonPrimitive("report_error"),
                        JsonPrimitive("Provider message\nwith control"),
                        JsonPrimitive("reason"),
                    ),
                ),
            ),
        )

        assertEquals(1, parsed.size)
        assertEquals("REPORT_ERROR", parsed.single().code)
        assertEquals("Provider messagewith control", parsed.single().message)
        assertEquals("reason", parsed.single().field)

        val malformed = parseRedditContentReportApiErrors(
            listOf(JsonPrimitive("not a tuple")),
        )
        assertTrue(malformed.isEmpty())
    }
}
