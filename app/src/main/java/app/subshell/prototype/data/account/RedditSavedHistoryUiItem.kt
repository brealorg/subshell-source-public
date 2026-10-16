package app.subshell.prototype.data.account

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture

sealed interface RedditSavedHistoryUiItem {
    val fullname: String

    data class PostItem(
        override val fullname: String,
        val post: Post,
    ) : RedditSavedHistoryUiItem

    data class CommentItem(
        override val fullname: String,
        val comment: ProfileCommentFixture,
    ) : RedditSavedHistoryUiItem
}
