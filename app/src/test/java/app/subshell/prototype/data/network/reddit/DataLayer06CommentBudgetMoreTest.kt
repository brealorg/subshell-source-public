package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DATALAYER06: Reddit's comment `limit` is approximate. Comments past the
 * mapping budget used to be skipped with no placeholder, leaving a gap that
 * could not be loaded; they now become (or join) a "More replies" node.
 */
class DataLayer06CommentBudgetMoreTest {
    private val json = SubshellNetworkJson.providerJson

    private fun comment(id: String, parent: String, replies: String = "\"\"") =
        """{"kind":"t1","data":{"id":"$id","name":"t1_$id","parent_id":"$parent","author":"a",
           "body":"body $id","score":1,"depth":0,"created_utc":1000,"replies":$replies}}"""

    private fun listing(children: List<String>) =
        """{"kind":"Listing","data":{"children":[${children.joinToString(",")}]}}"""

    private fun thread(children: List<String>): List<Comment> {
        val post = """{"kind":"Listing","data":{"children":[]}}"""
        val listings = json.decodeFromString<List<RedditCommentListingDto>>("[$post,${listing(children)}]")
        return listings.toComments(json = json, nowEpochSeconds = 2000, limit = 3, maxDepth = 8)
    }

    @Test
    fun topLevelCommentsPastTheBudgetBecomeOneMoreNode() {
        val comments = thread((1..5).map { index -> comment("c$index", "t3_post") })

        assertEquals(listOf("t1_c1", "t1_c2", "t1_c3"), comments.take(3).map(Comment::id))
        val more = comments.last()
        assertEquals(listOf("c4", "c5"), more.moreChildIds)
        assertEquals(2, more.moreCount)
        assertEquals(4, comments.size)
    }

    @Test
    fun overBudgetCommentsJoinRedditsOwnMoreNode() {
        val redditMore =
            """{"kind":"more","data":{"id":"m1","name":"t1_m1","parent_id":"t3_post","count":7,"depth":0,"children":["x1","x2"]}}"""
        val comments = thread((1..4).map { index -> comment("c$index", "t3_post") } + redditMore)

        val more = comments.single { comment -> comment.moreChildIds.isNotEmpty() }
        assertEquals(listOf("c4", "x1", "x2"), more.moreChildIds)
        assertEquals(8, more.moreCount)
    }

    @Test
    fun repliesThatArriveAfterTheBudgetRunsOutStayLoadable() {
        val replies = listing(listOf(comment("r1", "t1_c3"), comment("r2", "t1_c3")))
        val comments = thread(listOf(comment("c1", "t3_post"), comment("c2", "t3_post"), comment("c3", "t3_post", replies)))

        val last = comments.last()
        assertEquals("t1_c3", last.id)
        assertEquals(listOf("r1", "r2"), last.children.single().moreChildIds)
    }
}
