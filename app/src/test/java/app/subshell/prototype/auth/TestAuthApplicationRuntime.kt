package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.UnavailableAuthConnectFlow

/**
 * The runtime AuthApplicationRuntimeFactory returns when the encrypted
 * session store cannot be read: every capability absent and an
 * unavailable connect flow. Tests copy() the capability under test onto
 * it and inject the result through AuthConnectViewModel's runtime seam
 * (HARNESS02). Regenerate when AuthApplicationRuntime gains a field: the
 * compiler flags a missing argument.
 */
internal fun unavailableAuthApplicationRuntime(): AuthApplicationRuntime = AuthApplicationRuntime(
    accountSessionDirectory = null,
    customFeedReader = null,
    customFeedWriteCapability = null,
    customFeedWriteClientMode = null,
    customFeedCreator = null,
    inboxReadCapability = null,
    inboxReadClientMode = null,
    inboxReadMutationWriter = null,
    connectFlow = UnavailableAuthConnectFlow("Reddit"),
    commentWriteClientMode = null,
    composerFlairClientMode = null,
    commentSubmitter = null,
    textPostSubmitter = null,
    composerPolicyLoader = null,
    crosspostSourceLoader = null,
    composerCommunitySearchLoader = null,
    redditFeedUpdater = null,
    redditInboxLoader = null,
    redditCommentLoader = null,
    redditSubscriptionsLoader = null,
    redditCommunityDiscoveryLoader = null,
    communityWikiReadAuthorizer = null,
    communityWikiReadClientMode = null,
    redditCommunityWikiLoader = null,
    redditModerationLoader = null,
    moderationLogCapability = null,
    moderationLogClientMode = null,
    redditModerationLogLoader = null,
    moderatorUserManagementCapability = null,
    moderatorUserManagementClientMode = null,
    redditBannedUsersReader = null,
    redditModeratorUserWriter = null,
    redditMutedUsersReader = null,
    redditModeratorUserMuteWriter = null,
    redditApprovedUsersReader = null,
    moderatorTeamCapability = null,
    moderatorTeamClientMode = null,
    redditModeratorInviteWriter = null,
    redditModeratorPermissionWriter = null,
    redditModeratorRemovalWriter = null,
    redditModeratorUserApprovalWriter = null,
    modmailReadCapability = null,
    modmailReadClientMode = null,
    redditModmailLoader = null,
    redditModmailWriter = null,
    textPostWriteClientMode = null,
    commentEditClientMode = null,
    commentEditor = null,
    commentDeleter = null,
    postEditor = null,
    postDeleter = null,
    postManager = null,
    postModerationClientMode = null,
    moderationActionCapability = null,
    moderationActionClientMode = null,
    moderationActionWriter = null,
    removalReasonCapability = null,
    removalReasonClientMode = null,
    removalReasonLoader = null,
    commentEditAuthorizationCapability = null,
    commentDeleteAuthorizationCapability = null,
    commentWriteAuthorizationCapability = null,
    textPostWriteAuthorizationCapability = null,
    postEditAuthorizationCapability = null,
    postDeleteAuthorizationCapability = null,
    composerFlairAuthorizationCapability = null,
    composerPostRequirementsAuthorizationCapability = null,
    postManageEditAuthorizationCapability = null,
    postManageFlairAuthorizationCapability = null,
    postManageModerationAuthorizationCapability = null,
    communityMembershipCapability = null,
    communityMembershipClientMode = null,
    communityMembershipWriter = null,
    postVoteCapability = null,
    postVoteClientMode = null,
    postVoteWriter = null,
    postSaveCapability = null,
    postSaveClientMode = null,
    postSaveWriter = null,
    postFollowCapability = null,
    postFollowClientMode = null,
    postFollowWriter = null,
    contentSafetyCapability = null,
    contentSafetyClientMode = null,
    contentSafetyWriter = null,
    profileHistoryCapability = null,
    profileHistoryClientMode = null,
    profileHistoryAuthorizer = null,
    redditProfileCommentsLoader = null,
    redditAccountHistoryCommentLoader = null,
    redditAccountHistoryUpdater = null,
    redditSavedHistoryLoader = null,
    messageReplyWriter = null,
    onlineVisibilityService = null,
    redditUserFlairManager = null,
    authorizationOwnerResolver = null,
    // LOW11 (OCB-404): tests route callbacks inline, as before.
    callbackRoutingDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
)

// AUTHSPLIT02–05: the inbox, post composing, communities and reading areas live in their own
// ViewModels, made on the runtime an AuthConnectViewModel owns. A test gets
// the ones that belong to its AuthConnectViewModel; where callbacks are
// routed it makes them at once ([withAreaViewModels]), as MainActivity does.
private class AreaViewModels(auth: AuthConnectViewModel) {
    val inbox = InboxViewModel(auth.sharedRuntime)
    val postComposer = PostComposerViewModel(auth.sharedRuntime)
    val communities = CommunitiesViewModel(auth.sharedRuntime)
    val reading = ReadingViewModel(auth.sharedRuntime)
}

private val areaViewModels = java.util.WeakHashMap<AuthConnectViewModel, AreaViewModels>()

private fun AuthConnectViewModel.areas(): AreaViewModels = synchronized(areaViewModels) {
    areaViewModels.getOrPut(this) { AreaViewModels(this) }
}

internal fun AuthConnectViewModel.withAreaViewModels(): AuthConnectViewModel = also { it.areas() }

internal fun AuthConnectViewModel.inbox(): InboxViewModel = areas().inbox

internal fun AuthConnectViewModel.postComposer(): PostComposerViewModel = areas().postComposer

internal fun AuthConnectViewModel.communities(): CommunitiesViewModel = areas().communities

internal fun AuthConnectViewModel.reading(): ReadingViewModel = areas().reading
