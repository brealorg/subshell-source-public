package io.github.brealorg.subshell.data.account

import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.ProfileCommentFixture

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
