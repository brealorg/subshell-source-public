package io.github.brealorg.subshell.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.CommunityMembershipAction
import io.github.brealorg.subshell.data.account.CommunityMembershipUiState
import io.github.brealorg.subshell.data.account.ConnectAccountUiState
import io.github.brealorg.subshell.data.account.RedditAccountHistoryUiState
import io.github.brealorg.subshell.data.account.RedditCommunityDiscoveryUiState
import io.github.brealorg.subshell.data.account.RedditSavedHistoryUiState
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.model.CommunityDiscoverySort
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.ExpandedContextDiscoveryMaxWidth
import io.github.brealorg.subshell.ui.ExpandedContextReadingMaxWidth
import io.github.brealorg.subshell.ui.ExpandedContextUtilityMaxWidth
import io.github.brealorg.subshell.ui.RedditLinkHandlingState
import io.github.brealorg.subshell.ui.SecondaryDestination
import io.github.brealorg.subshell.ui.SubshellRoute
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.app.ExpandedContextSurface
import io.github.brealorg.subshell.ui.screens.ExploreCommunitiesScreen
import io.github.brealorg.subshell.ui.screens.HelpScreen
import io.github.brealorg.subshell.ui.screens.SettingsDestination
import io.github.brealorg.subshell.ui.screens.SettingsPaneRole
import io.github.brealorg.subshell.ui.screens.SettingsScreen
import io.github.brealorg.subshell.ui.screens.settingsRootDestinations

/**
 * The Secondary route branch of SubshellApp's route dispatch: Settings,
 * Library, Help and Explore, each on a width-bounded context surface.
 * Moved verbatim; the SubshellApp vars it writes arrive as MutableState.
 */
@Composable
internal fun SecondaryRouteHost(
    accountConnect: AccountConnectEntryPoints,
    redditAccountHistoryState: RedditAccountHistoryUiState,
    onRefreshRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit,
    onAppendRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit,
    redditSavedHistoryState: RedditSavedHistoryUiState,
    onRefreshRedditSavedHistory: (String, String) -> Unit,
    onAppendRedditSavedHistory: (String, String) -> Unit,
    onResolveRedditSavedCommentTarget: (String, String, String, (Post?) -> Unit) -> Unit,
    onAuthorizeRedditProfileHistory: (String, String) -> Unit,
    redditFeed: FeedEntryPoints,
    redditCommunity: CommunityEntryPoints,
    onRedditConfigurationChanged: () -> Unit,
    redditLinkHandlingState: RedditLinkHandlingState,
    onOpenRedditLinkSettings: () -> Unit,
    state: SubshellState,
    backupRepository: SettingsBackupRepository,
    route: SubshellRoute.Secondary,
    padding: PaddingValues,
    expandedWindowLayout: Boolean,
    screenStateHolder: SaveableStateHolder,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
    activeCustomFeedPathState: MutableState<String?>,
    requestedSettingsDestinationNameState: MutableState<String>,
) {
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    var activeCustomFeedPath by activeCustomFeedPathState
    var requestedSettingsDestinationName by requestedSettingsDestinationNameState
    val communityMembershipState = redditCommunity.communityMembershipState
    val onChangeCommunityMembership = redditCommunity.onChangeCommunityMembership
    val redditCommunityDiscoveryState = redditCommunity.redditCommunityDiscoveryState
    val onLoadRedditCommunityDiscovery = redditCommunity.onLoadRedditCommunityDiscovery
    val redditSubscriptionsState = redditFeed.redditSubscriptionsState
    val onLoadRedditSubscriptions = redditFeed.onLoadRedditSubscriptions
    val accountSessionDirectory = accountConnect.accountSessionDirectory
    val onLogoutActiveAccount = accountConnect.onLogoutActiveAccount
    val onReconnectAccount = accountConnect.onReconnectAccount
    val onlineVisibilityRuntime = accountConnect.onlineVisibilityRuntime
    val connectAccountState = accountConnect.connectAccountState
    val onConnectAccount = accountConnect.onConnectAccount
    val onCancelConnectAccount = accountConnect.onCancelConnectAccount
    val onResetConnectAccount = accountConnect.onResetConnectAccount
    when (route.secondaryDestination) {
        SecondaryDestination.NEW_MESSAGE -> Unit
        SecondaryDestination.SETTINGS -> {
            val settingsScreen: @Composable (
                Modifier,
                SettingsPaneRole,
                String?,
                ((String) -> Unit)?,
            ) -> Unit = { settingsModifier, paneRole, selectedDestination, onOpenDestination ->
                SettingsScreen(
                    modifier = settingsModifier,
                    state = state,
                    backupRepository = backupRepository,
                    onBack = state::closeSecondary,
                    accountSettingsRuntime =
                        io.github.brealorg.subshell.ui.components.AccountSettingsRuntime(
                            connectAccountState = connectAccountState,
                            onlineVisibility = onlineVisibilityRuntime,
                            onConnectAccount = onConnectAccount,
                            onCancelConnectAccount = onCancelConnectAccount,
                            onResetConnectAccount = onResetConnectAccount,
                            onReconnectAccount = onReconnectAccount,
                            checkRedditAccess = accountConnect.checkRedditAccess,
                            onLogoutActiveAccount = { onComplete ->
                                onLogoutActiveAccount { cleared ->
                                    if (cleared) {
                                        pendingAuthenticatedLandingAccountId = null
                                        activeCustomFeedPath = null
                                        runCatching {
                                            accountSessionDirectory?.snapshot()
                                        }.getOrNull()?.let(
                                            state::synchronizeAccountSessions,
                                        )
                                    }
                                    onComplete(cleared)
                                }
                            },
                        ),
                    initialDestinationName =
                        when (paneRole) {
                            SettingsPaneRole.SINGLE -> requestedSettingsDestinationName
                            SettingsPaneRole.LIST -> SettingsDestination.ROOT.name
                            SettingsPaneRole.DETAIL -> selectedDestination ?: SettingsDestination.ROOT.name
                        },
                    onInitialDestinationConsumed = {
                        requestedSettingsDestinationName =
                            SettingsDestination.ROOT.name
                    },
                    onRedditConfigurationChanged =
                        onRedditConfigurationChanged,
                    redditLinkHandlingState =
                        redditLinkHandlingState,
                    onOpenRedditLinkSettings =
                        onOpenRedditLinkSettings,
                    paneRole = paneRole,
                    selectedDestinationName = selectedDestination,
                    onOpenDestination = onOpenDestination,
                )
            }
            if (expandedWindowLayout) {
                // M3224_SETTINGS_LIST_DETAIL_V1 (#224, owner): the areas on the
                // left, the chosen area on the right.
                var selectedSettingsArea by rememberSaveable {
                    mutableStateOf(settingsRootDestinations.first().name)
                }
                LaunchedEffect(requestedSettingsDestinationName) {
                    if (requestedSettingsDestinationName != SettingsDestination.ROOT.name) {
                        selectedSettingsArea = requestedSettingsDestinationName
                        requestedSettingsDestinationName = SettingsDestination.ROOT.name
                    }
                }
                ExpandedListDetailSurface(
                    scaffoldPadding = padding,
                    listTitle = "Settings",
                    listSubtitle = null,
                    showListHeader = false,
                    listContent = { listModifier ->
                        settingsScreen(listModifier, SettingsPaneRole.LIST, selectedSettingsArea) { area ->
                            selectedSettingsArea = area
                        }
                    },
                    detailContent = { detailModifier ->
                        settingsScreen(detailModifier, SettingsPaneRole.DETAIL, selectedSettingsArea, null)
                    },
                )
            } else {
                ExpandedContextSurface(
                    scaffoldPadding = padding,
                    expanded = false,
                    maxWidth = ExpandedContextUtilityMaxWidth,
                ) { contextModifier ->
                    settingsScreen(contextModifier, SettingsPaneRole.SINGLE, null, null)
                }
            }
        }
        SecondaryDestination.HELP -> ExpandedContextSurface(
            scaffoldPadding = padding,
            expanded = expandedWindowLayout,
            maxWidth = ExpandedContextUtilityMaxWidth,
        ) { contextModifier ->
            HelpScreen(
                modifier = contextModifier,
                onBack = state::closeSecondary,
            )
        }
        SecondaryDestination.COMMUNITY_EXPLORE ->
            screenStateHolder.SaveableStateProvider("community-explore") {
                ExpandedContextSurface(
                    scaffoldPadding = padding,
                    expanded = expandedWindowLayout,
                    maxWidth = ExpandedContextDiscoveryMaxWidth,
                ) { contextModifier ->
                    ExploreCommunitiesScreen(
                    modifier = contextModifier,
                    state = state,
                    redditSubscriptionsState =
                        redditSubscriptionsState,
                    onLoadRedditSubscriptions =
                        onLoadRedditSubscriptions,
                    redditCommunityDiscoveryState =
                        redditCommunityDiscoveryState,
                    onLoadRedditCommunityDiscovery =
                        onLoadRedditCommunityDiscovery,
                    communityMembershipState =
                        communityMembershipState,
                    onChangeCommunityMembership =
                        onChangeCommunityMembership,
                    onBack = state::closeSecondary,
                    )
                }
            }
    }
}
