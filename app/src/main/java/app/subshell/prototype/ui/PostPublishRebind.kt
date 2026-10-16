package app.subshell.prototype.ui

import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind

// COMPOSER05_POST_PUBLISH_CANONICAL_REBIND_V1
// A text/link/media post opened after a successful submit is built from the
// request (local content:// media, the scraped link preview, score 1). Reddit's
// own copy replaces it once read back, but only when it is the same post and,
// for media, already carries the media: a just-uploaded image, video or gallery
// may still be processing, and the local copy keeps showing it until then.
internal fun canonicalTextPostCompletionOrFallback(
    submitted: SubmittedTextPost,
    fallback: Post,
    candidate: Post?,
): Post {
    val resolved = candidate ?: return fallback
    if (
        !redditTargetMatchesPost(submitted.id, resolved.id) ||
        !resolved.community.equals(submitted.community, ignoreCase = true) ||
        resolved.title != submitted.title
    ) {
        return fallback
    }
    val hasRemoteVideo = resolved.remoteVideoUri != null ||
        resolved.remoteVideoHlsUri != null ||
        resolved.remoteVideoFallbackUri != null
    val complete = when (fallback.kind) {
        PostKind.TEXT -> resolved.kind == PostKind.TEXT
        PostKind.LINK -> resolved.kind != PostKind.TEXT
        PostKind.IMAGE, PostKind.GIF -> resolved.remoteImageUri != null || hasRemoteVideo
        PostKind.VIDEO -> hasRemoteVideo
        PostKind.GALLERY -> resolved.gallery.size == fallback.gallery.size &&
            resolved.gallery.all { item -> item.remoteImageUri != null }
        else -> false
    }
    return if (complete) resolved else fallback
}
