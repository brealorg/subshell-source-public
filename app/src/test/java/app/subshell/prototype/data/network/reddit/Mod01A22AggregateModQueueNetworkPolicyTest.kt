package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.REDDIT_MODERATION_AGGREGATE_QUEUE
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mod01A22AggregateModQueueNetworkPolicyTest {
    @Test
    fun aggregateModQueueUsesRModAndPreservesMixedCommunityItems() {
        val request = redditModQueueRequest(
            REDDIT_MODERATION_AGGREGATE_QUEUE,
            "t1_after",
        )
        assertEquals(
            listOf("r", "mod", "about", "modqueue"),
            request.pathSegments,
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
                      "name":"t3_post123",
                      "author":"poster",
                      "subreddit":"subshell",
                      "title":"Reported post",
                      "num_reports":1,
                      "permalink":"/r/subshell/comments/post123/title/"
                    }
                  },
                  {
                    "kind":"t1",
                    "data":{
                      "name":"t1_comment456",
                      "author":"commenter",
                      "subreddit":"android",
                      "body":"Reported comment",
                      "num_reports":2,
                      "permalink":"/r/android/comments/post789/title/comment456/"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val aggregate = dto.toModeratorQueuePage(
            REDDIT_MODERATION_AGGREGATE_QUEUE,
        )
        assertEquals(2, aggregate.items.size)
        assertEquals(
            listOf("subshell", "android"),
            aggregate.items.map { item -> item.communityName },
        )
        assertEquals(RedditModeratorQueueKind.POST, aggregate.items[0].kind)
        assertEquals(RedditModeratorQueueKind.COMMENT, aggregate.items[1].kind)
        assertEquals("t1_next", aggregate.nextCursor)

        val communityOnly = dto.toModeratorQueuePage("subshell")
        assertEquals(1, communityOnly.items.size)
        assertTrue(communityOnly.items.all { item -> item.communityName == "subshell" })
    }
    @Test
    fun moderatorCommentPreviewHidesRedditImageProcessingPlaceholders() {
        assertEquals(
            "Mohahahah · Image",
            moderationQueueSummary(
                raw = "Mohahahah *Processing img 8lurpf69nlh1...*",
                kind = RedditModeratorQueueKind.COMMENT,
            ),
        )
        assertEquals(
            "Image comment",
            moderationQueueSummary(
                raw = "*Processing img 8lurpf69nlh1...*",
                kind = RedditModeratorQueueKind.COMMENT,
            ),
        )
        assertEquals(
            "Text · Image",
            moderationQueueSummary(
                raw = "Text ![img](preview-token)",
                kind = RedditModeratorQueueKind.COMMENT,
            ),
        )
        assertEquals(
            "Processing img should remain a post title",
            moderationQueueSummary(
                raw = "Processing img should remain a post title",
                kind = RedditModeratorQueueKind.POST,
            ),
        )
    }

}
