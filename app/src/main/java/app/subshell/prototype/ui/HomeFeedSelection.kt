package app.subshell.prototype.ui

import app.subshell.prototype.model.Post

internal fun availableFeedSorts(source: FeedSource): List<FeedSort> = when (source) {
    FeedSource.POPULAR,
    FeedSource.ALL,
    -> FeedSort.entries.filterNot { sort -> sort == FeedSort.BEST }
    FeedSource.HOME,
    FeedSource.SAVED,
    -> FeedSort.entries
}

internal fun normalizedFeedSort(
    source: FeedSource,
    sort: FeedSort,
): FeedSort = if (sort in availableFeedSorts(source)) sort else FeedSort.HOT

/**
 * Canonical local feed selection for the Home destination.
 *
 * HOME contains posts from joined communities.
 * POPULAR and ALL currently expose the complete local fixture corpus.
 * SAVED is independent of subscription state.
 *
 * All sources share stable post identity, hidden-post filtering and sorting.
 */
fun selectHomeFeedPosts(
    posts: List<Post>,
    source: FeedSource,
    sort: FeedSort,
    joinedCommunityIds: Collection<String>,
    savedPostIds: Collection<String>,
    hiddenPostIds: Collection<String>,
    readPostIds: Collection<String> = emptyList(),
    hideReadPosts: Boolean = false,
    contentFilters: ContentFilters = ContentFilters(),
): List<Post> {
    val joined = joinedCommunityIds.mapTo(linkedSetOf()) { it.lowercase() }
    val saved = savedPostIds.toHashSet()
    val hidden = hiddenPostIdentities(hiddenPostIds)
    val read = readPostIds.toHashSet()

    val selected = posts
        .distinctBy(Post::id)
        .asSequence()
        .filterNot { it.isHiddenBy(hidden) }
        .filterNot { hideReadPosts && it.id in read }
        .filterNot(contentFilters::hides)
        .filter { post ->
            when (source) {
                FeedSource.HOME -> post.community.lowercase() in joined
                FeedSource.POPULAR, FeedSource.ALL -> true
                FeedSource.SAVED -> post.id in saved
            }
        }
        .toList()

    return when (sort) {
        FeedSort.BEST -> selected.sortedByDescending { it.score + it.comments * 2 }
        FeedSort.HOT -> selected.sortedByDescending(Post::comments)
        FeedSort.NEW -> selected.reversed()
        FeedSort.TOP -> selected.sortedByDescending(Post::score)
        FeedSort.CONTROVERSIAL -> selected.sortedByDescending { it.comments * 4 - it.score }
        FeedSort.RISING -> selected.sortedByDescending { it.score / (it.comments + 1f) }
    }
}

fun selectLiveHomePosts(
    posts: List<Post>,
    hiddenPostIds: Collection<String>,
    readPostIds: Collection<String> = emptyList(),
    hideReadPosts: Boolean = false,
    contentFilters: ContentFilters = ContentFilters(),
): List<Post> {
    val hidden = hiddenPostIdentities(hiddenPostIds)
    val read = readPostIds.toHashSet()
    return posts
        .distinctBy(Post::id)
        .filterNot { post -> post.isHiddenBy(hidden) }
        .filterNot { post -> hideReadPosts && post.id in read }
        .filterNot(contentFilters::hides)
}
