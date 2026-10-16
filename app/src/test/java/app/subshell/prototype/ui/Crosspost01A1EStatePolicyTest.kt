package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.*
import org.junit.Test

class Crosspost01A1EStatePolicyTest {
    private val owner = AccountSessionSummary("owner", "crossposter", AccountSessionSource.AUTHENTICATED, AccountSessionState.CONNECTED)
    private val other = AccountSessionSummary("other", "otherposter", AccountSessionSource.AUTHENTICATED, AccountSessionState.CONNECTED)
    private val source = Post("t3_source123", "source", "author", "1h", "Original title", score = 7, comments = 2, kind = PostKind.TEXT)
    private fun state(drafts: List<PostComposerDraft> = emptyList()) = SubshellState(
        initialAccountSessions = listOf(owner, other), initialActiveAccountId = owner.stableId,
        initialPostComposerDrafts = drafts,
    )
    private fun submitted(draft: PostComposerDraft) = SubmittedTextPost(
        fullname = "t3_created123", id = "created123", community = draft.community,
        title = draft.title, text = "", permalink = "/r/${draft.community}/comments/created123/",
        flairText = draft.flair?.text, nsfw = draft.nsfw, spoiler = draft.spoiler,
        crosspostFullname = draft.crosspostSource?.fullname,
    )
    private fun ready(state: SubshellState): PostComposerDraft {
        assertTrue(state.openCrosspostComposer(source))
        state.updatePostComposerDraft(state.postComposerDraft.withCrosspostDestination("target"))
        return state.postComposerDraft
    }
    @Test fun entryPinsSourceOwnerAndOpensSameComposerRouteWithNoDestination() {
        val state = state(); assertTrue(state.openCrosspostComposer(source))
        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)
        assertEquals(ComposerPostType.CROSSPOST, state.postComposerDraft.type)
        assertEquals("owner", state.postComposerDraft.accountStableId)
        assertEquals("t3_source123", state.postComposerDraft.crosspostSource?.fullname)
        assertEquals("", state.postComposerDraft.community)
        assertEquals(state.postComposerDraft, state.postComposerDrafts.single())
    }
    @Test fun localDeletedAndNoncanonicalSourcesDoNotOpenCrosspost() {
        assertFalse(SubshellState().openCrosspostComposer(source))
        for (p in listOf(source.copy(id = "local-post-1"), source.copy(author = "[deleted]"), source.copy(id = "t1_source123"))) {
            val state = state(); assertFalse(state.openCrosspostComposer(p))
            assertFalse(state.currentNavigationRoute is SubshellRoute.PostComposer)
        }
    }
    @Test fun savedCrosspostReopensForOwnerAndPreservesLockedSource() {
        val state = state(); val draft = ready(state); state.closePostComposer()
        assertTrue(state.openPostComposerDraft(draft.id))
        assertEquals(draft.crosspostSource, state.postComposerDraft.crosspostSource)
        assertEquals("target", state.postComposerDraft.community)
    }
    @Test fun differentAccountCannotReopenSavedCrosspost() {
        val draft = crosspost01A1CDraft(); val state = state(listOf(draft)); state.switchAccount("other")
        assertFalse(state.openPostComposerDraft(draft.id))
        assertTrue(state.postComposerDrafts.any { it.id == draft.id })
    }
    @Test fun completionClearsOnlyMatchingDraftAndOpensNewWrapperNotOriginal() {
        val state = state(); val draft = ready(state)
        val key = requireNotNull(draft.crosspostRequestForIdentityOnly()).requestKey
        val created = requireNotNull(state.completeRemoteCrosspostComposer("owner", key, submitted(draft)))
        assertEquals("t3_created123", created.id); assertEquals("t3_source123", created.crosspost?.id)
        assertEquals("t3_created123", state.selectedPost?.id)
        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
        assertFalse(state.currentNavigationRoute is SubshellRoute.PostComposer)
    }
    @Test fun successAfterCloseStillRemovesExactSavedDraftWithoutSurpriseNavigation() {
        val state = state(); val draft = ready(state); state.closePostComposer()
        val route = state.currentNavigationRoute
        val key = requireNotNull(draft.crosspostRequestForIdentityOnly()).requestKey
        assertNotNull(state.completeRemoteCrosspostComposer("owner", key, submitted(draft)))
        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
        assertEquals(route, state.currentNavigationRoute)
    }
    @Test fun lateSuccessCannotDiscardOrCloseANewerComposerDraft() {
        val state = state(); val old = ready(state); state.closePostComposer(); state.openPostComposer()
        state.updatePostComposerDraft(state.postComposerDraft.copy(title = "Unrelated new draft"))
        val newer = state.postComposerDraft
        val key = requireNotNull(old.crosspostRequestForIdentityOnly()).requestKey
        state.completeRemoteCrosspostComposer("owner", key, submitted(old))
        assertEquals(newer, state.postComposerDraft)
        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)
        assertFalse(state.postComposerDrafts.any { it.id == old.id })
    }
    @Test fun wrongSourceCompletionCannotClearDraftOrNavigate() {
        val state = state(); val draft = ready(state)
        val key = requireNotNull(draft.crosspostRequestForIdentityOnly()).requestKey
        assertNull(state.completeRemoteCrosspostComposer("owner", key, submitted(draft).copy(crosspostFullname = "t3_other")))
        assertEquals(draft, state.postComposerDraft)
        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)
    }
}
