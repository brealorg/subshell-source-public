package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.RedditSavedHistoryUiItem
import app.subshell.prototype.model.Post

internal data class SavedCommentNavigationTarget(
    val postId: String,
    val commentId: String,
)

internal fun savedHistoryItemKey(
    prefix: String,
    item: RedditSavedHistoryUiItem,
): String = "$prefix-${item.fullname}"

internal fun savedHistoryMediaPosts(
    items: List<RedditSavedHistoryUiItem>,
): List<Post> = items.mapNotNull { item ->
    (item as? RedditSavedHistoryUiItem.PostItem)?.post
}

internal fun RedditSavedHistoryUiItem.savedCommentNavigationTarget():
    SavedCommentNavigationTarget? =
    (this as? RedditSavedHistoryUiItem.CommentItem)?.comment?.let { comment ->
        SavedCommentNavigationTarget(
            postId = comment.postId,
            commentId = comment.id,
        )
    }
