package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the comment composer draft inside SubshellState as it
 * behaves today, written before it moves to CommentDraftState. Current
 * behaviour, not a specification. The retarget guard and the process-state
 * round trip stay covered by CommentComposerStatePolicyTest.
 */
class CommentDraftStateCharacterisationTest {
    @Test
    fun reopeningTheSameTargetKeepsTheDraftAndAnEmptyDraftCanBeRetargeted() {
        val state = SubshellState()
        val post = SampleData.posts.first()
        val comment = state.commentsForPost(post).first()
        assertFalse(state.commentComposerVisible)
        assertNull(state.commentComposerDraft)

        assertTrue(state.openCommentComposer(post))
        val first = requireNotNull(state.commentComposerDraft)
        assertTrue(first.target.isPostTarget)
        state.updateCommentComposerDraft(first.copy(body = "typed"))
        state.closeCommentComposer()
        assertFalse(state.commentComposerVisible)

        assertTrue(state.openCommentComposer(post))
        assertTrue(state.commentComposerVisible)
        assertEquals("typed", state.commentComposerDraft?.body)

        // Without content the draft is replaced by the new target.
        state.updateCommentComposerDraft(first.copy(body = "   "))
        assertTrue(state.openCommentComposer(post, comment))
        assertEquals(comment.id, state.commentComposerDraft?.target?.parentCommentId)
        assertEquals("", state.commentComposerDraft?.body)
    }

    @Test
    fun updatesAreIgnoredWithoutADraftOrWithAnotherTarget() {
        val state = SubshellState()
        val post = SampleData.posts.first()
        val comment = state.commentsForPost(post).first()
        val stray = CommentComposerDraft(target = commentReplyTarget(post, comment), body = "stray")

        state.updateCommentComposerDraft(stray)
        assertNull(state.commentComposerDraft)

        assertTrue(state.openCommentComposer(post))
        val current = requireNotNull(state.commentComposerDraft)
        state.updateCommentComposerDraft(stray)
        assertSame(current, state.commentComposerDraft)
        state.updateCommentComposerDraft(current.copy(body = "mine"))
        assertEquals("mine", state.commentComposerDraft?.body)
    }

    @Test
    fun localPublishRequiresTheDraftsPostAndClearsOnSuccess() {
        val state = SubshellState()
        val post = SampleData.posts.first()
        val other = SampleData.posts.first { it.id != post.id }
        assertNull(state.publishLocalCommentComposer(post))

        assertTrue(state.openCommentComposer(post))
        val draft = requireNotNull(state.commentComposerDraft)
        state.updateCommentComposerDraft(draft.copy(body = "A local reply."))
        assertNull(state.publishLocalCommentComposer(other))
        assertNotNull(state.commentComposerDraft)

        val countBefore = state.commentsForPost(post).size
        val created = state.publishLocalCommentComposer(post)
        assertNotNull(created)
        assertEquals("A local reply.", created?.body)
        assertEquals(countBefore + 1, state.commentsForPost(post).size)
        assertNull(state.commentComposerDraft)
        assertFalse(state.commentComposerVisible)

        // A blank draft is refused by the local write and stays open.
        assertTrue(state.openCommentComposer(post))
        assertNull(state.publishLocalCommentComposer(post))
        assertNotNull(state.commentComposerDraft)
        assertTrue(state.commentComposerVisible)
    }

    @Test
    fun remoteCompletionClearsOnlyTheMatchingTarget() {
        val state = SubshellState()
        val post = SampleData.posts.first()
        val comment = state.commentsForPost(post).first()
        assertFalse(state.completeRemoteCommentComposer("nothing"))

        assertTrue(state.openCommentComposer(post, comment))
        val key = requireNotNull(state.commentComposerDraft).target.stableKey
        assertFalse(state.completeRemoteCommentComposer(commentReplyTarget(post).stableKey))
        assertNotNull(state.commentComposerDraft)
        assertTrue(state.commentComposerVisible)

        assertTrue(state.completeRemoteCommentComposer(key))
        assertNull(state.commentComposerDraft)
        assertFalse(state.commentComposerVisible)
        assertFalse(state.completeRemoteCommentComposer(key))
    }
}
