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

/**
 * NSFWMEDIA01_SHOW_NSFW_MEDIA_SETTING_V1: with "Show NSFW media" on, an NSFW
 * post's media shows and plays like any other media. A spoiler stays covered
 * whatever the setting says, so the setting never reveals a spoiler.
 */
internal fun nsfwMediaShownUncovered(post: Post, showNsfwMedia: Boolean): Boolean =
    showNsfwMedia && post.nsfw && !post.spoiler

internal fun sensitiveMediaCanAutoplay(
    post: Post,
    revealedMediaIds: Collection<String>,
    showNsfwMedia: Boolean = false,
): Boolean =
    (!post.nsfw && !post.spoiler) ||
        post.id in revealedMediaIds ||
        nsfwMediaShownUncovered(post, showNsfwMedia)

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
