package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.CommentComposerDraft
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.commentDraftBelongsToAnotherAccount
import app.subshell.prototype.ui.commentReplyTarget

/**
 * Comment composer draft state: the visible flag and the single draft with
 * its typed reply target, restored from the process snapshot. Extracted
 * verbatim from SubshellState, which holds one instance and delegates to
 * it; the local comment write itself stays in SubshellState and is
 * injected. Behaviour is unchanged.
 */
internal class CommentDraftState(
    initialCommentComposerVisible: Boolean,
    initialCommentComposerDraft: CommentComposerDraft?,
    private val addLocalComment: (Post, String, ComposerMediaAttachment?) -> Comment?,
    private val activeAccountId: () -> String? = { null },
) {
    var commentComposerVisible by mutableStateOf(initialCommentComposerVisible)
        private set
    var commentComposerDraft by mutableStateOf(initialCommentComposerDraft)
        private set

    fun openCommentComposer(
        post: Post,
        comment: Comment? = null,
    ): Boolean {
        val target = commentReplyTarget(post, comment)
        val currentDraft = commentComposerDraft
        val account = activeAccountId()
        // An unsent draft for another target, or written with another
        // account, is never retargeted silently: the caller asks first.
        if (
            currentDraft != null &&
            currentDraft.hasContent &&
            (
                currentDraft.target.stableKey != target.stableKey ||
                    commentDraftBelongsToAnotherAccount(currentDraft, account)
                )
        ) {
            commentComposerVisible = true
            return false
        }
        commentComposerDraft = currentDraft
            ?.takeIf { draft ->
                draft.target.stableKey == target.stableKey &&
                    !commentDraftBelongsToAnotherAccount(draft, account)
            }
            ?: CommentComposerDraft(target = target, accountStableId = account)
        commentComposerVisible = true
        return true
    }

    fun closeCommentComposer() {
        commentComposerVisible = false
    }

    fun updateCommentComposerDraft(draft: CommentComposerDraft) {
        val current = commentComposerDraft ?: return
        if (current.target.stableKey != draft.target.stableKey) return
        commentComposerDraft = draft
    }

    fun discardCommentComposerDraft() {
        commentComposerDraft = null
        commentComposerVisible = false
    }

    /**
     * COMMENTJUMP01_THE_THREAD_SCROLLS_TO_A_POSTED_COMMENT_V1: the comment just
     * posted (on Reddit or locally) and its post, until the post screen has
     * scrolled to it. The reader stayed at the top of the post and had to find
     * their own comment.
     */
    var publishedComment by mutableStateOf<PublishedCommentTarget?>(null)
        private set

    fun notePublishedComment(postId: String, commentId: String) {
        publishedComment = PublishedCommentTarget(postId = postId, commentId = commentId)
    }

    fun consumePublishedComment(target: PublishedCommentTarget) {
        if (publishedComment == target) publishedComment = null
    }

    fun publishLocalCommentComposer(post: Post): Comment? {
        val draft = commentComposerDraft ?: return null
        if (draft.target.postId != post.id) return null
        val created = addLocalComment(post, draft.body, draft.media) ?: return null
        commentComposerDraft = null
        commentComposerVisible = false
        notePublishedComment(post.id, created.id)
        return created
    }

    fun completeRemoteCommentComposer(targetStableKey: String): Boolean {
        val draft = commentComposerDraft
            ?.takeIf { candidate -> candidate.target.stableKey == targetStableKey }
            ?: return false
        commentComposerDraft = null
        commentComposerVisible = false
        return draft.target.stableKey == targetStableKey
    }
}

internal data class PublishedCommentTarget(
    val postId: String,
    val commentId: String,
) {
    // Reddit's write state names the post as a fullname, the screen by its id.
    fun isOn(postId: String): Boolean =
        this.postId.removePrefix("t3_") == postId.removePrefix("t3_")
}
