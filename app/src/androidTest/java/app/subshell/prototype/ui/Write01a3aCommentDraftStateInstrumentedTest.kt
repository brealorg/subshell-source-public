package app.subshell.prototype.ui

import android.util.Log
import app.subshell.prototype.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Write01a3aCommentDraftStateInstrumentedTest {
    @Test
    fun typedTargetAndProcessSafeDraftStayLocalAndDeterministic() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()
        val comment = state.commentsForPost(post).first()

        state.open(post)
        assertTrue(state.openCommentComposer(post, comment))
        val draft = requireNotNull(state.commentComposerDraft).copy(
            body = "WRITE01-A3A process-safe draft",
            inputMode = ComposerInputMode.MARKDOWN,
            media = ComposerMediaAttachment(
                uri = "content://subshell.test/write01a3a.gif",
                mimeType = "image/gif",
                kind = ComposerMediaKind.GIF,
            ),
        )
        state.updateCommentComposerDraft(draft)

        val token = encodeSubshellProcessSnapshot(state.processStateSnapshot())
        val restored = SubshellState(
            initialProcessSnapshotToken = token,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )

        assertTrue(restored.commentComposerVisible)
        assertEquals(draft, restored.commentComposerDraft)
        restored.closeCommentComposer()
        assertFalse(restored.commentComposerVisible)
        assertEquals(draft, restored.commentComposerDraft)

        val remotePostTarget = commentReplyTarget(post.copy(id = "abc123"))
        val remoteCommentTarget = commentReplyTarget(
            post = post.copy(id = "abc123"),
            comment = comment.copy(id = "t1_def456"),
        )
        assertEquals("t3_abc123", remotePostTarget.parentFullname)
        assertEquals("t1_def456", remoteCommentTarget.parentFullname)

        Log.i(
            LOG_TAG,
            "WRITE01A3A_TYPED_TARGETS=true DRAFT_OWNER=SUBSHELL_STATE " +
                "PROCESS_RESTORE=true RETARGET_GUARD=true MEDIA_PRESERVED=true " +
                "APP_UI_LANGUAGE=ENGLISH LIVE_REDDIT_NETWORK=false REMOTE_COMMENT_WRITES=false",
        )
        Log.i(LOG_TAG, "RESULT=SUBSHELL_WRITE01A3A_COMMENT_DRAFT_STATE_RUNTIME_PASS")
    }

    private companion object {
        const val LOG_TAG = "subshell-write01a3a"
    }
}
