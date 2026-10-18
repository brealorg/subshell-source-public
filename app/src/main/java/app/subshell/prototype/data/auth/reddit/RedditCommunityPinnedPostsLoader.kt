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
                    sort = RedditListingSort.HOT,
                    request = RepositoryPageRequest(limit = COMMUNITY_PINNED_POSTS_PAGE_LIMIT),
                )
            ) {
                is NetworkResult.Success -> result.value.items.filter { it.pinned }
                else -> null
            }
        }
}
