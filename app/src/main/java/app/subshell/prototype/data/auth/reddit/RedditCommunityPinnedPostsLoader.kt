package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityPinnedPostsLoader
import app.subshell.prototype.data.auth.COMMUNITY_PINNED_POSTS_PAGE_LIMIT
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditListingNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditListingSort
import app.subshell.prototype.model.Post
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call

// ISSUE230_PINNED_POSTS_FIRST_V1: the first Hot page of a community, read with
// the active account's own token, keeps only the pinned posts in Reddit's
// order. Read only, never cached.
internal class RedditCommunityPinnedPostsLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditCommunityPinnedPostsLoader {
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
            sort = RedditListingSort.HOT,
            limit = COMMUNITY_PINNED_POSTS_PAGE_LIMIT,
        )?.filter { it.pinned }
}

/**
 * One page of a community's listing read with [accountStableId]'s own token,
 * only while it is the active signed-in account of the compatibility client
 * with the core read scopes. Read only, never cached; null when the account,
 * token or read does not allow it. Shared by ISSUE230 (pinned posts) and
 * ISSUE239 (new posts in a watched community).
 */
internal suspend fun readCommunityListingWithAccount(
    callFactory: Call.Factory,
    endpoint: NetworkEndpoint,
    userAgent: String,
    configuration: OAuthProviderConfiguration,
    accountCoordinator: AuthAccountSessionCoordinator,
    ioDispatcher: CoroutineDispatcher,
    accountStableId: String,
    communityName: String,
    sort: RedditListingSort,
    limit: Int,
): List<Post>? =
    withContext(ioDispatcher) {
        if (
            configuration.clientMode != OAuthClientMode.REDREADER_COMPATIBILITY ||
            configuration.scopes != RedditOAuth.coreReadScopes
        ) {
            return@withContext null
        }
        val directory = accountCoordinator.snapshot()
        val active = directory.accounts.firstOrNull { it.stableId == directory.activeAccountId }
        if (
            active == null ||
            active.source != AccountSessionSource.AUTHENTICATED ||
            active.stableId != accountStableId
        ) {
            return@withContext null
        }
        val session = (
            accountCoordinator.ensureConfiguredAccessTokenForAccount(accountStableId)
                as? AuthSessionResult.Authenticated
            )?.session ?: return@withContext null
        if (
            session.clientMode != configuration.clientMode ||
            session.clientIdFingerprint != configuration.clientIdFingerprint ||
            !session.tokens.grants(RedditOAuth.coreReadScopes)
        ) {
            return@withContext null
        }
        val source = RedditListingNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider =
                    accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
            ),
        )
        when (
            val result = source.posts(
                community = communityName,
                sort = sort,
                request = RepositoryPageRequest(limit = limit),
            )
        ) {
            is NetworkResult.Success -> result.value.items
            else -> null
        }
    }
