package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.ui.screens.unsentCommentDraftConflictText
import app.subshell.prototype.ui.state.CommentDraftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UIPOST02 (ui/ review round 1, findings 2 and 5):
 * - an unsent draft is never retargeted silently: openCommentComposer
 *   refuses, and PostScreen asks "Discard your unsent reply?" instead of
 *   ignoring the refusal (which let Send post under the old target);
 * - a draft remembers the account it was written with; after an account
 *   switch it counts as a conflict and is never sent from the new account;
 * - the account travels in the process snapshot (appended field 11) and an
 *   older snapshot without it still restores the draft.
 */
class UiPost02CommentDraftTargetAndAccountTest {
    private val post = SampleData.posts.first()
    private var account: String? = "reddit:a"

    private fun draftState() = CommentDraftState(
        initialCommentComposerVisible = false,
        initialCommentComposerDraft = null,
        addLocalComment = { _, _, _ -> null },
        activeAccountId = { account },
    )

    @Test
    fun aDraftRemembersItsAccountAndIsNotReusedByAnother() {
        val drafts = draftState()
        assertTrue(drafts.openCommentComposer(post))
        assertEquals("reddit:a", drafts.commentComposerDraft?.accountStableId)
        drafts.updateCommentComposerDraft(requireNotNull(drafts.commentComposerDraft).copy(body = "typed as a"))

        account = "reddit:b"
        assertFalse(drafts.openCommentComposer(post))
        assertEquals("typed as a", drafts.commentComposerDraft?.body)
        assertTrue(commentDraftBelongsToAnotherAccount(requireNotNull(drafts.commentComposerDraft), account))

        drafts.discardCommentComposerDraft()
        assertTrue(drafts.openCommentComposer(post))
        assertEquals("reddit:b", drafts.commentComposerDraft?.accountStableId)
    }

    @Test
    fun anEmptyDraftFromAnotherAccountIsSimplyReplaced() {
        val drafts = draftState()
        assertTrue(drafts.openCommentComposer(post))
        account = "reddit:b"
        assertTrue(drafts.openCommentComposer(post))
        assertEquals("reddit:b", drafts.commentComposerDraft?.accountStableId)
    }

    @Test
    fun aDraftFromBeforeTheFieldIsNotBlocked() {
        val legacy = CommentComposerDraft(target = commentReplyTarget(post), body = "old")
        assertFalse(commentDraftBelongsToAnotherAccount(legacy, "reddit:b"))
    }

    @Test
    fun theConflictDialogNamesTheExistingDraft() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val comment = state.commentsForPost(post).first()
        val toComment = CommentComposerDraft(target = commentReplyTarget(post, comment), body = "x")
        assertEquals(
            "You have an unsent reply to u/${comment.author}. Discard it to start this one?",
            unsentCommentDraftConflictText(toComment, post.id, writtenWithAnotherAccount = false),
        )
        assertEquals(
            "You have an unsent reply on another post, written with another account. Discard it to start this one?",
            unsentCommentDraftConflictText(toComment, "t3_other", writtenWithAnotherAccount = true),
        )
    }

    @Test
    fun theAccountTravelsInTheProcessSnapshotAndOldSnapshotsStillRestore() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.open(post)
        assertTrue(state.openCommentComposer(post))
        val draft = requireNotNull(state.commentComposerDraft).copy(body = "keep me")
        state.updateCommentComposerDraft(draft)
        val encoded = encodeSubshellProcessSnapshot(state.processStateSnapshot())

        val restored = SubshellState(
            initialProcessSnapshotToken = encoded,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        assertEquals(draft, restored.commentComposerDraft)
        assertEquals(state.activeAccountId, restored.commentComposerDraft?.accountStableId)

        // A snapshot written before field 11 existed.
        val historical = encoded.lines().joinToString("\n") { line ->
            if (line.startsWith("COMMENT_COMPOSER|")) line.substringBeforeLast('|') else line
        }
        val fromHistorical = SubshellState(
            initialProcessSnapshotToken = historical,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        assertEquals("keep me", fromHistorical.commentComposerDraft?.body)
        assertNull(fromHistorical.commentComposerDraft?.accountStableId)
    }
}
