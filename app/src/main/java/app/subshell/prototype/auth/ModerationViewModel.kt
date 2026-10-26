package app.subshell.prototype.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.ModeratorContentControlConfirmation
import app.subshell.prototype.data.account.ModeratorContentControlsUiState
import app.subshell.prototype.data.account.ModeratorLogUiState
import app.subshell.prototype.data.account.ModeratorUserManagementUiState
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * AUTHSPLIT01_MODERATION_VIEWMODEL_V1: the moderator area's state holder
 * (Google: one ViewModel per screen area): mod queue and moderator directory,
 * approve/remove, reports, content controls, mod log, user management,
 * moderator team (invite, permissions, removal), removal reasons and modmail.
 * Moved out of AuthConnectViewModel verbatim; it shares the auth runtime
 * through [SharedAuthRuntime] and registers its coordinators' capability
 * callback routes there, which AuthConnectViewModel's route table hands on.
 */
internal class ModerationViewModel internal constructor(
    private val shared: SharedAuthRuntime,
) : ViewModel() {
    private val runtime = shared.runtime

    companion object {
        val Factory = viewModelFactory {
            initializer { ModerationViewModel(requireNotNull(this[AuthRuntimeKey])) }
        }
    }

    // MOD01A1_VIEWMODEL_MODERATION_STATE_V1
    // SUBSHELL_MODERATIONQUEUE01_COORDINATOR_V1: the moderator directory and
    // mod-queue reads live in ModerationQueueCoordinator.
    private val moderationQueueCoordinator = ModerationQueueCoordinator(
        moderationLoader = runtime.redditModerationLoader,
        scope = viewModelScope,
    )

    // MOD01A2_VIEWMODEL_MODERATION_ACTION_COORDINATOR_V1
    private val moderationActionCoordinator = ModerationActionCoordinator(
        capability = runtime.moderationActionCapability,
        writer = runtime.moderationActionWriter,
        clientMode = runtime.moderationActionClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
        onOptimisticApply = moderationQueueCoordinator::optimisticallyRemoveModerationQueueItem,
        onRollback = moderationQueueCoordinator::rollbackModerationQueueItem,
        onReconcile = moderationQueueCoordinator::reconcileModerationQueue,
        onApplied = { request ->
            val removed = when (request.action) {
                app.subshell.prototype.data.account.RedditModeratorAction.REMOVE,
                app.subshell.prototype.data.account.RedditModeratorAction.SPAM,
                -> true
                app.subshell.prototype.data.account.RedditModeratorAction.APPROVE -> false
                else -> null
            }
            if (removed != null && request.thingFullname.startsWith("t1_")) {
                // AUTHSPLIT01: the comment thread applies it (SharedAuthRuntime).
                shared.commentModerated(
                    ModeratedComment(
                        accountStableId = request.accountStableId,
                        commentFullname = request.thingFullname,
                        removed = removed,
                    ),
                )
            }
        },
    )

    val moderationActionUiState: StateFlow<RedditModeratorActionUiState> =
        moderationActionCoordinator.uiState

    // MODERATION02A2_SHARED_MODERATION_REPORT_COORDINATOR_V1
    private val moderatorReportsCoordinator = ModeratorReportsCoordinator(
        loader = runtime.redditModerationLoader,
        activeAccountStableId = { runtime.accountSessionDirectory?.snapshot()?.activeAccountId },
        performAction = { request, verify -> moderationActionCoordinator.reportAction(request, verify) },
    )
    val moderatorReportsUiState = moderatorReportsCoordinator.uiState

    fun openModeratorReports(accountStableId: String, target: app.subshell.prototype.data.account.ModeratorReportTarget?) {
        moderatorReportsCoordinator.open(accountStableId, target)
        if (target != null) viewModelScope.launch { moderatorReportsCoordinator.refresh(accountStableId) }
    }

    fun refreshModeratorReports(accountStableId: String) {
        viewModelScope.launch { moderatorReportsCoordinator.refresh(accountStableId) }
    }

    fun confirmModeratorReports(confirmation: app.subshell.prototype.data.account.ModeratorReportConfirmation) {
        viewModelScope.launch { moderatorReportsCoordinator.confirm(confirmation) }
    }

    fun dismissModeratorReports() = moderatorReportsCoordinator.dismiss()
    fun moderatorReportsAccountChanged(accountStableId: String) = moderatorReportsCoordinator.accountChanged(accountStableId)

    // MODERATION02A3_SHARED_CONTENT_CONTROLS_COORDINATOR_V1
    private val moderatorContentControlsCoordinator =
        ModeratorContentControlsCoordinator(
            loader = runtime.redditModerationLoader,
            activeAccountStableId = {
                runtime.accountSessionDirectory?.snapshot()?.activeAccountId
            },
            activeAccountUsername = {
                val snapshot = runtime.accountSessionDirectory?.snapshot()
                snapshot
                    ?.accounts
                    ?.firstOrNull { account ->
                        account.stableId == snapshot.activeAccountId
                    }
                    ?.username
            },
            performAction = { request, verify ->
                moderationActionCoordinator.contentControlAction(request, verify)
            },
        )

    val moderatorContentControlsUiState: StateFlow<ModeratorContentControlsUiState> =
        moderatorContentControlsCoordinator.uiState

    fun openModeratorContentControls(
        accountStableId: String,
        target: app.subshell.prototype.data.account.ModeratorReportTarget,
    ) {
        moderatorContentControlsCoordinator.open(accountStableId, target)
        viewModelScope.launch {
            moderatorContentControlsCoordinator.refresh(accountStableId)
        }
    }

    fun refreshModeratorContentControls(accountStableId: String) {
        viewModelScope.launch {
            moderatorContentControlsCoordinator.refresh(accountStableId)
        }
    }

    fun confirmModeratorContentControl(
        confirmation: ModeratorContentControlConfirmation,
    ) {
        viewModelScope.launch {
            moderatorContentControlsCoordinator.confirm(confirmation)
        }
    }

    fun dismissModeratorContentControls() =
        moderatorContentControlsCoordinator.dismiss()

    fun moderatorContentControlsAccountChanged(accountStableId: String) =
        moderatorContentControlsCoordinator.accountChanged(accountStableId)

    // MODERATION02A4_VIEWMODEL_MOD_LOG_COORDINATOR_V1
    private val moderatorLogCoordinator = ModerationLogCoordinator(
        capability = runtime.moderationLogCapability,
        loader = runtime.redditModerationLogLoader,
        clientMode = runtime.moderationLogClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
    )

    val moderatorLogUiState: StateFlow<ModeratorLogUiState> =
        moderatorLogCoordinator.uiState

    fun openModeratorLog(accountStableId: String, communityName: String) {
        moderatorLogCoordinator.open(accountStableId, communityName)
        viewModelScope.launch {
            moderatorLogCoordinator.load(accountStableId)
        }
    }

    fun refreshModeratorLog(accountStableId: String) {
        viewModelScope.launch {
            moderatorLogCoordinator.load(accountStableId, forceRefresh = true)
        }
    }

    fun appendModeratorLog(accountStableId: String) {
        viewModelScope.launch {
            moderatorLogCoordinator.append(accountStableId)
        }
    }

    suspend fun beginRedditModeratorLogAuthorization(): AuthBrowserLaunchResult =
        moderatorLogCoordinator.beginAuthorization()

    fun redditModeratorLogAuthorizationBrowserLaunchFailed() {
        moderatorLogCoordinator.browserLaunchFailed()
    }

    fun cancelRedditModeratorLogAuthorization() {
        moderatorLogCoordinator.cancelPendingAuthorization()
    }

    fun dismissModeratorLog() = moderatorLogCoordinator.dismiss()

    fun moderatorLogAccountChanged(accountStableId: String) =
        moderatorLogCoordinator.accountChanged(accountStableId)

    // MODERATION02A5A1_VIEWMODEL_USER_MANAGEMENT_COORDINATOR_V1
    private val moderatorUserManagementCoordinator = ModeratorUserManagementCoordinator(
        capability = runtime.moderatorUserManagementCapability,
        reader = runtime.redditBannedUsersReader,
        writer = runtime.redditModeratorUserWriter,
        mutedReader = runtime.redditMutedUsersReader,
        muteWriter = runtime.redditModeratorUserMuteWriter,
        approvedReader = runtime.redditApprovedUsersReader,
        approvalWriter = runtime.redditModeratorUserApprovalWriter,
        clientMode = runtime.moderatorUserManagementClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
        activeAccountUsername = {
            val snapshot = runtime.accountSessionDirectory?.snapshot()
            snapshot?.accounts?.firstOrNull { account ->
                account.stableId == snapshot.activeAccountId
            }?.username
        },
    )

    val moderatorUserManagementUiState: StateFlow<ModeratorUserManagementUiState> =
        moderatorUserManagementCoordinator.uiState

    fun openModeratorUserManagement(accountStableId: String, communityName: String) {
        moderatorUserManagementCoordinator.open(accountStableId, communityName)
        viewModelScope.launch {
            moderatorUserManagementCoordinator.load(accountStableId)
            moderatorUserManagementCoordinator.loadMuted(accountStableId)
            moderatorUserManagementCoordinator.loadApproved(accountStableId)
        }
    }

    fun refreshModeratorUserManagement(accountStableId: String) {
        viewModelScope.launch {
            moderatorUserManagementCoordinator.load(accountStableId, forceRefresh = true)
            moderatorUserManagementCoordinator.loadMuted(accountStableId, forceRefresh = true)
            moderatorUserManagementCoordinator.loadApproved(accountStableId, forceRefresh = true)
        }
    }

    fun appendModeratorUserManagement(accountStableId: String) {
        viewModelScope.launch { moderatorUserManagementCoordinator.append(accountStableId) }
    }

    fun appendMutedModeratorUserManagement(accountStableId: String) {
        viewModelScope.launch { moderatorUserManagementCoordinator.appendMuted(accountStableId) }
    }

    fun appendApprovedModeratorUserManagement(accountStableId: String) {
        viewModelScope.launch { moderatorUserManagementCoordinator.appendApproved(accountStableId) }
    }

    fun mutateModeratorUser(request: RedditModeratorUserBanRequest) {
        viewModelScope.launch { moderatorUserManagementCoordinator.mutate(request) }
    }

    fun mutateMutedModeratorUser(
        request: app.subshell.prototype.data.account.RedditModeratorUserMuteRequest,
    ) {
        viewModelScope.launch { moderatorUserManagementCoordinator.mutateMuted(request) }
    }

    fun mutateApprovedModeratorUser(
        request: app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest,
    ) {
        viewModelScope.launch { moderatorUserManagementCoordinator.mutateApproved(request) }
    }

    suspend fun beginRedditModeratorUserManagementAuthorization(): AuthBrowserLaunchResult =
        moderatorUserManagementCoordinator.beginAuthorization()

    fun redditModeratorUserManagementAuthorizationBrowserLaunchFailed() {
        moderatorUserManagementCoordinator.browserLaunchFailed()
    }

    fun dismissModeratorUserManagement() = moderatorUserManagementCoordinator.dismiss()

    fun moderatorUserManagementAccountChanged(accountStableId: String) =
        moderatorUserManagementCoordinator.accountChanged(accountStableId)

    // MODERATION02A5A5_VIEWMODEL_MODERATOR_INVITE_COORDINATOR_V1
    private val moderatorInviteCoordinator = ModeratorInviteCoordinator(
        capability = runtime.moderatorTeamCapability,
        writer = runtime.redditModeratorInviteWriter,
        clientMode = runtime.moderatorTeamClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
        activeAccountUsername = {
            val snapshot = runtime.accountSessionDirectory?.snapshot()
            snapshot?.accounts?.firstOrNull { account ->
                account.stableId == snapshot.activeAccountId
            }?.username
        },
    )

    val moderatorInviteUiState:
        StateFlow<app.subshell.prototype.data.account.ModeratorInviteUiState> =
        moderatorInviteCoordinator.uiState

    fun openModeratorInvite(accountStableId: String, communityName: String) {
        moderatorPermissionCoordinator.dismiss()
        moderatorRemovalCoordinator.dismiss()
        moderatorInviteCoordinator.open(accountStableId, communityName)
    }

    fun inviteModerator(
        request: app.subshell.prototype.data.account.RedditModeratorInviteRequest,
    ) {
        viewModelScope.launch { moderatorInviteCoordinator.invite(request) }
    }

    suspend fun beginRedditModeratorInviteAuthorization(): AuthBrowserLaunchResult =
        moderatorInviteCoordinator.beginAuthorization()

    fun redditModeratorInviteAuthorizationBrowserLaunchFailed() {
        moderatorInviteCoordinator.browserLaunchFailed()
    }

    fun dismissModeratorInvite() = moderatorInviteCoordinator.dismiss()

    fun moderatorInviteAccountChanged(accountStableId: String) =
        moderatorInviteCoordinator.accountChanged(accountStableId)

    // MODERATION02A5A6_VIEWMODEL_MODERATOR_PERMISSION_COORDINATOR_V1
    private val moderatorPermissionCoordinator = ModeratorPermissionCoordinator(
        capability = runtime.moderatorTeamCapability,
        writer = runtime.redditModeratorPermissionWriter,
        clientMode = runtime.moderatorTeamClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
        activeAccountUsername = {
            val snapshot = runtime.accountSessionDirectory?.snapshot()
            snapshot?.accounts?.firstOrNull { account ->
                account.stableId == snapshot.activeAccountId
            }?.username
        },
    )

    val moderatorPermissionUpdateUiState:
        StateFlow<app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState> =
        moderatorPermissionCoordinator.uiState

    fun openModeratorPermissionUpdate(
        accountStableId: String,
        communityName: String,
        username: String,
    ) {
        moderatorInviteCoordinator.dismiss()
        moderatorRemovalCoordinator.dismiss()
        moderatorPermissionCoordinator.open(accountStableId, communityName, username)
    }

    fun updateModeratorPermissions(
        request: app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest,
    ) {
        viewModelScope.launch { moderatorPermissionCoordinator.update(request) }
    }

    suspend fun beginRedditModeratorPermissionAuthorization(): AuthBrowserLaunchResult =
        moderatorPermissionCoordinator.beginAuthorization()

    fun redditModeratorPermissionAuthorizationBrowserLaunchFailed() {
        moderatorPermissionCoordinator.browserLaunchFailed()
    }

    fun dismissModeratorPermissionUpdate() = moderatorPermissionCoordinator.dismiss()

    fun moderatorPermissionUpdateAccountChanged(accountStableId: String) =
        moderatorPermissionCoordinator.accountChanged(accountStableId)

    // MODERATION02A5A7_VIEWMODEL_MODERATOR_REMOVAL_COORDINATOR_V1
    private val moderatorRemovalCoordinator = ModeratorRemovalCoordinator(
        capability = runtime.moderatorTeamCapability,
        writer = runtime.redditModeratorRemovalWriter,
        clientMode = runtime.moderatorTeamClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
        activeAccountUsername = {
            val snapshot = runtime.accountSessionDirectory?.snapshot()
            snapshot?.accounts?.firstOrNull { account ->
                account.stableId == snapshot.activeAccountId
            }?.username
        },
    )

    val moderatorRemovalUiState:
        StateFlow<app.subshell.prototype.data.account.ModeratorRemovalUiState> =
        moderatorRemovalCoordinator.uiState

    fun openModeratorRemoval(
        accountStableId: String,
        communityName: String,
        username: String,
    ) {
        moderatorInviteCoordinator.dismiss()
        moderatorPermissionCoordinator.dismiss()
        moderatorRemovalCoordinator.open(accountStableId, communityName, username)
    }

    fun removeModerator(
        request: app.subshell.prototype.data.account.RedditModeratorRemovalRequest,
    ) {
        viewModelScope.launch { moderatorRemovalCoordinator.remove(request) }
    }

    suspend fun beginRedditModeratorRemovalAuthorization(): AuthBrowserLaunchResult =
        moderatorRemovalCoordinator.beginAuthorization()

    fun redditModeratorRemovalAuthorizationBrowserLaunchFailed() {
        moderatorRemovalCoordinator.browserLaunchFailed()
    }

    fun dismissModeratorRemoval() = moderatorRemovalCoordinator.dismiss()

    fun moderatorRemovalAccountChanged(accountStableId: String) =
        moderatorRemovalCoordinator.accountChanged(accountStableId)

    // MOD01A21_VIEWMODEL_REMOVAL_REASON_COORDINATOR_V1
    private val removalReasonCoordinator = RemovalReasonCoordinator(
        capability = runtime.removalReasonCapability,
        loader = runtime.removalReasonLoader,
        clientMode = runtime.removalReasonClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
    )

    val removalReasonUiState: StateFlow<RedditRemovalReasonUiState> =
        removalReasonCoordinator.uiState

    // MODMAIL01A1_VIEWMODEL_READ_COORDINATOR_V1
    private val modmailCoordinator = ModmailCoordinator(
        capability = runtime.modmailReadCapability,
        loader = runtime.redditModmailLoader,
        writer = runtime.redditModmailWriter,
        clientMode = runtime.modmailReadClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
    )

    val redditModmailUiState: StateFlow<RedditModmailUiState> =
        modmailCoordinator.uiState

    val redditModerationUiState: StateFlow<RedditModerationUiState> =
        moderationQueueCoordinator.redditModerationUiState

    // MOD01A1_VIEWMODEL_MODERATOR_DIRECTORY_AND_MODQUEUE_V1
    // MOD01A22_VIEWMODEL_MODERATION_TARGET_RESOLUTION_V1
    fun loadRedditModeratorCommunities(
        accountStableId: String,
        forceRefresh: Boolean = false,
    ) =
        moderationQueueCoordinator.loadRedditModeratorCommunities(accountStableId, forceRefresh)

    fun loadRedditModQueue(
        accountStableId: String,
        rawCommunityName: String,
        forceRefresh: Boolean = false,
    ) =
        moderationQueueCoordinator.loadRedditModQueue(accountStableId, rawCommunityName, forceRefresh)

    fun resolveRedditModerationTarget(
        accountStableId: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) =
        moderationQueueCoordinator.resolveRedditModerationTarget(accountStableId, postId, onResolved)

    fun appendRedditModQueue(
        accountStableId: String,
        rawCommunityName: String,
    ) =
        moderationQueueCoordinator.appendRedditModQueue(accountStableId, rawCommunityName)

    // MOD01A2_VIEWMODEL_APPROVE_REMOVE_ACTIONS_V1
    fun moderateRedditQueueItem(request: RedditModeratorActionRequest) {
        viewModelScope.launch {
            moderationActionCoordinator.moderate(request)
        }
    }

    fun retryRedditModerationAction() {
        viewModelScope.launch {
            moderationActionCoordinator.retry()
        }
    }

    suspend fun beginRedditModerationActionAuthorization(): AuthBrowserLaunchResult =
        moderationActionCoordinator.beginAuthorization()

    fun redditModerationActionAuthorizationBrowserLaunchFailed() {
        moderationActionCoordinator.browserLaunchFailed()
    }

    fun cancelRedditModerationActionAuthorization() {
        moderationActionCoordinator.cancelPendingAuthorization()
    }

    // MOD01A21_VIEWMODEL_REMOVAL_REASON_ACTIONS_V1
    fun loadRedditRemovalReasons(
        accountStableId: String,
        communityName: String,
        force: Boolean,
    ) {
        viewModelScope.launch {
            removalReasonCoordinator.load(accountStableId, communityName, force)
        }
    }

    suspend fun beginRedditRemovalReasonAuthorization(): AuthBrowserLaunchResult =
        removalReasonCoordinator.beginAuthorization()

    fun redditRemovalReasonAuthorizationBrowserLaunchFailed() {
        removalReasonCoordinator.browserLaunchFailed()
    }

    fun cancelRedditRemovalReasonAuthorization() {
        removalReasonCoordinator.cancelPendingAuthorization()
    }

    // MODMAIL01A1_VIEWMODEL_READ_ACTIONS_V1
    fun loadRedditModmail(
        accountStableId: String,
        forceRefresh: Boolean,
    ) {
        val account = accountStableId.trim()
        // LOW11_BACKGROUND_SYNC_SCHEDULED_ON_CHANGE_V1 (OCB-405): as the inbox
        // does, only while inbox notifications are on.
        shared.inboxBackgroundSync.keep(account)
        if (account.isEmpty()) return
        viewModelScope.launch {
            modmailCoordinator.load(account, forceRefresh)
        }
    }

    fun appendRedditModmail(accountStableId: String) {
        viewModelScope.launch {
            modmailCoordinator.append(accountStableId)
        }
    }

    fun selectRedditModmailListMode(
        accountStableId: String,
        listMode: RedditModmailListMode,
    ) {
        viewModelScope.launch { modmailCoordinator.selectListMode(accountStableId, listMode) }
    }

    fun openRedditModmailConversation(
        accountStableId: String,
        conversationId: String,
    ) {
        viewModelScope.launch {
            modmailCoordinator.openConversation(accountStableId, conversationId)
        }
    }

    fun closeRedditModmailConversation() {
        modmailCoordinator.closeConversation()
    }

    fun setRedditModmailUnread(
        accountStableId: String,
        conversationId: String,
        unread: Boolean,
    ) {
        viewModelScope.launch {
            modmailCoordinator.setUnread(accountStableId, conversationId, unread)
        }
    }

    fun verifyRedditModmailReadState(
        accountStableId: String,
        conversationId: String,
    ) {
        viewModelScope.launch {
            modmailCoordinator.verifyReadState(accountStableId, conversationId)
        }
    }

    // MODMAIL02_VIEWMODEL_REPLY_INTERNAL_NOTE_ACTION_V1
    fun sendRedditModmailMessage(
        accountStableId: String,
        conversationId: String,
        body: String,
        internal: Boolean,
        authorHidden: Boolean,
    ) {
        viewModelScope.launch {
            modmailCoordinator.sendMessage(
                accountStableId = accountStableId,
                rawConversationId = conversationId,
                body = body,
                internal = internal,
                authorHidden = authorHidden,
            )
        }
    }

    fun setRedditModmailArchived(
        accountStableId: String,
        conversationId: String,
        archived: Boolean,
    ) {
        viewModelScope.launch {
            modmailCoordinator.setArchived(accountStableId, conversationId, archived)
        }
    }

    fun applyRedditModmailTriage(
        accountStableId: String,
        conversationId: String,
        action: RedditModmailTriageAction,
    ) {
        viewModelScope.launch {
            modmailCoordinator.applyTriage(accountStableId, conversationId, action)
        }
    }

    suspend fun beginRedditModmailAuthorization(
        accountStableId: String,
    ): AuthBrowserLaunchResult =
        modmailCoordinator.beginAuthorization(accountStableId)

    fun redditModmailAuthorizationBrowserLaunchFailed() {
        modmailCoordinator.browserLaunchFailed()
    }

    fun cancelRedditModmailAuthorization() {
        modmailCoordinator.cancelPendingAuthorization()
    }

    init {
        shared.registerCapabilityRoutes(
            mapOf(
                AuthCapabilityAuthorizationOwner.MODERATION_ACTION to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        moderationActionCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        moderationActionCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.MODERATION_REASON to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        removalReasonCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        removalReasonCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.MODMAIL_READ to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        modmailCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        modmailCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.MODERATION_LOG to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        moderatorLogCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        moderatorLogCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.MODERATION_USERS to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        moderatorUserManagementCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        moderatorUserManagementCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.MODERATION_TEAM to CapabilityCallbackRoute(
                    handle = handle@{ resultToken, _ ->
                        if (moderatorInviteCoordinator.handleCallbackResultTokenIfOwned(resultToken)) {
                            return@handle
                        }
                        if (moderatorPermissionCoordinator.handleCallbackResultTokenIfOwned(resultToken)) {
                            return@handle
                        }
                        moderatorRemovalCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = resume@{
                        if (moderatorInviteCoordinator.resumeStoredAuthorizationIfPresent()) {
                            return@resume
                        }
                        if (moderatorPermissionCoordinator.resumeStoredAuthorizationIfPresent()) {
                            return@resume
                        }
                        moderatorRemovalCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),
            ),
        )
    }
}
