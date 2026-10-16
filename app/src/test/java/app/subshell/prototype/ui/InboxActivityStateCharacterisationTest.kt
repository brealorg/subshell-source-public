package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Characterisation of the inbox activity lifecycle, read receipts, read ids
 * and conversation drafts inside SubshellState as they behave today, written
 * before they move to InboxActivityState. Current behaviour, not a
 * specification.
 */
class InboxActivityStateCharacterisationTest {
    @Test
    fun dismissAndRestoreArePerAccountTrimmedDedupedAndBumpTheRevisionOnlyOnChange() {
        val store = InboxActivityStore()
        val state = state(store)
        assertEquals(0, state.inboxActivityLifecycleRevision)
        assertEquals(emptySet<String>(), state.dismissedInboxActivityIds("acct-a"))

        state.dismissInboxActivityItem("acct-a", " i1 ")
        assertEquals(setOf("i1"), state.dismissedInboxActivityIds("acct-a"))
        assertEquals(1, state.inboxActivityLifecycleRevision)

        state.dismissInboxActivityItem("acct-a", "i1")
        state.dismissInboxActivityItem("acct-a", "   ")
        assertEquals(1, state.inboxActivityLifecycleRevision)
        assertEquals(1, store.dismissWrites)

        state.dismissInboxActivityItem("acct-b", "i1")
        assertEquals(setOf("i1"), state.dismissedInboxActivityIds("acct-b"))
        assertEquals(setOf("i1"), state.dismissedInboxActivityIds("acct-a"))
        assertEquals(2, state.inboxActivityLifecycleRevision)

        state.restoreInboxActivityItem("acct-a", "missing")
        assertEquals(2, state.inboxActivityLifecycleRevision)
        state.restoreInboxActivityItem("acct-a", "i1")
        assertEquals(emptySet<String>(), state.dismissedInboxActivityIds("acct-a"))
        assertEquals(setOf("i1"), state.dismissedInboxActivityIds("acct-b"))
        assertEquals(3, state.inboxActivityLifecycleRevision)
    }

    @Test
    fun clearMovesTheBoundaryForwardAndUndoRestoresThePreviousBoundaryOnce() {
        val store = InboxActivityStore()
        val clock = MutableClock(1_700_000_000_500L)
        val state = state(store, clock)
        assertEquals(0L, state.inboxActivityClearBoundary("acct-a"))
        assertEquals(0L, state.inboxActivityPreviousClearBoundary("acct-a"))

        state.clearInboxActivity("acct-a")
        assertEquals(1_700_000_000L, state.inboxActivityClearBoundary("acct-a"))
        assertEquals(0L, state.inboxActivityPreviousClearBoundary("acct-a"))
        assertEquals(1, state.inboxActivityLifecycleRevision)

        // A clear within the same second still moves the boundary by at least one.
        state.clearInboxActivity("acct-a")
        assertEquals(1_700_000_001L, state.inboxActivityClearBoundary("acct-a"))
        assertEquals(1_700_000_000L, state.inboxActivityPreviousClearBoundary("acct-a"))
        assertEquals(2, state.inboxActivityLifecycleRevision)

        state.undoInboxActivityClear("acct-a")
        assertEquals(1_700_000_000L, state.inboxActivityClearBoundary("acct-a"))
        assertEquals(1_700_000_000L, state.inboxActivityPreviousClearBoundary("acct-a"))
        assertEquals(3, state.inboxActivityLifecycleRevision)

        // Undo is single-level: a second undo is a no-op.
        state.undoInboxActivityClear("acct-a")
        assertEquals(1_700_000_000L, state.inboxActivityClearBoundary("acct-a"))
        assertEquals(3, state.inboxActivityLifecycleRevision)

        assertEquals(0L, state.inboxActivityClearBoundary("acct-b"))
        clock.now = 1_800_000_000_000L
        state.clearInboxActivity("acct-b")
        assertEquals(1_800_000_000L, state.inboxActivityClearBoundary("acct-b"))
        assertEquals(1_700_000_000L, state.inboxActivityClearBoundary("acct-a"))
    }

    @Test
    fun readIdsDedupe() {
        val state = state(InboxActivityStore())
        val unreadBefore = state.unreadInboxCount
        assertTrue(unreadBefore > 0)

        val first = SampleData.inbox.first { it.unread }
        state.markInboxRead(first.id)
        state.markInboxRead(first.id)
        assertEquals(listOf(first.id), state.readInboxIds.toList())
        assertEquals(unreadBefore - 1, state.unreadInboxCount)
    }

    @Test
    fun conversationDraftsAreKeyedByAccountAndItemBoundedAndRemovedWhenEmpty() {
        val state = state(InboxActivityStore())
        assertEquals("", state.messageConversationDraft("acct-a", "i1"))

        state.updateMessageConversationDraft(" acct-a ", " i1 ", "hello")
        assertEquals("hello", state.messageConversationDraft("acct-a", "i1"))
        assertEquals("", state.messageConversationDraft("acct-b", "i1"))
        assertEquals("", state.messageConversationDraft("acct-a", "i2"))

        state.updateMessageConversationDraft("acct-a", "i1", "x".repeat(12_000))
        assertEquals(10_000, state.messageConversationDraft("acct-a", "i1").length)

        state.updateMessageConversationDraft("acct-a", "i1", "")
        assertEquals("", state.messageConversationDraft("acct-a", "i1"))

        try {
            state.messageConversationDraft(" ", "i1")
            fail("blank account must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Message draft account cannot be blank", expected.message)
        }
        try {
            state.updateMessageConversationDraft("acct-a", "", "text")
            fail("blank item must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Message draft thread cannot be blank", expected.message)
        }
    }

    @Test
    fun readReceiptIsConsumedOnlyByTheMatchingTargetAndOnlyOnce() {
        val state = state(InboxActivityStore())
        val item = SampleData.inbox.first { it.targetPostId == "m3-terminal" && it.targetCommentId == "c2" }

        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c2"))

        state.openInboxItem(item, verifiedReadAccountStableId = "acct-a")
        assertNull(state.consumeInboxReadReceipt("other-post", "c2"))
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c9"))
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "not a fullname!"))

        val receipt = state.consumeInboxReadReceipt("t3_m3-terminal", "t1_C2")
        assertNotNull(receipt)
        assertEquals(
            PendingInboxReadReceipt(
                accountStableId = "acct-a",
                inboxItemId = item.id,
                postId = "m3-terminal",
                commentFullname = "t1_c2",
            ),
            receipt,
        )
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c2"))

        // Opening without a verified account leaves no receipt; closing the
        // post clears a pending one.
        state.openInboxItem(item)
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c2"))
        state.openInboxItem(item, verifiedReadAccountStableId = "acct-a")
        state.closePost()
        assertNull(state.consumeInboxReadReceipt("m3-terminal", "c2"))
        assertFalse(state.readInboxIds.isEmpty())
    }

    private fun state(store: InboxActivityStore, clock: MutableClock = MutableClock()): SubshellState =
        SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            loadInboxActivityDismissedIds = store::dismissed,
            persistInboxActivityDismissedIds = store::persistDismissed,
            loadInboxActivityClearBoundary = store::boundary,
            persistInboxActivityClearBoundary = store::persistBoundary,
            loadInboxActivityPreviousClearBoundary = store::previousBoundary,
            persistInboxActivityPreviousClearBoundary = store::persistPreviousBoundary,
            currentTimeMillis = { clock.now },
        )
}

private class MutableClock(var now: Long = 1_700_000_000_000L)

/** In-memory stand-in for the per-account inbox activity preferences. */
private class InboxActivityStore {
    private val dismissedByAccount = mutableMapOf<String, Set<String>>()
    private val boundaries = mutableMapOf<String, Long>()
    private val previousBoundaries = mutableMapOf<String, Long>()
    var dismissWrites = 0

    fun dismissed(account: String): Set<String> = dismissedByAccount[account].orEmpty()
    fun persistDismissed(account: String, ids: Set<String>) {
        dismissWrites += 1
        dismissedByAccount[account] = ids
    }

    fun boundary(account: String): Long = boundaries[account] ?: 0L
    fun persistBoundary(account: String, value: Long) {
        boundaries[account] = value
    }

    fun previousBoundary(account: String): Long = previousBoundaries[account] ?: 0L
    fun persistPreviousBoundary(account: String, value: Long) {
        previousBoundaries[account] = value
    }
}
