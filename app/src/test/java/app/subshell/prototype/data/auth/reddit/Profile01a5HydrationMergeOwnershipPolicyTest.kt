package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.mergeExpandedComments
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class Profile01a5HydrationMergeOwnershipPolicyTest {
    @Test
    fun currentInMemoryTreeWinsWithoutReadingAStaleRoomSnapshot() {
        val current = listOf(comment("t1_target"), more("more:t1_root:child"))
        var cacheRead = false

        val selected = redditHydrationMergeBase(current) {
            cacheRead = true
            listOf(more("more:t1_root:child"))
        }

        assertSame(current, selected)
        assertEquals(false, cacheRead)
        assertEquals("t1_target", selected.first().id)
    }

    @Test
    fun hydrationMergeRetainsTargetThatExistsOnlyInCurrentUiTree() {
        val moreId = "more:t1_root:child"
        val current = listOf(comment("t1_target"), more(moreId))
        val staleRoom = listOf(more(moreId))

        val base = redditHydrationMergeBase(current) { staleRoom }
        val merged = mergeExpandedComments(
            current = base,
            moreNodeId = moreId,
            expanded = listOf(comment("t1_child")),
            requestedChildCount = 1,
        )

        assertEquals(listOf("t1_target", "t1_child"), merged.map(Comment::id))
    }

    @Test
    fun legacyCallersStillUseRoomWhenNoInMemoryTreeIsProvided() {
        val cached = listOf(comment("t1_cached"))
        var cacheRead = false

        val selected = redditHydrationMergeBase(null) {
            cacheRead = true
            cached
        }

        assertSame(cached, selected)
        assertEquals(true, cacheRead)
    }

    private fun comment(id: String): Comment = Comment(
        id = id,
        author = "tester",
        age = "now",
        body = id,
        score = 1,
        depth = 0,
        accent = 0xff000000L,
    )

    private fun more(id: String): Comment = Comment(
        id = id,
        author = "More replies",
        age = "",
        body = "",
        score = 0,
        depth = 0,
        accent = 0xff000000L,
        moreChildIds = listOf("child"),
        moreCount = 1,
    )
}
