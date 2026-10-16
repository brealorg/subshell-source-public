package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Test

class Report01A1CommunityReportMetadataTest {
    @Test fun aboutCarriesFreeFormReports() {
        val mapped = RedditCommunitySidebarDto(
            data = RedditCommunitySidebarDataDto(description = " About ", freeFormReports = false),
        ).toSidebarInfo()
        assertEquals("About", mapped.markdown)
        assertEquals(false, mapped.freeFormReports)
    }

    @Test fun rulesCarryViolationReason() {
        val mapped = RedditCommunityRulesDto(rules = listOf(
            RedditCommunityRuleDto(kind = "link", shortName = "Rule 1", violationReason = "Rule 1 - Off topic"),
        )).toCommunityRules()
        assertEquals("Rule 1 - Off topic", mapped.single().violationReason)
    }
}
