package app.subshell.prototype.data.auth

import app.subshell.prototype.model.Post

// ISSUE230_PINNED_POSTS_FIRST_V1 (owner 2026-10-16): a community's pinned
// (stickied) posts, in Reddit's sticky order, so the feed can show them first
// in every sort. Reddit puts them first only in its Hot listing; null means the
// read failed, and the feed then shows its listing as before.
internal interface AuthenticatedRedditCommunityPinnedPostsLoader {
    suspend fun load(accountStableId: String, communityName: String): List<Post>?
}

/** Reddit allows two stickies; the first Hot page holds them, so a few posts are enough. */
internal const val COMMUNITY_PINNED_POSTS_PAGE_LIMIT = 5
