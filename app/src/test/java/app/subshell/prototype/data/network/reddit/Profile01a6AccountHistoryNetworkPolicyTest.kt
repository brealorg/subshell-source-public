package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.RedditAccountHistoryContentKind
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryPageRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a6AccountHistoryNetworkPolicyTest {
    @Test
    fun sharedHistoryTaxonomyCoversProfileAndSavedConsumers() {
        assertEquals(
            listOf(
                RedditAccountHistoryKind.COMMENTS,
                RedditAccountHistoryKind.UPVOTED,
                RedditAccountHistoryKind.DOWNVOTED,
                RedditAccountHistoryKind.HIDDEN,
                RedditAccountHistoryKind.SAVED_POSTS,
                RedditAccountHistoryKind.SAVED_COMMENTS,
            ),
            RedditAccountHistoryKind.entries.toList(),
        )
        assertEquals(
            RedditAccountHistoryContentKind.COMMENT,
            RedditAccountHistoryKind.COMMENTS.contentKind,
        )
        assertEquals(
            RedditAccountHistoryContentKind.POST,
            RedditAccountHistoryKind.UPVOTED.contentKind,
        )
        assertEquals("links", RedditAccountHistoryKind.UPVOTED.typeFilter)
        assertEquals("links", RedditAccountHistoryKind.DOWNVOTED.typeFilter)
        assertEquals("links", RedditAccountHistoryKind.HIDDEN.typeFilter)
        assertEquals("links", RedditAccountHistoryKind.SAVED_POSTS.typeFilter)
        assertEquals("comments", RedditAccountHistoryKind.SAVED_COMMENTS.typeFilter)
    }

    @Test
    fun profileActivityHistoriesUseOneAccountHistoryRequestBuilder() {
        listOf(
            RedditAccountHistoryKind.UPVOTED to "upvoted",
            RedditAccountHistoryKind.DOWNVOTED to "downvoted",
            RedditAccountHistoryKind.HIDDEN to "hidden",
        ).forEach { (kind, where) ->
            val request = redditAccountHistoryJsonRequest(
                username = "u/BrealOrg",
                kind = kind,
                request = RepositoryPageRequest(
                    cursor = "t3_after",
                    limit = 25,
                ),
            )
            assertEquals(listOf("user", "brealorg", where), request.pathSegments)
            val query = request.queryParameters.toMap()
            assertEquals("1", query["raw_json"])
            assertEquals("new", query["sort"])
            assertEquals("25", query["limit"])
            assertEquals("t3_after", query["after"])
            assertEquals("links", query["type"])
        }
    }

    @Test
    fun savedKindsRemainDistinctWithoutCreatingSavedUiHere() {
        val savedPosts = redditAccountHistoryJsonRequest(
            username = "brealorg",
            kind = RedditAccountHistoryKind.SAVED_POSTS,
        )
        val savedComments = redditAccountHistoryJsonRequest(
            username = "brealorg",
            kind = RedditAccountHistoryKind.SAVED_COMMENTS,
        )

        assertEquals(listOf("user", "brealorg", "saved"), savedPosts.pathSegments)
        assertEquals(listOf("user", "brealorg", "saved"), savedComments.pathSegments)
        assertEquals("links", savedPosts.queryParameters.toMap()["type"])
        assertEquals("comments", savedComments.queryParameters.toMap()["type"])
        assertTrue(RedditAccountHistoryKind.SAVED_POSTS.isPostHistory)
        assertFalse(RedditAccountHistoryKind.SAVED_COMMENTS.isPostHistory)
    }

    @Test
    fun profileCommentsUseTheSharedHistoryRequestBuilderWithoutAPostTypeFilter() {
        val request = redditAccountHistoryJsonRequest(
            username = "u/BrealOrg",
            kind = RedditAccountHistoryKind.COMMENTS,
            request = RepositoryPageRequest(
                cursor = "t1_after",
                limit = 25,
            ),
        )

        assertEquals(listOf("user", "brealorg", "comments"), request.pathSegments)
        val query = request.queryParameters.toMap()
        assertEquals("1", query["raw_json"])
        assertEquals("new", query["sort"])
        assertEquals("25", query["limit"])
        assertEquals("t1_after", query["after"])
        assertFalse(query.containsKey("type"))
    }

}
