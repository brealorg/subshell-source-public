package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.subshell.prototype.ui.ModeratorTeamRuntimeEntryPoints
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.ModeratorContentControlsEntryPoints
import app.subshell.prototype.ui.components.ModeratorLogDialog
import app.subshell.prototype.ui.components.ModeratorLogEntryPoints
import app.subshell.prototype.ui.components.ModeratorReportsEntryPoints
import app.subshell.prototype.ui.components.ModeratorTeamContent
import app.subshell.prototype.ui.components.ModeratorToolsNavigationEntryPoints
import app.subshell.prototype.ui.components.ModeratorToolsWorkspaceContentEntryPoints
import app.subshell.prototype.ui.components.ModeratorToolsWorkspaceDestination
import app.subshell.prototype.ui.components.ModeratorUserManagementDialog
import app.subshell.prototype.ui.components.ModeratorUserManagementEntryPoints

/** What [moderatorToolsRootEntryPoints] hands back to SubshellApp. */
internal class ModeratorToolsRootEntryPoints(
    val workspaceCommunity: String?,
    val navigation: ModeratorToolsNavigationEntryPoints,
    val workspaceContent: ModeratorToolsWorkspaceContentEntryPoints,
    val reports: ModeratorReportsEntryPoints,
    val contentControls: ModeratorContentControlsEntryPoints,
    val log: ModeratorLogEntryPoints,
    val userManagement: ModeratorUserManagementEntryPoints,
)

/**
 * The moderator tools root owners SubshellApp builds for the whole tree:
 * the single-sheet workspace navigation (its saveable community and
 * destination, reset per account), the workspace content (mod log, user
 * management, moderator team), the moderator team's per-account resets,
 * and the reports / content controls / log / user management entry
 * points the CompositionLocals provide.
 *
 * Moved out of SubshellApp verbatim, in their original order; the locals
 * the body reads are taken from the same bundles, with the same lines.
 * A value-returning composable is not a restart scope, so everything
 * here still recomposes with SubshellApp.
 */
@Composable
internal fun moderatorToolsRootEntryPoints(
    state: SubshellState,
    redditCommunity: CommunityEntryPoints,
    redditModeration: ModerationQueueEntryPoints,
    moderatorTools: ModeratorToolsEntryPoints,
    moderatorTeamRuntime: ModeratorTeamRuntimeEntryPoints,
): ModeratorToolsRootEntryPoints {
    val redditModerationState = redditModeration.redditModerationState
    val redditCommunityInfoState = redditCommunity.redditCommunityInfoState
    val onLoadRedditCommunityInfo = redditCommunity.onLoadRedditCommunityInfo
    val redditCommunityDiscoveryState = redditCommunity.redditCommunityDiscoveryState
    val onLoadRedditCommunityDiscovery = redditCommunity.onLoadRedditCommunityDiscovery
    val onOpenModeratorReports = moderatorTools.onOpenModeratorReports
    val onOpenModeratorContentControls = moderatorTools.onOpenModeratorContentControls
    val moderatorLogState = moderatorTools.moderatorLogState
    val onOpenModeratorLog = moderatorTools.onOpenModeratorLog
    val onRefreshModeratorLog = moderatorTools.onRefreshModeratorLog
    val onAppendModeratorLog = moderatorTools.onAppendModeratorLog
    val onDismissModeratorLog = moderatorTools.onDismissModeratorLog
    val onAuthorizeModeratorLog = moderatorTools.onAuthorizeModeratorLog
    val onCancelModeratorLogAuthorization = moderatorTools.onCancelModeratorLogAuthorization
    val moderatorUserManagementState = moderatorTools.moderatorUserManagementState
    val onOpenModeratorUserManagement = moderatorTools.onOpenModeratorUserManagement
    val onRefreshModeratorUserManagement = moderatorTools.onRefreshModeratorUserManagement
    val onAppendModeratorUserManagement = moderatorTools.onAppendModeratorUserManagement
    val onAppendMutedModeratorUserManagement = moderatorTools.onAppendMutedModeratorUserManagement
    val onAppendApprovedModeratorUserManagement = moderatorTools.onAppendApprovedModeratorUserManagement
    val onMutateModeratorUser = moderatorTools.onMutateModeratorUser
    val onMutateMutedModeratorUser = moderatorTools.onMutateMutedModeratorUser
    val onMutateApprovedModeratorUser = moderatorTools.onMutateApprovedModeratorUser
    val onDismissModeratorUserManagement = moderatorTools.onDismissModeratorUserManagement
    val onAuthorizeModeratorUserManagement = moderatorTools.onAuthorizeModeratorUserManagement
    val moderatorInviteState = moderatorTeamRuntime.moderatorInviteState
    val onOpenModeratorInvite = moderatorTeamRuntime.onOpenModeratorInvite
    val onInviteModerator = moderatorTeamRuntime.onInviteModerator
    val onDismissModeratorInvite = moderatorTeamRuntime.onDismissModeratorInvite
    val onModeratorInviteAccountChanged =
        moderatorTeamRuntime.onModeratorInviteAccountChanged
    val onAuthorizeModeratorInvite = moderatorTeamRuntime.onAuthorizeModeratorInvite
    val moderatorPermissionUpdateState =
        moderatorTeamRuntime.moderatorPermissionUpdateState
    val onOpenModeratorPermissionUpdate =
        moderatorTeamRuntime.onOpenModeratorPermissionUpdate
    val onUpdateModeratorPermissions =
        moderatorTeamRuntime.onUpdateModeratorPermissions
    val onDismissModeratorPermissionUpdate =
        moderatorTeamRuntime.onDismissModeratorPermissionUpdate
    val onModeratorPermissionUpdateAccountChanged =
        moderatorTeamRuntime.onModeratorPermissionUpdateAccountChanged
    val onAuthorizeModeratorPermissionUpdate =
        moderatorTeamRuntime.onAuthorizeModeratorPermissionUpdate
    val moderatorRemovalState = moderatorTeamRuntime.moderatorRemovalState
    val onOpenModeratorRemoval = moderatorTeamRuntime.onOpenModeratorRemoval
    val onRemoveModerator = moderatorTeamRuntime.onRemoveModerator
    val onDismissModeratorRemoval = moderatorTeamRuntime.onDismissModeratorRemoval
    val onModeratorRemovalAccountChanged =
        moderatorTeamRuntime.onModeratorRemovalAccountChanged
    val onAuthorizeModeratorRemoval = moderatorTeamRuntime.onAuthorizeModeratorRemoval

    // MODERATION02A5A1R3_MOD_TOOLS_WORKSPACE_NAVIGATION_V1
    // LOWUI8_ACCOUNT_RESETS_ONLY_ON_A_CHANGE_V1 (OCB-115): keyed on the
    // account, so an account change starts empty and a recreation keeps the
    // restored workspace (a LaunchedEffect also ran on first composition).
    var moderatorToolsWorkspaceCommunity by rememberSaveable(state.activeAccountId) {
        mutableStateOf<String?>(null)
    }
    var moderatorToolsWorkspaceDestination by rememberSaveable(state.activeAccountId) {
        mutableStateOf<ModeratorToolsWorkspaceDestination?>(null)
    }
    val moderatorToolsNavigation = ModeratorToolsNavigationEntryPoints(
        isRootVisible = { community ->
            moderatorToolsWorkspaceCommunity == community &&
                moderatorToolsWorkspaceDestination ==
                ModeratorToolsWorkspaceDestination.ROOT
        },
        isVisible = { community ->
            moderatorToolsWorkspaceCommunity == community &&
                moderatorToolsWorkspaceDestination != null
        },
        destinationFor = { community ->
            moderatorToolsWorkspaceDestination.takeIf {
                moderatorToolsWorkspaceCommunity == community
            }
        },
        openRoot = { community ->
            moderatorToolsWorkspaceCommunity = community
            moderatorToolsWorkspaceDestination =
                ModeratorToolsWorkspaceDestination.ROOT
        },
        openChild = { community, destination ->
            if (
                moderatorToolsWorkspaceCommunity == community &&
                moderatorToolsWorkspaceDestination ==
                ModeratorToolsWorkspaceDestination.ROOT &&
                destination != ModeratorToolsWorkspaceDestination.ROOT
            ) {
                moderatorToolsWorkspaceDestination = destination
            }
        },
        returnToRoot = { community, destination ->
            if (
                moderatorToolsWorkspaceCommunity == community &&
                moderatorToolsWorkspaceDestination == destination
            ) {
                moderatorToolsWorkspaceDestination =
                    ModeratorToolsWorkspaceDestination.ROOT
            }
        },
        dismiss = { community ->
            if (moderatorToolsWorkspaceCommunity == community) {
                moderatorToolsWorkspaceCommunity = null
                moderatorToolsWorkspaceDestination = null
            }
        },
    )

    // MODERATION02A5A1R4_SINGLE_SHEET_WORKSPACE_V1
    val moderatorToolsWorkspaceContent = ModeratorToolsWorkspaceContentEntryPoints(
        modLog = { onBack ->
            ModeratorLogDialog(
                accountStableId = state.activeAccountId,
                state = moderatorLogState,
                onRefresh = { onRefreshModeratorLog(state.activeAccountId) },
                onAppend = { onAppendModeratorLog(state.activeAccountId) },
                onAuthorize = onAuthorizeModeratorLog,
                onCancelAuthorization = onCancelModeratorLogAuthorization,
                onDismiss = onBack,
                embeddedInModToolsWorkspace = true,
            )
        },
        userManagement = { onBack ->
            ModeratorUserManagementDialog(
                accountStableId = state.activeAccountId,
                activeUsername = state.activeAccountUsername,
                state = moderatorUserManagementState,
                onRefresh = { onRefreshModeratorUserManagement(state.activeAccountId) },
                onAppend = { onAppendModeratorUserManagement(state.activeAccountId) },
                onAppendMuted = {
                    onAppendMutedModeratorUserManagement(state.activeAccountId)
                },
                onAppendApproved = {
                    onAppendApprovedModeratorUserManagement(state.activeAccountId)
                },
                onMutate = onMutateModeratorUser,
                onMutateMuted = onMutateMutedModeratorUser,
                onMutateApproved = onMutateApprovedModeratorUser,
                onAuthorize = onAuthorizeModeratorUserManagement,
                onDismiss = onBack,
                embeddedInModToolsWorkspace = true,
            )
        },
        // MODERATION02A5A4_COMMUNITY_INFO_REUSE_V1
        moderators = { community, onBack ->
            val owner = state.activeAccountId
            val canRead = state.activeAccountIsAuthenticated &&
                redditModerationState.matchesAccount(owner) &&
                redditModerationState.moderates(community)
            ModeratorTeamContent(
                accountStableId = owner,
                communityName = community,
                activeUsername = state.activeAccountUsername,
                canRead = canRead,
                uiState = redditCommunityInfoState,
                avatarImageUri = { username -> state.userFor(username).avatarImageUri },
                onEnsureIdentity = { username ->
                    if (state.activeAccountId == owner && canRead) {
                        state.ensureUserIdentity(username)
                    }
                },
                onLoad = { force ->
                    if (state.activeAccountId == owner && canRead) {
                        onLoadRedditCommunityInfo(owner, community, force)
                    }
                },
                inviteState = moderatorInviteState,
                onOpenInviteContext = { onOpenModeratorInvite(owner, community) },
                onInvite = onInviteModerator,
                onAuthorizeInvite = onAuthorizeModeratorInvite,
                onDismissInvite = onDismissModeratorInvite,
                permissionUpdateState = moderatorPermissionUpdateState,
                onOpenPermissionUpdateContext = { username ->
                    onOpenModeratorPermissionUpdate(owner, community, username)
                },
                onUpdatePermissions = onUpdateModeratorPermissions,
                onAuthorizePermissionUpdate = onAuthorizeModeratorPermissionUpdate,
                onDismissPermissionUpdate = onDismissModeratorPermissionUpdate,
                removalState = moderatorRemovalState,
                onOpenRemovalContext = { username ->
                    onOpenModeratorRemoval(owner, community, username)
                },
                onRemoveModerator = onRemoveModerator,
                onAuthorizeRemoval = onAuthorizeModeratorRemoval,
                onDismissRemoval = onDismissModeratorRemoval,
                onBack = {
                    onDismissModeratorInvite()
                    onDismissModeratorPermissionUpdate()
                    onDismissModeratorRemoval()
                    onBack()
                },
            )
        },
        dismissModLog = onDismissModeratorLog,
        dismissUserManagement = onDismissModeratorUserManagement,
    )

    // MODERATION02A5A5_ROOT_MODERATOR_INVITE_OWNER_V1
    LaunchedEffect(state.activeAccountId) {
        onModeratorInviteAccountChanged(state.activeAccountId)
    }
    // MODERATION02A5A6_ROOT_MODERATOR_PERMISSION_OWNER_V1
    LaunchedEffect(state.activeAccountId) {
        onModeratorPermissionUpdateAccountChanged(state.activeAccountId)
    }
    // MODERATION02A5A7_ROOT_MODERATOR_REMOVAL_OWNER_V1
    LaunchedEffect(state.activeAccountId) {
        onModeratorRemovalAccountChanged(state.activeAccountId)
    }
    LaunchedEffect(moderatorToolsWorkspaceCommunity, moderatorToolsWorkspaceDestination) {
        if (moderatorToolsWorkspaceDestination != ModeratorToolsWorkspaceDestination.MODERATORS) {
            onDismissModeratorInvite()
            onDismissModeratorPermissionUpdate()
            onDismissModeratorRemoval()
        }
    }
    val moderatorReportsEntries = ModeratorReportsEntryPoints(
        hasModeratorAccess = state.activeAccountIsAuthenticated &&
            redditModerationState.matchesAccount(state.activeAccountId) &&
            redditModerationState.moderatedCommunities.isNotEmpty(),
        canInspect = { community ->
            state.activeAccountIsAuthenticated && redditModerationState.matchesAccount(state.activeAccountId) &&
                redditModerationState.moderates(community)
        },
        open = { target -> onOpenModeratorReports(state.activeAccountId, target) },
    )
    val moderatorContentControlEntries = ModeratorContentControlsEntryPoints(
        canInspect = { community ->
            state.activeAccountIsAuthenticated &&
                redditModerationState.matchesAccount(state.activeAccountId) &&
                redditModerationState.moderates(community)
        },
        open = { target ->
            onOpenModeratorContentControls(state.activeAccountId, target)
        },
    )
    val moderatorLogEntries = ModeratorLogEntryPoints(
        canInspect = { community ->
            state.activeAccountIsAuthenticated &&
                redditModerationState.matchesAccount(state.activeAccountId) &&
                redditModerationState.moderates(community)
        },
        open = { community ->
            onOpenModeratorLog(state.activeAccountId, community)
        },
    )
    val moderatorUserManagementEntries = ModeratorUserManagementEntryPoints(
        canInspect = { community ->
            state.activeAccountIsAuthenticated &&
                redditModerationState.matchesAccount(state.activeAccountId) &&
                redditModerationState.moderates(community)
        },
        open = { community ->
            onOpenModeratorUserManagement(state.activeAccountId, community)
        },
        peopleSearchState = { redditCommunityDiscoveryState.peopleSearch },
        searchPeople = { query ->
            onLoadRedditCommunityDiscovery(
                state.activeAccountId,
                query,
                null,
                null,
                false,
            )
        },
    )
    return ModeratorToolsRootEntryPoints(
        workspaceCommunity = moderatorToolsWorkspaceCommunity,
        navigation = moderatorToolsNavigation,
        workspaceContent = moderatorToolsWorkspaceContent,
        reports = moderatorReportsEntries,
        contentControls = moderatorContentControlEntries,
        log = moderatorLogEntries,
        userManagement = moderatorUserManagementEntries,
    )
}
