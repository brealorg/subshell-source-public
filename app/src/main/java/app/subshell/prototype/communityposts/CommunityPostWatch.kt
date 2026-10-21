package app.subshell.prototype.communityposts

import android.content.Context
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
internal fun redditPostOrdinal(postId: String): Long? =
    postId.trim().removePrefix("t3_")
        .takeIf { id -> id.length in 1..12 && id.all { it.isDigit() || it in 'a'..'z' } }
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
 * goes back, so a deleted post does not make older ones new again.
 */
internal fun checkForNewPosts(posts: List<Post>, watermark: Long?): NewPostCheck {
    val ordered = posts.mapNotNull { post -> redditPostOrdinal(post.id)?.let { it to post } }
    val next = listOfNotNull(watermark, ordered.maxOfOrNull { it.first }).maxOrNull()
    if (watermark == null) return NewPostCheck(newPosts = emptyList(), watermark = next)
    val fresh = ordered
        .filter { (ordinal, _) -> ordinal > watermark }
        .sortedByDescending { (ordinal, _) -> ordinal }
        .distinctBy { (ordinal, _) -> ordinal }
        .take(MAX_NOTIFIED_POSTS_PER_COMMUNITY)
        .map { (_, post) -> post }
    return NewPostCheck(newPosts = fresh, watermark = next)
}
