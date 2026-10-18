package app.subshell.prototype.data.account

import app.subshell.prototype.model.Post

// ISSUE230_PINNED_POSTS_FIRST_V1: the pinned posts of the community open for one account.
data class RedditCommunityPinnedPostsUiState(
    val accountStableId: String? = null,
    val communityName: String? = null,
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
) {
    fun matches(accountStableId: String, communityName: String): Boolean =
        this.accountStableId == accountStableId &&
            this.communityName.equals(communityName, ignoreCase = true)

    /** The pinned posts for exactly this account and community, else none. */
    fun postsFor(accountStableId: String, communityName: String): List<Post> =
        if (matches(accountStableId, communityName)) posts else emptyList()
}
