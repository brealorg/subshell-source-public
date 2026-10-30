package app.subshell.prototype.communityposts

import android.content.Context
import app.subshell.prototype.data.auth.CommunityComment
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.Post

// ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1 (owner 2026-10-21): the reader
// picks communities to be notified about; nothing is watched by default.

/** Owner: at most 10 per account (each costs one Reddit read per check). */
internal const val MAX_WATCHED_COMMUNITIES = 10

/** Owner: one notification per new post; a check notifies at most this many per community. */
internal const val MAX_NOTIFIED_POSTS_PER_COMMUNITY = 5

internal const val COMMUNITY_POST_WATCH_PREFERENCES = "subshell_preferences"
private const val COMMUNITY_POST_WATCH_KEY_PREFIX = "community_post_watch:"

/** The watched communities of [accountStableId] in subshell_preferences (backed up). */
internal fun communityPostWatchPreferenceKey(accountStableId: String): String =
    COMMUNITY_POST_WATCH_KEY_PREFIX + accountStableId

/** Valid Reddit names, lowercased, distinct, sorted, at most [MAX_WATCHED_COMMUNITIES]. */
internal fun normalizeWatchedCommunityNames(names: Iterable<String>): List<String> =
    names
        .mapNotNull { name -> runCatching { normalizeRedditCommunityName(name) }.getOrNull() }
        .distinct()
        .sorted()
        .take(MAX_WATCHED_COMMUNITIES)

internal fun readWatchedCommunityNames(context: Context, accountStableId: String): List<String> =
    runCatching {
        context.getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(communityPostWatchPreferenceKey(accountStableId), emptySet())
            .orEmpty()
    }.map(::normalizeWatchedCommunityNames).getOrDefault(emptyList())

/**
 * Reddit post ids are base 36 and grow with every new post, so "newer than
 * the newest seen" needs no timestamp (Post carries only a relative age).
 */
internal fun redditPostOrdinal(postId: String): Long? = redditThingOrdinal(postId.trim().removePrefix("t3_"))

/** Comment ids (t1_) grow the same way. */
internal fun redditCommentOrdinal(commentId: String): Long? = redditThingOrdinal(commentId.trim().removePrefix("t1_"))

// Comment ids reached 13 characters in 2026 ("19rum59qz15eq"); 13 base-36
// digits still fit a Long up to "1y2p0ij32e8e7", beyond that toLongOrNull is null.
private fun redditThingOrdinal(id: String): Long? =
    id.takeIf { it.length in 1..13 && it.all { char -> char.isDigit() || char in 'a'..'z' } }
        ?.toLongOrNull(36)

internal data class NewPostCheck(
    /** Newest first, at most [MAX_NOTIFIED_POSTS_PER_COMMUNITY]. */
    val newPosts: List<Post>,
    /** The newest post id seen so far; null while the community shows nothing. */
    val watermark: Long?,
)

/**
 * The first check of a community (no [watermark]) is a silent baseline; later
 * a post is new when its id is above the newest one seen. The watermark never
 * goes back, so a deleted post does not make older ones new again. The
 * reader's own posts ([ownUsername]) move the watermark but never notify
 * (ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1, owner: "not mine").
 */
internal fun checkForNewPosts(posts: List<Post>, watermark: Long?, ownUsername: String? = null): NewPostCheck {
    val check = checkForNewItems(posts, watermark, { redditPostOrdinal(it.id) }) { post -> isOwn(post.author, ownUsername) }
    return NewPostCheck(newPosts = check.first, watermark = check.second)
}

internal data class NewCommentCheck(
    /** Newest first, at most [MAX_NOTIFIED_POSTS_PER_COMMUNITY]. */
    val newComments: List<CommunityComment>,
    val watermark: Long?,
)

/** As [checkForNewPosts], for a community's comment stream. */
internal fun checkForNewComments(
    comments: List<CommunityComment>,
    watermark: Long?,
    ownUsername: String? = null,
): NewCommentCheck {
    val check = checkForNewItems(comments, watermark, { redditCommentOrdinal(it.id) }) { comment ->
        isOwn(comment.author, ownUsername)
    }
    return NewCommentCheck(newComments = check.first, watermark = check.second)
}

private fun isOwn(author: String, ownUsername: String?): Boolean =
    ownUsername != null && author.trim().removePrefix("u/").equals(ownUsername.trim().removePrefix("u/"), ignoreCase = true)

private fun <T> checkForNewItems(
    items: List<T>,
    watermark: Long?,
    ordinal: (T) -> Long?,
    own: (T) -> Boolean,
): Pair<List<T>, Long?> {
    val ordered = items.mapNotNull { item -> ordinal(item)?.let { it to item } }
    val next = listOfNotNull(watermark, ordered.maxOfOrNull { it.first }).maxOrNull()
    if (watermark == null) return emptyList<T>() to next
    val fresh = ordered
        .filter { (ordinal, item) -> ordinal > watermark && !own(item) }
        .sortedByDescending { (ordinal, _) -> ordinal }
        .distinctBy { (ordinal, _) -> ordinal }
        .take(MAX_NOTIFIED_POSTS_PER_COMMUNITY)
        .map { (_, item) -> item }
    return fresh to next
}
