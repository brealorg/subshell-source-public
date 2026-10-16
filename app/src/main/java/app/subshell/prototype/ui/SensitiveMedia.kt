package app.subshell.prototype.ui

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind

enum class SensitiveMediaPreviewMode(val label: String) {
    BLUR_AND_PRELOAD("Blurred"),
    LOAD_ON_TAP("Load on tap"),
    HIDE_PREVIEW("Hide preview"),
}

internal fun postVisibleWithNsfwPreference(post: Post, showNsfwPosts: Boolean): Boolean =
    showNsfwPosts || !post.nsfw

internal fun sensitiveMediaCanAutoplay(post: Post, revealedMediaIds: Collection<String>): Boolean =
    (!post.nsfw && !post.spoiler) || post.id in revealedMediaIds

/**
 * Blurred sensitive video keeps preload/visibility registration but renders only its poster until
 * explicit reveal, so a platform video surface cannot bypass the Compose blur layer.
 */
internal fun sensitiveMediaUsesPosterOnly(
    post: Post,
    mode: SensitiveMediaPreviewMode,
    revealed: Boolean,
): Boolean =
    post.kind == PostKind.VIDEO &&
        (post.nsfw || post.spoiler) &&
        !revealed &&
        mode == SensitiveMediaPreviewMode.BLUR_AND_PRELOAD
