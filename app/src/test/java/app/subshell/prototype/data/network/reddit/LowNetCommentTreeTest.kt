package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.continueThreadNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NET-B (low data/network findings, comment tree): rail colours follow depth
 * after an expansion and a continued thread (OCB-543), replies embedded under
 * an over-budget comment stay loadable (OCB-630), a blank comment keeps its
 * replies (OCB-631).
 */
class LowNetCommentTreeTest {
    private val json = SubshellNetworkJson.providerJson

    private fun comment(id: String, parent: String, depth: Int = 0, body: String = "body $id", replies: String = "\"\"") =
        """{"kind":"t1","data":{"id":"$id","name":"t1_$id","parent_id":"$parent","author":"a",
           "body":"$body","score":1,"depth":$depth,"created_utc":1000,"replies":$replies}}"""

    private fun listing(children: List<String>) =
        """{"kind":"Listing","data":{"children":[${children.joinToString(",")}]}}"""

    private fun thread(children: List<String>, limit: Int = 100): List<Comment> {
        val post = """{"kind":"Listing","data":{"children":[]}}"""
        val listings = json.decodeFromString<List<RedditCommentListingDto>>("[$post,${listing(children)}]")
        return listings.toComments(json = json, nowEpochSeconds = 2000, limit = limit, maxDepth = 8)
    }

    @Test
    fun expandedRepliesTakeTheColourOfTheirDepth() {
        val response = json.decodeFromString<RedditMoreChildrenResponseDto>(
            """{"json":{"errors":[],"data":{"things":[
                ${comment("e1", "t1_p", depth = 4)},
                ${comment("e2", "t1_e1", depth = 5)}
            ]}}}""",
        )

        val expanded = response.toComments(jsonCodec = json, nowEpochSeconds = 2000, maxDepth = 8)

        val first = expanded.single()
        assertEquals(redditCommentAccent(4), first.accent)
        assertEquals(redditCommentAccent(5), first.children.single().accent)
        assertTrue(redditCommentAccent(4) != redditCommentAccent(0))
    }

    @Test
    fun aContinuedThreadTakesTheColourOfItsNewDepth() {
        fun c(id: String, depth: Int, children: List<Comment> = emptyList()) = Comment(
            id = id, author = "a", age = "", body = id, score = 0, depth = depth,
            accent = redditCommentAccent(depth), children = children,
        )
        val node = continueThreadNode("t1_deep", depth = 3, accent = redditCommentAccent(3))
        val current = listOf(c("t1_top", 0, listOf(c("t1_deep", 2, listOf(node)))))
        val focused = listOf(c("t1_deep", 0, listOf(c("t1_r1", 1))))

        val merged = mergeContinuedThread(current, node.id, focused)!!

        val reply = merged.single().children.single().children.single()
        assertEquals(3, reply.depth)
        assertEquals(redditCommentAccent(3), reply.accent)
    }

    @Test
    fun repliesEmbeddedUnderAnOverBudgetCommentAreListedInTheMoreNode() {
        val nestedMore =
            """{"kind":"more","data":{"id":"m9","name":"t1_m9","parent_id":"t1_r1","count":1,"depth":2,"children":["x9"]}}"""
        val deepReplies = listing(listOf(comment("rr1", "t1_r1", depth = 2), nestedMore))
        val replies = listing(listOf(comment("r1", "t1_c2", depth = 1, replies = deepReplies)))
        val comments = thread(
            listOf(comment("c1", "t3_post"), comment("c2", "t3_post", replies = replies), comment("c3", "t3_post")),
            limit = 1,
        )

        val more = comments.last()
        assertEquals(listOf("c2", "r1", "rr1", "x9", "c3"), more.moreChildIds)
    }

    @Test
    fun aBlankCommentWithRepliesKeepsThemAndALeafBlankIsDropped() {
        val replies = listing(listOf(comment("r1", "t1_blank", depth = 1)))
        val comments = thread(
            listOf(
                comment("blank", "t3_post", body = "", replies = replies),
                comment("empty", "t3_post", body = ""),
            ),
        )

        val kept = comments.single()
        assertEquals("t1_blank", kept.id)
        assertEquals("[no text]", kept.body)
        assertEquals(listOf("t1_r1"), kept.children.map(Comment::id))
    }
}
