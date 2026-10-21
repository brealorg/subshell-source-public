package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityNewPostsLoader
import app.subshell.prototype.data.auth.COMMUNITY_NEW_POSTS_PAGE_LIMIT
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.reddit.RedditListingSort
import app.subshell.prototype.model.Post
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.Call

// ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1: the first New page of a
// community, read with the active account's own token. Read only, never cached.
internal class RedditCommunityNewPostsLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditCommunityNewPostsLoader {
    override suspend fun load(accountStableId: String, communityName: String): List<Post>? =
        readCommunityListingWithAccount(
            callFactory = callFactory,
            endpoint = endpoint,
            userAgent = userAgent,
            configuration = configuration,
            accountCoordinator = accountCoordinator,
            ioDispatcher = ioDispatcher,
            accountStableId = accountStableId,
            communityName = communityName,
            sort = RedditListingSort.NEW,
            limit = COMMUNITY_NEW_POSTS_PAGE_LIMIT,
        )
}
