package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedComments01MixedSavedTransportPolicyTest {
    @Test
    fun mixedSavedRequestUsesOneUntypedSavedListingAndOneOpaqueCursor() {
        val request = redditMixedSavedHistoryJsonRequest(
            username = "u/BRealOrg",
            request = RepositoryPageRequest(
                cursor = "t1_after_mixed",
                limit = 50,
            ),
        )

        assertEquals(listOf("user", "brealorg", "saved"), request.pathSegments)
        val query = request.queryParameters.toMap()
        assertEquals("1", query["raw_json"])
        assertEquals("new", query["sort"])
        assertEquals("50", query["limit"])
        assertEquals("t1_after_mixed", query["after"])
        assertFalse(query.containsKey("type"))
    }

    @Test
    fun oneMixedListingPreservesRedditT3T1T3OrderAndCursor() {
        val json = SubshellNetworkJson.providerJson
        val listing = json.decodeFromString(
            RedditSavedListingDto.serializer(),
            """
            {
              "kind": "Listing",
              "data": {
                "after": "t3_next_cursor",
                "children": [
                  {
                    "kind": "t3",
                    "data": {
                      "id": "PostOne",
                      "name": "t3_PostOne",
                      "subreddit": "subshell",
                      "subreddit_name_prefixed": "r/subshell",
                      "author": "post_author",
                      "created_utc": 900,
                      "title": "First saved post",
                      "score": 10,
                      "num_comments": 2,
                      "saved": true
                    }
                  },
                  {
                    "kind": "t1",
                    "data": {
                      "id": "CommentOne",
                      "name": "t1_CommentOne",
                      "author": "comment_author",
                      "body": "Saved comment body",
                      "score": 7,
                      "created_utc": 950,
                      "link_id": "t3_ParentPost",
                      "subreddit": "subshell",
                      "subreddit_name_prefixed": "r/subshell",
                      "link_title": "Parent discussion"
                    }
                  },
                  {
                    "kind": "t3",
                    "data": {
                      "id": "PostTwo",
                      "name": "t3_PostTwo",
                      "subreddit": "android",
                      "author": "second_author",
                      "created_utc": 975,
                      "title": "Second saved post",
                      "score": 4,
                      "num_comments": 1,
                      "saved": true
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val page = listing.toSavedHistoryPage(
            json = json,
            nowEpochSeconds = 1_000L,
        )

        assertEquals("t3_next_cursor", page.nextCursor)
        assertEquals(3, page.items.size)

        val first = page.items[0] as RedditSavedHistoryItem.PostItem
        val second = page.items[1] as RedditSavedHistoryItem.CommentItem
        val third = page.items[2] as RedditSavedHistoryItem.PostItem

        assertEquals("t3_postone", first.fullname)
        assertEquals("First saved post", first.post.title)

        assertEquals("t1_commentone", second.fullname)
        assertEquals("parentpost", second.comment.postId)
        assertEquals("subshell", second.comment.community)
        assertEquals("Parent discussion", second.comment.postTitle)
        assertEquals("Saved comment body", second.comment.body)

        assertEquals("t3_posttwo", third.fullname)
        assertEquals("Second saved post", third.post.title)
    }

    @Test
    fun unsupportedOrInvalidChildrenAreSkippedWithoutSplittingTheCursorChain() {
        val json = SubshellNetworkJson.providerJson
        val listing = json.decodeFromString(
            RedditSavedListingDto.serializer(),
            """
            {
              "data": {
                "after": "opaque_after",
                "children": [
                  {
                    "kind": "t5",
                    "data": {
                      "id": "ignored"
                    }
                  },
                  {
                    "kind": "t1",
                    "data": {
                      "id": "missing_parent",
                      "body": "Cannot navigate without link_id"
                    }
                  },
                  {
                    "kind": "t3",
                    "data": {
                      "id": "valid",
                      "name": "t3_valid",
                      "subreddit": "subshell",
                      "author": "author",
                      "created_utc": 990,
                      "title": "Valid saved post"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val page = listing.toSavedHistoryPage(
            json = json,
            nowEpochSeconds = 1_000L,
        )

        assertEquals("opaque_after", page.nextCursor)
        assertEquals(1, page.items.size)
        assertTrue(page.items.single() is RedditSavedHistoryItem.PostItem)
        assertEquals(
            "t3_valid",
            (page.items.single() as RedditSavedHistoryItem.PostItem).fullname,
        )
    }
}
