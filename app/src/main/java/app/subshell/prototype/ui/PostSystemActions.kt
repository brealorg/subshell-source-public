package app.subshell.prototype.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedVisibility
import app.subshell.prototype.data.account.customFeedPathSegments
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.error.ViewportErrorHostState
import java.net.URI

internal fun canonicalRedditPostUrl(post: Post): String {
    val permalink = post.permalink?.trim().orEmpty()
    val permalinkScheme = permalink
        .takeIf(String::isNotBlank)
        ?.let(::webSchemeOrNull)
    return when {
        permalinkScheme != null -> permalink
        permalink.startsWith("/") -> redditWebUrl(permalink)
        else -> {
            val id = post.id.trim().lowercase().removePrefix("t3_")
            redditWebUrl("/comments/$id")
        }
    }
}

internal fun canonicalRedditCommentUrl(post: Post, commentId: String): String {
    val normalizedCommentId = commentId
        .trim()
        .lowercase()
        .removePrefix("t1_")
        .takeIf { value -> value.isNotEmpty() && value.all(Char::isLetterOrDigit) }
        ?: return canonicalRedditPostUrl(post)
    val postUri = runCatching { URI(canonicalRedditPostUrl(post)) }.getOrNull()
    val permalinkPath = postUri?.path
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.trimEnd('/')
    val postId = post.id.trim().lowercase().removePrefix("t3_")
    val commentPath = if (permalinkPath?.contains("/comments/") == true) {
        "$permalinkPath/$normalizedCommentId/"
    } else {
        "/comments/$postId/_/$normalizedCommentId/"
    }
    return redditWebUrl(commentPath)
}

internal fun externalPostUrl(post: Post): String? = post.externalUrl
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.takeIf { url -> webSchemeOrNull(url) != null }

private fun webSchemeOrNull(value: String): String? = runCatching {
    URI(value).scheme?.lowercase()
}
    .getOrNull()
    ?.takeIf { it in setOf("http", "https") }

private fun redditWebUrl(path: String): String = URI(
    REDDIT_WEB_SCHEME,
    REDDIT_WEB_HOST,
    path,
    null,
).toASCIIString()

private const val REDDIT_WEB_SCHEME = "https"
private const val REDDIT_WEB_HOST = "www.reddit.com"

internal fun sharePostLink(context: Context, post: Post, notices: ViewportErrorHostState?) {
    val url = canonicalRedditPostUrl(post)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, post.title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share post"))
    }.onFailure {
        notices.noticeFailure("Couldn't share this post", "No app on this device can share it")
    }
}

internal fun copyPostLink(context: Context, post: Post, notices: ViewportErrorHostState?) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) {
        notices.noticeFailure("Couldn't copy the link", "The clipboard is unavailable")
        return
    }
    clipboard.setPrimaryClip(
        ClipData.newPlainText("Reddit post link", canonicalRedditPostUrl(post)),
    )
    notices.noticeCopied("Link")
}

internal fun shareCommentLink(context: Context, post: Post, commentId: String, notices: ViewportErrorHostState?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, post.title)
        putExtra(Intent.EXTRA_TEXT, canonicalRedditCommentUrl(post, commentId))
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share comment"))
    }.onFailure {
        notices.noticeFailure("Couldn't share this comment", "No app on this device can share it")
    }
}

// REDDITURL05_CUSTOM_FEED_LINK_V1: the web link of a custom feed. Its path
// holds the feed's URL name, which Reddit keeps when the display name is
// changed, so the link is the only place a user can see it.
internal fun canonicalRedditCustomFeedUrl(feed: CustomFeedSummary): String? =
    customFeedPathSegments(feed.path)?.let { segments -> redditWebUrl("/" + segments.joinToString("/") + "/") }

/** Only public feeds open for other people; private and hidden ones do not. */
internal fun customFeedLinkShareable(feed: CustomFeedSummary): Boolean =
    feed.visibility.equals(CustomFeedVisibility.PUBLIC.apiValue, ignoreCase = true) &&
        canonicalRedditCustomFeedUrl(feed) != null

internal fun shareCustomFeedLink(context: Context, feed: CustomFeedSummary, notices: ViewportErrorHostState?) {
    val url = canonicalRedditCustomFeedUrl(feed) ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, feed.displayName)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share feed"))
    }.onFailure {
        notices.noticeFailure("Couldn't share this feed", "No app on this device can share it")
    }
}

internal fun copyCustomFeedLink(context: Context, feed: CustomFeedSummary, notices: ViewportErrorHostState?) {
    val url = canonicalRedditCustomFeedUrl(feed) ?: return
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) {
        notices.noticeFailure("Couldn't copy the link", "The clipboard is unavailable")
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("Reddit custom feed link", url))
    notices.noticeCopied("Link")
}
