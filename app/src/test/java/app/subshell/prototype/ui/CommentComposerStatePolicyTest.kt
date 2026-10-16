package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentComposerStatePolicyTest {
    @Test
    fun replyTargetsRetainTypedT3AndT1Parents() {
        val post = SampleData.posts.first().copy(id = "abc123")
        val comment = SampleData.comments.first().copy(id = "t1_def456")

        val postTarget = commentReplyTarget(post)
        val commentTarget = commentReplyTarget(post, comment)

        assertTrue(postTarget.isPostTarget)
        assertEquals("t3_abc123", postTarget.parentFullname)
        assertFalse(commentTarget.isPostTarget)
        assertEquals("t1_def456", commentTarget.parentFullname)
        assertEquals(comment.id, commentTarget.parentCommentId)
        assertEquals(comment.author, commentTarget.parentAuthor)
    }

    @Test
    fun unsentDraftCannotBeSilentlyRetargeted() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()
        val comment = state.commentsForPost(post).first()

        assertTrue(state.openCommentComposer(post))
        val original = requireNotNull(state.commentComposerDraft)
        state.updateCommentComposerDraft(original.copy(body = "Keep this unsent draft."))
        state.closeCommentComposer()

        assertFalse(state.openCommentComposer(post, comment))
        assertTrue(state.commentComposerVisible)
        assertEquals(original.target, state.commentComposerDraft?.target)
        assertEquals("Keep this unsent draft.", state.commentComposerDraft?.body)

        state.discardCommentComposerDraft()
        assertFalse(state.commentComposerVisible)
        assertNull(state.commentComposerDraft)
    }

    @Test
    fun draftTargetModeAndMediaRoundTripThroughProcessState() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()
        val comment = state.commentsForPost(post).first()
        state.open(post)
        assertTrue(state.openCommentComposer(post, comment))

        val media = ComposerMediaAttachment(
            uri = "content://subshell.test/comment.gif",
            mimeType = "image/gif",
            kind = ComposerMediaKind.GIF,
            sourceLinkUri = "https://giphy.com/gifs/comment-draft",
        )
        val draft = requireNotNull(state.commentComposerDraft).copy(
            body = "A process-safe **comment draft**.",
            inputMode = ComposerInputMode.MARKDOWN,
            media = media,
        )
        state.updateCommentComposerDraft(draft)

        val encoded = encodeSubshellProcessSnapshot(state.processStateSnapshot())
        val restored = SubshellState(
            initialProcessSnapshotToken = encoded,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )

        assertTrue(restored.commentComposerVisible)
        assertEquals(draft, restored.commentComposerDraft)
        assertEquals(post.id, restored.selectedPost?.id)

        restored.closeCommentComposer()
        assertFalse(restored.commentComposerVisible)
        assertEquals(draft, restored.commentComposerDraft)
    }
}
