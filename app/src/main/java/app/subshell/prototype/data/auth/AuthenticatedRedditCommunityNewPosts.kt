package app.subshell.prototype.data.auth

import app.subshell.prototype.model.Post

// ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1 (owner 2026-10-21): the newest
// posts of a community the reader watches, newest first, for the background
// check; null means the read failed or the account may not read it now.
internal interface AuthenticatedRedditCommunityNewPostsLoader {
    suspend fun load(accountStableId: String, communityName: String): List<Post>?
}

/** One small New page per check: enough to notice what arrived in 15 minutes. */
internal const val COMMUNITY_NEW_POSTS_PAGE_LIMIT = 10
