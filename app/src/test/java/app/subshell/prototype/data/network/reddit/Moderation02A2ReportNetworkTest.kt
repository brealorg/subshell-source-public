package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.*
import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.*
import org.junit.Test

class Moderation02A2ReportNetworkTest {
    @Test fun reportWritesUseOnlyTheirOwnEndpointAndExactId() {
        listOf("t1_abc", "t3_abc").forEach { id ->
            listOf(RedditModeratorAction.IGNORE_REPORTS to "ignore_reports", RedditModeratorAction.UNIGNORE_REPORTS to "unignore_reports").forEach { (action, endpoint) ->
                val form = redditModerationActionRequest(RedditModeratorActionRequest("owner", "norge", id, action))
                assertEquals(listOf("api", endpoint), form.pathSegments)
                assertEquals(listOf("id" to id), form.fields)
            }
        }
    }
    @Test fun targetLookupDoesNotDependOnModqueueMembership() {
        // ISSUE281_READS_AVOID_API_INFO_V1: read from the post's thread.
        assertEquals(listOf("comments", "p1", "_", "abc"), redditModeratorReportTargetRequest(ModeratorReportTarget("t1_abc", "norge", "t3_p1"), "t3_p1").pathSegments)
        assertEquals(listOf("comments", "abc"), redditModeratorReportTargetRequest(ModeratorReportTarget("t3_abc"), "t3_abc").pathSegments)
    }
    @Test fun actualJsonDtoPreservesReportsAndKnownIgnoredState() {
        val json = """{"data":{"children":[{"kind":"t1","data":{"name":"t1_abc","subreddit":"norge","body":"Reported comment","ignore_reports":true,"can_mod_post":true,"user_reports":[["Spam",3]],"mod_reports":[["Rule 1","mod_a"]]}}]}}"""
        val item = requireNotNull(SubshellNetworkJson.providerJson.decodeFromString(RedditModerationListingDto.serializer(), json).toModeratorReportTarget(ModeratorReportTarget("t1_abc", "norge")))
        assertEquals(4, item.reportDetails.totalCount)
        assertEquals(true, item.reportDetails.ignored)
        assertEquals("Spam", item.reportDetails.userReports?.single()?.reason)
    }
    @Test fun lookupRejectsWrongTargetCommunityKindAndMultipleMatches() {
        val child = RedditModerationThingDto("t1", RedditModerationThingDataDto(name = "t1_abc", subreddit = "norge", body = "comment"))
        fun listing(vararg children: RedditModerationThingDto) = RedditModerationListingDto(RedditModerationListingDataDto(children = children.toList()))
        assertNotNull(listing(child).toModeratorReportTarget(ModeratorReportTarget("t1_abc", "norge")))
        assertNull(listing(child).toModeratorReportTarget(ModeratorReportTarget("t1_other", "norge")))
        assertNull(listing(child).toModeratorReportTarget(ModeratorReportTarget("t1_abc", "android")))
        assertNull(listing(child, child).toModeratorReportTarget(ModeratorReportTarget("t1_abc")))
        assertNull(listing(child.copy(kind = "t3")).toModeratorReportTarget(ModeratorReportTarget("t1_abc")))
    }
}
