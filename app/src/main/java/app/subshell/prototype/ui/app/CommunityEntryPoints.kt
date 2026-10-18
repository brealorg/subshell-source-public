package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.RedditCommunityPinnedPostsUiState
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditFavoriteUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.model.CommunityDiscoverySort

/**
 * The community runtime as SubshellApp receives it from MainActivity: the
 * community feed refresh/append, the wiki, community info, membership and
 * discovery UI states with their entry points and authorizations. Property
 * names and defaults are the former SubshellApp parameters, unchanged.
 */
data class CommunityEntryPoints(
    val onRefreshRedditCommunityFeed: (
        String,
        String,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    val onAppendRedditCommunityFeed: (
        String,
        String,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    val redditCommunityWikiState: RedditCommunityWikiUiState =
        RedditCommunityWikiUiState(),
    val onLoadRedditCommunityWikiIndex: (String, String, Boolean) -> Unit =
        { _, _, _ -> },
    val onLoadRedditCommunityWikiPage: (String, String, String, Boolean) -> Unit =
        { _, _, _, _ -> },
    val onAuthorizeRedditCommunityWiki: (String, String) -> Unit = { _, _ -> },
    val redditCommunityInfoState: RedditCommunityInfoUiState =
        RedditCommunityInfoUiState(),
    val onLoadRedditCommunityInfo: (String, String, Boolean) -> Unit =
        { _, _, _ -> },
    // ISSUE230_PINNED_POSTS_FIRST_V1
    val redditPinnedPostsState: RedditCommunityPinnedPostsUiState = RedditCommunityPinnedPostsUiState(),
    val onLoadRedditPinnedPosts: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val communityMembershipState: CommunityMembershipUiState =
        CommunityMembershipUiState(),
    val onChangeCommunityMembership: (
        String,
        String,
        CommunityMembershipAction,
    ) -> Unit = { _, _, _ -> },
    val onAuthorizeCommunityMembership: () -> Unit = {},
    val redditCommunityDiscoveryState: RedditCommunityDiscoveryUiState =
        RedditCommunityDiscoveryUiState(),
    val onLoadRedditCommunityDiscovery: (
        String,
        String?,
        CommunityDiscoverySort?,
        Boolean?,
        Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    // ISSUE229_MENTION_SEARCH_HAS_ITS_OWN_SLOT_V1: a composer's u/ suggestions
    // from Reddit (account, query), apart from the Search workspace's people.
    val redditMentionSearchState: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
    val onSearchMentions: (String, String) -> Unit = { _, _ -> },
    // REDDITFAV02_FAVORITE_WRITE_V1
    val redditFavoriteState: RedditFavoriteUiState = RedditFavoriteUiState(),
    val onUpdateRedditFavorite: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val onDismissRedditFavoriteFailure: () -> Unit = {},
    val onAuthorizeRedditFavorite: () -> Unit = {},
    // REDDITFAV04_CUSTOM_FEED_FAVORITES_V1
    val onUpdateRedditCustomFeedFavorite: (String, String, Boolean) -> Unit = { _, _, _ -> },
    // ISSUE211_QUARANTINE_OPT_IN_V1: "View anyway" on a quarantined community.
    val onViewQuarantinedCommunity:
        (suspend (String, String) -> app.subshell.prototype.data.account.QuarantineOptInResult)? = null,
    // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: a random NSFW community for an account.
    val randomNsfwCommunity: (suspend (String) -> String?)? = null,
)
