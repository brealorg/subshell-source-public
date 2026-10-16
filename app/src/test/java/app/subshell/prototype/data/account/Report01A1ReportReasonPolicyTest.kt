package app.subshell.prototype.data.account

import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Report01A1ReportReasonPolicyTest {
    private val snapshot = CommunityInfoSnapshot(
        communityName = "android",
        freeFormReports = false,
        rules = listOf(
            CommunityRule("Posts", kind = "link", violationReason = "Post violation"),
            CommunityRule("Comments", kind = "comment", violationReason = "Comment violation"),
            CommunityRule("All", kind = "all", violationReason = "Shared violation"),
        ),
    )

    @Test fun targetKindsUseOnlyApplicableViolationReasons() {
        assertEquals(listOf("Post violation", "Shared violation"),
            snapshot.toRedditReportReasonPolicy(RedditReportTargetKind.POST).options.map { it.wireReason })
        assertEquals(listOf("Comment violation", "Shared violation"),
            snapshot.toRedditReportReasonPolicy(RedditReportTargetKind.COMMENT).options.map { it.wireReason })
    }

    @Test fun freeFormFailsClosedUnlessExplicitlyTrue() {
        assertFalse(snapshot.toRedditReportReasonPolicy(RedditReportTargetKind.POST).freeFormReportsAllowed)
        assertFalse(snapshot.copy(freeFormReports = null).toRedditReportReasonPolicy(RedditReportTargetKind.POST).freeFormReportsAllowed)
        assertTrue(snapshot.copy(freeFormReports = true).toRedditReportReasonPolicy(RedditReportTargetKind.POST).freeFormReportsAllowed)
    }
}
