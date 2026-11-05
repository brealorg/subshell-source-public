package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.data.PostRepository
import io.github.brealorg.subshell.data.itemsOrEmpty
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.Post

// Helpers only tests call (1.0 audit, #40): they lived in the release code.

internal fun postCommentsFor(
    post: Post,
    repository: PostRepository,
): List<Comment> = repository.commentsForPost(post.id).itemsOrEmpty()

internal fun focusedCommentTopLevelIndex(
    comments: List<Comment>,
    focusedCommentId: String?,
): Int = if (focusedCommentId == null) {
    -1
} else {
    comments.indexOfFirst { it.containsComment(focusedCommentId) }
}

internal fun commentThreadInsetDp(depth: Int): Int =
    if (depth <= 0) 0 else COMMENT_THREAD_INSET_DP

internal fun savedPostsForProfile(
    username: String,
    localUsername: String,
    posts: List<Post>,
    savedPostIds: Collection<String>,
): List<Post> =
    if (username.equals(localUsername, ignoreCase = true)) {
        posts.filter { it.id in savedPostIds }
    } else {
        emptyList()
    }

private fun Comment.containsComment(commentId: String): Boolean =
    id == commentId || children.any { it.containsComment(commentId) }
