package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateListOf
import app.subshell.prototype.ui.CommentVisitHistory
import app.subshell.prototype.ui.SubshellSettings

/**
 * Comment thread state: the collapsed-comment list the post screen mutates
 * directly, and the per-account comment visit history behind the
 * new-comment markers. Extracted verbatim from SubshellState, which holds
 * one instance and delegates to it. Behaviour is unchanged.
 */
internal class CommentThreadState(
    initialCommentVisitHistoryToken: String,
    private val persistPreference: (String, String) -> Unit,
) {
    private var commentVisitHistory = CommentVisitHistory.decode(initialCommentVisitHistoryToken)
    val collapsedCommentIds = mutableStateListOf<String>()

    fun toggleComment(id: String) {
        if (!collapsedCommentIds.remove(id)) collapsedCommentIds.add(id)
        trimCollapsedCommentIds()
    }

    // LOWUI5_COLLAPSED_COMMENTS_CAPPED_V1 (OCB-287): the process keeps the most
    // recent collapses only (comment ids are globally unique, so the list need
    // not be per post); the oldest go first.
    private fun trimCollapsedCommentIds() {
        val excess = collapsedCommentIds.size - MAX_COLLAPSED_COMMENT_IDS
        if (excess > 0) collapsedCommentIds.removeRange(0, excess)
    }

    fun revealNewCommentPaths(
        ancestorIds: Set<String>,
        siblingBranchIds: Set<String>,
        manuallyCollapsedIds: Set<String>,
    ) {
        collapsedCommentIds.removeAll(ancestorIds - manuallyCollapsedIds)
        siblingBranchIds.forEach { id ->
            if (id !in collapsedCommentIds) collapsedCommentIds.add(id)
        }
        trimCollapsedCommentIds()
    }

    fun commentVisitBaseline(accountStableId: String, postId: String): Set<String>? =
        commentVisitHistory.baseline(accountStableId, postId)

    fun recordCommentVisit(
        accountStableId: String,
        postId: String,
        commentIds: Set<String>,
    ) {
        if (!commentVisitHistory.recordingChanges(accountStableId, postId, commentIds)) return
        val updated = commentVisitHistory.recording(accountStableId, postId, commentIds)
        commentVisitHistory = updated
        persistPreference(SubshellSettings.COMMENT_VISIT_HISTORY, updated.encode())
    }
}

internal const val MAX_COLLAPSED_COMMENT_IDS = 2_000
