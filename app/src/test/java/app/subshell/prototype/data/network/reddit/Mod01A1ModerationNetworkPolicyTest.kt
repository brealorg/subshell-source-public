package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Mod01A1ModerationNetworkPolicyTest {
    @Test
    fun moderatorDirectoryUsesCanonicalRelationshipListingAndAfterCursor() {
        val first = redditModeratedCommunitiesRequest()
        assertEquals(
            listOf("subreddits", "mine", "moderator"),
            first.pathSegments,
        )
        assertEquals(
            listOf("limit" to "100", "raw_json" to "1"),
            first.queryParameters,
        )

        val next = redditModeratedCommunitiesRequest("t5_next")
        assertEquals(
            listOf(
                "limit" to "100",
                "raw_json" to "1",
                "after" to "t5_next",
            ),
            next.queryParameters,
        )
    }

    @Test
    fun moderatorDirectoryProjectionNormalizesNamesAndPreservesPaging() {
        val dto = SubshellNetworkJson.providerJson.decodeFromString(
            RedditModerationListingDto.serializer(),
            """
            {
              "data": {
                "after": "t5_next",
                "children": [
                  {"kind":"t5","data":{"display_name":"Norge"}},
                  {"kind":"t5","data":{"display_name":"android"}},
                  {"kind":"t5","data":{"display_name":"norge"}},
                  {"kind":"t5","data":{"display_name":"   "}}
                ]
              }
            }
            """.trimIndent(),
        )
        val page = dto.toModeratedCommunityPage()
        assertEquals(listOf("norge", "android"), page.items)
        assertEquals("t5_next", page.nextCursor)
    }

    @Test
    fun modQueueRequestAndProjectionSupportPostsCommentsReportsAndPaging() {
        val request = redditModQueueRequest(" /r/Norge ", "t3_after")
        assertEquals(
            listOf("r", "norge", "about", "modqueue"),
            request.pathSegments,
        )
        assertEquals(
            listOf(
                "limit" to "50",
                "raw_json" to "1",
                "after" to "t3_after",
            ),
            request.queryParameters,
        )

        val dto = SubshellNetworkJson.providerJson.decodeFromString(
            RedditModerationListingDto.serializer(),
            """
            {
              "data": {
                "after": "t1_next",
                "children": [
                  {
                    "kind":"t3",
                    "data":{
                      "name":"t3_abc123",
                      "author":"poster",
                      "subreddit":"Norge",
                      "title":"  Reported post title  ",
                      "num_reports":3,
                      "permalink":"/r/norge/comments/abc123/test/"
                    }
                  },
                  {
                    "kind":"t1",
                    "data":{
                      "name":"t1_def456",
                      "author":"commenter",
                      "subreddit":"norge",
                      "body":"Reported   comment body",
                      "user_reports":[["spam",1]],
                      "mod_reports":[["rule", "mod"]]
                    }
                  },
                  {
                    "kind":"t3",
                    "data":{
                      "name":"t3_other",
                      "author":"foreign",
                      "subreddit":"android",
                      "title":"Must be excluded",
                      "num_reports":99
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val page = dto.toModeratorQueuePage("norge")
        assertEquals(2, page.items.size)
        assertEquals(RedditModeratorQueueKind.POST, page.items[0].kind)
        assertEquals("Reported post title", page.items[0].summary)
        assertEquals(3, page.items[0].reportCount)
        assertEquals(RedditModeratorQueueKind.COMMENT, page.items[1].kind)
        assertEquals("Reported comment body", page.items[1].summary)
        assertEquals(2, page.items[1].reportCount)
        assertEquals("t1_next", page.nextCursor)
    }

    @Test
    fun blankAfterCursorIsNotSent() {
        assertNull(
            redditModQueueRequest("norge", "   ")
                .queryParameters
                .firstOrNull { it.first == "after" },
        )
    }
}
