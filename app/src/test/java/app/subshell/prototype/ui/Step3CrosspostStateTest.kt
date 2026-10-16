package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.toV4RemoteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (crosspost01a1c, crosspost01a1d): while a crosspost is being written
 * its source and its owner cannot change and it gets no body (a title edit
 * is taken); the ordinary post composer never sends a crosspost draft; an
 * open crosspost comes back with the same locked source after the process is
 * recreated.
 */
class Step3CrosspostStateTest {
    private val owner = AccountSessionSummary("owner", "crossposter", AccountSessionSource.AUTHENTICATED, AccountSessionState.CONNECTED)
    private val other = AccountSessionSummary("other", "otherposter", AccountSessionSource.AUTHENTICATED, AccountSessionState.CONNECTED)
    private val source = Post("t3_source123", "source", "author", "1h", "Original title", score = 7, comments = 2, kind = PostKind.TEXT)

    private fun ready(): SubshellState {
        val state = SubshellState(initialAccountSessions = listOf(owner, other), initialActiveAccountId = owner.stableId)
        assertTrue(state.openCrosspostComposer(source))
        state.updatePostComposerDraft(state.postComposerDraft.withCrosspostDestination("target"))
        return state
    }

    @Test
    fun theSourceAndOwnerCannotChangeButTheTitleCan() {
        val state = ready()
        val draft = state.postComposerDraft

        state.updatePostComposerDraft(draft.copy(crosspostSource = crosspost01A1CSource().copy(fullname = "t3_other999")))
        state.updatePostComposerDraft(draft.copy(accountStableId = "other"))
        state.updatePostComposerDraft(draft.copy(body = "smuggled body"))
        assertEquals("t3_source123", state.postComposerDraft.crosspostSource?.fullname)
        assertEquals("owner", state.postComposerDraft.accountStableId)
        assertEquals("", state.postComposerDraft.body)

        state.updatePostComposerDraft(state.postComposerDraft.copy(title = "My title"))
        assertEquals("My title", state.postComposerDraft.title)
    }

    @Test
    fun theOrdinaryComposerNeverSendsACrosspostDraft() {
        assertNull(crosspost01A1CDraft().toV4RemoteRequest("owner", null))
        assertNull(
            crosspost01A1CDraft().copy(type = ComposerPostType.TEXT, title = "Plain", body = "text").toV4RemoteRequest("owner", null),
        )
    }

    @Test
    fun anOpenCrosspostComesBackWithItsLockedSourceAfterProcessRestore() {
        val state = ready()
        state.updatePostComposerDraft(state.postComposerDraft.copy(title = "My title"))

        val restored = SubshellState(
            initialAccountSessions = listOf(owner, other),
            initialActiveAccountId = owner.stableId,
            initialProcessSnapshotToken = encodeSubshellProcessSnapshot(state.processStateSnapshot()),
        )

        assertTrue(restored.currentNavigationRoute is SubshellRoute.PostComposer)
        assertEquals(ComposerPostType.CROSSPOST, restored.postComposerDraft.type)
        assertEquals(state.postComposerDraft.crosspostSource, restored.postComposerDraft.crosspostSource)
        assertEquals("My title", restored.postComposerDraft.title)
    }
}
