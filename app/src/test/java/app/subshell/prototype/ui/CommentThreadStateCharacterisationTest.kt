package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Characterisation of the comment collapse set and the comment visit
 * history inside SubshellState as they behave today, written before they
 * move to CommentThreadState. Current behaviour, not a specification;
 * the reveal rule and the backup exclusion are already covered by
 * NewCommentsPolicyTest.
 */
class CommentThreadStateCharacterisationTest {
    private val writes = mutableListOf<Pair<String, String>>()

    @Test
    fun toggleCollapsesAnUnknownIdAndExpandsAKnownOneKeepingInsertionOrder() {
        val state = state()
        assertEquals(emptyList<String>(), state.collapsedCommentIds.toList())

        state.toggleComment("t1_a")
        state.toggleComment("t1_b")
        assertEquals(listOf("t1_a", "t1_b"), state.collapsedCommentIds.toList())

        state.toggleComment("t1_a")
        assertEquals(listOf("t1_b"), state.collapsedCommentIds.toList())

        state.toggleComment("t1_a")
        assertEquals(listOf("t1_b", "t1_a"), state.collapsedCommentIds.toList())

        // The list is one object for the lifetime of the state; the post
        // screen mutates it directly.
        val list = state.collapsedCommentIds
        list.add("t1_c")
        assertSame(list, state.collapsedCommentIds)
        assertEquals(listOf("t1_b", "t1_a", "t1_c"), state.collapsedCommentIds.toList())
    }

    @Test
    fun revealNeverDuplicatesSiblingIdsAndKeepsManuallyCollapsedAncestors() {
        val state = state()
        state.collapsedCommentIds.addAll(listOf("t1_root", "t1_sib"))

        state.revealNewCommentPaths(
            ancestorIds = setOf("t1_root", "t1_path"),
            siblingBranchIds = setOf("t1_sib", "t1_sib2"),
            manuallyCollapsedIds = setOf("t1_root"),
        )
        assertEquals(listOf("t1_root", "t1_sib", "t1_sib2"), state.collapsedCommentIds.toList())

        state.revealNewCommentPaths(
            ancestorIds = setOf("t1_root"),
            siblingBranchIds = emptySet(),
            manuallyCollapsedIds = emptySet(),
        )
        assertEquals(listOf("t1_sib", "t1_sib2"), state.collapsedCommentIds.toList())
    }

    @Test
    fun visitHistoryAccumulatesPerAccountAndPostAndPersistsOnlyOnChange() {
        val state = state()
        assertNull(state.commentVisitBaseline("acct-a", "post-1"))

        state.recordCommentVisit("acct-a", "post-1", setOf("t1_b", "t1_a"))
        assertEquals(setOf("t1_a", "t1_b"), state.commentVisitBaseline("acct-a", "post-1"))
        assertEquals(1, writes.size)
        assertEquals(SubshellSettings.COMMENT_VISIT_HISTORY, writes.single().first)
        val firstToken = writes.single().second

        // Same ids again: the encoded history is unchanged, nothing persisted.
        state.recordCommentVisit("acct-a", "post-1", setOf("t1_a"))
        assertEquals(1, writes.size)

        // New ids accumulate on top of the earlier visit.
        state.recordCommentVisit("acct-a", "post-1", setOf("t1_c"))
        assertEquals(setOf("t1_a", "t1_b", "t1_c"), state.commentVisitBaseline("acct-a", "post-1"))
        assertEquals(2, writes.size)

        // Other account or post: independent baselines; an empty visit is
        // still a recorded (empty) baseline.
        state.recordCommentVisit("acct-b", "post-1", setOf("t1_z"))
        state.recordCommentVisit("acct-a", "post-2", emptySet())
        assertEquals(setOf("t1_z"), state.commentVisitBaseline("acct-b", "post-1"))
        assertEquals(emptySet<String>(), state.commentVisitBaseline("acct-a", "post-2"))
        assertEquals(setOf("t1_a", "t1_b", "t1_c"), state.commentVisitBaseline("acct-a", "post-1"))
        assertEquals(4, writes.size)

        // A state built from the first token reproduces the first baseline.
        val restored = state(initialToken = firstToken)
        assertEquals(setOf("t1_a", "t1_b"), restored.commentVisitBaseline("acct-a", "post-1"))
        assertNull(restored.commentVisitBaseline("acct-b", "post-1"))
    }

    @Test
    fun aMalformedInitialTokenDecodesToAnEmptyHistory() {
        val state = state(initialToken = "not a history line")
        assertNull(state.commentVisitBaseline("acct-a", "post-1"))
        state.recordCommentVisit("acct-a", "post-1", setOf("t1_a"))
        assertEquals(setOf("t1_a"), state.commentVisitBaseline("acct-a", "post-1"))
    }

    private fun state(initialToken: String = ""): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
        initialCommentVisitHistoryToken = initialToken,
        persistPreference = { key, value -> writes += key to value },
    )
}
