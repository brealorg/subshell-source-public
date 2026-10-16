package app.subshell.prototype.auth

import app.subshell.prototype.ui.CrosspostReviewUiState
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.crosspostDraftWriteAvailable

import app.subshell.prototype.data.isValidRedditUsername
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import android.app.Application
import app.subshell.prototype.inbox.forgetInboxNotificationsForAccount
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostDeleteRequest
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
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
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationRoutingKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.ui.ComposerCommunitySearchSnapshot
import app.subshell.prototype.ui.CommentMediaPolicy
import app.subshell.prototype.ui.ComposerPolicySnapshot
import app.subshell.prototype.data.RedditFeedSort
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipRequest
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.RedditFavoriteUiState
import app.subshell.prototype.data.auth.reddit.RedditFavoriteRequest
import app.subshell.prototype.data.account.SharedPreferencesRedditFavoriteCommunityStore
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.auth.reddit.redditChatMatrixReactionKeyOrNull
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.isBrowserEndedWithoutCallbackToken

internal class AuthConnectViewModel internal constructor(
    application: Application,
    // HARNESS02_RUNTIME_SEAM_V1: tests inject a runtime; production passes null.
    runtimeOverride: AuthApplicationRuntime?,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, null)

    // AUTHSTART01_RUNTIME_PREPARED_OFF_MAIN_V1: MainActivity has the runtime
    // built on IO first; building it here is the fallback.
    private val runtime = runtimeOverride
        ?: AuthRuntimePreparation.takePrepared()
        ?: AuthApplicationRuntimeFactory.create(application)

    init {
        AuthRuntimePreparation.liveViewModels.incrementAndGet()
    }
    private val textPostSubmitAttemptStore = TextPostSubmitAttemptStore(
        application.getSharedPreferences("subshell_preferences", 0),
    )
    private val connectFlow = runtime.connectFlow

    // LOW4_CONNECT_PHASE_RESTORED_OFF_MAIN_V1 (OCB-474): the flow starts IDLE
    // and reads a sign-in left waiting from the store off the main thread.
    init {
        viewModelScope.launch { connectFlow.reset() }
    }
    // USERSTATUS01A1_ACCOUNT_VISIBILITY_COORDINATOR_V1
    private val onlineVisibilityCoordinator = OnlineVisibilityCoordinator(
        scope = viewModelScope,
        service = runtime.onlineVisibilityService,
        activeAccountId = { runtime.accountSessionDirectory?.snapshot()?.activeAccountId },
    )
    val onlineVisibilityUiState = onlineVisibilityCoordinator.uiState
    fun openOnlineVisibility(account: String) = onlineVisibilityCoordinator.open(account)
    fun closeOnlineVisibility(account: String) = onlineVisibilityCoordinator.close(account)
    fun refreshOnlineVisibility(account: String) = onlineVisibilityCoordinator.refresh(account)
    fun proposeOnlineVisibility(account: String, desired: Boolean) = onlineVisibilityCoordinator.propose(account, desired)
    fun confirmOnlineVisibility(confirmation: app.subshell.prototype.data.account.OnlineVisibilityConfirmation) =
        onlineVisibilityCoordinator.confirm(confirmation)
    fun cancelOnlineVisibilityConfirmation() = onlineVisibilityCoordinator.cancelConfirmation()

    // #216: the #211 NSFW preference read, random NSFW community and
    // quarantine opt-in live in CommunityAccessCoordinator.
    private val communityAccessCoordinator = CommunityAccessCoordinator(
        accessService = runtime.communityAccessService,
        discoveryLoader = runtime.redditCommunityDiscoveryLoader,
        activeAccountId = { runtime.accountSessionDirectory?.snapshot()?.activeAccountId },
    )
    suspend fun readNsfwAccountPreferences(account: String): app.subshell.prototype.data.account.NsfwPreferencesResult =
        communityAccessCoordinator.readNsfwAccountPreferences(account)
    suspend fun randomNsfwCommunity(account: String): String? =
        communityAccessCoordinator.randomNsfwCommunity(account)
    suspend fun optInToQuarantinedCommunity(
        account: String,
        communityName: String,
    ): app.subshell.prototype.data.account.QuarantineOptInResult =
        communityAccessCoordinator.optInToQuarantinedCommunity(account, communityName)

    init {
        viewModelScope.launch {
            runtime.accountSessionDirectory?.revision?.collect {
                onlineVisibilityCoordinator.accountChanged()
            }
        }
    }
    // SUBSHELL_CUSTOMFEED01_COORDINATOR_V1: custom-feed reads, writes and
    // outcome reconciliation live in CustomFeedCoordinator.
    private val customFeedCoordinator = CustomFeedCoordinator(
        feedReader = runtime.customFeedReader,
        feedCreator = runtime.customFeedCreator,
        writeCapability = runtime.customFeedWriteCapability,
        writeClientMode = runtime.customFeedWriteClientMode,
        scope = viewModelScope,
    )
    // SUBSHELL_COMMENTTHREAD01_COORDINATOR_V1: the comment-thread reads and
    // local edit application live in CommentThreadCoordinator.
    private val commentThreadCoordinator = CommentThreadCoordinator(
        commentLoader = runtime.redditCommentLoader,
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
    // MOD01A1_VIEWMODEL_MODERATION_STATE_V1
    // SUBSHELL_MODERATIONQUEUE01_COORDINATOR_V1: the moderator directory and
    // mod-queue reads live in ModerationQueueCoordinator.
    private val moderationQueueCoordinator = ModerationQueueCoordinator(
        moderationLoader = runtime.redditModerationLoader,
        scope = viewModelScope,
    )
    // SUBSHELL_INBOX01_COORDINATOR_V1: the inbox reads, read mutations and
    // read authorization live in InboxCoordinator.
    private val inboxCoordinator = InboxCoordinator(
        application = application,
        redditInboxLoader = runtime.redditInboxLoader,
        inboxReadMutationWriter = runtime.inboxReadMutationWriter,
        inboxReadCapability = runtime.inboxReadCapability,
        inboxReadClientMode = runtime.inboxReadClientMode,
        accountSessionDirectory = runtime.accountSessionDirectory,
        scope = viewModelScope,
    )
    // SUBSHELL_MESSAGEREPLY01_COORDINATOR_V1: message replies live in
    // MessageReplyCoordinator.
    private val messageReplyCoordinator = MessageReplyCoordinator(
        messageReplyWriter = runtime.messageReplyWriter,
        loadRedditInbox = { accountStableId, forceRefresh ->
            loadRedditInbox(accountStableId, forceRefresh)
        },
        scope = viewModelScope,
    )
    // SUBSHELL_DIRECTCHAT01_COORDINATOR_V1: Direct Chat start and the live
    // session live in DirectChatCoordinator.
    private val directChatCoordinator = DirectChatCoordinator(
        directChatStartWriter = runtime.directChatStartWriter,
        directChatLiveReader = runtime.directChatLiveReader,
        directChatTypingWriter = runtime.directChatTypingWriter,
        directChatRedactionWriter = runtime.directChatRedactionWriter,
        directChatReactionWriter = runtime.directChatReactionWriter,
        directChatReactionPresentationCache = runtime.directChatReactionPresentationCache,
        directChatReactionRelationsProbeReader = runtime.directChatReactionRelationsProbeReader,
        onChatStarted = { accountStableId -> loadRedditInbox(accountStableId, true) },
        scope = viewModelScope,
    )
    private val redditAccountHistoryCoordinator = RedditAccountHistoryRequestCoordinator(
        scope = viewModelScope,
        updater = runtime.redditAccountHistoryUpdater,
        commentLoader = runtime.redditAccountHistoryCommentLoader,
    )
    private val redditSavedHistoryCoordinator = RedditSavedHistoryRequestCoordinator(
        scope = viewModelScope,
        loader = runtime.redditSavedHistoryLoader,
    )
    // #215: the inbox/deep-link and history comment target resolvers live
    // in RedditNavigationTargetCoordinator.
    private val redditNavigationTargetCoordinator = RedditNavigationTargetCoordinator(
        scope = viewModelScope,
        inboxLoader = runtime.redditInboxLoader,
        commentLoader = runtime.redditAccountHistoryCommentLoader,
        redditAccountHistoryCoordinator = redditAccountHistoryCoordinator,
        redditSavedHistoryCoordinator = redditSavedHistoryCoordinator,
    )
    private val redditFeedCoordinator = RedditFeedRequestCoordinator(
        scope = viewModelScope,
        updater = runtime.redditFeedUpdater,
        onStart = { operation, target ->
            authDebugLog(
                FEED_LOAD_LOG_TAG,
                "start operation=${operation.name.lowercase()} " +
                    "scope=${target.feedLogScope()} sort=${target.sort.name.lowercase()} " +
                    "time=${target.effectiveTimePeriod.apiValue}",
            )
        },
        onApplied = { result ->
            authDebugLog(
                FEED_LOAD_LOG_TAG,
                "applied operation=${result.operation.name.lowercase()} " +
                    "network=${result.networkItemCount} stored=${result.storedItemCount} " +
                    "end=${result.endReached}",
            )
        },
        onFailed = { operation, result ->
            authDebugLog(
                FEED_LOAD_LOG_TAG,
                "failed operation=${operation.name.lowercase()} " +
                    "kind=${result.kind.name.lowercase()} retryable=${result.retryable} " +
                    "http=${result.details?.httpStatusCode} reason=${result.details?.providerCode?.value}",
            )
        },
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

    private val postVoteCoordinator = PostVoteCoordinator(
        capability = runtime.postVoteCapability,
        writer = runtime.postVoteWriter,
        clientMode = runtime.postVoteClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory
                ?.snapshot()
                ?.activeAccountId
        },
    )

    val postVoteUiState: StateFlow<PostVoteUiState> =
        postVoteCoordinator.uiState

    private val postSaveCoordinator = PostSaveCoordinator(
        capability = runtime.postSaveCapability,
        writer = runtime.postSaveWriter,
        clientMode = runtime.postSaveClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory
                ?.snapshot()
                ?.activeAccountId
        },
    )

    val postSaveUiState: StateFlow<PostSaveUiState> =
        postSaveCoordinator.uiState

    private val postFollowCoordinator = PostFollowCoordinator(
        capability = runtime.postFollowCapability,
        writer = runtime.postFollowWriter,
        clientMode = runtime.postFollowClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory
                ?.snapshot()
                ?.activeAccountId
        },
    )

    val postFollowUiState: StateFlow<PostFollowUiState> =
        postFollowCoordinator.uiState

    private val contentSafetyCoordinator = ContentSafetyCoordinator(
        capability = runtime.contentSafetyCapability,
        writer = runtime.contentSafetyWriter,
        clientMode = runtime.contentSafetyClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory
                ?.snapshot()
                ?.activeAccountId
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
    )

    val contentSafetyUiState: StateFlow<ContentSafetyUiState> =
        contentSafetyCoordinator.uiState

    // SUBSHELL_AUTHCALLBACK03_OWNER_BOUND_CAPABILITIES_ONLY_V1: coordinators take
    // the owner-bound capability only. The predecessor capability is built
    // from the same selection, so it was never the one chosen.
    private val commentWriteCoordinator = CommentWriteCoordinator(
        capability =
            runtime.commentWriteAuthorizationCapability,
        submitter = runtime.commentSubmitter,
        commentLoader = runtime.redditCommentLoader,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.commentWriteClientMode,
        onThreadUpdated = commentThreadCoordinator::applySubmittedCommentThread,
        displayedThread = commentThreadCoordinator::displayedThreadFor,
    )
    private val commentEditCoordinator = CommentEditCoordinator(
        capability =
            runtime.commentEditAuthorizationCapability,
        editor = runtime.commentEditor,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.commentEditClientMode,
        onCommentEdited = commentThreadCoordinator::applyEditedComment,
    )
    private val commentDeleteCoordinator = CommentDeleteCoordinator(
        capability =
            runtime.commentDeleteAuthorizationCapability,
        deleter = runtime.commentDeleter,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.commentEditClientMode,
        onCommentDeleted = commentThreadCoordinator::applyDeletedComment,
    )
    private val postManageCoordinator = PostManageCoordinator(
        manager = runtime.postManager,
        accountDirectory = runtime.accountSessionDirectory,
        editCapability =
            runtime.postManageEditAuthorizationCapability,
        editClientMode = runtime.commentEditClientMode,
        flairCapability =
            runtime.postManageFlairAuthorizationCapability,
        flairClientMode = runtime.composerFlairClientMode,
        moderationCapability =
            runtime.postManageModerationAuthorizationCapability,
        moderationClientMode = runtime.postModerationClientMode,
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
                commentThreadCoordinator.applyModeratedComment(
                    accountStableId = request.accountStableId,
                    commentFullname = request.thingFullname,
                    removed = removed,
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

    private val postEditCoordinator = PostEditCoordinator(
        capability =
            runtime.postEditAuthorizationCapability,
        editor = runtime.postEditor,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.commentEditClientMode,
    )
    private val postDeleteCoordinator = PostDeleteCoordinator(
        capability =
            runtime.postDeleteAuthorizationCapability,
        deleter = runtime.postDeleter,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.commentEditClientMode,
        deletedPostCacheRemover = runtime.deletedPostCacheRemover,
    )
    private val textPostWriteCoordinator = TextPostWriteCoordinator(
        capability =
            runtime.textPostWriteAuthorizationCapability,
        submitter = runtime.textPostSubmitter,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.textPostWriteClientMode,
        attemptStore = textPostSubmitAttemptStore,
    )
    init {
        viewModelScope.launch {
            textPostWriteCoordinator.restorePendingSubmitState()
        }
    }

    // CROSSPOST01A1E_VIEWMODEL_REVIEW_OWNER_V1
    private val crosspostCreateCoordinator = CrosspostCreateCoordinator(
        sourceLoader = runtime.crosspostSourceLoader,
        policyLoader = runtime.composerPolicyLoader,
        activeAccountId = {
            val snapshot = runtime.accountSessionDirectory?.snapshot()
            snapshot?.activeAccountId?.takeIf { id ->
                snapshot.accounts.any {
                    it.stableId == id && it.source == app.subshell.prototype.data.account.AccountSessionSource.AUTHENTICATED
                }
            }
        },
        writeAvailable = { draft ->
            crosspostDraftWriteAvailable(textPostWriteCoordinator.uiState.value, draft)
        },
        submit = { request -> textPostWriteCoordinator.submit(request) },
    )
    val crosspostReviewUiState: StateFlow<CrosspostReviewUiState> = crosspostCreateCoordinator.uiState

    fun reviewCrosspostDraft(draft: PostComposerDraft) {
        viewModelScope.launch { crosspostCreateCoordinator.review(draft) }
    }

    fun publishReviewedCrosspost(draft: PostComposerDraft, revision: Long) {
        viewModelScope.launch { crosspostCreateCoordinator.publish(draft, revision) }
    }

    fun invalidateCrosspostReview() = crosspostCreateCoordinator.invalidateReview()

    private val userFlairCoordinator = UserFlairCoordinator(
        manager = runtime.redditUserFlairManager,
        activeAccountStableId = {
            runtime.accountSessionDirectory?.snapshot()?.activeAccountId
        },
    )
    private val composerFlairAuthorizationCoordinator = TextPostWriteCoordinator(
        capability =
            runtime.composerFlairAuthorizationCapability,
        submitter = null,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.composerFlairClientMode,
    )
    private val composerPostRequirementsAuthorizationCoordinator = TextPostWriteCoordinator(
        capability =
            runtime.composerPostRequirementsAuthorizationCapability,
        submitter = null,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.commentWriteClientMode,
    )
    // SUBSHELL_COMPOSERPOLICY01_COORDINATOR_V1: the composer policy and
    // community search live in ComposerPolicyCoordinator.
    private val composerPolicyCoordinator = ComposerPolicyCoordinator(
        composerPolicyLoader = runtime.composerPolicyLoader,
        composerCommunitySearchLoader = runtime.composerCommunitySearchLoader,
        userFlairCoordinator = userFlairCoordinator,
        accountSessionDirectory = { runtime.accountSessionDirectory },
        scope = viewModelScope,
    )

    val accountSessionDirectory: AccountSessionDirectory?
        get() = runtime.accountSessionDirectory

    fun logoutActiveAccount(onComplete: (Boolean) -> Unit) {
        val coordinator =
            runtime.accountSessionDirectory as? AuthAccountSessionCoordinator
        if (coordinator == null) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            // CONC01_LOGOUT_RETHROWS_CANCELLATION_V1 (OCB-402)
            val logout = try {
                coordinator.logoutActive()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val cleared = logout?.localSessionCleared == true
            // ACCOUNT01_REACTION_CACHE_ACCOUNT_SCOPED_V1: account-scoped chat
            // presentation state goes with the account.
            if (cleared) {
                logout.removedAccountStableId?.let { removed ->
                    runtime.directChatReactionPresentationCache?.removeAccount(removed)
                    // MED4A_LOGOUT_REMOVES_ACCOUNT_CACHE_V1 (OCB-527)
                    runtime.accountCacheRemover?.let { remover ->
                        withContext(Dispatchers.IO) {
                            runCatching { remover.removeAccount(removed) }
                        }
                    }
                    // INBOX08_LOGOUT_FORGETS_ACCOUNT_NOTIFICATIONS_V1 (#147)
                    forgetInboxNotificationsForAccount(getApplication(), removed)
                    // REDDITFAV03_LOGOUT_FORGETS_REDDIT_FAVORITES_V1
                    SharedPreferencesRedditFavoriteCommunityStore(getApplication())
                        .remove(removed)
                }
            }
            if (cleared) {
                // AUTH08A1_LOGOUT_RESETS_CONNECT_FLOW_V1: the connect flow still
                // holds the phase of the account that was just logged out (a
                // completed connect stays CONNECTED). A state that no longer has
                // an account behind it must not keep speaking for one, so the
                // flow is put back to its stored initial phase; a pending
                // authorization survives because reset() restores from the store.
                connectFlow.reset()
            }
            onComplete(cleared)
        }
    }

    val customFeedsUiState: StateFlow<CustomFeedsUiState> =
        customFeedCoordinator.customFeedsUiState

    val customFeedPostsUiState: StateFlow<CustomFeedPostsUiState> =
        customFeedCoordinator.customFeedPostsUiState

    fun loadCustomFeeds(accountStableId: String, forceRefresh: Boolean) =
        customFeedCoordinator.loadCustomFeeds(accountStableId, forceRefresh)

    fun resolveCustomFeed(
        accountStableId: String,
        feedPath: String,
        onResolved: (app.subshell.prototype.data.account.CustomFeedSummary?) -> Unit,
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

    val uiState: StateFlow<ConnectAccountUiState>
        get() = connectFlow.uiState

    val redditFeedUiState: StateFlow<RedditFeedUiState> =
        redditFeedCoordinator.uiState

    val redditAccountHistoryUiState: StateFlow<RedditAccountHistoryUiState> =
        redditAccountHistoryCoordinator.uiState
    val redditSavedHistoryUiState: StateFlow<RedditSavedHistoryUiState> =
        redditSavedHistoryCoordinator.uiState

    val redditCommentsUiState: StateFlow<RedditCommentsUiState> =
        commentThreadCoordinator.redditCommentsUiState

    fun loadRedditComments(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ) =
        commentThreadCoordinator.loadRedditComments(accountStableId, postId, sort)

    fun loadCachedRedditComments(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ) =
        commentThreadCoordinator.loadCachedRedditComments(accountStableId, postId, sort)

    fun refreshRedditCommentsPreservingFocusedTarget(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
    ) =
        commentThreadCoordinator.refreshRedditCommentsPreservingFocusedTarget(accountStableId, postId, sort, focusedCommentId)

    fun loadFocusedRedditComment(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
    ) =
        commentThreadCoordinator.loadFocusedRedditComment(accountStableId, postId, sort, focusedCommentId)

    fun loadMoreRedditComments(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
    ): Boolean =
        commentThreadCoordinator.loadMoreRedditComments(accountStableId, postId, sort, moreNodeId)

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

    val commentWriteUiState: StateFlow<CommentWriteUiState> =
        commentWriteCoordinator.uiState

    val commentEditUiState: StateFlow<CommentEditUiState> =
        commentEditCoordinator.uiState

    val commentDeleteUiState: StateFlow<CommentDeleteUiState> =
        commentDeleteCoordinator.uiState

    val postEditUiState: StateFlow<PostEditUiState> =
        postEditCoordinator.uiState

    val postDeleteUiState: StateFlow<PostDeleteUiState> =
        postDeleteCoordinator.uiState

    val postManageUiState: StateFlow<PostManageUiState> =
        postManageCoordinator.uiState

    val userFlairUiState: StateFlow<UserFlairUiState> =
        userFlairCoordinator.uiState

    val textPostWriteUiState: StateFlow<TextPostWriteUiState> =
        textPostWriteCoordinator.uiState

    val composerPolicy: StateFlow<ComposerPolicySnapshot> =
        composerPolicyCoordinator.composerPolicy

    val composerCommunitySearch: StateFlow<ComposerCommunitySearchSnapshot> =
        composerPolicyCoordinator.composerCommunitySearch

    fun searchComposerCommunities(accountStableId: String, query: String) =
        composerPolicyCoordinator.searchComposerCommunities(accountStableId, query)

    fun loadComposerPolicy(accountStableId: String, community: String) =
        composerPolicyCoordinator.loadComposerPolicy(accountStableId, community)

    // COMPOSER10_COMMENT_MEDIA_RULES_V1
    val commentMediaPolicies: StateFlow<Map<String, CommentMediaPolicy>> =
        composerPolicyCoordinator.commentMediaPolicies

    fun loadCommentMediaPolicy(accountStableId: String, community: String) =
        composerPolicyCoordinator.loadCommentMediaPolicy(accountStableId, community)

    private fun reloadComposerPolicyAfterCapabilityAuthorization() =
        composerPolicyCoordinator.reloadComposerPolicyAfterCapabilityAuthorization()

    // SUBSHELL_PROFILECOMMENTS01_COORDINATOR_V1: profile comments and the
    // profile-history authorization live in ProfileCommentsCoordinator.
    private val profileCommentsCoordinator = ProfileCommentsCoordinator(
        profileHistoryAuthorizer = runtime.profileHistoryAuthorizer,
        profileHistoryClientMode = runtime.profileHistoryClientMode,
        redditAccountHistoryCoordinator = redditAccountHistoryCoordinator,
        redditSavedHistoryCoordinator = redditSavedHistoryCoordinator,
        activeAuthenticatedAccountStableId = ::activeAuthenticatedProfileAccountId,
    )

    suspend fun beginProfileHistoryAuthorization(
        accountStableId: String,
        username: String,
    ): AuthBrowserLaunchResult =
        profileCommentsCoordinator.beginProfileHistoryAuthorization(accountStableId, username)

    fun profileHistoryAuthorizationBrowserLaunchFailed() =
        profileCommentsCoordinator.profileHistoryAuthorizationBrowserLaunchFailed()

    val redditInboxUiState: StateFlow<RedditInboxUiState> =
        inboxCoordinator.redditInboxUiState

    fun loadRedditInbox(
        accountStableId: String,
        forceRefresh: Boolean,
    ) =
        inboxCoordinator.loadRedditInbox(accountStableId, forceRefresh)

    fun appendRedditInbox(accountStableId: String) =
        inboxCoordinator.appendRedditInbox(accountStableId)

    fun markRedditInboxRead(
        accountStableId: String,
        itemId: String,
    ) =
        inboxCoordinator.markRedditInboxRead(accountStableId, itemId)

    fun resolveRedditInboxTarget(
        accountStableId: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) =
        inboxCoordinator.resolveRedditInboxTarget(accountStableId, postId, onResolved)

    suspend fun beginInboxAuthorization(accountStableId: String): AuthBrowserLaunchResult =
        inboxCoordinator.beginInboxAuthorization(accountStableId)

    fun inboxAuthorizationBrowserLaunchFailed() =
        inboxCoordinator.inboxAuthorizationBrowserLaunchFailed()

    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: the user gives up waiting for
    // the browser (no result comes back on the ACTION_VIEW fallback). Same as
    // a "cancelled" callback: drop the pending record and ask again.
    fun cancelInboxAuthorization() {
        runtime.inboxReadCapability?.cancelPendingAuthorization()
        inboxCoordinator.cancelPendingInboxAuthorization()
    }

    val messageReplyUiState: StateFlow<MessageReplyUiState> =
        messageReplyCoordinator.messageReplyUiState

    fun sendRedditMessageReply(request: MessageReplyRequest) =
        messageReplyCoordinator.sendRedditMessageReply(request)

    fun resetRedditMessageReplyState() =
        messageReplyCoordinator.resetRedditMessageReplyState()

    val directChatStartUiState: StateFlow<DirectChatStartUiState> =
        directChatCoordinator.directChatStartUiState

    val directChatLiveUiState: StateFlow<DirectChatLiveUiState> =
        directChatCoordinator.directChatLiveUiState

    fun startRedditDirectChat(request: DirectChatStartRequest) =
        directChatCoordinator.startRedditDirectChat(request)

    fun resetRedditDirectChatStartState() =
        directChatCoordinator.resetRedditDirectChatStartState(
            activeAccountStableId = runtime.accountSessionDirectory?.snapshot()?.activeAccountId,
        )

    fun startRedditDirectChatLive(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
    ) =
        directChatCoordinator.startRedditDirectChatLive(accountStableId, inboxItemId, participantUsername)

    fun updateRedditDirectChatTyping(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
        typing: Boolean,
    ) =
        directChatCoordinator.updateRedditDirectChatTyping(accountStableId, inboxItemId, participantUsername, typing)

    fun stopRedditDirectChatLive(
        accountStableId: String,
        inboxItemId: String,
    ) =
        directChatCoordinator.stopRedditDirectChatLive(accountStableId, inboxItemId)

    fun deleteRedditDirectChatMessage(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
        eventId: String,
    ) =
        directChatCoordinator.deleteRedditDirectChatMessage(accountStableId, inboxItemId, participantUsername, eventId)

    fun resetRedditDirectChatDeleteFailure(
        accountStableId: String,
        inboxItemId: String,
    ) =
        directChatCoordinator.resetRedditDirectChatDeleteFailure(accountStableId, inboxItemId)

    fun reactRedditDirectChatMessage(
        accountStableId: String,
        inboxItemId: String,
        participantUsername: String,
        eventId: String,
        reactionKey: String,
    ) =
        directChatCoordinator.reactRedditDirectChatMessage(accountStableId, inboxItemId, participantUsername, eventId, reactionKey)

    fun resetRedditDirectChatReactionFailure(
        accountStableId: String,
        inboxItemId: String,
    ) =
        directChatCoordinator.resetRedditDirectChatReactionFailure(accountStableId, inboxItemId)

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

    fun votePost(request: PostVoteRequest) {
        viewModelScope.launch {
            postVoteCoordinator.vote(request)
        }
    }

    fun retryPostVote() {
        viewModelScope.launch {
            postVoteCoordinator.retry()
        }
    }

    suspend fun beginPostVoteAuthorization(): AuthBrowserLaunchResult =
        postVoteCoordinator.beginAuthorization()

    fun postVoteAuthorizationBrowserLaunchFailed() {
        postVoteCoordinator.browserLaunchFailed()
    }

    fun savePost(request: PostSaveRequest) {
        viewModelScope.launch {
            postSaveCoordinator.setSaved(request)
        }
    }

    fun retryPostSave() {
        viewModelScope.launch {
            postSaveCoordinator.retry()
        }
    }

    suspend fun beginPostSaveAuthorization(): AuthBrowserLaunchResult =
        postSaveCoordinator.beginAuthorization()

    fun postSaveAuthorizationBrowserLaunchFailed() {
        postSaveCoordinator.browserLaunchFailed()
    }

    fun followPost(request: PostFollowRequest) {
        viewModelScope.launch {
            postFollowCoordinator.setFollowed(request)
        }
    }

    fun retryPostFollow() {
        viewModelScope.launch {
            postFollowCoordinator.retry()
        }
    }

    suspend fun beginPostFollowAuthorization(): AuthBrowserLaunchResult =
        postFollowCoordinator.beginAuthorization()

    fun postFollowAuthorizationBrowserLaunchFailed() {
        postFollowCoordinator.browserLaunchFailed()
    }

    fun setPostHidden(request: RedditPostVisibilityRequest) {
        viewModelScope.launch {
            contentSafetyCoordinator.setPostHidden(request)
        }
    }

    fun reportContent(request: RedditContentReportRequest) {
        viewModelScope.launch {
            contentSafetyCoordinator.report(request)
        }
    }

    // USERACTION01A1_VIEWMODEL_BLOCK_UNBLOCK_V1
    fun setUserBlocked(request: RedditUserBlockRequest) {
        viewModelScope.launch {
            contentSafetyCoordinator.setUserBlocked(request)
        }
    }

    // USERACTION01A2_VIEWMODEL_BLOCKED_ACCOUNTS_V1
    fun refreshBlockedAccounts(accountStableId: String) {
        viewModelScope.launch {
            contentSafetyCoordinator.refreshBlockedAccounts(accountStableId)
        }
    }

    fun appendBlockedAccounts(accountStableId: String) {
        viewModelScope.launch {
            contentSafetyCoordinator.appendBlockedAccounts(accountStableId)
        }
    }

    fun retryContentSafetyMutation() {
        viewModelScope.launch {
            contentSafetyCoordinator.retry()
        }
    }

    suspend fun beginContentSafetyAuthorization(): AuthBrowserLaunchResult =
        contentSafetyCoordinator.beginAuthorization()

    fun contentSafetyAuthorizationBrowserLaunchFailed() {
        contentSafetyCoordinator.browserLaunchFailed()
    }

    // OCB414_BLOCKED_LIST_CAN_ASK_FOR_AUTHORIZATION_V1
    suspend fun beginBlockedAccountsAuthorization(accountStableId: String): AuthBrowserLaunchResult =
        contentSafetyCoordinator.beginBlockedAccountsAuthorization(accountStableId)

    fun blockedAccountsAuthorizationBrowserLaunchFailed() {
        contentSafetyCoordinator.blockedAccountsAuthorizationBrowserLaunchFailed()
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

    suspend fun beginConnect(): AuthBrowserLaunchResult =
        connectFlow.beginAuthorization()

    suspend fun beginReconnect(
        accountStableId: String,
    ): AuthBrowserLaunchResult =
        connectFlow.beginReauthentication(accountStableId)

    fun browserLaunchFailed() {
        viewModelScope.launch { connectFlow.browserLaunchFailed() }
    }

    fun cancelConnect() {
        viewModelScope.launch { connectFlow.cancel() }
    }

    fun resetConnectState() {
        viewModelScope.launch { connectFlow.reset() }
    }

    fun handleCallbackResultToken(resultToken: String) {
        viewModelScope.launch {
            // SUBSHELL_AUTHCALLBACK04_ONE_ROUTING_SNAPSHOT_V1
            // One read of the pending and stored callback records decides
            // the route, so a store read that fails part-way can no longer
            // hand an owned callback to the connect flow: an unreadable
            // store resolves as INVALID.
            // LOW11_CALLBACK_ROUTING_READS_THE_STORE_OFF_MAIN_V1 (OCB-404): the
            // pending/callback records are decrypted on IO.
            val authorizationResolution = withContext(runtime.callbackRoutingDispatcher) {
                runtime.authorizationOwnerResolver?.currentResolution()
            }
            val authorizationRoute = authorizationResolution?.route
            if (authorizationRoute != null) {
                callbackOwnerRoute(authorizationRoute.owner)
                    .handle(resultToken, authorizationRoute.contextKey)
                return@launch
            }

            // SUBSHELL_AUTHCALLBACK02_OWNERLESS_TO_CONNECT_FLOW_V1
            // An owned callback has returned above. An owner-less one belongs
            // to the connect flow (login, reauthentication); an invalid
            // routing reaches nobody.
            if (
                authorizationResolution?.kind ==
                AuthCapabilityAuthorizationRoutingKind.INVALID
            ) {
                return@launch
            }

            // SUBSHELL_AUTHCALLBACK09_CANCEL_REACHES_ONLY_A_WAITING_LOGIN_V1
            // A browser that ended without a callback (closed, or failed:
            // AUTHCALLBACK11), with no authorization on record and no login
            // in progress, has nobody to reach; handing it to the connect
            // flow would reset an idle or connected flow.
            if (
                isBrowserEndedWithoutCallbackToken(resultToken) &&
                authorizationResolution?.kind !=
                AuthCapabilityAuthorizationRoutingKind.LEGACY_OWNERLESS &&
                !connectFlow.uiState.value.inProgress
            ) {
                return@launch
            }

            connectFlow.handleCallbackResultToken(resultToken)
        }
    }

    fun resumeStoredCallbackIfPresent() {
        viewModelScope.launch {
            // SUBSHELL_AUTHCALLBACK04_ONE_ROUTING_SNAPSHOT_V1
            // One read of the pending and stored callback records decides
            // the route, so a store read that fails part-way can no longer
            // hand an owned callback to the connect flow: an unreadable
            // store resolves as INVALID.
            // LOW11_CALLBACK_ROUTING_READS_THE_STORE_OFF_MAIN_V1 (OCB-404): the
            // pending/callback records are decrypted on IO.
            val authorizationResolution = withContext(runtime.callbackRoutingDispatcher) {
                runtime.authorizationOwnerResolver?.currentResolution()
            }
            val authorizationRoute = authorizationResolution?.route
            if (authorizationRoute != null) {
                callbackOwnerRoute(authorizationRoute.owner)
                    .resume(authorizationRoute.contextKey)
                return@launch
            }

            // SUBSHELL_AUTHCALLBACK02_OWNERLESS_TO_CONNECT_FLOW_V1
            // An owned callback has returned above. An owner-less one belongs
            // to the connect flow (login, reauthentication); an invalid
            // routing reaches nobody.
            if (
                authorizationResolution?.kind ==
                AuthCapabilityAuthorizationRoutingKind.INVALID
            ) {
                return@launch
            }

            connectFlow.resumeStoredCallbackIfPresent()
        }
    }

    /**
     * One callback route per authorization owner: [handle] takes the result
     * token of a browser callback, [resume] a callback stored while the app
     * was away. Both reach only the owner's own coordinator and never the
     * connect flow. The owner-less and invalid-routing paths stay in the two
     * entry points above.
     */
    private class CallbackOwnerRoute(
        val handle: suspend (resultToken: String, contextKey: String?) -> Unit,
        val resume: suspend (contextKey: String?) -> Unit,
    )

    // SUBSHELL_AUTHCALLBACK01_OWNER_ROUTE_TABLE_V1
    private fun callbackOwnerRoute(
        authorizationOwner: AuthCapabilityAuthorizationOwner,
    ): CallbackOwnerRoute =
        when (authorizationOwner) {
            AuthCapabilityAuthorizationOwner.COMMENT_EDIT -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    commentEditCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (commentEditCoordinator.resumeStoredAuthorizationIfPresent()) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.COMMENT_DELETE -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    commentDeleteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (commentDeleteCoordinator.resumeStoredAuthorizationIfPresent()) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.COMMENT_WRITE -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    commentWriteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (commentWriteCoordinator.resumeStoredAuthorizationIfPresent()) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.TEXT_POST_WRITE -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    textPostWriteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (textPostWriteCoordinator.resumeStoredAuthorizationIfPresent()) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.POST_EDIT -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    postEditCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (postEditCoordinator.resumeStoredAuthorizationIfPresent()) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.POST_DELETE -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    postDeleteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (postDeleteCoordinator.resumeStoredAuthorizationIfPresent()) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.POST_VOTE -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    postVoteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    postVoteCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.POST_SAVE -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    postSaveCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    postSaveCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.POST_FOLLOW -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    postFollowCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    postFollowCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.CONTENT_SAFETY -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    contentSafetyCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    contentSafetyCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.MODERATION_ACTION -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    moderationActionCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    moderationActionCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.MODERATION_REASON -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    removalReasonCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    removalReasonCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.MODMAIL_READ -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    modmailCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    modmailCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.MODERATION_LOG -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    moderatorLogCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    moderatorLogCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.MODERATION_USERS -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    moderatorUserManagementCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    moderatorUserManagementCoordinator.resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.MODERATION_TEAM -> CallbackOwnerRoute(
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
            )

            AuthCapabilityAuthorizationOwner.POST_MANAGE -> CallbackOwnerRoute(
                handle = { resultToken, authorizationContextKey ->
                    postManageCoordinator.handlePersistedAuthorizationRoute(
                        resultToken = resultToken,
                        contextKey =
                            authorizationContextKey,
                    )
                },
                resume = { authorizationContextKey ->
                    postManageCoordinator
                        .resumePersistedAuthorizationRouteIfPresent(
                            contextKey =
                                authorizationContextKey,
                        )
                },
            )

            AuthCapabilityAuthorizationOwner.COMPOSER_FLAIR -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    composerFlairAuthorizationCoordinator
                        .handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (
                        composerFlairAuthorizationCoordinator
                            .resumeStoredAuthorizationIfPresent()
                    ) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.COMPOSER_REQUIREMENTS -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    composerPostRequirementsAuthorizationCoordinator
                        .handleCallbackResultTokenIfOwned(resultToken)
                    reloadComposerPolicyAfterCapabilityAuthorization()
                },
                resume = {
                    if (
                        composerPostRequirementsAuthorizationCoordinator
                            .resumeStoredAuthorizationIfPresent()
                    ) {
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    communityMembershipCoordinator
                        .handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    communityMembershipCoordinator
                        .resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.REDDIT_FAVORITE -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    redditFavoriteCoordinator
                        .handleCallbackResultTokenIfOwned(resultToken)
                },
                resume = {
                    redditFavoriteCoordinator
                        .resumeStoredAuthorizationIfPresent()
                },
            )

            AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ -> CallbackOwnerRoute(
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
            )

            AuthCapabilityAuthorizationOwner.PROFILE_HISTORY -> CallbackOwnerRoute(
                handle = { resultToken, authorizationContextKey ->
                    profileCommentsCoordinator.completeProfileHistoryAuthorization(
                        resultToken = resultToken,
                        contextKey =
                            authorizationContextKey,
                    )
                },
                resume = { authorizationContextKey ->
                    profileCommentsCoordinator.resumeProfileHistoryAuthorization(
                        contextKey =
                            authorizationContextKey,
                    )
                },
            )

            AuthCapabilityAuthorizationOwner.INBOX_READ -> CallbackOwnerRoute(
                handle = { resultToken, _ ->
                    val capability = runtime.inboxReadCapability
                    if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
                        // AUTHCALLBACK09: the user closed the browser; ask
                        // again without reporting a failure.
                        capability?.cancelPendingAuthorization()
                        inboxCoordinator.cancelPendingInboxAuthorization()
                    } else if (capability?.hasStoredCallback() == true) {
                        inboxCoordinator.completeInboxAuthorization(capability)
                    } else {
                        capability?.cancelPendingAuthorization()
                        inboxCoordinator.failPendingInboxAuthorization()
                    }
                },
                resume = {
                    val capability = runtime.inboxReadCapability
                    when {
                        capability?.hasStoredCallback() == true ->
                            inboxCoordinator.completeInboxAuthorization(capability)
                        capability?.hasPendingAuthorization() == true -> Unit
                        else ->
                            inboxCoordinator.failPendingInboxAuthorization()
                    }
                },
            )

            AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE -> CallbackOwnerRoute(
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
            )
        }

    fun submitComment(request: CommentWriteRequest) {
        viewModelScope.launch {
            commentWriteCoordinator.submit(request)
            commentWriteCoordinator.followProcessingMediaComment()
        }
    }

    suspend fun beginCommentAuthorization(): AuthBrowserLaunchResult =
        commentWriteCoordinator.beginAuthorization()

    fun commentAuthorizationBrowserLaunchFailed() {
        commentWriteCoordinator.browserLaunchFailed()
    }

    fun cancelCommentAuthorization() {
        commentWriteCoordinator.cancelPendingAuthorization()
    }

    fun resetCommentWriteState() {
        commentWriteCoordinator.reset()
    }

    fun editComment(request: CommentEditRequest) {
        viewModelScope.launch {
            commentEditCoordinator.edit(request)
        }
    }

    suspend fun beginCommentEditAuthorization(): AuthBrowserLaunchResult =
        commentEditCoordinator.beginAuthorization()

    fun commentEditAuthorizationBrowserLaunchFailed() {
        commentEditCoordinator.browserLaunchFailed()
    }

    fun cancelCommentEditAuthorization() {
        commentEditCoordinator.cancelPendingAuthorization()
    }

    fun resetCommentEditState() {
        commentEditCoordinator.reset()
    }

    fun checkCommentEdit() {
        viewModelScope.launch { commentEditCoordinator.checkAgain() }
    }

    fun deleteComment(request: CommentDeleteRequest) {
        viewModelScope.launch { commentDeleteCoordinator.delete(request) }
    }

    suspend fun beginCommentDeleteAuthorization(): AuthBrowserLaunchResult = commentDeleteCoordinator.beginAuthorization()
    fun commentDeleteAuthorizationBrowserLaunchFailed() { commentDeleteCoordinator.browserLaunchFailed() }
    fun cancelCommentDeleteAuthorization() { commentDeleteCoordinator.cancelPendingAuthorization() }
    fun resetCommentDeleteState() { commentDeleteCoordinator.reset() }

    fun editPost(request: PostEditRequest) {
        viewModelScope.launch {
            postEditCoordinator.edit(request)
        }
    }

    suspend fun beginPostEditAuthorization(): AuthBrowserLaunchResult =
        postEditCoordinator.beginAuthorization()

    fun postEditAuthorizationBrowserLaunchFailed() {
        postEditCoordinator.browserLaunchFailed()
    }

    fun cancelPostEditAuthorization() {
        postEditCoordinator.cancelPendingAuthorization()
    }

    fun resetPostEditState() {
        postEditCoordinator.reset()
    }

    fun checkPostEdit() {
        viewModelScope.launch { postEditCoordinator.checkAgain() }
    }

    fun deletePost(request: PostDeleteRequest) {
        viewModelScope.launch { postDeleteCoordinator.delete(request) }
    }

    suspend fun beginPostDeleteAuthorization(): AuthBrowserLaunchResult =
        postDeleteCoordinator.beginAuthorization()

    fun postDeleteAuthorizationBrowserLaunchFailed() {
        postDeleteCoordinator.browserLaunchFailed()
    }

    fun cancelPostDeleteAuthorization() {
        postDeleteCoordinator.cancelPendingAuthorization()
    }

    fun resetPostDeleteState() {
        postDeleteCoordinator.reset()
    }

    fun checkPostDelete() {
        viewModelScope.launch { postDeleteCoordinator.checkAgain() }
    }

    fun loadPostManagement(target: PostManageTarget) {
        viewModelScope.launch {
            postManageCoordinator.load(target)
        }
    }

    fun loadUserFlair(
        accountStableId: String,
        communityName: String,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            userFlairCoordinator.load(
                accountStableId = accountStableId,
                rawCommunityName = communityName,
                force = forceRefresh,
            )
        }
    }

    fun setUserFlair(request: UserFlairMutationRequest) {
        viewModelScope.launch { userFlairCoordinator.mutate(request) }
    }

    suspend fun beginUserFlairAuthorization(
        accountStableId: String,
    ): AuthBrowserLaunchResult =
        composerFlairAuthorizationCoordinator
            .beginCapabilityAuthorization(accountStableId)

    fun userFlairAuthorizationBrowserLaunchFailed() {
        composerFlairAuthorizationCoordinator.capabilityBrowserLaunchFailed()
    }

    fun mutatePostManagement(request: PostManageMutationRequest) {
        viewModelScope.launch {
            postManageCoordinator.mutate(request)
        }
    }

    suspend fun beginPostManageAuthorization(): AuthBrowserLaunchResult =
        postManageCoordinator.beginAuthorization()

    fun postManageAuthorizationBrowserLaunchFailed() {
        postManageCoordinator.browserLaunchFailed()
    }

    fun cancelPostManageAuthorization() {
        postManageCoordinator.cancelPendingAuthorization()
    }

    fun resetPostManageState() {
        postManageCoordinator.reset()
    }

    fun submitTextPost(request: TextPostWriteRequest) {
        viewModelScope.launch {
            textPostWriteCoordinator.submit(request)
        }
    }

    fun reconcileTextPostSubmit() {
        viewModelScope.launch {
            textPostWriteCoordinator.reconcilePendingSubmit()
        }
    }

    suspend fun beginTextPostAuthorization(
        request: TextPostWriteRequest,
    ): AuthBrowserLaunchResult = textPostWriteCoordinator.beginAuthorization(request)

    fun textPostAuthorizationBrowserLaunchFailed() {
        textPostWriteCoordinator.browserLaunchFailed()
    }

    fun cancelTextPostAuthorization() {
        textPostWriteCoordinator.cancelPendingAuthorization()
    }

    fun resetTextPostWriteState() {
        textPostWriteCoordinator.reset()
    }

    suspend fun beginComposerFlairAuthorization(accountStableId: String): AuthBrowserLaunchResult =
        composerFlairAuthorizationCoordinator.beginCapabilityAuthorization(accountStableId)

    fun composerFlairAuthorizationBrowserLaunchFailed() {
        composerFlairAuthorizationCoordinator.capabilityBrowserLaunchFailed()
    }

    suspend fun beginComposerPostRequirementsAuthorization(
        accountStableId: String,
    ): AuthBrowserLaunchResult =
        composerPostRequirementsAuthorizationCoordinator
            .beginCapabilityAuthorization(accountStableId)

    fun composerPostRequirementsAuthorizationBrowserLaunchFailed() {
        composerPostRequirementsAuthorizationCoordinator.capabilityBrowserLaunchFailed()
    }

    fun refreshRedditFeed(
        accountStableId: String,
        feed: RedditMainFeed,
        sort: RedditFeedSort,
        timePeriod: RedditListingTimePeriod,
    ) {
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.REFRESH,
            target = AuthenticatedRedditFeedTarget.main(feed, sort, timePeriod),
        )
    }

    fun appendRedditFeed(
        accountStableId: String,
        feed: RedditMainFeed,
        sort: RedditFeedSort,
        timePeriod: RedditListingTimePeriod,
    ) {
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.APPEND,
            target = AuthenticatedRedditFeedTarget.main(feed, sort, timePeriod),
        )
    }

    fun refreshRedditCommunityFeed(
        accountStableId: String,
        communityName: String,
        sort: RedditFeedSort,
        timePeriod: RedditListingTimePeriod,
    ) {
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.REFRESH,
            target = AuthenticatedRedditFeedTarget.community(communityName, sort, timePeriod),
        )
    }

    fun appendRedditCommunityFeed(
        accountStableId: String,
        communityName: String,
        sort: RedditFeedSort,
        timePeriod: RedditListingTimePeriod,
    ) {
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.APPEND,
            target = AuthenticatedRedditFeedTarget.community(communityName, sort, timePeriod),
        )
    }

    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1 (#211): searches and discovery
    // send include_over_18 following "Show NSFW posts", read from the
    // settings file the moment the request starts (the setting is persisted
    // as it changes), so no UI call needs a new parameter.
    private fun showNsfwPostsSetting(): Boolean =
        getApplication<android.app.Application>()
            .getSharedPreferences("subshell_preferences", android.content.Context.MODE_PRIVATE)
            .getBoolean(app.subshell.prototype.ui.SubshellSettings.SHOW_NSFW_POSTS, false)

    fun refreshRedditSearch(
        accountStableId: String,
        query: String,
        communityName: String?,
    ) {
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.REFRESH,
            target = AuthenticatedRedditFeedTarget.search(query, communityName, showNsfwPostsSetting()),
        )
    }

    fun appendRedditSearch(
        accountStableId: String,
        query: String,
        communityName: String?,
    ) {
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.APPEND,
            target = AuthenticatedRedditFeedTarget.search(query, communityName, showNsfwPostsSetting()),
        )
    }

    fun refreshRedditUserSubmitted(
        accountStableId: String,
        username: String,
    ) {
        // ISSUE46_REDDIT_USERNAME_LENGTH_V1: an invalid name does nothing;
        // building the target used to throw out of the ViewModel.
        if (!isValidRedditUsername(username)) return
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.REFRESH,
            target = AuthenticatedRedditFeedTarget.user(username),
        )
    }

    fun appendRedditUserSubmitted(
        accountStableId: String,
        username: String,
    ) {
        if (!isValidRedditUsername(username)) return
        updateRedditFeed(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.APPEND,
            target = AuthenticatedRedditFeedTarget.user(username),
        )
    }

    fun refreshRedditAccountHistory(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ) {
        redditAccountHistoryCoordinator.update(
            accountStableId = accountStableId,
            username = username,
            kind = kind,
            operation = RepositoryLoadOperation.REFRESH,
        )
    }

    fun appendRedditAccountHistory(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ) {
        redditAccountHistoryCoordinator.update(
            accountStableId = accountStableId,
            username = username,
            kind = kind,
            operation = RepositoryLoadOperation.APPEND,
        )
    }

    fun refreshRedditSavedHistory(
        accountStableId: String,
        username: String,
    ) {
        redditSavedHistoryCoordinator.update(
            accountStableId = accountStableId,
            username = username,
            operation = RepositoryLoadOperation.REFRESH,
        )
    }

    fun appendRedditSavedHistory(
        accountStableId: String,
        username: String,
    ) {
        redditSavedHistoryCoordinator.update(
            accountStableId = accountStableId,
            username = username,
            operation = RepositoryLoadOperation.APPEND,
        )
    }

    fun resolveRedditNavigationTargetPost(
        accountStableId: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) = redditNavigationTargetCoordinator.resolveRedditNavigationTargetPost(accountStableId, postId, onResolved)

    fun resolveRedditProfileCommentTarget(
        accountStableId: String,
        username: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) = redditNavigationTargetCoordinator.resolveRedditProfileCommentTarget(accountStableId, username, postId, onResolved)

    fun resolveRedditSavedCommentTarget(
        accountStableId: String,
        username: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) = redditNavigationTargetCoordinator.resolveRedditSavedCommentTarget(accountStableId, username, postId, onResolved)

    private fun activeAuthenticatedProfileAccountId(): String? {
        val snapshot =
            runtime.accountSessionDirectory?.snapshot() ?: return null
        return snapshot.accounts
            .firstOrNull { account ->
                account.stableId == snapshot.activeAccountId &&
                    account.source == AccountSessionSource.AUTHENTICATED
            }
            ?.stableId
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

    // WIKI01A2_VIEWMODEL_WIKI_LOAD_AND_AUTH_V1
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
        inboxCoordinator.keepBackgroundSyncScheduled(account)
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

    private fun updateRedditFeed(
        accountStableId: String,
        operation: RepositoryLoadOperation,
        target: AuthenticatedRedditFeedTarget,
    ) {
        redditFeedCoordinator.update(accountStableId, operation, target)
    }

    override fun onCleared() {
        AuthRuntimePreparation.liveViewModels.decrementAndGet()
        redditFeedCoordinator.cancelPending()
        redditAccountHistoryCoordinator.cancelPending()
        commentThreadCoordinator.cancelPending()
        redditSubscriptionsCoordinator.cancelPending()
        communityMembershipReconciliationJob?.cancel()
        customFeedCoordinator.cancelPending()
        communityDiscoveryCoordinator.cancelPending()
        composerPolicyCoordinator.cancelPending()
        super.onCleared()
    }

}

private val COMMUNITY_MEMBERSHIP_RECONCILIATION_DELAYS_MS =
    listOf(0L, 1_500L, 5_000L, 15_000L)

private const val FEED_LOAD_LOG_TAG = "subshell-feed"

private fun AuthenticatedRedditFeedTarget.feedLogScope(): String = when {
    normalizedUsername != null -> "user:$normalizedUsername"
    normalizedSearchQuery != null -> "search"
    normalizedCommunityName != null -> "community:$normalizedCommunityName"
    else -> requireNotNull(mainFeed).queryToken
}
