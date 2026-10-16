package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.continueThreadNode
import app.subshell.prototype.model.isContinueThreadNode
import app.subshell.prototype.model.isMoreRepliesNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #3: Reddit's "Continue this thread" (a more child with no ids) and
 * replies past the requested depth used to vanish. They are now a continue
 * node under the parent, and loading it grafts the parent's replies in place.
 */
class Issue3ContinueThreadTest {
    private val json = SubshellNetworkJson.providerJson

    private fun comment(id: String, parent: String, depth: Int, replies: String = "\"\"") =
        """{"kind":"t1","data":{"id":"$id","name":"t1_$id","parent_id":"$parent","author":"a",
           "body":"body $id","score":1,"depth":$depth,"created_utc":1000,"replies":$replies}}"""

    private fun listing(children: List<String>) =
        """{"kind":"Listing","data":{"children":[${children.joinToString(",")}]}}"""

    private fun thread(children: List<String>, maxDepth: Int = 8): List<Comment> {
        val post = """{"kind":"Listing","data":{"children":[]}}"""
        val listings = json.decodeFromString<List<RedditCommentListingDto>>("[$post,${listing(children)}]")
        return listings.toComments(json = json, nowEpochSeconds = 2000, limit = 100, maxDepth = maxDepth)
    }

    private val continueMore =
        """{"kind":"more","data":{"id":"_","name":"t1__","parent_id":"t1_deep","count":0,"depth":2,"children":[]}}"""

    @Test
    fun redditsContinueThisThreadBecomesAContinueNodeUnderItsParent() {
        val deep = comment("deep", "t1_mid", 1, listing(listOf(continueMore)))
        val comments = thread(listOf(comment("top", "t3_post", 0, listing(listOf(deep)))))

        val node = comments.single().children.single().children.single()
        assertTrue(node.isContinueThreadNode)
        assertTrue(node.isMoreRepliesNode)
        assertEquals("t1_deep", node.parentId)
        assertEquals(2, node.depth)
        assertEquals(emptyList<String>(), node.moreChildIds)
    }

    @Test
    fun anEmptyMoreChildAtTheTopLevelIsStillDropped() {
        val topMore =
            """{"kind":"more","data":{"id":"_","name":"t1__","parent_id":"t3_post","count":0,"depth":0,"children":[]}}"""
        val comments = thread(listOf(comment("top", "t3_post", 0), topMore))

        assertEquals(listOf("t1_top"), comments.map(Comment::id))
    }

    @Test
    fun repliesPastTheRequestedDepthLeaveAContinueNode() {
        val tooDeep = comment("d2", "t1_d1", 2)
        val d1 = comment("d1", "t1_d0", 1, listing(listOf(tooDeep)))
        val comments = thread(listOf(comment("d0", "t3_post", 0, listing(listOf(d1)))), maxDepth = 1)

        val d1Mapped = comments.single().children.single()
        val node = d1Mapped.children.single()
        assertTrue(node.isContinueThreadNode)
        assertEquals("t1_d1", node.parentId)
        assertEquals(2, node.depth)
    }

    private fun c(id: String, depth: Int, children: List<Comment> = emptyList()) =
        Comment(id = id, author = "a", age = "", body = id, score = 0, depth = depth, accent = 0L, children = children)

    @Test
    fun loadingAContinueNodeGraftsTheParentsRepliesInPlaceAtTheRightDepth() {
        val node = continueThreadNode("t1_deep", depth = 9, accent = 0L)
        val current = listOf(c("t1_top", 0, listOf(c("t1_deep", 8, listOf(node)), c("t1_sibling", 8))))
        // A thread focused on t1_deep (context 0): the parent is the root at depth 0.
        val focused = listOf(c("t1_deep", 0, listOf(c("t1_r1", 1, listOf(c("t1_r2", 2))), c("t1_r3", 1))))

        val merged = mergeContinuedThread(current, node.id, focused)!!

        val deep = merged.single().children.first()
        assertEquals(listOf("t1_r1", "t1_r3"), deep.children.map(Comment::id))
        assertEquals(listOf(9, 9), deep.children.map(Comment::depth))
        assertEquals(10, deep.children.first().children.single().depth)
        assertEquals("t1_sibling", merged.single().children.last().id)
    }

    @Test
    fun aFocusedAnswerWithoutTheParentIsNotMerged() {
        val node = continueThreadNode("t1_deep", depth = 9, accent = 0L)
        val current = listOf(c("t1_deep", 8, listOf(node)))

        assertNull(mergeContinuedThread(current, node.id, listOf(c("t1_other", 0))))
    }
}
