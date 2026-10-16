package app.subshell.prototype.ui

data class DebugFocusedCommentLaunch(
    val postId: String,
    val commentId: String,
    val username: String,
    val requestId: Long,
)

internal fun debugFocusedCommentLaunchOrNull(
    postId: String?,
    commentId: String?,
    username: String?,
    requestId: Long,
): DebugFocusedCommentLaunch? {
    val normalizedPostId = postId
        ?.trim()
        ?.removePrefix("t3_")
        ?.takeIf(String::isNotEmpty)
        ?.let { "t3_$it" }
        ?: return null
    val normalizedCommentId = commentId
        ?.trim()
        ?.removePrefix("t1_")
        ?.takeIf(String::isNotEmpty)
        ?.let { "t1_$it" }
        ?: return null
    val normalizedUsername = username
        ?.trim()
        ?.removePrefix("u/")
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return DebugFocusedCommentLaunch(
        postId = normalizedPostId,
        commentId = normalizedCommentId,
        username = normalizedUsername,
        requestId = requestId,
    )
}
