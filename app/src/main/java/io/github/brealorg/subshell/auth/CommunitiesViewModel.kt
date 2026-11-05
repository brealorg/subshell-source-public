package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.RedditCommunityPinnedPostsUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import io.github.brealorg.subshell.data.account.RedditCommunityDiscoveryUiState
import io.github.brealorg.subshell.data.account.RedditPeopleSearchUiState
import io.github.brealorg.subshell.data.account.RedditCommunityInfoUiState
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiState
import io.github.brealorg.subshell.model.CommunityDiscoverySort
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.data.auth.AuthBrowserLaunchResult
import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.data.account.CustomFeedCreateRequest
import io.github.brealorg.subshell.data.account.CustomFeedDeleteRequest
import io.github.brealorg.subshell.data.account.CustomFeedPostsUiState
import io.github.brealorg.subshell.data.account.CustomFeedUpdateRequest
import io.github.brealorg.subshell.data.account.CommunityMembershipAction
import io.github.brealorg.subshell.data.account.CommunityMembershipFailureKind
import io.github.brealorg.subshell.data.account.CommunityMembershipPhase
import io.github.brealorg.subshell.data.account.CommunityMembershipRequest
import io.github.brealorg.subshell.data.account.CommunityMembershipUiState
import io.github.brealorg.subshell.data.account.RedditFavoriteUiState
import io.github.brealorg.subshell.data.auth.reddit.RedditFavoriteRequest

/**
 * AUTHSPLIT04_COMMUNITIES_VIEWMODEL_V1: the communities area's state holder
 * (Google: one ViewModel per screen area): subscriptions, joining and leaving
 * (with the membership read-back), Reddit favorites, custom feeds (lists,
 * posts and writes; a custom feed favorite reads its list back), community
 * discovery and people search, community info, pinned posts, the wiki and
 * NSFW/quarantine access. Moved out of AuthConnectViewModel verbatim; it
 * shares the auth runtime through [SharedAuthRuntime] and registers its
 * owners' capability callback routes there.
 */
internal class CommunitiesViewModel internal constructor(
    private val shared: SharedAuthRuntime,
) : ViewModel() {
    private val runtime = shared.runtime

    companion object {
        val Factory = viewModelFactory {
            initializer { CommunitiesViewModel(requireNotNull(this[AuthRuntimeKey])) }
        }
    }

    private fun showNsfwPostsSetting(): Boolean = shared.showNsfwPosts()

    private fun activeAuthenticatedProfileAccountId(): String? = shared.activeAuthenticatedAccountId()

    // #216: the #211 NSFW preference read, random NSFW community and
    // quarantine opt-in live in CommunityAccessCoordinator.
    private val communityAccessCoordinator = CommunityAccessCoordinator(
        accessService = runtime.communityAccessService,
        discoveryLoader = runtime.redditCommunityDiscoveryLoader,
        activeAccountId = { runtime.accountSessionDirectory?.snapshot()?.activeAccountId },
    )
    suspend fun readNsfwAccountPreferences(account: String): io.github.brealorg.subshell.data.account.NsfwPreferencesResult =
        communityAccessCoordinator.readNsfwAccountPreferences(account)
    // REDDITCHECK01_CHECK_REDDIT_ACCESS_V1
    suspend fun checkRedditAccess(account: String): io.github.brealorg.subshell.data.account.RedditAccessCheckResult =
        communityAccessCoordinator.checkRedditAccess(account)
    suspend fun randomNsfwCommunity(account: String): io.github.brealorg.subshell.model.RandomNsfwCommunityPick =
        communityAccessCoordinator.randomNsfwCommunity(account).also { pick ->
            if (pick is io.github.brealorg.subshell.model.RandomNsfwCommunityPick.NoneOffered) {
                recordNsfwCommunitiesNoneOffered(account, pick)
            }
        }

    // ISSUE263_NO_NSFW_COMMUNITY_OFFERED_IS_RECORDED_V1 (#263): Reddit offering
    // no NSFW community depends on the account (and maybe its client or
    // region), so the diagnostics log keeps how many communities each read
    // returned and the account's own NSFW preferences, read after the notice
    // shows. Counts and booleans only, no names.
    private fun recordNsfwCommunitiesNoneOffered(
        account: String,
        pick: io.github.brealorg.subshell.model.RandomNsfwCommunityPick.NoneOffered,
    ) {
        viewModelScope.launch {
            val preferences = (
                communityAccessCoordinator.readNsfwAccountPreferences(account)
                    as? io.github.brealorg.subshell.data.account.NsfwPreferencesResult.Known
                )?.preferences
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                io.github.brealorg.subshell.data.diagnostics.DiagnosticEventStore(shared.application)
                    .recordNsfwCommunitiesNoneOffered(
                        popularCommunities = pick.popularCommunities,
                        searchedCommunities = pick.searchedCommunities,
                        accountOver18 = preferences?.over18,
                        accountSearchOver18 = preferences?.searchIncludeOver18,
                    )
            }
        }
    }
    suspend fun optInToQuarantinedCommunity(
        account: String,
        communityName: String,
    ): io.github.brealorg.subshell.data.account.QuarantineOptInResult =
        communityAccessCoordinator.optInToQuarantinedCommunity(account, communityName)

    // SUBSHELL_CUSTOMFEED01_COORDINATOR_V1: custom-feed reads, writes and
    // outcome reconciliation live in CustomFeedCoordinator.
    private val customFeedCoordinator = CustomFeedCoordinator(
        feedReader = runtime.customFeedReader,
        feedCreator = runtime.customFeedCreator,
        writeCapability = runtime.customFeedWriteCapability,
        writeClientMode = runtime.customFeedWriteClientMode,
        scope = viewModelScope,
    )
    private var communityMembershipReconciliationJob: Job? = null
    // SUBSHELL_COMMUNITYDISCOVERY01_COORDINATOR_V1 / COMMUNITYINFO01: the
    // discovery/people-search and community-info reads live in their
    // coordinators.
    private val communityDiscoveryCoordinator = CommunityDiscoveryCoordinator(
        discoveryLoader = runtime.redditCommunityDiscoveryLoader,
        scope = viewModelScope,
        showNsfwPosts = ::showNsfwPostsSetting,
    )
    private val communityInfoCoordinator = CommunityInfoCoordinator(
        infoLoader = runtime.redditCommunityInfoLoader,
        scope = viewModelScope,
    )
    // ISSUE230_PINNED_POSTS_FIRST_V1
    private val communityPinnedPostsCoordinator = CommunityPinnedPostsCoordinator(
        loader = runtime.redditCommunityPinnedPostsLoader,
        scope = viewModelScope,
    )
    // WIKI01A2_VIEWMODEL_WIKI_STATE_V1
    // SUBSHELL_COMMUNITYWIKI01_COORDINATOR_V1: wiki reads and their read
    // authorization live in CommunityWikiCoordinator.
    private val communityWikiCoordinator = CommunityWikiCoordinator(
        wikiLoader = runtime.redditCommunityWikiLoader,
        wikiAuthorizer = runtime.communityWikiReadAuthorizer,
        wikiClientMode = runtime.communityWikiReadClientMode,
        activeAuthenticatedAccountStableId = ::activeAuthenticatedProfileAccountId,
        scope = viewModelScope,
    )
    private val communityMembershipCoordinator =
        CommunityMembershipCoordinator(
            capability = runtime.communityMembershipCapability,
            writer = runtime.communityMembershipWriter,
            clientMode = runtime.communityMembershipClientMode,
            activeAccountStableId = {
                runtime.accountSessionDirectory
                    ?.snapshot()
                    ?.activeAccountId
            },
            onMembershipUpdated = ::scheduleCommunityMembershipReconciliation,
        )

    val communityMembershipUiState:
        StateFlow<CommunityMembershipUiState> =
        communityMembershipCoordinator.uiState

    private val redditSubscriptionsCoordinator = RedditSubscriptionsCoordinator(
        redditSubscriptionsLoader = runtime.redditSubscriptionsLoader,
        communityMembershipCoordinator = communityMembershipCoordinator,
        scope = viewModelScope,
    )

    // REDDITFAV02_FAVORITE_WRITE_V1
    private val redditFavoriteCoordinator = RedditFavoriteCoordinator(
        writer = runtime.redditFavoriteWriter,
        capability = runtime.redditFavoriteCapability,
        clientMode = runtime.redditFavoriteClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory
                ?.snapshot()
                ?.activeAccountId
        },
        readBack = ::readBackRedditFavorite,
        scope = viewModelScope,
    )

    val redditFavoriteUiState: StateFlow<RedditFavoriteUiState> =
        redditFavoriteCoordinator.uiState

    val customFeedsUiState: StateFlow<CustomFeedsUiState> =
        customFeedCoordinator.customFeedsUiState

    val customFeedPostsUiState: StateFlow<CustomFeedPostsUiState> =
        customFeedCoordinator.customFeedPostsUiState

    fun loadCustomFeeds(accountStableId: String, forceRefresh: Boolean) =
        customFeedCoordinator.loadCustomFeeds(accountStableId, forceRefresh)

    fun resolveCustomFeed(
        accountStableId: String,
        feedPath: String,
        onResolved: (io.github.brealorg.subshell.data.account.CustomFeedSummary?) -> Unit,
    ) = customFeedCoordinator.resolveCustomFeed(accountStableId, feedPath, onResolved)

    suspend fun beginCustomFeedCreation(request: CustomFeedCreateRequest): AuthBrowserLaunchResult =
        customFeedCoordinator.beginCustomFeedCreation(request)

    fun customFeedAuthorizationBrowserLaunchFailed() =
        customFeedCoordinator.customFeedAuthorizationBrowserLaunchFailed()

    fun resetCustomFeedCreateState() =
        customFeedCoordinator.resetCustomFeedCreateState()

    suspend fun beginCustomFeedUpdate(request: CustomFeedUpdateRequest): AuthBrowserLaunchResult =
        customFeedCoordinator.beginCustomFeedUpdate(request)

    suspend fun beginCustomFeedDeletion(request: CustomFeedDeleteRequest): AuthBrowserLaunchResult =
        customFeedCoordinator.beginCustomFeedDeletion(request)

    fun resetCustomFeedManageState() =
        customFeedCoordinator.resetCustomFeedManageState()

    fun loadCustomFeedPosts(
        accountStableId: String,
        feedPath: String,
        forceRefresh: Boolean,
    ) =
        customFeedCoordinator.loadCustomFeedPosts(accountStableId, feedPath, forceRefresh)

    fun appendCustomFeedPosts(accountStableId: String, feedPath: String) =
        customFeedCoordinator.appendCustomFeedPosts(accountStableId, feedPath)

    val redditSubscriptionsUiState: StateFlow<RedditSubscriptionsUiState> =
        redditSubscriptionsCoordinator.redditSubscriptionsUiState

    val redditCommunityDiscoveryUiState:
        StateFlow<RedditCommunityDiscoveryUiState> =
        communityDiscoveryCoordinator.redditCommunityDiscoveryUiState

    val redditCommunityInfoUiState:
        StateFlow<RedditCommunityInfoUiState> =
        communityInfoCoordinator.redditCommunityInfoUiState

    fun loadRedditCommunityDiscovery(
        accountStableId: String,
        searchQuery: String?,
        searchSort: CommunityDiscoverySort?,
        autocompleteIncludeOver18: Boolean?,
        forceRefresh: Boolean,
    ) =
        communityDiscoveryCoordinator.loadRedditCommunityDiscovery(accountStableId, searchQuery, searchSort, autocompleteIncludeOver18, forceRefresh)

    val redditMentionSearchUiState: StateFlow<RedditPeopleSearchUiState> =
        communityDiscoveryCoordinator.redditMentionSearchUiState

    fun loadRedditMentionSearch(accountStableId: String, query: String) =
        communityDiscoveryCoordinator.loadRedditMentionSearch(accountStableId, query)

    // ISSUE230_PINNED_POSTS_FIRST_V1
    val redditCommunityPinnedPostsUiState: StateFlow<RedditCommunityPinnedPostsUiState> =
        communityPinnedPostsCoordinator.uiState

    fun loadRedditCommunityPinnedPosts(accountStableId: String, communityName: String, forceRefresh: Boolean) =
        communityPinnedPostsCoordinator.load(accountStableId, communityName, forceRefresh)

    // COMMUNITYINFO01A2_VIEWMODEL_LATEST_REQUEST_WIRING_V1
    fun loadRedditCommunityInfo(
        accountStableId: String,
        rawCommunityName: String,
        forceRefresh: Boolean = false,
    ) =
        communityInfoCoordinator.loadRedditCommunityInfo(accountStableId, rawCommunityName, forceRefresh)

    val redditCommunityWikiUiState:
        StateFlow<RedditCommunityWikiUiState> =
        communityWikiCoordinator.redditCommunityWikiUiState

    fun loadRedditCommunityWikiIndex(
        accountStableId: String,
        rawCommunityName: String,
        forceRefresh: Boolean = false,
    ) =
        communityWikiCoordinator.loadRedditCommunityWikiIndex(accountStableId, rawCommunityName, forceRefresh)

    fun loadRedditCommunityWikiPage(
        accountStableId: String,
        rawCommunityName: String,
        rawPagePath: String,
        forceRefresh: Boolean = false,
    ) =
        communityWikiCoordinator.loadRedditCommunityWikiPage(accountStableId, rawCommunityName, rawPagePath, forceRefresh)

    suspend fun beginRedditCommunityWikiAuthorization(
        accountStableId: String,
        rawCommunityName: String,
    ): AuthBrowserLaunchResult =
        communityWikiCoordinator.beginRedditCommunityWikiAuthorization(accountStableId, rawCommunityName)

    fun redditCommunityWikiAuthorizationBrowserLaunchFailed() =
        communityWikiCoordinator.redditCommunityWikiAuthorizationBrowserLaunchFailed()

    fun updateCommunityMembership(
        accountStableId: String,
        communityName: String,
        action: CommunityMembershipAction,
    ) {
        communityMembershipReconciliationJob?.cancel()
        viewModelScope.launch {
            communityMembershipCoordinator.update(
                CommunityMembershipRequest(
                    accountStableId = accountStableId,
                    communityName = communityName,
                    action = action,
                ),
            )
        }
    }

    private fun scheduleCommunityMembershipReconciliation(
        request: CommunityMembershipRequest,
    ) {
        communityMembershipReconciliationJob?.cancel()
        communityMembershipReconciliationJob = viewModelScope.launch {
            for (delayMs in COMMUNITY_MEMBERSHIP_RECONCILIATION_DELAYS_MS) {
                if (delayMs > 0L) delay(delayMs)
                val current = communityMembershipCoordinator.uiState.value
                val pending = current.matches(
                    request.accountStableId,
                    request.communityName,
                ) && (
                    current.phase == CommunityMembershipPhase.SUCCEEDED ||
                        (
                            current.phase == CommunityMembershipPhase.FAILED &&
                                current.failureKind ==
                                CommunityMembershipFailureKind.OUTCOME_UNKNOWN
                        )
                )
                if (!pending) return@launch
                // MED2_MEMBERSHIP_RECONCILIATION_STOPS_ON_ACCOUNT_SWITCH_V1
                // (OCB-403): after a switch the old account's refresh would
                // cancel the new account's subscriptions load.
                if (runtime.accountSessionDirectory?.snapshot()?.activeAccountId != request.accountStableId) {
                    return@launch
                }

                loadRedditSubscriptions(
                    accountStableId = request.accountStableId,
                    forceRefresh = true,
                )
                redditSubscriptionsCoordinator.redditSubscriptionsJob?.join()

                val reconciled = communityMembershipCoordinator.uiState.value
                if (
                    !reconciled.matches(
                        request.accountStableId,
                        request.communityName,
                    ) ||
                    reconciled.phase == CommunityMembershipPhase.IDLE
                ) {
                    return@launch
                }
            }
            communityMembershipCoordinator.reconciliationExhausted(request)
        }
    }

    suspend fun beginCommunityMembershipAuthorization():
        AuthBrowserLaunchResult =
        communityMembershipCoordinator.beginAuthorization()

    fun communityMembershipAuthorizationBrowserLaunchFailed() {
        communityMembershipCoordinator.browserLaunchFailed()
    }

    fun cancelCommunityMembershipAuthorization() {
        communityMembershipCoordinator.cancelPendingAuthorization()
    }

    fun loadCachedRedditSubscriptions(accountStableId: String) {
        redditSubscriptionsCoordinator.loadCachedRedditSubscriptions(accountStableId)
    }

    fun loadRedditSubscriptions(
        accountStableId: String,
        forceRefresh: Boolean,
    ) {
        redditSubscriptionsCoordinator.loadRedditSubscriptions(accountStableId, forceRefresh)
    }

    fun updateRedditFavorite(
        accountStableId: String,
        communityName: String,
        makeFavorite: Boolean,
    ) {
        redditFavoriteCoordinator.update(accountStableId, communityName, makeFavorite)
    }

    // REDDITFAV04_CUSTOM_FEED_FAVORITES_V1
    fun updateRedditCustomFeedFavorite(
        accountStableId: String,
        customFeedPath: String,
        makeFavorite: Boolean,
    ) {
        val displayName = customFeedCoordinator.customFeedsUiState.value.feeds
            .firstOrNull { feed -> feed.path.equals(customFeedPath, ignoreCase = true) }
            ?.displayName
            ?: customFeedPath.trimEnd('/').substringAfterLast('/')
        redditFavoriteCoordinator.update(accountStableId, displayName, makeFavorite, customFeedPath)
    }

    fun dismissRedditFavoriteFailure() {
        redditFavoriteCoordinator.dismissFailure()
    }

    // REDDITFAV03_FAVORITE_AUTHORIZATION_V1
    suspend fun beginRedditFavoriteAuthorization(): AuthBrowserLaunchResult =
        redditFavoriteCoordinator.beginAuthorization()

    fun redditFavoriteAuthorizationBrowserLaunchFailed() {
        redditFavoriteCoordinator.browserLaunchFailed()
    }

    // Whether a fresh read of the request's list shows it as a favorite;
    // null when the read failed or another account took over meanwhile.
    private suspend fun readBackRedditFavorite(request: RedditFavoriteRequest): Boolean? {
        val path = request.customFeedPath ?: return readBackRedditFavorites(request.accountStableId)
            ?.any { name -> name.equals(request.communityName, ignoreCase = true) }
        customFeedCoordinator.customFeedLoadJob?.join()
        customFeedCoordinator.loadCustomFeeds(request.accountStableId, forceRefresh = true)
        customFeedCoordinator.customFeedLoadJob?.join()
        val read = customFeedCoordinator.customFeedsUiState.value
        if (read.accountStableId != request.accountStableId || read.loading || read.failureKind != null) {
            return null
        }
        return read.feeds.any { feed -> feed.path.equals(path, ignoreCase = true) && feed.redditFavorite }
    }

    private suspend fun readBackRedditFavorites(accountStableId: String): Set<String>? {
        redditSubscriptionsCoordinator.redditSubscriptionsJob?.join()
        redditSubscriptionsCoordinator.loadRedditSubscriptions(accountStableId, forceRefresh = true)
        redditSubscriptionsCoordinator.redditSubscriptionsJob?.join()
        val read = redditSubscriptionsCoordinator.redditSubscriptionsUiState.value
        return read.favoriteCommunityNames.takeIf {
            read.matches(accountStableId) &&
                read.loaded &&
                !read.fromCache &&
                !read.loading &&
                read.failureKind == null
        }
    }

    init {
        shared.registerCapabilityRoutes(
            mapOf(
                AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        communityMembershipCoordinator
                            .handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        communityMembershipCoordinator
                            .resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.REDDIT_FAVORITE to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        redditFavoriteCoordinator
                            .handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        redditFavoriteCoordinator
                            .resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ to CapabilityCallbackRoute(
                    handle = { resultToken, authorizationContextKey ->
                        communityWikiCoordinator.completeCommunityWikiAuthorization(
                            resultToken = resultToken,
                            contextKey = authorizationContextKey,
                        )
                    },
                    resume = { authorizationContextKey ->
                        communityWikiCoordinator.resumeCommunityWikiAuthorization(
                            contextKey = authorizationContextKey,
                        )
                    },
                ),

                AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE to CapabilityCallbackRoute(
                    handle = { _, _ ->
                        val capability = runtime.customFeedWriteCapability
                        if (capability?.hasStoredCallback() == true) {
                            customFeedCoordinator.completeCustomFeedAuthorization(capability)
                        } else {
                            capability?.cancelPendingAuthorization()
                            customFeedCoordinator.failPendingCustomFeedAuthorization(
                                "Custom feed authorization was cancelled",
                            )
                        }
                    },
                    resume = {
                        val capability = runtime.customFeedWriteCapability
                        when {
                            capability?.hasStoredCallback() == true ->
                                customFeedCoordinator.completeCustomFeedAuthorization(capability)
                            capability?.hasPendingAuthorization() == true -> Unit
                            else -> {
                                customFeedCoordinator.failPendingCustomFeedAuthorization(
                                    "Reddit authorization did not complete",
                                )
                            }
                        }
                    },
                ),
            ),
        )
    }

    override fun onCleared() {
        redditSubscriptionsCoordinator.cancelPending()
        communityMembershipReconciliationJob?.cancel()
        customFeedCoordinator.cancelPending()
        communityDiscoveryCoordinator.cancelPending()
        super.onCleared()
    }
}

private val COMMUNITY_MEMBERSHIP_RECONCILIATION_DELAYS_MS =
    listOf(0L, 1_500L, 5_000L, 15_000L)
