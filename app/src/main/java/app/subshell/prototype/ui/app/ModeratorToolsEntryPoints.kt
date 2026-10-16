package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.ModeratorContentControlConfirmation
import app.subshell.prototype.data.account.ModeratorContentControlsUiState
import app.subshell.prototype.data.account.ModeratorLogUiState
import app.subshell.prototype.data.account.ModeratorReportConfirmation
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.ModeratorReportsUiState
import app.subshell.prototype.data.account.ModeratorUserManagementUiState
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest

/**
 * The moderator tools runtime as SubshellApp receives it from MainActivity:
 * the moderator-communities loader and the reports, content-controls, mod-log
 * and user-management UI states with their open/refresh/append, confirm or
 * mutate, dismiss, account-changed and authorization entry points. The
 * moderator team runtime stays its own bundle (ModeratorTeamRuntimeEntryPoints).
 * Property names and defaults are the former SubshellApp parameters, unchanged.
 */
data class ModeratorToolsEntryPoints(
    val onLoadRedditModeratorCommunities: (String, Boolean) -> Unit = { _, _ -> },
    val moderatorReportsState: ModeratorReportsUiState = ModeratorReportsUiState(),
    val onOpenModeratorReports: (String, ModeratorReportTarget?) -> Unit = { _, _ -> },
    val onRefreshModeratorReports: (String) -> Unit = {},
    val onConfirmModeratorReports: (ModeratorReportConfirmation) -> Unit = {},
    val onDismissModeratorReports: () -> Unit = {},
    val onModeratorReportsAccountChanged: (String) -> Unit = {},
    val moderatorContentControlsState: ModeratorContentControlsUiState =
        ModeratorContentControlsUiState(),
    val onOpenModeratorContentControls: (String, ModeratorReportTarget) -> Unit =
        { _, _ -> },
    val onRefreshModeratorContentControls: (String) -> Unit = {},
    val onConfirmModeratorContentControl: (ModeratorContentControlConfirmation) -> Unit = {},
    val onDismissModeratorContentControls: () -> Unit = {},
    val onModeratorContentControlsAccountChanged: (String) -> Unit = {},
    val moderatorLogState: ModeratorLogUiState = ModeratorLogUiState(),
    val onOpenModeratorLog: (String, String) -> Unit = { _, _ -> },
    val onRefreshModeratorLog: (String) -> Unit = {},
    val onAppendModeratorLog: (String) -> Unit = {},
    val onDismissModeratorLog: () -> Unit = {},
    val onModeratorLogAccountChanged: (String) -> Unit = {},
    val onAuthorizeModeratorLog: () -> Unit = {},
    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1
    val onCancelModeratorLogAuthorization: () -> Unit = {},
    val moderatorUserManagementState: ModeratorUserManagementUiState =
        ModeratorUserManagementUiState(),
    val onOpenModeratorUserManagement: (String, String) -> Unit = { _, _ -> },
    val onRefreshModeratorUserManagement: (String) -> Unit = {},
    val onAppendModeratorUserManagement: (String) -> Unit = {},
    val onAppendMutedModeratorUserManagement: (String) -> Unit = {},
    val onAppendApprovedModeratorUserManagement: (String) -> Unit = {},
    val onMutateModeratorUser: (RedditModeratorUserBanRequest) -> Unit = {},
    val onMutateMutedModeratorUser: (
        app.subshell.prototype.data.account.RedditModeratorUserMuteRequest,
    ) -> Unit = {},
    val onMutateApprovedModeratorUser: (
        app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest,
    ) -> Unit = {},
    val onDismissModeratorUserManagement: () -> Unit = {},
    val onModeratorUserManagementAccountChanged: (String) -> Unit = {},
    val onAuthorizeModeratorUserManagement: () -> Unit = {},
)
