package app.subshell.prototype

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.subshell.prototype.auth.AuthConnectViewModel
import app.subshell.prototype.ui.ModeratorTeamRuntimeEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsEntryPoints

/**
 * The moderator tools entry points SubshellApp receives, built from the
 * AuthConnectViewModel: the state flows are collected here and the
 * callbacks are the ViewModel's. The authorization launches stay
 * MainActivity's (they need its auth tab launcher) and arrive as lambdas.
 *
 * Moved out of MainActivity.onCreate's SubshellApp(...) call verbatim,
 * with the collects of the states only these bundles use; the launch
 * references read the lambda parameters instead of MainActivity members.
 */
@Composable
internal fun moderatorToolsEntryPoints(
    authConnectViewModel: AuthConnectViewModel,
    launchRedditModeratorLogAuthorization: () -> Unit,
    launchRedditModeratorUserManagementAuthorization: () -> Unit,
): ModeratorToolsEntryPoints {
    val moderatorReportsState by
        authConnectViewModel.moderatorReportsUiState.collectAsStateWithLifecycle()
    val moderatorContentControlsState by
        authConnectViewModel.moderatorContentControlsUiState.collectAsStateWithLifecycle()
    val moderatorLogState by
        authConnectViewModel.moderatorLogUiState.collectAsStateWithLifecycle()
    val moderatorUserManagementState by
        authConnectViewModel.moderatorUserManagementUiState.collectAsStateWithLifecycle()
    return ModeratorToolsEntryPoints(
        onLoadRedditModeratorCommunities =
            authConnectViewModel::loadRedditModeratorCommunities,
        moderatorReportsState = moderatorReportsState,
        onOpenModeratorReports = authConnectViewModel::openModeratorReports,
        onRefreshModeratorReports = authConnectViewModel::refreshModeratorReports,
        onConfirmModeratorReports = authConnectViewModel::confirmModeratorReports,
        onDismissModeratorReports = authConnectViewModel::dismissModeratorReports,
        onModeratorReportsAccountChanged = authConnectViewModel::moderatorReportsAccountChanged,
        moderatorContentControlsState = moderatorContentControlsState,
        onOpenModeratorContentControls =
            authConnectViewModel::openModeratorContentControls,
        onRefreshModeratorContentControls =
            authConnectViewModel::refreshModeratorContentControls,
        onConfirmModeratorContentControl =
            authConnectViewModel::confirmModeratorContentControl,
        onDismissModeratorContentControls =
            authConnectViewModel::dismissModeratorContentControls,
        onModeratorContentControlsAccountChanged =
            authConnectViewModel::moderatorContentControlsAccountChanged,
        moderatorLogState = moderatorLogState,
        onOpenModeratorLog = authConnectViewModel::openModeratorLog,
        onRefreshModeratorLog = authConnectViewModel::refreshModeratorLog,
        onAppendModeratorLog = authConnectViewModel::appendModeratorLog,
        onDismissModeratorLog = authConnectViewModel::dismissModeratorLog,
        onModeratorLogAccountChanged =
            authConnectViewModel::moderatorLogAccountChanged,
        onAuthorizeModeratorLog =
            launchRedditModeratorLogAuthorization,
        onCancelModeratorLogAuthorization =
            authConnectViewModel::cancelRedditModeratorLogAuthorization,
        moderatorUserManagementState = moderatorUserManagementState,
        onOpenModeratorUserManagement =
            authConnectViewModel::openModeratorUserManagement,
        onRefreshModeratorUserManagement =
            authConnectViewModel::refreshModeratorUserManagement,
        onAppendModeratorUserManagement =
            authConnectViewModel::appendModeratorUserManagement,
        onAppendMutedModeratorUserManagement =
            authConnectViewModel::appendMutedModeratorUserManagement,
        onAppendApprovedModeratorUserManagement =
            authConnectViewModel::appendApprovedModeratorUserManagement,
        onMutateModeratorUser = authConnectViewModel::mutateModeratorUser,
        onMutateMutedModeratorUser =
            authConnectViewModel::mutateMutedModeratorUser,
        onMutateApprovedModeratorUser =
            authConnectViewModel::mutateApprovedModeratorUser,
        onDismissModeratorUserManagement =
            authConnectViewModel::dismissModeratorUserManagement,
        onModeratorUserManagementAccountChanged =
            authConnectViewModel::moderatorUserManagementAccountChanged,
        onAuthorizeModeratorUserManagement =
            launchRedditModeratorUserManagementAuthorization,
    )
}

/** The moderator team (invite, permissions, removal) runtime entry points. */
@Composable
internal fun moderatorTeamRuntimeEntryPoints(
    authConnectViewModel: AuthConnectViewModel,
    launchRedditModeratorInviteAuthorization: () -> Unit,
    launchRedditModeratorPermissionAuthorization: () -> Unit,
    launchRedditModeratorRemovalAuthorization: () -> Unit,
): ModeratorTeamRuntimeEntryPoints {
    val moderatorInviteState by
        authConnectViewModel.moderatorInviteUiState.collectAsStateWithLifecycle()
    val moderatorPermissionUpdateState by
        authConnectViewModel.moderatorPermissionUpdateUiState.collectAsStateWithLifecycle()
    val moderatorRemovalState by
        authConnectViewModel.moderatorRemovalUiState.collectAsStateWithLifecycle()
    return ModeratorTeamRuntimeEntryPoints(
        moderatorInviteState = moderatorInviteState,
        onOpenModeratorInvite =
            authConnectViewModel::openModeratorInvite,
        onInviteModerator =
            authConnectViewModel::inviteModerator,
        onDismissModeratorInvite =
            authConnectViewModel::dismissModeratorInvite,
        onModeratorInviteAccountChanged =
            authConnectViewModel::moderatorInviteAccountChanged,
        onAuthorizeModeratorInvite =
            launchRedditModeratorInviteAuthorization,
        moderatorPermissionUpdateState = moderatorPermissionUpdateState,
        onOpenModeratorPermissionUpdate =
            authConnectViewModel::openModeratorPermissionUpdate,
        onUpdateModeratorPermissions =
            authConnectViewModel::updateModeratorPermissions,
        onDismissModeratorPermissionUpdate =
            authConnectViewModel::dismissModeratorPermissionUpdate,
        onModeratorPermissionUpdateAccountChanged =
            authConnectViewModel::moderatorPermissionUpdateAccountChanged,
        onAuthorizeModeratorPermissionUpdate =
            launchRedditModeratorPermissionAuthorization,
        moderatorRemovalState = moderatorRemovalState,
        onOpenModeratorRemoval =
            authConnectViewModel::openModeratorRemoval,
        onRemoveModerator =
            authConnectViewModel::removeModerator,
        onDismissModeratorRemoval =
            authConnectViewModel::dismissModeratorRemoval,
        onModeratorRemovalAccountChanged =
            authConnectViewModel::moderatorRemovalAccountChanged,
        onAuthorizeModeratorRemoval =
            launchRedditModeratorRemovalAuthorization,
    )
}
