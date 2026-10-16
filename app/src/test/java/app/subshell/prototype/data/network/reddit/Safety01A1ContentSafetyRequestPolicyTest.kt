package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.REPORT_COMMUNITY_MAX_CHARS
import app.subshell.prototype.data.account.REPORT_REASON_MAX_CHARS
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditContentSafetyThingKind
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.normalizeRedditSafetyCommentFullname
import app.subshell.prototype.data.account.normalizeRedditSafetyPostFullname
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Safety01A1ContentSafetyRequestPolicyTest {
    @Test
    fun hideAndUnhideArePostOnlyCanonicalReportScopeRequests() {
        val hide = redditPostVisibilityFormRequest(
            RedditPostVisibilityRequest(
                accountStableId = "acct",
                postFullname = "t3_abc123",
                hidden = true,
            ),
        )
        val unhide = redditPostVisibilityFormRequest(
            RedditPostVisibilityRequest(
                accountStableId = "acct",
                postFullname = "t3_abc123",
                hidden = false,
            ),
        )

        assertEquals(listOf("api", "hide"), hide.pathSegments)
        assertEquals(mapOf("id" to "t3_abc123"), hide.fields.toMap())
        assertEquals(listOf("api", "unhide"), unhide.pathSegments)
        assertEquals(mapOf("id" to "t3_abc123"), unhide.fields.toMap())
        assertEquals("t3_rawid", normalizeRedditSafetyPostFullname(" RAWID "))
        assertTrue(
            runCatching { normalizeRedditSafetyPostFullname("t1_comment") }.isFailure,
        )
    }

    @Test
    fun reportAcceptsCanonicalPostsAndCommentsWithBoundedMetadata() {
        val postRequest = redditContentReportFormRequest(
            RedditContentReportRequest(
                accountStableId = "acct",
                thingFullname = "t3_post1",
                reason = "Breaks r/subshell rules",
                communityName = "subshell",
            ),
        )
        val commentRequest = redditContentReportFormRequest(
            RedditContentReportRequest(
                accountStableId = "acct",
                thingFullname = normalizeRedditSafetyCommentFullname("comment1"),
                reason = "Spam",
            ),
        )

        assertEquals(listOf("api", "report"), postRequest.pathSegments)
        val postFields = postRequest.fields.toMap()
        assertEquals("json", postFields["api_type"])
        assertEquals("t3_post1", postFields["id"])
        assertEquals("Breaks r/subshell rules", postFields["reason"])
        assertEquals(setOf("api_type", "id", "reason", "sr_name"), postFields.keys)
        assertEquals("subshell", postFields["sr_name"])

        assertEquals(listOf("api", "report"), commentRequest.pathSegments)
        val commentFields = commentRequest.fields.toMap()
        assertEquals("t1_comment1", commentFields["id"])
        assertEquals("Spam", commentFields["reason"])
        assertEquals(setOf("api_type", "id", "reason"), commentFields.keys)

        assertEquals(
            RedditContentSafetyThingKind.POST,
            RedditContentReportRequest("acct", "t3_post1", "Reason").thingKind,
        )
        assertEquals(
            RedditContentSafetyThingKind.COMMENT,
            RedditContentReportRequest("acct", "t1_comment1", "Reason").thingKind,
        )
    }

    @Test
    fun reportBoundaryFailsClosedForAmbiguousTargetsAndOversizedFields() {
        assertTrue(
            runCatching {
                RedditContentReportRequest(
                    accountStableId = "acct",
                    thingFullname = "raw_without_type",
                    reason = "Reason",
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                RedditContentReportRequest(
                    accountStableId = "acct",
                    thingFullname = "t3_post1",
                    reason = "x".repeat(REPORT_REASON_MAX_CHARS + 1),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                RedditContentReportRequest(
                    accountStableId = "acct",
                    thingFullname = "t3_post1",
                    reason = "Reason",
                    communityName = "x".repeat(REPORT_COMMUNITY_MAX_CHARS + 1),
                )
            }.isFailure,
        )
    }

    // STEP3 (safety01a1): a report reason is at most 100 characters, the
    // limit the report dialog's own text field enforces.
    @Test
    fun aReportReasonIsAtMostOneHundredCharacters() {
        val longest = RedditContentReportRequest(
            accountStableId = "acct",
            thingFullname = "t3_post1",
            reason = "x".repeat(100),
        )
        assertEquals("x".repeat(100), redditContentReportFormRequest(longest).fields.toMap()["reason"])
        assertTrue(
            runCatching {
                RedditContentReportRequest(
                    accountStableId = "acct",
                    thingFullname = "t3_post1",
                    reason = "x".repeat(101),
                )
            }.isFailure,
        )
    }
}
