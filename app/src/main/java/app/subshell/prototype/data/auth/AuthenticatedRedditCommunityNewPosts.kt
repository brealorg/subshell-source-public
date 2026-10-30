package app.subshell.prototype.data.auth

import app.subshell.prototype.model.Post

// ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1 (owner 2026-10-21): the newest
// posts of a community the reader watches, newest first, for the background
// check; null means the read failed or the account may not read it now.
internal interface AuthenticatedRedditCommunityNewPostsLoader {
    suspend fun load(accountStableId: String, communityName: String): List<Post>?

    /** ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1: the community's newest comments, newest first. */
    suspend fun loadComments(accountStableId: String, communityName: String): List<CommunityComment>? = null
}

/** A comment from a community's comment stream, with what a notification needs. */
internal data class CommunityComment(
    /** The comment's id, with or without the t1_ prefix. */
    val id: String,
    val author: String,
    val body: String,
    val postTitle: String,
    /** The comment's own permalink (/r/x/comments/post/title/comment/). */
    val permalink: String?,
    /** The post it belongs to is NSFW. */
    val nsfw: Boolean,
)

/** One small New page per check: enough to notice what arrived in 15 minutes. */
internal const val COMMUNITY_NEW_POSTS_PAGE_LIMIT = 10

/** Comments come faster than posts; one page of the community's comment stream. */
internal const val COMMUNITY_NEW_COMMENTS_PAGE_LIMIT = 25
