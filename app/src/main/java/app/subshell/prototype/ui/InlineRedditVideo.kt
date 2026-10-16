package app.subshell.prototype.ui

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind

// ISSUE41_INLINE_VIDEO_LINK_TARGET_V1
// v.redd.it serves the playlists of an inline video under its media id,
// unsigned (verified 2026-09-25 on a video whose community is banned).
internal fun inlineRedditVideoDashUrl(mediaId: String): String =
    "https://v.redd.it/$mediaId/DASHPlaylist.mpd"

internal fun inlineRedditVideoHlsUrl(mediaId: String): String =
    "https://v.redd.it/$mediaId/HLSPlaylist.m3u8"

/**
 * A stand-alone video post for the media viewer. Its id is not a Reddit
 * fullname: it must not replace the text post it came from in the known
 * posts, and vote/save reject it, so the viewer offers no actions on it.
 * The viewer's title is the source post's when Subshell has it loaded.
 */
internal fun inlineRedditVideoPost(
    target: RedditTarget.InlineVideo,
    sourcePost: Post? = null,
): Post = Post(
    id = "$INLINE_REDDIT_VIDEO_POST_ID_PREFIX${target.mediaId}",
    community = sourcePost?.community ?: "reddit",
    author = sourcePost?.author ?: "[inline video]",
    age = "",
    title = sourcePost?.title?.takeIf(String::isNotBlank) ?: INLINE_REDDIT_VIDEO_FALLBACK_TITLE,
    score = 0,
    comments = 0,
    kind = PostKind.VIDEO,
    mediaLabel = "Video",
    remoteVideoUri = inlineRedditVideoDashUrl(target.mediaId),
    remoteVideoHlsUri = inlineRedditVideoHlsUrl(target.mediaId),
    domain = "v.redd.it",
    externalUrl = target.postId
        ?.let { postId -> "https://www.reddit.com/link/$postId/video/${target.mediaId}/player" }
        ?: "https://$INLINE_REDDIT_VIDEO_HOST/${target.mediaId}",
)

internal const val INLINE_REDDIT_VIDEO_POST_ID_PREFIX = "inline-video-"
internal const val INLINE_REDDIT_VIDEO_FALLBACK_TITLE = "Embedded video"
private const val INLINE_REDDIT_VIDEO_HOST = "v.redd.it"

// REDDITURL02_MEDIA_LINK_TARGET_V1
/**
 * A stand-alone image or GIF post for the media viewer, for a link to
 * Reddit's media hosts. Like the inline video, its id is not a Reddit
 * fullname, so it never replaces a real post and vote/save reject it.
 */
internal fun redditImageLinkPost(target: RedditTarget.Image): Post = Post(
    id = "$REDDIT_IMAGE_LINK_POST_ID_PREFIX${Integer.toHexString(target.url.hashCode())}",
    community = "reddit",
    author = "[linked media]",
    age = "",
    title = if (target.animated) "Linked GIF" else "Linked image",
    score = 0,
    comments = 0,
    kind = if (target.animated) PostKind.GIF else PostKind.IMAGE,
    mediaLabel = if (target.animated) "GIF" else "Image",
    remoteImageUri = target.url,
    domain = runCatching { java.net.URI(target.url).host }.getOrNull(),
    externalUrl = target.url,
)

internal const val REDDIT_IMAGE_LINK_POST_ID_PREFIX = "linked-image-"
