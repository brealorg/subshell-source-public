package app.subshell.prototype.ui



import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.key
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.share.ShareIntoSubshellLaunch
import app.subshell.prototype.ui.app.AccountScopedEffects
import app.subshell.prototype.ui.app.AppBottomBar
import app.subshell.prototype.ui.app.routeKeepsNavigationBar
import app.subshell.prototype.ui.app.ExpandedMainNavigationRail
import app.subshell.prototype.ui.app.RuntimeErrorReports
import app.subshell.prototype.ui.app.AutomaticBackupHistoryEffect
import app.subshell.prototype.ui.app.CommunityPostCheckEffect
import app.subshell.prototype.ui.app.NsfwAccountPreferenceNotice
import app.subshell.prototype.ui.app.CustomFeedLinkEffect
import app.subshell.prototype.ui.app.LauncherShortcutsEffect
import app.subshell.prototype.ui.app.LauncherShortcutTargetEffect
import app.subshell.prototype.ui.app.ShareIntoSubshellEffect
import app.subshell.prototype.ui.app.rememberChromeHiddenFractions
import app.subshell.prototype.ui.app.rememberChromeScrollConnection
import app.subshell.prototype.ui.app.AppEffectsBridge
import app.subshell.prototype.ui.app.AppFloatingActionButton
import app.subshell.prototype.ui.app.AppSheetsHost
import app.subshell.prototype.ui.app.userProfileContents
import app.subshell.prototype.ui.app.AppTopBar
import app.subshell.prototype.ui.app.ModeratorToolsDialogs
import app.subshell.prototype.ui.app.NewMessageRouteHost
import app.subshell.prototype.ui.app.predictiveRouteBack
import app.subshell.prototype.ui.app.rememberPredictiveRouteBackState
import app.subshell.prototype.ui.app.RootBackHandlers
import app.subshell.prototype.ui.app.moderatorToolsRootEntryPoints
import app.subshell.prototype.ui.app.ExpandedListDetailSurface
import app.subshell.prototype.ui.app.MainRouteHost
import app.subshell.prototype.ui.app.ModmailEntryPoints
import app.subshell.prototype.ui.app.RedditLinkUnavailable
import app.subshell.prototype.ui.app.RedditLinkUnavailableNoticeEffect
import app.subshell.prototype.ui.app.FirstRunSetupScreen
import app.subshell.prototype.ui.app.InboxNotificationPermissionEffect
import app.subshell.prototype.ui.app.JankScreenStateEffect
import app.subshell.prototype.ui.app.SystemMotionEffect
import app.subshell.prototype.ui.app.RuntimeStateBridge
import app.subshell.prototype.ui.app.rememberPostVisitPruningStateHolder
import app.subshell.prototype.ui.app.CommunityRouteHost
import app.subshell.prototype.ui.app.PostRouteHost
import app.subshell.prototype.ui.app.ConversationRouteHost
import app.subshell.prototype.ui.app.SearchRouteHost
import app.subshell.prototype.ui.app.SecondaryRouteHost
import app.subshell.prototype.ui.app.UserRouteHost
import app.subshell.prototype.ui.app.SubshellDrawer
import app.subshell.prototype.ui.app.ModerationQueueEntryPoints
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.CustomFeedsEntryPoints
import app.subshell.prototype.ui.app.MessagingEntryPoints
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.RedditFavoriteSyncEffects
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.app.CommentLoadingEntryPoints
import app.subshell.prototype.ui.app.CommentMutationEntryPoints
import app.subshell.prototype.ui.app.PostMutationEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsEntryPoints
import app.subshell.prototype.ui.app.AccountConnectEntryPoints
import app.subshell.prototype.ui.components.LocalNavigationRailShown
import app.subshell.prototype.ui.components.ModeratorToolsWorkspaceContentEntryPoints
import app.subshell.prototype.ui.components.ModeratorTeamContent
import app.subshell.prototype.ui.components.LocalModeratorToolsWorkspaceContent
import app.subshell.prototype.ui.components.ModeratorToolsWorkspaceDestination
import app.subshell.prototype.ui.components.ModeratorToolsNavigationEntryPoints
import app.subshell.prototype.ui.components.LocalModeratorToolsNavigation
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import app.subshell.prototype.ui.components.LocalModeratorReports
import app.subshell.prototype.ui.components.ModeratorReportsEntryPoints
import app.subshell.prototype.ui.components.LocalModeratorContentControls
import app.subshell.prototype.ui.components.ModeratorContentControlsEntryPoints
import app.subshell.prototype.ui.components.LocalModeratorLog
import app.subshell.prototype.ui.components.ModeratorLogEntryPoints
import app.subshell.prototype.ui.components.ModeratorLogDialog
import app.subshell.prototype.ui.components.LocalModeratorUserManagement
import app.subshell.prototype.ui.components.ModeratorUserManagementEntryPoints
import app.subshell.prototype.ui.components.ModeratorUserManagementDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.Post
import app.subshell.prototype.media.ProvideVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.screens.InboxContextualSelection
import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import app.subshell.prototype.ui.screens.MediaViewerScreen
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.RedditOnboardingScreen
import app.subshell.prototype.ui.screens.RedditLinkHandlingOnboardingScreen
import app.subshell.prototype.ui.components.ViewportErrorDetailsSheet
import app.subshell.prototype.ui.components.ViewportErrorTrayHost
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.rememberViewportErrorHostState
import app.subshell.prototype.ui.components.LocalRouteTopBarBehavior
import app.subshell.prototype.ui.components.LocalRouteTopBarDrawsStatusBar
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.SubshellThemeSettings
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.backup.rememberSettingsBackupRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.ui.screens.CommentMediaViewerScreen
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.ui.app.SettingsReviewNudge
import app.subshell.prototype.ui.app.CrashReportPrompt






// CROSSPOST01A1E1_POST_COMPOSER_WRITE_RUNTIME_BUNDLE_V1
data class PostComposerWriteRuntimeEntryPoints(
    val textPostWriteState: TextPostWriteUiState = TextPostWriteUiState(),
    val crosspostReviewState: CrosspostReviewUiState = CrosspostReviewUiState(),
    val onReviewCrosspost: (PostComposerDraft) -> Unit = {},
    val onPublishReviewedCrosspost: (PostComposerDraft, Long) -> Unit = { _, _ -> },
    val onInvalidateCrosspostReview: () -> Unit = {},
)

// MODERATION02A5A6_MODERATOR_TEAM_RUNTIME_BUNDLE_V1
data class ModeratorTeamRuntimeEntryPoints(
    val moderatorInviteState:
        app.subshell.prototype.data.account.ModeratorInviteUiState =
        app.subshell.prototype.data.account.ModeratorInviteUiState(),
    val onOpenModeratorInvite: (String, String) -> Unit = { _, _ -> },
    val onInviteModerator:
        (app.subshell.prototype.data.account.RedditModeratorInviteRequest) -> Unit = {},
    val onDismissModeratorInvite: () -> Unit = {},
    val onModeratorInviteAccountChanged: (String) -> Unit = {},
    val onAuthorizeModeratorInvite: () -> Unit = {},
    val moderatorPermissionUpdateState:
        app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState =
        app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState(),
    val onOpenModeratorPermissionUpdate:
        (String, String, String) -> Unit = { _, _, _ -> },
    val onUpdateModeratorPermissions:
        (app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest) -> Unit = {},
    val onDismissModeratorPermissionUpdate: () -> Unit = {},
    val onModeratorPermissionUpdateAccountChanged: (String) -> Unit = {},
    val onAuthorizeModeratorPermissionUpdate: () -> Unit = {},
    val moderatorRemovalState:
        app.subshell.prototype.data.account.ModeratorRemovalUiState =
        app.subshell.prototype.data.account.ModeratorRemovalUiState(),
    val onOpenModeratorRemoval:
        (String, String, String) -> Unit = { _, _, _ -> },
    val onRemoveModerator:
        (app.subshell.prototype.data.account.RedditModeratorRemovalRequest) -> Unit = {},
    val onDismissModeratorRemoval: () -> Unit = {},
    val onModeratorRemovalAccountChanged: (String) -> Unit = {},
    val onAuthorizeModeratorRemoval: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubshellApp(
    videoPlaybackCoordinator: VideoPlaybackCoordinator,
    accountConnect: AccountConnectEntryPoints = AccountConnectEntryPoints(),
    customFeeds: CustomFeedsEntryPoints = CustomFeedsEntryPoints(),
    redditFeed: FeedEntryPoints = FeedEntryPoints(),
    redditCommunity: CommunityEntryPoints = CommunityEntryPoints(),
    onRefreshRedditUserSubmitted: (String, String) -> Unit = { _, _ -> },
    onAppendRedditUserSubmitted: (String, String) -> Unit = { _, _ -> },
    redditAccountHistoryState: RedditAccountHistoryUiState =
        RedditAccountHistoryUiState(),
    onRefreshRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit =
        { _, _, _ -> },
    onAppendRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit =
        { _, _, _ -> },
    redditSavedHistoryState: RedditSavedHistoryUiState =
        RedditSavedHistoryUiState(),
    onRefreshRedditSavedHistory: (String, String) -> Unit = { _, _ -> },
    onAppendRedditSavedHistory: (String, String) -> Unit = { _, _ -> },
    onResolveRedditProfileCommentTarget: (
        String,
        String,
        String,
        (Post?) -> Unit,
    ) -> Unit = { _, _, _, onResolved -> onResolved(null) },
    onResolveRedditSavedCommentTarget: (
        String,
        String,
        String,
        (Post?) -> Unit,
    ) -> Unit = { _, _, _, onResolved -> onResolved(null) },
    onAuthorizeRedditProfileHistory: (String, String) -> Unit = { _, _ -> },
    redditComments: CommentLoadingEntryPoints = CommentLoadingEntryPoints(),
    redditUserFlairState: UserFlairUiState = UserFlairUiState(),
    onLoadRedditUserFlair: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSetRedditUserFlair: (UserFlairMutationRequest) -> Unit = {},
    onAuthorizeRedditUserFlair: (String) -> Unit = {},
    redditModeration: ModerationQueueEntryPoints = ModerationQueueEntryPoints(),
    moderatorTools: ModeratorToolsEntryPoints = ModeratorToolsEntryPoints(),
    moderatorTeamRuntime: ModeratorTeamRuntimeEntryPoints =
        ModeratorTeamRuntimeEntryPoints(),
    redditModmail: ModmailEntryPoints = ModmailEntryPoints(),
    postVoteState: PostVoteUiState = PostVoteUiState(),
    onVotePost: (PostVoteRequest) -> Unit = {},
    onRetryPostVote: () -> Unit = {},
    onAuthorizePostVote: () -> Unit = {},
    postSaveState: PostSaveUiState = PostSaveUiState(),
    onSavePost: (PostSaveRequest) -> Unit = {},
    onRetryPostSave: () -> Unit = {},
    onAuthorizePostSave: () -> Unit = {},
    contentSafetyState: ContentSafetyUiState = ContentSafetyUiState(),
    onSetPostHidden: (RedditPostVisibilityRequest) -> Unit = {},
    onReportContent: (RedditContentReportRequest) -> Unit = {},
    onSetUserBlocked: (RedditUserBlockRequest) -> Unit = {},
    onRefreshBlockedAccounts: (String) -> Unit = {},
    onAuthorizeBlockedAccounts: (String) -> Unit = {},
    onAppendBlockedAccounts: (String) -> Unit = {},
    onRetryContentSafety: () -> Unit = {},
    onAuthorizeContentSafety: () -> Unit = {},
    commentMutations: CommentMutationEntryPoints = CommentMutationEntryPoints(),
    postMutations: PostMutationEntryPoints = PostMutationEntryPoints(),
    postComposerWriteRuntime: PostComposerWriteRuntimeEntryPoints =
        PostComposerWriteRuntimeEntryPoints(),
    composerPolicy: ComposerPolicySnapshot = ComposerPolicySnapshot(),
    onLoadComposerPolicy: (String, String) -> Unit = { _, _ -> },
    composerCommunitySearch: ComposerCommunitySearchSnapshot =
        ComposerCommunitySearchSnapshot(),
    onSearchComposerCommunities: (String, String) -> Unit = { _, _ -> },
    onSubmitTextPost: (TextPostWriteRequest) -> Unit = {},
    onReconcileTextPostSubmit: () -> Unit = {},
    onAuthorizeTextPostWrite: (TextPostWriteRequest) -> Unit = {},
    onAuthorizeComposerPostRequirements: (String) -> Boolean = { false },
    onAuthorizeComposerFlair: (String) -> Unit = {},
    onCancelTextPostWriteAuthorization: () -> Unit = {},
    onResetTextPostWriteState: () -> Unit = {},
    redditInbox: InboxEntryPoints = InboxEntryPoints(),
    redditMessaging: MessagingEntryPoints = MessagingEntryPoints(),
    directChatStartState: DirectChatStartUiState = DirectChatStartUiState(),
    onStartRedditDirectChat: (DirectChatStartRequest) -> Unit = {},
    onResetRedditDirectChatStartState: () -> Unit = {},
    // SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1
    onResolveRedditNavigationTarget: (String, String, (Post?) -> Unit) -> Unit =
        { _, _, onResolved -> onResolved(null) },
    onRedditConfigurationChanged: () -> Unit = {},
    debugFocusedCommentLaunch: DebugFocusedCommentLaunch? = null,
    onDebugFocusedCommentConsumed: () -> Unit = {},
    redditIntentLaunch: RedditIntentLaunch? = null,
    // ISSUE33_SHARE_INTO_SUBSHELL_V1
    shareIntoSubshellLaunch: ShareIntoSubshellLaunch? = null,
    onShareIntoSubshellConsumed: () -> Unit = {},
    onRedditIntentConsumed: () -> Unit = {},
    onResolveRedditShareLink: (String, (String?) -> Unit) -> Unit = { _, onResolved -> onResolved(null) },
    redditLinkHandlingState: RedditLinkHandlingState = RedditLinkHandlingState(),
    redditLinkHandlingPromptSeen: Boolean = true,
    onOpenRedditLinkSettings: () -> Unit = {},
    onDismissRedditLinkPrompt: () -> Unit = {},
    state: SubshellState = rememberSubshellState(
        accountSessionDirectory = accountConnect.accountSessionDirectory,
        redditUserAgent = accountConnect.redditUserAgent,
    ),
    backupRepository: SettingsBackupRepository = rememberSettingsBackupRepository(),
    onThemeSettingsChanged: (SubshellThemeSettings) -> Unit = {},
) {
    val redditModmailState = redditModmail.redditModmailState
    val redditInboxState = redditInbox.redditInboxState
    val customFeedsState = customFeeds.customFeedsState
    val onLoadCustomFeeds = customFeeds.onLoadCustomFeeds
    val redditFeedState = redditFeed.redditFeedState
    val redditSubscriptionsState = redditFeed.redditSubscriptionsState
    val accountSessionDirectory = accountConnect.accountSessionDirectory
    val connectAccountState = accountConnect.connectAccountState
    val onConnectAccount = accountConnect.onConnectAccount
    val onCancelConnectAccount = accountConnect.onCancelConnectAccount
    val onResetConnectAccount = accountConnect.onResetConnectAccount
    val redditLinkUnavailableState = remember { mutableStateOf<RedditLinkUnavailable?>(null) }
    SystemMotionEffect(state)
    InboxNotificationPermissionEffect(state = state)
    JankScreenStateEffect(state = state)
    // ISSUE268_READING_STATS_COUNT_OPENS_AND_TIME_V1
    app.subshell.prototype.ui.app.ReadingStatsEffect(state = state)
    RuntimeStateBridge(
        accountConnect = accountConnect,
        redditFeed = redditFeed,
        redditAccountHistoryState = redditAccountHistoryState,
        onRefreshRedditAccountHistory = onRefreshRedditAccountHistory,
        onResolveRedditProfileCommentTarget = onResolveRedditProfileCommentTarget,
        redditUserFlairState = redditUserFlairState,
        onLoadRedditUserFlair = onLoadRedditUserFlair,
        onSetRedditUserFlair = onSetRedditUserFlair,
        onAuthorizeRedditUserFlair = onAuthorizeRedditUserFlair,
        redditCommunity = redditCommunity,
        moderatorTools = moderatorTools,
        postVoteState = postVoteState,
        onVotePost = onVotePost,
        onAuthorizePostVote = onAuthorizePostVote,
        postSaveState = postSaveState,
        onSavePost = onSavePost,
        onAuthorizePostSave = onAuthorizePostSave,
        contentSafetyState = contentSafetyState,
        onSetPostHidden = onSetPostHidden,
        onReportContent = onReportContent,
        onSetUserBlocked = onSetUserBlocked,
        onRefreshBlockedAccounts = onRefreshBlockedAccounts,
        onAuthorizeContentSafety = onAuthorizeContentSafety,
        commentMutations = commentMutations,
        postComposerWriteRuntime = postComposerWriteRuntime,
        onResetTextPostWriteState = onResetTextPostWriteState,
        redditInbox = redditInbox,
        onResolveRedditNavigationTarget = onResolveRedditNavigationTarget,
        debugFocusedCommentLaunch = debugFocusedCommentLaunch,
        onDebugFocusedCommentConsumed = onDebugFocusedCommentConsumed,
        redditIntentLaunch = redditIntentLaunch,
        onRedditIntentConsumed = onRedditIntentConsumed,
        onRedditLinkUnavailable = { unavailable -> redditLinkUnavailableState.value = unavailable },
        onResolveRedditShareLink = onResolveRedditShareLink,
        state = state,
    )

    val postFollowRuntime = LocalPostFollowRuntime.current

    // MODERATION02A5A6_MODERATOR_TEAM_RUNTIME_BUNDLE_ALIASES_V1
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

    // ISSUE66_LANDING_IS_NOT_RE_ARMED_BY_RECREATION_V1: the landing after a
    // connect (AUTH07B2) was armed by a plain remember, so every recreation
    // (theme, locale, font scale, process restore) armed it again and it
    // overrode the restored feed source and sort; it is consumed once.
    val pendingAuthenticatedLandingAccountIdState = rememberSaveable(
        state.activeAccountId,
        state.activeAccountIsAuthenticated,
    ) {
        mutableStateOf(
            state.activeAccountId.takeIf {
                state.activeAccountIsAuthenticated
            },
        )
    }
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    // ISSUE257_COLD_START_LANDS_ON_CACHED_SUBSCRIPTIONS_V1: the account this
    // composition started with; once another account is active, every landing
    // is an activation (sign-in, switch) and waits for Reddit as before.
    var coldStartAccountId by rememberSaveable {
        mutableStateOf(state.activeAccountId.takeIf { state.activeAccountIsAuthenticated })
    }
    LaunchedEffect(state.activeAccountId, state.activeAccountIsAuthenticated) {
        if (coldStartAccountId != state.activeAccountId || !state.activeAccountIsAuthenticated) {
            coldStartAccountId = null
        }
    }

    var redditOnboardingConnectRequested by rememberSaveable {
        mutableStateOf(false)
    }
    val redditProductAccounts =
        accountSwitcherSessions(state.accountSessions)
    val redditOnboardingConnectedAccount =
        if (redditOnboardingConnectRequested) {
            redditProductAccounts.firstOrNull { account ->
                account.stableId == state.activeAccountId
            } ?: redditProductAccounts.firstOrNull()
        } else {
            null
        }
    val showRedditOnboarding =
        redditProductAccounts.isEmpty() ||
            redditOnboardingConnectRequested
    // ISSUE271_ONE_SETUP_FLOW_AFTER_SIGN_IN_V1: a fresh install (no side
    // chosen yet) goes through the setup flow once it has an account; the link
    // screen and the side and backup dialogs are part of it.
    val showFirstRunSetup =
        redditProductAccounts.isNotEmpty() && state.commentActionOnboardingVisible
    val showRedditLinkHandlingPrompt =
        !showFirstRunSetup &&
            shouldShowRedditLinkHandlingPrompt(
                hasAuthenticatedAccounts = redditProductAccounts.isNotEmpty(),
                promptSeen = redditLinkHandlingPromptSeen,
                state = redditLinkHandlingState,
            )

    val moderatorToolsRoot = moderatorToolsRootEntryPoints(
        state = state,
        redditCommunity = redditCommunity,
        redditModeration = redditModeration,
        moderatorTools = moderatorTools,
        moderatorTeamRuntime = moderatorTeamRuntime,
    )
    val moderatorToolsWorkspaceCommunity = moderatorToolsRoot.workspaceCommunity
    val moderatorToolsNavigation = moderatorToolsRoot.navigation
    val moderatorToolsWorkspaceContent = moderatorToolsRoot.workspaceContent
    val moderatorReportsEntries = moderatorToolsRoot.reports
    val moderatorContentControlEntries = moderatorToolsRoot.contentControls
    val moderatorLogEntries = moderatorToolsRoot.log
    val moderatorUserManagementEntries = moderatorToolsRoot.userManagement
    ProvideVideoPlaybackCoordinator(
        state = state,
        coordinator = videoPlaybackCoordinator,
    ) {
        CompositionLocalProvider(
            LocalModeratorToolsNavigation provides moderatorToolsNavigation,
            LocalModeratorToolsWorkspaceContent provides moderatorToolsWorkspaceContent,
            LocalModeratorReports provides moderatorReportsEntries,
            LocalModeratorContentControls provides moderatorContentControlEntries,
            LocalModeratorLog provides moderatorLogEntries,
            LocalModeratorUserManagement provides moderatorUserManagementEntries,
        ) {
            val themeSettings = SubshellThemeSettings(
                mode = state.themeMode,
                dynamicColor = state.dynamicColor,
                pureBlack = state.pureBlackDarkTheme,
                textScale = state.appTextScale.factor,
                uiScale = state.appUiScale.factor,
            )
            SideEffect { onThemeSettingsChanged(themeSettings) }
            SubshellTheme(
            mode = state.themeMode,
            dynamicColor = state.dynamicColor,
            pureBlack = state.pureBlackDarkTheme,
            textScale = state.appTextScale.factor,
            uiScale = state.appUiScale.factor,
            codeFontFamily = state.codeFont.fontFamily,
            reduceMotion = state.motionReduced,
            manageSystemBarContrast =
                state.currentNavigationRoute !is SubshellRoute.Media &&
                    state.currentNavigationRoute !is SubshellRoute.CommentMedia,
        ) {
            ModeratorToolsDialogs(
                state = state,
                redditModeration = redditModeration,
                moderatorTools = moderatorTools,
                moderatorToolsNavigation = moderatorToolsNavigation,
                moderatorToolsWorkspaceCommunity = moderatorToolsWorkspaceCommunity,
            )
            if (showRedditOnboarding) {
                RedditOnboardingScreen(
                    authenticatedAccountUsername =
                        redditOnboardingConnectedAccount?.username,
                    connectAccountState = connectAccountState,
                    onConnectAccount = {
                        redditOnboardingConnectRequested = true
                        onConnectAccount()
                    },
                    onCancelConnectAccount =
                        onCancelConnectAccount,
                    onResetConnectAccount =
                        onResetConnectAccount,
                    onRedditConfigurationChanged =
                        onRedditConfigurationChanged,
                    linkHandlingStepPending =
                        showRedditLinkHandlingPrompt || showFirstRunSetup,
                    onFinish = {
                        redditOnboardingConnectRequested = false
                    },
                )
                return@SubshellTheme
            }

            if (showFirstRunSetup) {
                FirstRunSetupScreen(
                    state = state,
                    backupRepository = backupRepository,
                    linkHandlingState = redditLinkHandlingState,
                    onOpenRedditLinkSettings = onOpenRedditLinkSettings,
                    onDismissRedditLinkPrompt = onDismissRedditLinkPrompt,
                )
                return@SubshellTheme
            }

            if (showRedditLinkHandlingPrompt) {
                RedditLinkHandlingOnboardingScreen(
                    state = redditLinkHandlingState,
                    onOpenSettings = onOpenRedditLinkSettings,
                    onNotNow = onDismissRedditLinkPrompt,
                )
                return@SubshellTheme
            }

        // ISSUE67_STATE_SURVIVES_THE_FULL_SCREEN_ROUTES_V1 /
        // ISSUE68_LAUNCHES_ARE_HANDLED_UNDER_THE_COMPOSER_V1: the full-screen post
        // composer and New message return early below; state and effects that
        // came after those returns left the composition (a custom feed, the inbox
        // tab, every saved screen state were reset; shares and shortcuts waited).
        // They live here, above the returns, so every route keeps them.
        val inContext = state.currentNavigationRoute !is SubshellRoute.Main
        val inboxContextualSelectionState = remember {
            mutableStateOf<InboxContextualSelection?>(null)
        }
        var inboxContextualSelection by inboxContextualSelectionState
        // NEXT01A3A_ACCOUNT_SCOPED_SAVEABLE_PRIMARY_TAB_OWNER_V1
        val inboxPrimaryTabState = rememberSaveable(state.activeAccountId) {
            mutableStateOf(InboxPrimaryTab.ACTIVITY)
        }
        var inboxPrimaryTab by inboxPrimaryTabState
        // NEXT01A3C_ACCOUNT_SCOPED_SAVEABLE_MOD_SECTION_OWNER_V1
        val inboxModSectionState = rememberSaveable(state.activeAccountId) {
            mutableStateOf(InboxModSection.QUEUE)
        }
        var inboxModSection by inboxModSectionState
        val activeInboxContextualSelection = inboxContextualSelection.takeIf {
            !inContext && state.destination == MainDestination.INBOX
        }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()
        val mutedCommunitiesSheetVisibleState = rememberSaveable(state.activeAccountId) {
            mutableStateOf(false)
        }
        var mutedCommunitiesSheetVisible by mutedCommunitiesSheetVisibleState
        val communityMuteEditorNameState = rememberSaveable(state.activeAccountId) {
            mutableStateOf<String?>(null)
        }
        var communityMuteEditorName by communityMuteEditorNameState
        LaunchedEffect(state.requestedCommunityMuteEditorName) {
            state.requestedCommunityMuteEditorName?.let { requestedCommunityName ->
                communityMuteEditorName = requestedCommunityName
                state.clearRequestedCommunityMuteEditor()
            }
        }
        val screenStateHolder = rememberPostVisitPruningStateHolder(state.livePostVisitIds)
        val viewportErrorHostState = rememberViewportErrorHostState()
        // ISSUE268_SUGGESTIONS_HIDE_OR_LEAVE_V1: the signed-in account's
        // subscriptions and Leave, for "Your reading"'s suggestions.
        val readingStatsCommunities = app.subshell.prototype.ui.screens.readingStatsCommunities(
            accountStableId = state.activeAccountId,
            authenticated = state.activeAccountIsAuthenticated,
            subscriptions = redditSubscriptionsState,
            membership = redditCommunity.communityMembershipState,
            onChangeMembership = redditCommunity.onChangeCommunityMembership,
        )
        // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: one pick action for every placement.
        val randomNsfwCommunityAction = app.subshell.prototype.ui.app.rememberRandomNsfwCommunityAction(
            state = state,
            pick = redditCommunity.randomNsfwCommunity,
            noticeHost = viewportErrorHostState,
        )
        val density = LocalDensity.current
        // ISSUE247_LAYOUT_FOLLOWS_WINDOW_SIZE_CLASSES_V1: Google's window size class
        // of this window decides the rail and the two panes.
        val windowSizeClass = currentUnscaledWindowSizeClass()
        val hapticFeedback = LocalHapticFeedback.current
        val layoutDirection = LocalLayoutDirection.current
        val navInteractionSources = remember {
            MainDestination.entries.associateWith { MutableInteractionSource() }
        }
        val youInteractionSource = navInteractionSources.getValue(MainDestination.YOU)
        val suppressYouClickState = remember { mutableStateOf(false) }
        var suppressYouClick by suppressYouClickState
        val accountSwitcherVisibleState = remember { mutableStateOf(false) }
        var accountSwitcherVisible by accountSwitcherVisibleState
        val requestedSettingsDestinationNameState = rememberSaveable {
            mutableStateOf(SettingsDestination.ROOT.name)
        }
        var requestedSettingsDestinationName by requestedSettingsDestinationNameState
        AutomaticBackupHistoryEffect(state = state, backupRepository = backupRepository)
        CommunityPostCheckEffect(state = state)
        LauncherShortcutsEffect(state = state, customFeedsState = customFeedsState)
        LoadCachedCommunityListIdentities(state = state)
        val activeCustomFeedPathState = rememberSaveable(state.activeAccountId) {
            mutableStateOf<String?>(null)
        }
        var activeCustomFeedPath by activeCustomFeedPathState
        // REDDITURL04_MULTIREDDIT_LINK_V1: a feed opened from a link that is
        // not in the account's list.
        val linkedCustomFeedState = remember(state.activeAccountId) {
            mutableStateOf<CustomFeedSummary?>(null)
        }
        CustomFeedLinkEffect(
            state = state,
            customFeedsState = customFeedsState,
            activeCustomFeedPathState = activeCustomFeedPathState,
            linkedCustomFeedState = linkedCustomFeedState,
            onResolveCustomFeed = customFeeds.onResolveCustomFeed,
        )
        val activeCustomFeed = customFeedsState
            .takeIf { feeds ->
                feeds.accountStableId.isBlank() ||
                    feeds.accountStableId == state.activeAccountId
            }
            ?.feeds
            ?.firstOrNull { feed -> feed.path == activeCustomFeedPath }
            ?: linkedCustomFeedState.value?.takeIf { feed -> feed.path == activeCustomFeedPath }
        LauncherShortcutTargetEffect(
            state = state,
            feedTarget = customFeeds.launcherShortcutTarget,
            onFeedTargetConsumed = customFeeds.onLauncherShortcutConsumed,
            inboxTarget = redditInbox.launcherShortcutTarget,
            onInboxTargetConsumed = redditInbox.onLauncherShortcutConsumed,
            customFeedsState = customFeedsState,
            onLoadCustomFeeds = onLoadCustomFeeds,
            activeCustomFeedPathState = activeCustomFeedPathState,
            pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
            noticeHost = viewportErrorHostState,
        )
        // ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1
        NsfwAccountPreferenceNotice(
            state = state,
            readPreferences = accountConnect.readNsfwAccountPreferences,
            noticeHost = viewportErrorHostState,
        )
        // SETTINGSAUDIT01_REVIEW_SETTINGS_NUDGE_V1
        SettingsReviewNudge(
            state = state,
            noticeHost = viewportErrorHostState,
        )
        // CRASH01_NEXT_START_OFFERS_A_REPORT_V1
        CrashReportPrompt(
            noticeHost = viewportErrorHostState,
            onOpenDiagnostics = {
                requestedSettingsDestinationName = SettingsDestination.DIAGNOSTICS.name
                state.openSecondary(SecondaryDestination.SETTINGS)
            },
        )
        ShareIntoSubshellEffect(
            state = state,
            launch = shareIntoSubshellLaunch,
            onConsumed = onShareIntoSubshellConsumed,
            noticeHost = viewportErrorHostState,
            postWriteInProgress = postComposerWriteRuntime.textPostWriteState.inProgress,
        )
        RedditLinkUnavailableNoticeEffect(
            unavailable = redditLinkUnavailableState.value,
            onShown = { redditLinkUnavailableState.value = null },
            noticeHost = viewportErrorHostState,
        )
        RedditFavoriteSyncEffects(
            state = state,
            subscriptions = redditSubscriptionsState,
            customFeeds = customFeedsState,
            community = redditCommunity,
            noticeHost = viewportErrorHostState,
        )

        AccountScopedEffects(
            state = state,
            moderatorTools = moderatorTools,
            redditFeed = redditFeed,
            activeCustomFeedPath = activeCustomFeedPath,
            pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
            coldStartAccountId = coldStartAccountId,
        )

        AppEffectsBridge(
            state = state,
            redditInbox = redditInbox,
            redditModmail = redditModmail,
            redditFeed = redditFeed,
            inContext = inContext,
            drawerState = drawerState,
            youInteractionSource = youInteractionSource,
            hapticFeedback = hapticFeedback,
            accountSwitcherVisibleState = accountSwitcherVisibleState,
            suppressYouClickState = suppressYouClickState,
        )

            // NEXT01A4C_SHARED_POST_COMPOSER_HOST_V1
            val postComposerContent:
                @Composable () -> Unit = {
                    // ISSUE33_SHARE_INTO_SUBSHELL_V1: a new draft (a share, a
                    // reopened draft) starts the editor fresh.
                    key(state.postComposerDraft.id) {
                    CreatePostScreen(
                        state = state,
                        textPostWriteState = postComposerWriteRuntime.textPostWriteState,
                        crosspostReviewState = postComposerWriteRuntime.crosspostReviewState,
                        onReviewCrosspost = postComposerWriteRuntime.onReviewCrosspost,
                        onPublishReviewedCrosspost =
                            postComposerWriteRuntime.onPublishReviewedCrosspost,
                        onInvalidateCrosspostReview =
                            postComposerWriteRuntime.onInvalidateCrosspostReview,
                        composerPolicy = composerPolicy,
                        // ISSUE229_POST_BODY_MENTIONS_V1
                        mentionSearchState = redditCommunity.redditMentionSearchState,
                        onSearchMentions = { query ->
                            redditCommunity.onSearchMentions(state.activeAccountId, query)
                        },
                        onLoadComposerPolicy = onLoadComposerPolicy,
                        composerCommunitySearch = composerCommunitySearch,
                        onSearchComposerCommunities = onSearchComposerCommunities,
                        onSubmitTextPost = onSubmitTextPost,
                        onAuthorizeTextPostWrite = onAuthorizeTextPostWrite,
                        onAuthorizeComposerPostRequirements =
                            onAuthorizeComposerPostRequirements,
                        onAuthorizeComposerFlair = onAuthorizeComposerFlair,
                        onCancelTextPostWriteAuthorization =
                            onCancelTextPostWriteAuthorization,
                        onResetTextPostWriteState = onResetTextPostWriteState,
                        onReconcileTextPostSubmit = onReconcileTextPostSubmit,
                        subscribedCommunities = redditSubscriptionsState
                            .takeIf { it.accountStableId == state.activeAccountId }
                            ?.communities
                            .orEmpty(),
                    )
                    }
                }

            val earlyPostComposerRoute =
                state.currentNavigationRoute is SubshellRoute.PostComposer
            val earlyYouDraftsWorkspace =
                earlyPostComposerRoute &&
                    useExpandedYouDraftsWorkspaceListDetail(
                        expandedWindow =
                            useExpandedMainLayout(windowSizeClass),
                        supportedOrigin =
                            state.currentPostComposerSupportsExpandedYouDraftsListDetail(),
                        selectedTab =
                            state.userProfileTab(
                                state.activeAccountUsername,
                            ),
                        tabletLayoutMode =
                            state.tabletLayoutMode,
                    )

            if (earlyPostComposerRoute && !earlyYouDraftsWorkspace) {
                postComposerContent()
                return@SubshellTheme
            }

            val newMessageRoute =
                state.currentNavigationRoute as? SubshellRoute.Secondary
            if (
                newMessageRoute?.secondaryDestination ==
                    SecondaryDestination.NEW_MESSAGE
            ) {
                NewMessageRouteHost(
                    state = state,
                    redditCommunity = redditCommunity,
                    redditInbox = redditInbox,
                    directChatStartState = directChatStartState,
                    onStartRedditDirectChat = onStartRedditDirectChat,
                    onResetRedditDirectChatStartState = onResetRedditDirectChatStartState,
                )
                return@SubshellTheme
            }

        val navigationBarInsetHeight =
            with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val compactNavigationBarHeight =
            navigationBarBaseHeightForFontScale(visibleFontScale()) +
                navigationBarInsetHeight
        val expandedWindowLayout =
            useExpandedMainLayout(windowSizeClass)
        // NEXT01A3D_SPLIT_AWARE_MODMAIL_THREAD_SHELL_V1
        val modmailWorkspaceListDetail =
            useExpandedInboxModmailWorkspaceListDetail(
                expandedWindow = expandedWindowLayout,
                selectedTab = inboxPrimaryTab,
                selectedModSection = inboxModSection,
                tabletLayoutMode = state.tabletLayoutMode,
            )
        val modmailThreadOpen =
            !inContext &&
                state.destination == MainDestination.INBOX &&
                redditModmailState.selectedConversationId != null &&
                !modmailWorkspaceListDetail
        val expandedMainLayout =
            !inContext && !modmailThreadOpen && expandedWindowLayout
        // Full-screen media keeps the whole screen.
        val navigationRailShown =
            useNavigationRail(windowSizeClass) &&
                state.currentNavigationRoute !is SubshellRoute.Media &&
                state.currentNavigationRoute !is SubshellRoute.CommentMedia
        // ISSUE274_LIST_ROUTES_KEEP_THE_NAVIGATION_BAR_V1: the phone's bar in a
        // community, a profile or search; it hides and returns with the route's
        // own scrolling, like on Home.
        val routeNavigationBarShown =
            !navigationRailShown && routeKeepsNavigationBar(state.currentNavigationRoute)
        val fabTravelPx = with(density) { 120.dp.toPx() }
        val chromeScrollConnection = rememberChromeScrollConnection(
            state = state,
            density = density,
            chromeShown = !modmailThreadOpen,
        )
        val subsCommunityActions = !inContext &&
            state.destination == MainDestination.SUBS &&
            state.activeAccountIsAuthenticated
        val (topBarHiddenFractionState, bottomBarHiddenFractionState) = rememberChromeHiddenFractions(
            state = state,
            activeInboxContextualSelection = activeInboxContextualSelection,
        )
        val topBarHiddenFraction by topBarHiddenFractionState
        val bottomBarHiddenFraction by bottomBarHiddenFractionState
        // LONG01B_SCROLL_CAPTURE_BOTTOM_BOUND_V1
        // SUBS01B6_STABLE_MAIN_VIEWPORT_V1
        val stableMainContentBottomPadding =
            96.dp +
                // No bottom bar beside the rail.
                if (navigationRailShown) {
                    navigationBarInsetHeight
                } else {
                    compactNavigationBarHeight
                }
        val customFeedFabVisibleState = remember { mutableStateOf(false) }
        var customFeedFabVisible by customFeedFabVisibleState
        val customFeedCreateRequestedState = remember { mutableStateOf(false) }
        var customFeedCreateRequested by customFeedCreateRequestedState
        val (mainYouProfileContent, publicUserProfileContent) = userProfileContents(
            state = state,
            redditFeedState = redditFeedState,
            onRefreshRedditUserSubmitted = onRefreshRedditUserSubmitted,
            onAppendRedditUserSubmitted = onAppendRedditUserSubmitted,
            redditAccountHistoryState = redditAccountHistoryState,
            onRefreshRedditAccountHistory = onRefreshRedditAccountHistory,
            onAppendRedditAccountHistory = onAppendRedditAccountHistory,
            redditSavedHistoryState = redditSavedHistoryState,
            onRefreshRedditSavedHistory = onRefreshRedditSavedHistory,
            onAppendRedditSavedHistory = onAppendRedditSavedHistory,
            onResolveRedditProfileCommentTarget = onResolveRedditProfileCommentTarget,
            onResolveRedditSavedCommentTarget = onResolveRedditSavedCommentTarget,
            onAuthorizeRedditProfileHistory = onAuthorizeRedditProfileHistory,
            contentSafetyState = contentSafetyState,
            onRefreshBlockedAccounts = onRefreshBlockedAccounts,
            onAppendBlockedAccounts = onAppendBlockedAccounts,
            onAuthorizeBlockedAccounts = onAuthorizeBlockedAccounts,
        )

        val createPostButtonVisible = when (val route = state.currentNavigationRoute) {
            is SubshellRoute.Main -> route.destination == MainDestination.HOME
            is SubshellRoute.Community -> true
            else -> false
        }

        // MODMAIL06R1_UNREAD_BADGE_HIERARCHY_V1
        val redditInboxUnreadCount = if (
            state.activeAccountIsAuthenticated &&
            redditInboxState.matches(state.activeAccountId)
        ) {
            redditInboxState.unreadCount
        } else {
            state.unreadInboxCount
        }
        val redditModmailUnreadCount = if (
            state.activeAccountIsAuthenticated &&
            redditModmailState.matches(state.activeAccountId)
        ) {
            redditModmailState.unreadCount
        } else {
            0
        }
        val inboxUnreadCount = (
            redditInboxUnreadCount.toLong() +
                redditModmailUnreadCount.toLong()
            )
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val messageButtonVisible = !inContext &&
            state.destination == MainDestination.INBOX &&
            state.activeAccountIsAuthenticated &&
            inboxPrimaryTab != InboxPrimaryTab.MOD
        val createButtonVisible = createPostButtonVisible || messageButtonVisible

        val predictiveRouteBackState = rememberPredictiveRouteBackState()
        val backExitNoticeState = rememberViewportErrorHostState()
        RootBackHandlers(
            state = state,
            predictiveRouteBackState = predictiveRouteBackState,
            backExitNoticeState = backExitNoticeState,
            activeCustomFeed = activeCustomFeed,
            activeCustomFeedPathState = activeCustomFeedPathState,
            pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
            redditSubscriptionsState = redditSubscriptionsState,
        )


        val authenticatedLandingPending =
            state.activeAccountIsAuthenticated &&
                pendingAuthenticatedLandingAccountId ==
                state.activeAccountId &&
                authenticatedMainFeedLandingRouteEligible(
                    currentRoute = state.currentNavigationRoute,
                    activeCustomFeedPath = activeCustomFeedPath,
                )

        val authenticatedHomeWithoutSubscriptions =
            state.activeAccountIsAuthenticated &&
                authoritativeZeroSubscriptionHome(
                    accountStableId = state.activeAccountId,
                    source = state.feedSource,
                    subscriptions = redditSubscriptionsState,
                )

        val drawerLayoutDirection = when (state.resolvedDrawerSide) {
            InteractionSide.LEFT -> LayoutDirection.Ltr
            InteractionSide.RIGHT -> LayoutDirection.Rtl
        }
        // ISSUE16_DRAWER_GESTURE_ARBITRATION_V1
        // Configurable content swipes own horizontal drag while enabled.
        // The drawer remains available through its explicit control.
        // ISSUE23_DRAWER_DISMISS_OWNERSHIP_V1: only drag-to-open is
        // given up; an open drawer keeps scrim tap and drag-to-close.
        val drawerGesturesEnabled = drawerGesturesEnabled(
            inContext = inContext,
            swipeActionsEnabled = state.swipeActionsEnabled,
            drawerOpen = drawerState.isOpen,
        )
        LaunchedEffect(drawerState.currentValue, drawerState.targetValue, drawerGesturesEnabled) {
            uiDebugLog(
                "subshell-issue23",
                "drawer current=${drawerState.currentValue} target=${drawerState.targetValue} " +
                    "swipe=${state.swipeActionsEnabled} inContext=$inContext " +
                    "gesturesEnabled=$drawerGesturesEnabled",
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides drawerLayoutDirection) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerGesturesEnabled,
                drawerContent = {
                    // ISSUE23_DRAWER_DISMISS_OWNERSHIP_V1: the drawerState overload
                    // owns Back (and predictive back) for the open drawer.
                    ModalDrawerSheet(
                        drawerState = drawerState,
                        modifier = Modifier.widthIn(min = 296.dp, max = 336.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                            SubshellDrawer(
                                state = state,
                                redditFeed = redditFeed,
                                customFeeds = customFeeds,
                                activeCustomFeedPath = activeCustomFeedPath,
                                onSelectFeedSource = { source ->
                                    pendingAuthenticatedLandingAccountId = null
                                    activeCustomFeedPath = null
                                    state.selectFeedSource(source)
                                },
                                onOpenCustomFeed = { feedPath ->
                                    pendingAuthenticatedLandingAccountId = null
                                    activeCustomFeedPath = feedPath
                                    state.navigate(MainDestination.HOME)
                                },
                                onOpenMutedCommunities = {
                                    mutedCommunitiesSheetVisible = true
                                },
                                onEditCommunityMute = { communityName ->
                                    communityMuteEditorName = communityName
                                },
                                // ISSUE211_RANDOM_NSFW_COMMUNITY_V1
                                onRandomNsfwCommunity = randomNsfwCommunityAction?.let { action -> action::start },
                            ) { action ->
                                action()
                                drawerScope.launch { drawerState.close() }
                            }
                        }
                    }
                },
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection,
                    LocalViewportErrorHostState provides viewportErrorHostState,
                    app.subshell.prototype.ui.app.LocalRandomNsfwCommunityAction provides randomNsfwCommunityAction,
                    app.subshell.prototype.ui.screens.LocalReadingStatsCommunities provides readingStatsCommunities,
                ) {
                    RuntimeErrorReports(
                        postVoteState = postVoteState,
                        onRetryPostVote = onRetryPostVote,
                        postSaveState = postSaveState,
                        onRetryPostSave = onRetryPostSave,
                        postFollowRuntime = postFollowRuntime,
                        contentSafetyState = contentSafetyState,
                        onRetryContentSafety = onRetryContentSafety,
                    )
                    // M3224_NAVIGATION_RAIL_FROM_MEDIUM_WIDTH_V1 (#224, owner): from 600 dp
                    // the rail sits beside the whole app, on every route and beside
                    // splits, so Communities, Inbox and You are always one tap away.
                    CompositionLocalProvider(LocalNavigationRailShown provides navigationRailShown) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                    if (navigationRailShown) {
                        ExpandedMainNavigationRail(
                            // The wide rail sizes itself (collapsed width).
                            modifier = Modifier.fillMaxHeight(),
                            state = state,
                            inboxUnreadCount = inboxUnreadCount,
                            navInteractionSources = navInteractionSources,
                            suppressYouClick = suppressYouClick,
                            onSuppressYouClickChange = { suppressYouClick = it },
                            onNavigate = { destination ->
                                if (destination == MainDestination.HOME) {
                                    activeCustomFeedPath = null
                                }
                                state.navigate(destination)
                            },
                            onOpenAccountSwitcher = {
                                accountSwitcherVisible = true
                            },
                        )
                    }
                    Scaffold(
                modifier = Modifier.weight(1f).predictiveRouteBack(
                    state = predictiveRouteBackState,
                    backdrop = MaterialTheme.colorScheme.surfaceDim,
                    reduceMotion = state.motionReduced,
                ),
                containerColor = MaterialTheme.colorScheme.background,
                // M3223_CUTOUT_IS_SAFE_V1: content stays clear of a side display cutout.
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                ).let { insets ->
                    // The rail takes the start inset itself.
                    if (navigationRailShown) insets.only(WindowInsetsSides.End + WindowInsetsSides.Vertical) else insets
                },
                snackbarHost = {
                    Column {
                        ViewportErrorTrayHost(state = backExitNoticeState)
                        // M3223_TRAY_SITS_ON_THE_FAB_V1 (#223): Scaffold already
                        // places the snackbar slot above the FAB.
                        ViewportErrorTrayHost(state = viewportErrorHostState)
                    }
                },
                floatingActionButtonPosition = if (state.interactionSide.floatingActionAtStart()) {
                    FabPosition.Start
                } else {
                    FabPosition.End
                },
                topBar = {
                    AppTopBar(
                        state = state,
                        inContext = inContext,
                        modmailThreadOpen = modmailThreadOpen,
                        topBarHiddenFractionState = topBarHiddenFractionState,
                        activeInboxContextualSelection = activeInboxContextualSelection,
                        activeCustomFeed = activeCustomFeed,
                        subsCommunityActions = subsCommunityActions,
                        pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
                    )
                },
                bottomBar = {
                    AppBottomBar(
                        state = state,
                        inContext = inContext,
                        routeKeepsNavigationBar = routeNavigationBarShown,
                        expandedMainLayout = navigationRailShown, // the rail replaces the bar
                        modmailThreadOpen = modmailThreadOpen,
                        compactNavigationBarHeight = compactNavigationBarHeight,
                        bottomBarHiddenFractionState = bottomBarHiddenFractionState,
                        density = density,
                        inboxUnreadCount = inboxUnreadCount,
                        navInteractionSources = navInteractionSources,
                        accountSwitcherVisibleState = accountSwitcherVisibleState,
                        suppressYouClickState = suppressYouClickState,
                        activeCustomFeedPathState = activeCustomFeedPathState,
                    )
                },
                floatingActionButton = {
                    AppFloatingActionButton(
                        state = state,
                        customFeedFabVisible = customFeedFabVisible,
                        createButtonVisible = createButtonVisible,
                        createPostButtonVisible = createPostButtonVisible,
                        fabTravelPx = fabTravelPx,
                        bottomBarHiddenFractionState = bottomBarHiddenFractionState,
                        customFeedCreateRequestedState = customFeedCreateRequestedState,
                    )
                },
            ) { scaffoldPadding ->
                // M3223_ONE_ROUTE_TOP_BAR_V1: on a phone a route's own top bar draws
                // behind the status bar, so the route gets no top padding.
                val routeDrawsStatusBar = !expandedWindowLayout &&
                    state.currentNavigationRoute !is SubshellRoute.Main
                // ISSUE274_LIST_ROUTES_KEEP_THE_NAVIGATION_BAR_V1: the route ends where
                // the bar is now, so a hidden bar leaves no empty strip.
                val routeBottomPadding = if (routeNavigationBarShown) {
                    (
                        scaffoldPadding.calculateBottomPadding() -
                            navigationBarBaseHeightForFontScale(visibleFontScale()) * bottomBarHiddenFraction
                        ).coerceAtLeast(0.dp)
                } else {
                    scaffoldPadding.calculateBottomPadding()
                }
                val padding = if (routeDrawsStatusBar || routeNavigationBarShown) {
                    PaddingValues(
                        start = scaffoldPadding.calculateStartPadding(layoutDirection),
                        top = if (routeDrawsStatusBar) 0.dp else scaffoldPadding.calculateTopPadding(),
                        end = scaffoldPadding.calculateEndPadding(layoutDirection),
                        bottom = routeBottomPadding,
                    )
                } else {
                    scaffoldPadding
                }
                CompositionLocalProvider(
                    LocalRouteTopBarDrawsStatusBar provides routeDrawsStatusBar,
                    LocalRouteTopBarBehavior provides state.topBarBehavior,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (routeNavigationBarShown) {
                                    Modifier.nestedScroll(chromeScrollConnection)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                    when (val route = state.currentNavigationRoute) {
                        is SubshellRoute.CommentMedia -> {
                            CommentMediaViewerScreen(
                                modifier = Modifier,
                                uri = route.uri,
                                onBack = { state.closeCommentMedia() },
                                reduceMotion = state.motionReduced,
                            )
                        }
                        is SubshellRoute.Media -> {
                            val post = state.selectedMediaPost
                            if (post == null) {
                                LaunchedEffect(route.postId) { state.closeMedia() }
                            } else {
                                MediaViewerScreen(
                                    modifier = Modifier,
                                    post = post,
                                    state = state,
                                    onBack = state::closeMedia,
                                )
                            }
                        }
                        is SubshellRoute.Post -> PostRouteHost(
                            redditFeed = redditFeed,
                            redditCommunity = redditCommunity,
                            redditComments = redditComments,
                            redditModeration = redditModeration,
                            redditModmail = redditModmail,
                            commentMutations = commentMutations,
                            postMutations = postMutations,
                            redditInbox = redditInbox,
                            onResolveRedditNavigationTarget = onResolveRedditNavigationTarget,
                            state = state,
                            route = route,
                            padding = padding,
                            expandedWindowLayout = expandedWindowLayout,
                            screenStateHolder = screenStateHolder,
                            authenticatedLandingPending = authenticatedLandingPending,
                            authenticatedHomeWithoutSubscriptions = authenticatedHomeWithoutSubscriptions,
                            mainYouProfileContent = mainYouProfileContent,
                            publicUserProfileContent = publicUserProfileContent,
                            pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
                            activeCustomFeedPathState = activeCustomFeedPathState,
                            inboxPrimaryTabState = inboxPrimaryTabState,
                            inboxModSectionState = inboxModSectionState,
                        )
                        is SubshellRoute.User -> UserRouteHost(
                            state = state,
                            route = route,
                            padding = padding,
                            expandedWindowLayout = expandedWindowLayout,
                            screenStateHolder = screenStateHolder,
                            mainYouProfileContent = mainYouProfileContent,
                            publicUserProfileContent = publicUserProfileContent,
                        )
                        is SubshellRoute.Conversation -> ConversationRouteHost(
                            redditModeration = redditModeration,
                            commentMutations = commentMutations,
                            redditInbox = redditInbox,
                            redditMessaging = redditMessaging,
                            redditModmail = redditModmail,
                            state = state,
                            route = route,
                            padding = padding,
                            expandedWindowLayout = expandedWindowLayout,
                            inboxPrimaryTabState = inboxPrimaryTabState,
                            inboxModSectionState = inboxModSectionState,
                        )
                        is SubshellRoute.Secondary -> SecondaryRouteHost(
                            accountConnect = accountConnect,
                            redditAccountHistoryState = redditAccountHistoryState,
                            onRefreshRedditAccountHistory = onRefreshRedditAccountHistory,
                            onAppendRedditAccountHistory = onAppendRedditAccountHistory,
                            redditSavedHistoryState = redditSavedHistoryState,
                            onRefreshRedditSavedHistory = onRefreshRedditSavedHistory,
                            onAppendRedditSavedHistory = onAppendRedditSavedHistory,
                            onResolveRedditSavedCommentTarget = onResolveRedditSavedCommentTarget,
                            onAuthorizeRedditProfileHistory = onAuthorizeRedditProfileHistory,
                            redditFeed = redditFeed,
                            redditCommunity = redditCommunity,
                            onRedditConfigurationChanged = onRedditConfigurationChanged,
                            redditLinkHandlingState = redditLinkHandlingState,
                            onOpenRedditLinkSettings = onOpenRedditLinkSettings,
                            state = state,
                            backupRepository = backupRepository,
                            route = route,
                            padding = padding,
                            expandedWindowLayout = expandedWindowLayout,
                            screenStateHolder = screenStateHolder,
                            pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
                            activeCustomFeedPathState = activeCustomFeedPathState,
                            requestedSettingsDestinationNameState = requestedSettingsDestinationNameState,
                        )
                        is SubshellRoute.Search -> SearchRouteHost(
                            state = state,
                            padding = padding,
                            expandedWindowLayout = expandedWindowLayout,
                            screenStateHolder = screenStateHolder,
                            redditFeed = redditFeed,
                            redditCommunity = redditCommunity,
                            onResolveRedditNavigationTarget = onResolveRedditNavigationTarget,
                        )
                        is SubshellRoute.Community -> CommunityRouteHost(
                            customFeeds = customFeeds,
                            redditFeed = redditFeed,
                            redditCommunity = redditCommunity,
                            redditModeration = redditModeration,
                            state = state,
                            route = route,
                            padding = padding,
                            expandedWindowLayout = expandedWindowLayout,
                            screenStateHolder = screenStateHolder,
                            pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
                            activeCustomFeedPathState = activeCustomFeedPathState,
                            customFeedCreateRequestedState = customFeedCreateRequestedState,
                            customFeedFabVisibleState = customFeedFabVisibleState,
                        )
                        is SubshellRoute.Main -> MainRouteHost(
                            customFeeds = customFeeds,
                            redditFeed = redditFeed,
                            redditCommunity = redditCommunity,
                            redditModeration = redditModeration,
                            redditModmail = redditModmail,
                            redditInbox = redditInbox,
                            state = state,
                            route = route,
                            padding = padding,
                            screenStateHolder = screenStateHolder,
                            density = density,
                            layoutDirection = layoutDirection,
                            navInteractionSources = navInteractionSources,
                            navigationBarInsetHeight = navigationBarInsetHeight,
                            expandedWindowLayout = expandedWindowLayout,
                            modmailWorkspaceListDetail = modmailWorkspaceListDetail,
                            expandedMainLayout = expandedMainLayout,
                            chromeScrollConnection = chromeScrollConnection,
                            topBarHiddenFraction = topBarHiddenFraction,
                            bottomBarHiddenFraction = bottomBarHiddenFraction,
                            stableMainContentBottomPadding = stableMainContentBottomPadding,
                            mainYouProfileContent = mainYouProfileContent,
                            inboxUnreadCount = inboxUnreadCount,
                            activeCustomFeed = activeCustomFeed,
                            authenticatedLandingPending = authenticatedLandingPending,
                            authenticatedHomeWithoutSubscriptions = authenticatedHomeWithoutSubscriptions,
                            pendingAuthenticatedLandingAccountIdState = pendingAuthenticatedLandingAccountIdState,
                            inboxContextualSelectionState = inboxContextualSelectionState,
                            inboxPrimaryTabState = inboxPrimaryTabState,
                            inboxModSectionState = inboxModSectionState,
                            suppressYouClickState = suppressYouClickState,
                            accountSwitcherVisibleState = accountSwitcherVisibleState,
                            customFeedFabVisibleState = customFeedFabVisibleState,
                            customFeedCreateRequestedState = customFeedCreateRequestedState,
                            activeCustomFeedPathState = activeCustomFeedPathState,
                        )
                        SubshellRoute.PostComposer -> {
                            val selectedDraftsTab =
                                state.userProfileTab(
                                    state.activeAccountUsername,
                                )
                            val youDraftsWorkspace =
                                useExpandedYouDraftsWorkspaceListDetail(
                                    expandedWindow =
                                        expandedWindowLayout,
                                    supportedOrigin =
                                        state.currentPostComposerSupportsExpandedYouDraftsListDetail(),
                                    selectedTab =
                                        selectedDraftsTab,
                                    tabletLayoutMode =
                                        state.tabletLayoutMode,
                                )

                            if (youDraftsWorkspace) {
                                // NEXT01A4C_DRAFT_EDITOR_TWO_PANE_V1
                                ExpandedListDetailSurface(
                                    scaffoldPadding = padding,
                                    listTitle = "You",
                                    listSubtitle = "Drafts",
                                    listContent = { listModifier ->
                                        mainYouProfileContent(
                                            state.activeAccountUsername,
                                            listModifier,
                                            PaddingValues(
                                                horizontal = 8.dp,
                                                vertical = 8.dp,
                                            ),
                                            null,
                                            { selectedTab ->
                                                if (
                                                    shouldCloseYouDraftsDetailOnTabSelection(
                                                        selectedTab,
                                                    )
                                                ) {
                                                    state.closePostComposer()
                                                }
                                            },
                                            null,
                                        )
                                    },
                                    detailContent = { detailModifier ->
                                        Box(
                                            modifier =
                                                detailModifier.fillMaxSize(),
                                        ) {
                                            postComposerContent()
                                        }
                                    },
                                )
                            } else {
                                postComposerContent()
                            }
                        }
                        }
                    }
                                    }
                }
                    }
                    }
                    ViewportErrorDetailsSheet(viewportErrorHostState)
            }
        }
    }

        AppSheetsHost(
            state = state,
            accountConnect = accountConnect,
            backupRepository = backupRepository,
            accountSwitcherVisibleState = accountSwitcherVisibleState,
            requestedSettingsDestinationNameState = requestedSettingsDestinationNameState,
            mutedCommunitiesSheetVisibleState = mutedCommunitiesSheetVisibleState,
            communityMuteEditorNameState = communityMuteEditorNameState,
        )
        }
        }
    }
}
