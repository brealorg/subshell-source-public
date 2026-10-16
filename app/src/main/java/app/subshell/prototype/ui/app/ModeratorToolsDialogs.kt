package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.ModeratorContentControlsDialog
import app.subshell.prototype.ui.components.ModeratorLogDialog
import app.subshell.prototype.ui.components.ModeratorReportsDialog
import app.subshell.prototype.ui.components.ModeratorToolsNavigationEntryPoints
import app.subshell.prototype.ui.components.ModeratorToolsWorkspaceDestination
import app.subshell.prototype.ui.components.ModeratorUserManagementDialog

/**
 * The four root moderator dialogs SubshellApp hosts for an authenticated
 * account: reports, content controls, the mod log and user management. The
 * log and user-management dialogs are the root owners, so they only render
 * while no moderator-tools workspace is open, and dismissing them returns
 * the workspace to its root.
 *
 * Moved out of SubshellApp verbatim; the entry points arrive as the same two
 * bundles SubshellApp receives.
 */
@Composable
internal fun ModeratorToolsDialogs(
    state: SubshellState,
    redditModeration: ModerationQueueEntryPoints,
    moderatorTools: ModeratorToolsEntryPoints,
    moderatorToolsNavigation: ModeratorToolsNavigationEntryPoints,
    moderatorToolsWorkspaceCommunity: String?,
) {
    val redditModerationState = redditModeration.redditModerationState
    val moderationActionState = redditModeration.moderationActionState
    val onAuthorizeRedditModerationAction = redditModeration.onAuthorizeRedditModerationAction
    val moderatorReportsState = moderatorTools.moderatorReportsState
    val onOpenModeratorReports = moderatorTools.onOpenModeratorReports
    val onRefreshModeratorReports = moderatorTools.onRefreshModeratorReports
    val onConfirmModeratorReports = moderatorTools.onConfirmModeratorReports
    val onDismissModeratorReports = moderatorTools.onDismissModeratorReports
    val moderatorContentControlsState = moderatorTools.moderatorContentControlsState
    val onRefreshModeratorContentControls = moderatorTools.onRefreshModeratorContentControls
    val onConfirmModeratorContentControl = moderatorTools.onConfirmModeratorContentControl
    val onDismissModeratorContentControls = moderatorTools.onDismissModeratorContentControls
    val moderatorLogState = moderatorTools.moderatorLogState
    val onRefreshModeratorLog = moderatorTools.onRefreshModeratorLog
    val onAppendModeratorLog = moderatorTools.onAppendModeratorLog
    val onDismissModeratorLog = moderatorTools.onDismissModeratorLog
    val onAuthorizeModeratorLog = moderatorTools.onAuthorizeModeratorLog
    val onCancelModeratorLogAuthorization = moderatorTools.onCancelModeratorLogAuthorization
    val moderatorUserManagementState = moderatorTools.moderatorUserManagementState
    val onRefreshModeratorUserManagement = moderatorTools.onRefreshModeratorUserManagement
    val onAppendModeratorUserManagement = moderatorTools.onAppendModeratorUserManagement
    val onAppendMutedModeratorUserManagement = moderatorTools.onAppendMutedModeratorUserManagement
    val onAppendApprovedModeratorUserManagement = moderatorTools.onAppendApprovedModeratorUserManagement
    val onMutateModeratorUser = moderatorTools.onMutateModeratorUser
    val onMutateMutedModeratorUser = moderatorTools.onMutateMutedModeratorUser
    val onMutateApprovedModeratorUser = moderatorTools.onMutateApprovedModeratorUser
    val onDismissModeratorUserManagement = moderatorTools.onDismissModeratorUserManagement
    val onAuthorizeModeratorUserManagement = moderatorTools.onAuthorizeModeratorUserManagement
    if (state.activeAccountIsAuthenticated) {
        ModeratorReportsDialog(
            accountStableId = state.activeAccountId,
            state = moderatorReportsState,
            moderationState = redditModerationState,
            actionState = moderationActionState,
            onOpen = { target -> onOpenModeratorReports(state.activeAccountId, target) },
            onRefresh = { onRefreshModeratorReports(state.activeAccountId) },
            onConfirm = onConfirmModeratorReports,
            onAuthorize = onAuthorizeRedditModerationAction,
            onDismiss = onDismissModeratorReports,
        )
        ModeratorContentControlsDialog(
            accountStableId = state.activeAccountId,
            activeUsername = state.activeAccountUsername,
            state = moderatorContentControlsState,
            actionState = moderationActionState,
            onRefresh = {
                onRefreshModeratorContentControls(state.activeAccountId)
            },
            onConfirm = onConfirmModeratorContentControl,
            onAuthorize = onAuthorizeRedditModerationAction,
            onDismiss = onDismissModeratorContentControls,
        )
        if (moderatorToolsWorkspaceCommunity == null) {
            ModeratorLogDialog(
                accountStableId = state.activeAccountId,
                state = moderatorLogState,
                onRefresh = { onRefreshModeratorLog(state.activeAccountId) },
                onAppend = { onAppendModeratorLog(state.activeAccountId) },
                onAuthorize = onAuthorizeModeratorLog,
                onCancelAuthorization = onCancelModeratorLogAuthorization,
                onDismiss = {
                    val community = moderatorLogState.communityName
                    onDismissModeratorLog()
                    if (community != null) {
                        moderatorToolsNavigation.returnToRoot(
                            community,
                            ModeratorToolsWorkspaceDestination.MOD_LOG,
                        )
                    }
                },
            )
        }
        if (moderatorToolsWorkspaceCommunity == null) {
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
                onDismiss = {
                    val community = moderatorUserManagementState.communityName
                    onDismissModeratorUserManagement()
                    if (community != null) {
                        moderatorToolsNavigation.returnToRoot(
                            community,
                            ModeratorToolsWorkspaceDestination.USER_MANAGEMENT,
                        )
                    }
                },
            )
        }
    }
}
