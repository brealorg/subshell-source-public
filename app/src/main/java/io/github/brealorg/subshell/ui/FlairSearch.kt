package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.REDDIT_SEARCH_QUERY_MAX_LENGTH
import io.github.brealorg.subshell.data.RedditSearchOrder
import io.github.brealorg.subshell.data.RedditSearchSort
import io.github.brealorg.subshell.model.Post

/**
 * ISSUE294_FLAIR_OPENS_A_FLAIR_SEARCH_V1 (#294, reader request): a tap on a
 * post's flair searches the post's community for posts with that flair, the
 * same search a Reddit link `r/<name>/search?q=flair_name:"…"&restrict_sr=1`
 * runs. Reddit's search syntax has no escape for a quote, so quotes are left
 * out of the flair. A post on a profile (community u_<name>) has no community
 * search, and a flair without text has nothing to search for: both are null.
 * The newest posts with the flair come first (owner, 2026-11-09).
 */
internal fun flairSearchTarget(post: Post): RedditTarget.Search? {
    val flair = post.flair
        ?.replace("\"", "")
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val community = post.community
        .trim()
        .removePrefix("/")
        .removePrefix("r/")
        .takeIf { name ->
            name.isNotEmpty() &&
                !name.startsWith("u_", ignoreCase = true) &&
                name.all { char -> char.isLetterOrDigit() || char == '_' }
        }
        ?: return null
    val query = "flair_name:\"$flair\""
    if (query.length > REDDIT_SEARCH_QUERY_MAX_LENGTH) return null
    return RedditTarget.Search(query, community, RedditSearchOrder(sort = RedditSearchSort.NEW))
}
