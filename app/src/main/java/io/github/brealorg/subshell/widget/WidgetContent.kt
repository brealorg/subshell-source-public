package io.github.brealorg.subshell.widget

import io.github.brealorg.subshell.communityposts.communityPostLink
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.Post

// ISSUE267_WIDGETS_SHOW_WHAT_THE_APP_HAS_STORED_V1 (#267, owner: no setting —
// a widget is something the reader adds themselves): the Inbox and Posts
// widgets show what Subshell already stored for the active signed-in account
// and never ask Reddit themselves. While the app lock hides notification
// content they hide theirs too; NSFW posts follow "Show NSFW posts" and hidden
// communities stay out, as on Home.

internal const val INBOX_WIDGET_ROWS = 8
internal const val POSTS_WIDGET_ROWS = 12

internal data class InboxWidgetRow(
    val id: String,
    val sender: String,
    val title: String,
    val preview: String,
    val unread: Boolean,
    val createdEpochSeconds: Long?,
)

internal sealed interface InboxWidgetContent {
    /** No signed-in Reddit account is active. */
    data object SignedOut : InboxWidgetContent

    /** The app lock hides content outside the app. */
    data object Locked : InboxWidgetContent

    /** Signed in, but the Inbox was never loaded on this device. */
    data class NotStored(val accountStableId: String) : InboxWidgetContent

    data class Shown(
        val accountStableId: String,
        val unreadCount: Int,
        val rows: List<InboxWidgetRow>,
    ) : InboxWidgetContent
}

/** Where a Posts widget takes its posts from: Home, or one community. */
internal data class PostsWidgetSource(val community: String?) {
    val label: String get() = community?.let { name -> "r/$name" } ?: "Home"

    companion object {
        val Home = PostsWidgetSource(community = null)
    }
}

internal data class PostsWidgetRow(
    val id: String,
    val title: String,
    val community: String,
    val score: Int,
    val comments: Int,
    val link: String,
)

internal sealed interface PostsWidgetContent {
    data object SignedOut : PostsWidgetContent

    data object Locked : PostsWidgetContent

    /** Nothing stored for [source] yet: it fills once the reader opens it in the app. */
    data class NotStored(val source: PostsWidgetSource) : PostsWidgetContent

    data class Shown(
        val source: PostsWidgetSource,
        val rows: List<PostsWidgetRow>,
    ) : PostsWidgetContent
}

/** An item counts as unread as the Inbox counts it: unread on Reddit and not opened here. */
internal fun inboxWidgetContent(
    accountStableId: String?,
    items: List<InboxItem>?,
    locallySeenIds: Set<String>,
    hidesContent: Boolean,
    limit: Int = INBOX_WIDGET_ROWS,
): InboxWidgetContent {
    if (hidesContent) return InboxWidgetContent.Locked
    val account = accountStableId ?: return InboxWidgetContent.SignedOut
    val stored = items ?: return InboxWidgetContent.NotStored(account)
    fun InboxItem.isUnread() = unread && id !in locallySeenIds
    return InboxWidgetContent.Shown(
        accountStableId = account,
        unreadCount = stored.count { item -> item.isUnread() },
        rows = stored.take(limit).map { item ->
            InboxWidgetRow(
                id = item.id,
                sender = item.sender,
                title = item.widgetTitle(),
                preview = item.preview,
                unread = item.isUnread(),
                createdEpochSeconds = item.createdEpochSeconds,
            )
        },
    )
}

internal fun postsWidgetContent(
    accountStableId: String?,
    source: PostsWidgetSource,
    posts: List<Post>?,
    hidesContent: Boolean,
    showNsfwPosts: Boolean,
    hiddenCommunities: Set<String>,
    limit: Int = POSTS_WIDGET_ROWS,
): PostsWidgetContent {
    if (hidesContent) return PostsWidgetContent.Locked
    if (accountStableId == null) return PostsWidgetContent.SignedOut
    val stored = posts ?: return PostsWidgetContent.NotStored(source)
    val hidden = hiddenCommunities.mapTo(HashSet()) { name -> name.removePrefix("r/").lowercase() }
    val rows = stored
        .asSequence()
        .filter { post -> !post.pinned }
        .filter { post -> showNsfwPosts || !post.nsfw }
        .filter { post -> post.community.removePrefix("r/").lowercase() !in hidden }
        .distinctBy(Post::id)
        .take(limit)
        .map { post ->
            val community = post.community.removePrefix("r/")
            PostsWidgetRow(
                id = post.id,
                title = post.title,
                community = community,
                score = post.score,
                comments = post.comments,
                link = communityPostLink(community, post),
            )
        }
        .toList()
    return PostsWidgetContent.Shown(source = source, rows = rows)
}

private fun InboxItem.widgetTitle(): String = when (kind) {
    ActivityKind.MESSAGE -> title.ifBlank { "Message" }
    else -> postTitle?.takeIf(String::isNotBlank)?.let { post -> "$title · $post" } ?: title
}

/** "now", "5m", "3h", "2d", "4w", "1y" — the Inbox's own short form. */
internal fun widgetAge(createdEpochSeconds: Long?, nowEpochSeconds: Long): String? {
    val created = createdEpochSeconds ?: return null
    val seconds = (nowEpochSeconds - created).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        seconds < 31_536_000 -> "${seconds / 604_800}w"
        else -> "${seconds / 31_536_000}y"
    }
}

/** 999, 1.2k, 34k, 1.5m. */
internal fun widgetCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 10_000 -> "%.1fk".format(java.util.Locale.ROOT, value / 1_000.0).replace(".0k", "k")
    value < 1_000_000 -> "${value / 1_000}k"
    else -> "%.1fm".format(java.util.Locale.ROOT, value / 1_000_000.0).replace(".0m", "m")
}
