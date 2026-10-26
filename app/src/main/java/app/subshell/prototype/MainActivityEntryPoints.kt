package app.subshell.prototype

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.subshell.prototype.auth.ModerationViewModel
import app.subshell.prototype.ui.ModeratorTeamRuntimeEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsEntryPoints

/**
 * The moderator tools entry points SubshellApp receives, built from the
 * ModerationViewModel: the state flows are collected here and the
 * callbacks are the ViewModel's. The authorization launches stay
 * MainActivity's (they need its auth tab launcher) and arrive as lambdas.
 *
 * Moved out of MainActivity.onCreate's SubshellApp(...) call verbatim,
 * with the collects of the states only these bundles use; the launch
 * references read the lambda parameters instead of MainActivity members.
 */
@Composable
internal fun moderatorToolsEntryPoints(
    moderationViewModel: ModerationViewModel,
    launchRedditModeratorLogAuthorization: () -> Unit,
    launchRedditModeratorUserManagementAuthorization: () -> Unit,
): ModeratorToolsEntryPoints {
    val moderatorReportsState by
        moderationViewModel.moderatorReportsUiState.collectAsStateWithLifecycle()
    val moderatorContentControlsState by
        moderationViewModel.moderatorContentControlsUiState.collectAsStateWithLifecycle()
    val moderatorLogState by
        moderationViewModel.moderatorLogUiState.collectAsStateWithLifecycle()
    val moderatorUserManagementState by
        moderationViewModel.moderatorUserManagementUiState.collectAsStateWithLifecycle()
    return ModeratorToolsEntryPoints(
        onLoadRedditModeratorCommunities =
            moderationViewModel::loadRedditModeratorCommunities,
        moderatorReportsState = moderatorReportsState,
        onOpenModeratorReports = moderationViewModel::openModeratorReports,
        onRefreshModeratorReports = moderationViewModel::refreshModeratorReports,
        onConfirmModeratorReports = moderationViewModel::confirmModeratorReports,
        onDismissModeratorReports = moderationViewModel::dismissModeratorReports,
        onModeratorReportsAccountChanged = moderationViewModel::moderatorReportsAccountChanged,
        moderatorContentControlsState = moderatorContentControlsState,
        onOpenModeratorContentControls =
            moderationViewModel::openModeratorContentControls,
        onRefreshModeratorContentControls =
            moderationViewModel::refreshModeratorContentControls,
        onConfirmModeratorContentControl =
            moderationViewModel::confirmModeratorContentControl,
        onDismissModeratorContentControls =
            moderationViewModel::dismissModeratorContentControls,
        onModeratorContentControlsAccountChanged =
            moderationViewModel::moderatorContentControlsAccountChanged,
        moderatorLogState = moderatorLogState,
        onOpenModeratorLog = moderationViewModel::openModeratorLog,
        onRefreshModeratorLog = moderationViewModel::refreshModeratorLog,
        onAppendModeratorLog = moderationViewModel::appendModeratorLog,
        onDismissModeratorLog = moderationViewModel::dismissModeratorLog,
        onModeratorLogAccountChanged =
            moderationViewModel::moderatorLogAccountChanged,
        onAuthorizeModeratorLog =
            launchRedditModeratorLogAuthorization,
        onCancelModeratorLogAuthorization =
            moderationViewModel::cancelRedditModeratorLogAuthorization,
        moderatorUserManagementState = moderatorUserManagementState,
        onOpenModeratorUserManagement =
            moderationViewModel::openModeratorUserManagement,
        onRefreshModeratorUserManagement =
            moderationViewModel::refreshModeratorUserManagement,
        onAppendModeratorUserManagement =
            moderationViewModel::appendModeratorUserManagement,
        onAppendMutedModeratorUserManagement =
            moderationViewModel::appendMutedModeratorUserManagement,
        onAppendApprovedModeratorUserManagement =
            moderationViewModel::appendApprovedModeratorUserManagement,
        onMutateModeratorUser = moderationViewModel::mutateModeratorUser,
        onMutateMutedModeratorUser =
            moderationViewModel::mutateMutedModeratorUser,
        onMutateApprovedModeratorUser =
            moderationViewModel::mutateApprovedModeratorUser,
        onDismissModeratorUserManagement =
            moderationViewModel::dismissModeratorUserManagement,
        onModeratorUserManagementAccountChanged =
            moderationViewModel::moderatorUserManagementAccountChanged,
        onAuthorizeModeratorUserManagement =
            launchRedditModeratorUserManagementAuthorization,
    )
}

/** The moderator team (invite, permissions, removal) runtime entry points. */
@Composable
internal fun moderatorTeamRuntimeEntryPoints(
    moderationViewModel: ModerationViewModel,
    launchRedditModeratorInviteAuthorization: () -> Unit,
    launchRedditModeratorPermissionAuthorization: () -> Unit,
    launchRedditModeratorRemovalAuthorization: () -> Unit,
): ModeratorTeamRuntimeEntryPoints {
    val moderatorInviteState by
        moderationViewModel.moderatorInviteUiState.collectAsStateWithLifecycle()
    val moderatorPermissionUpdateState by
        moderationViewModel.moderatorPermissionUpdateUiState.collectAsStateWithLifecycle()
    val moderatorRemovalState by
        moderationViewModel.moderatorRemovalUiState.collectAsStateWithLifecycle()
    return ModeratorTeamRuntimeEntryPoints(
        moderatorInviteState = moderatorInviteState,
        onOpenModeratorInvite =
            moderationViewModel::openModeratorInvite,
        onInviteModerator =
            moderationViewModel::inviteModerator,
        onDismissModeratorInvite =
            moderationViewModel::dismissModeratorInvite,
        onModeratorInviteAccountChanged =
            moderationViewModel::moderatorInviteAccountChanged,
        onAuthorizeModeratorInvite =
            launchRedditModeratorInviteAuthorization,
        moderatorPermissionUpdateState = moderatorPermissionUpdateState,
        onOpenModeratorPermissionUpdate =
            moderationViewModel::openModeratorPermissionUpdate,
        onUpdateModeratorPermissions =
            moderationViewModel::updateModeratorPermissions,
        onDismissModeratorPermissionUpdate =
            moderationViewModel::dismissModeratorPermissionUpdate,
        onModeratorPermissionUpdateAccountChanged =
            moderationViewModel::moderatorPermissionUpdateAccountChanged,
        onAuthorizeModeratorPermissionUpdate =
            launchRedditModeratorPermissionAuthorization,
        moderatorRemovalState = moderatorRemovalState,
        onOpenModeratorRemoval =
            moderationViewModel::openModeratorRemoval,
        onRemoveModerator =
            moderationViewModel::removeModerator,
        onDismissModeratorRemoval =
            moderationViewModel::dismissModeratorRemoval,
        onModeratorRemovalAccountChanged =
            moderationViewModel::moderatorRemovalAccountChanged,
        onAuthorizeModeratorRemoval =
            launchRedditModeratorRemovalAuthorization,
    )
}
