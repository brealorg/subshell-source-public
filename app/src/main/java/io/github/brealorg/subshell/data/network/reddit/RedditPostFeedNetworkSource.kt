package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.network.NetworkPage
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.sync.PostFeedNetworkSource
import io.github.brealorg.subshell.model.Post

internal class RedditPostFeedNetworkSource(
    private val dataSource: RedditListingNetworkDataSource,
    private val community: String? = null,
    private val sort: RedditListingSort = RedditListingSort.BEST,
    private val timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
) : PostFeedNetworkSource {
    override suspend fun fetch(
        request: RepositoryPageRequest,
    ): NetworkResult<NetworkPage<Post>> = dataSource.posts(
        community = community,
        sort = sort,
        timePeriod = timePeriod,
        request = request,
    )
}

internal class RedditSearchPostFeedNetworkSource(
    private val dataSource: RedditListingNetworkDataSource,
    private val query: String,
    private val community: String? = null,
    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1
    private val includeOver18: Boolean? = null,
    private val order: io.github.brealorg.subshell.data.RedditSearchOrder =
        io.github.brealorg.subshell.data.RedditSearchOrder.DEFAULT,
) : PostFeedNetworkSource {
    override suspend fun fetch(
        request: RepositoryPageRequest,
    ): NetworkResult<NetworkPage<Post>> = dataSource.searchPosts(
        query = query,
        community = community,
        request = request,
        includeOver18 = includeOver18,
        order = order,
    ).also { result ->
        // ISSUE211_SEARCH_NSFW_COUNT_IS_LOGGED_V1: whether Reddit honours
        // include_over_18 shows in how many NSFW posts a page carries (debug only).
        if (result is NetworkResult.Success) {
            io.github.brealorg.subshell.auth.authDebugLog(
                "subshell-nsfw",
                "search include_over_18=$includeOver18 results=${result.value.items.size} " +
                    "nsfw=${result.value.items.count { post -> post.nsfw }}",
            )
        }
    }
}

internal class RedditUserSubmittedPostFeedNetworkSource(
    private val dataSource: RedditListingNetworkDataSource,
    private val username: String,
    private val order: io.github.brealorg.subshell.data.RedditProfilePostsOrder =
        io.github.brealorg.subshell.data.RedditProfilePostsOrder.DEFAULT,
) : PostFeedNetworkSource {
    override suspend fun fetch(
        request: RepositoryPageRequest,
    ): NetworkResult<NetworkPage<Post>> = dataSource.userSubmittedPosts(
        username = username,
        request = request,
        order = order,
    )
}
