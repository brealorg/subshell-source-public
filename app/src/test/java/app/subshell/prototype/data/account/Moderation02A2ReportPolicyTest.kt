package app.subshell.prototype.data.account

import org.junit.Assert.*
import org.junit.Test

class Moderation02A2ReportPolicyTest {
    @Test fun exactPostAndCommentIdsAccepted() {
        assertEquals("t3_abc", moderatorReportTargetOrNull(" t3_abc ")?.fullname)
        assertEquals("t1_def", moderatorReportTargetOrNull("t1_def", "Norge")?.fullname)
        assertEquals("norge", moderatorReportTargetOrNull("t1_def", "Norge")?.communityName)
    }
    @Test fun permalinkKeepsExactCommentNotParentPost() {
        val target = moderatorReportTargetOrNull("https://www.reddit.com/r/norge/comments/abc/title/def/?context=3")
        assertEquals(ModeratorReportTarget("t1_def", "norge"), target)
        assertEquals(ModeratorReportTarget("t3_abc", "norge"), moderatorReportTargetOrNull("/r/norge/comments/abc/title/"))
        assertEquals(ModeratorReportTarget("t3_abc"), moderatorReportTargetOrNull("https://redd.it/abc"))
    }
    @Test fun lookupRejectsWrongHostAmbiguousIdentityAndConflictingCommunity() {
        listOf("https://reddit.com.evil/r/norge/comments/abc/title/", "https://reddit.com@evil/comments/abc", "//evil/comments/abc", "http://reddit.com/comments/abc", "t3_abc,t1_def", "abc", "t2_abc", "https://reddit.com:8443/comments/abc", "/r/norge/comments/abc/title/def/extra", "https://reddit.com/comments/%61bc").forEach {
            assertNull(it, moderatorReportTargetOrNull(it))
        }
        assertNull(moderatorReportTargetOrNull("/r/norge/comments/abc/title/", "android"))
    }
    @Test fun unknownIgnoredStateAndMissingPermissionNeverEnableAWrite() {
        assertNull(reportConfirmationOrNull(state(ignored = null), "owner"))
        assertNull(reportConfirmationOrNull(state(permission = null), "owner"))
        assertNull(reportConfirmationOrNull(state(permission = false), "owner"))
        assertNull(reportConfirmationOrNull(state().copy(loading = true), "owner"))
        assertNull(reportConfirmationOrNull(state().copy(mutating = true), "owner"))
    }
    @Test fun confirmationPinsOwnerTargetActionAndRevision() {
        val first = requireNotNull(reportConfirmationOrNull(state(), "owner"))
        assertEquals(RedditModeratorAction.IGNORE_REPORTS, first.action)
        assertNull(reportConfirmationOrNull(state(), "other"))
        assertFalse(first == reportConfirmationOrNull(state().copy(revision = 2), "owner"))
        assertEquals(RedditModeratorAction.UNIGNORE_REPORTS, reportConfirmationOrNull(state(ignored = true), "owner")?.action)
        assertNull(reportConfirmationOrNull(state().copy(target = ModeratorReportTarget("t3_other", "norge")), "owner"))
    }
    @Test fun mismatchedThingKindCannotCreateAConfirmation() {
        val current = state()
        assertNull(reportConfirmationOrNull(current.copy(item = current.item!!.copy(kind = RedditModeratorQueueKind.COMMENT)), "owner"))
    }
    @Test fun absentCountIsNotShownAsNoReports() {
        val item = requireNotNull(state().item)
        assertEquals("Report count unavailable", moderatorReportCountLabel(item))
        assertEquals("No reports returned", moderatorReportCountLabel(item.copy(reportDetails = item.reportDetails.copy(totalCount = 0))))
    }
    @Test fun reportControlIsSeparateFromContentDecisions() {
        listOf(RedditModeratorAction.APPROVE, RedditModeratorAction.REMOVE, RedditModeratorAction.SPAM).forEach {
            assertFalse(it.isReportControl()); assertNull(it.desiredReportsIgnored())
        }
        assertTrue(RedditModeratorAction.IGNORE_REPORTS.isReportControl())
        assertEquals(false, RedditModeratorAction.UNIGNORE_REPORTS.desiredReportsIgnored())
    }
    private fun state(ignored: Boolean? = false, permission: Boolean? = true): ModeratorReportsUiState {
        val target = ModeratorReportTarget("t3_abc", "norge")
        return ModeratorReportsUiState(
            accountStableId = "owner", visible = true, target = target, revision = 1,
            item = RedditModeratorQueueItem("t3_abc", RedditModeratorQueueKind.POST, "norge", "author", "summary", reportDetails = RedditModeratorReportDetails(ignored = ignored, canModerate = permission)),
        )
    }
}
