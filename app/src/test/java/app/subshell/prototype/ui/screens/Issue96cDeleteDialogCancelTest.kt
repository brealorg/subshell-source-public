package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.CommentDeletePhase
import app.subshell.prototype.data.account.PostDeletePhase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1 (96c): the post and comment delete
 * dialogs can be closed (cancelling the authorization) while waiting for the
 * browser; only deleting and checking keep them open.
 */
class Issue96cDeleteDialogCancelTest {
    @Test
    fun aPostDeleteDialogIsLockedOnlyWhileDeletingOrChecking() {
        assertEquals(
            setOf(PostDeletePhase.SENDING, PostDeletePhase.CHECKING),
            PostDeletePhase.entries.filter(::postDeleteCloseLocked).toSet(),
        )
    }

    @Test
    fun aCommentDeleteDialogIsLockedOnlyWhileDeleting() {
        assertEquals(
            listOf(CommentDeletePhase.SENDING),
            CommentDeletePhase.entries.filter(::commentDeleteCloseLocked),
        )
    }
}
