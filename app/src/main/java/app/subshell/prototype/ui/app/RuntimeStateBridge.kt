package app.subshell.prototype.ui.app

import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.redditAccountHistoryPostQueryKey
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.DebugFocusedCommentLaunch
import app.subshell.prototype.ui.LocalPostFollowRuntime
import app.subshell.prototype.ui.PostComposerWriteRuntimeEntryPoints
import app.subshell.prototype.ui.RedditIntentLaunch
import app.subshell.prototype.ui.RedditTarget
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.explicitDrawerSidePreference
import app.subshell.prototype.ui.postIdOrNull
import app.subshell.prototype.ui.redditTargetMatchesPost
import app.subshell.prototype.ui.resolveRedditTarget
import app.subshell.prototype.ui.uiDebugLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The runtime→state bridge of SubshellApp: the effects that register
 * requesters on SubshellState, mirror the runtime UI states into it, run
 * the per-account resets and the one-shot launches. No composition happens
 * here; SubshellApp calls this first so the effects keep their order.
 */
@Composable
internal fun RuntimeStateBridge(
    accountConnect: AccountConnectEntryPoints,
    redditFeed: FeedEntryPoints,
    redditAccountHistoryState: RedditAccountHistoryUiState,
    onRefreshRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit,
    onResolveRedditProfileCommentTarget: (String, String, String, (Post?) -> Unit) -> Unit,
    redditUserFlairState: UserFlairUiState,
    onLoadRedditUserFlair: (String, String, Boolean) -> Unit,
    onSetRedditUserFlair: (UserFlairMutationRequest) -> Unit,
    onAuthorizeRedditUserFlair: (String) -> Unit,
    redditCommunity: CommunityEntryPoints,
    moderatorTools: ModeratorToolsEntryPoints,
    postVoteState: PostVoteUiState,
    onVotePost: (PostVoteRequest) -> Unit,
    onAuthorizePostVote: () -> Unit,
    postSaveState: PostSaveUiState,
    onSavePost: (PostSaveRequest) -> Unit,
    onAuthorizePostSave: () -> Unit,
    contentSafetyState: ContentSafetyUiState,
    onSetPostHidden: (RedditPostVisibilityRequest) -> Unit,
    onReportContent: (RedditContentReportRequest) -> Unit,
    onSetUserBlocked: (RedditUserBlockRequest) -> Unit,
    onRefreshBlockedAccounts: (String) -> Unit,
    onAuthorizeContentSafety: () -> Unit,
    commentMutations: CommentMutationEntryPoints,
    postComposerWriteRuntime: PostComposerWriteRuntimeEntryPoints,
    onResetTextPostWriteState: () -> Unit,
    redditInbox: InboxEntryPoints,
    onResolveRedditNavigationTarget: (String, String, (Post?) -> Unit) -> Unit,
    debugFocusedCommentLaunch: DebugFocusedCommentLaunch?,
    onDebugFocusedCommentConsumed: () -> Unit,
    redditIntentLaunch: RedditIntentLaunch?,
    onRedditIntentConsumed: () -> Unit,
    onRedditLinkUnavailable: (RedditLinkUnavailable) -> Unit,
    onResolveRedditShareLink: (String, (String?) -> Unit) -> Unit,
    state: SubshellState,
) {
    val onLoadRedditInbox = redditInbox.onLoadRedditInbox
    val redditCommunityInfoState = redditCommunity.redditCommunityInfoState
    val onLoadRedditCommunityInfo = redditCommunity.onLoadRedditCommunityInfo
    val communityMembershipState = redditCommunity.communityMembershipState
    val onAuthorizeCommunityMembership = redditCommunity.onAuthorizeCommunityMembership
    val redditFeedState = redditFeed.redditFeedState
    val onLoadRedditSubscriptions = redditFeed.onLoadRedditSubscriptions
    val commentWriteState = commentMutations.commentWriteState
    val onResetCommentWriteState = commentMutations.onResetCommentWriteState
    val onModeratorReportsAccountChanged = moderatorTools.onModeratorReportsAccountChanged
    val onModeratorContentControlsAccountChanged = moderatorTools.onModeratorContentControlsAccountChanged
    val onModeratorLogAccountChanged = moderatorTools.onModeratorLogAccountChanged
    val onModeratorUserManagementAccountChanged = moderatorTools.onModeratorUserManagementAccountChanged
    val accountSessionDirectory = accountConnect.accountSessionDirectory
    val connectAccountState = accountConnect.connectAccountState
    // USERFLAIR01A1_SHARED_SUBSHELLSTATE_RUNTIME_BRIDGE_V1
    LaunchedEffect(
        onLoadRedditUserFlair,
        onSetRedditUserFlair,
        onAuthorizeRedditUserFlair,
    ) {
        state.setAuthenticatedUserFlairRequesters(
            loader = onLoadRedditUserFlair,
            mutator = onSetRedditUserFlair,
            authorizer = onAuthorizeRedditUserFlair,
        )
    }
    LaunchedEffect(redditUserFlairState) {
        state.synchronizeUserFlairUiState(redditUserFlairState)
    }

    // REPORT01A1_REUSE_EXISTING_COMMUNITYINFO_RUNTIME_V1
    LaunchedEffect(onLoadRedditCommunityInfo) {
        state.setAuthenticatedReportReasonInfoRequester(onLoadRedditCommunityInfo)
    }
    LaunchedEffect(redditCommunityInfoState) {
        state.synchronizeReportReasonCommunityInfo(redditCommunityInfoState)
    }

    val postFollowRuntime = LocalPostFollowRuntime.current

    val diagnosticAppContext = LocalContext.current.applicationContext
    val diagnosticEventStore = remember(diagnosticAppContext) {
        DiagnosticEventStore(diagnosticAppContext)
    }
    LaunchedEffect(diagnosticEventStore) {
        // LOWUI8_ONE_SESSION_START_PER_PROCESS_V1 (OCB-116): an activity
        // recreation composes this again; the session started only once.
        if (!AppSessionStartRecord.claim()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                diagnosticEventStore.recordAppSessionStarted(BuildConfig.VERSION_CODE.toLong())
            }
        }
    }

    SideEffect {
        state.setAuthenticatedPostVoteRequester(onVotePost)
        state.setAuthenticatedPostSaveRequester(onSavePost)
        state.setAuthenticatedPostFollowRequester(
            postFollowRuntime.onSetFollowed,
        )
        state.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester = onSetPostHidden,
            reportRequester = onReportContent,
            userBlockRequester = onSetUserBlocked,
        )
    }
    val nextCommunityMuteExpiry = state.nextCommunityMuteExpiryEpochMillis
    LaunchedEffect(state.activeAccountId, nextCommunityMuteExpiry) {
        val delayMillis = state.millisUntilNextCommunityMuteExpiry()
            ?: return@LaunchedEffect
        delay(delayMillis + 50L)
        state.refreshCommunityMuteClock()
    }
    LaunchedEffect(state.drawerSidePreference, state.interactionSide) {
        val explicitDrawerSide = explicitDrawerSidePreference(
            preference = state.drawerSidePreference,
            interactionSide = state.interactionSide,
        )
        if (explicitDrawerSide != state.drawerSidePreference) {
            state.updateDrawerSidePreference(explicitDrawerSide)
        }
    }
    LaunchedEffect(postVoteState) {
        state.synchronizePostVoteState(postVoteState)
    }
    LaunchedEffect(postSaveState) {
        state.synchronizePostSaveState(postSaveState)
    }
    LaunchedEffect(postFollowRuntime.state) {
        state.synchronizePostFollowState(
            postFollowRuntime.state,
        )
    }
    LaunchedEffect(contentSafetyState) {
        state.synchronizeContentSafetyState(contentSafetyState)
    }
    // SAFETY01A3_AUTHORITATIVE_HIDDEN_CACHE_RELOAD_V1
    LaunchedEffect(
        contentSafetyState.completionRevision,
        contentSafetyState.hiddenHistoryReconciliation,
        contentSafetyState.accountStableId,
    ) {
        if (
            contentSafetyState.phase == ContentSafetyPhase.SUCCEEDED &&
            contentSafetyState.hiddenHistoryReconciliation ==
                HiddenHistoryReconciliation.REFRESHED &&
            contentSafetyState.accountStableId == state.activeAccountId &&
            state.activeAccountIsAuthenticated
        ) {
            val hiddenQueryKey = redditAccountHistoryPostQueryKey(
                accountStableId = state.activeAccountId,
                username = state.activeAccountUsername,
                kind = RedditAccountHistoryKind.HIDDEN,
            )
            state.synchronizePostPagingAfterRemoteWrite(hiddenQueryKey)
        }
    }

    // USERACTION01A2_RECONCILE_BLOCKED_LIST_AFTER_MUTATION_V1
    LaunchedEffect(
        contentSafetyState.completionRevision,
        contentSafetyState.operation,
        contentSafetyState.phase,
        contentSafetyState.accountStableId,
    ) {
        val accountStableId = contentSafetyState.accountStableId
        if (
            contentSafetyState.phase == ContentSafetyPhase.SUCCEEDED &&
            contentSafetyState.operation == ContentSafetyOperation.SET_USER_BLOCKED &&
            accountStableId != null &&
            accountStableId == state.activeAccountId
        ) {
            onRefreshBlockedAccounts(accountStableId)
        }
    }

    LaunchedEffect(debugFocusedCommentLaunch?.requestId, state.activeAccountId) {
        val target = debugFocusedCommentLaunch ?: return@LaunchedEffect
        if (!state.activeAccountIsAuthenticated) {
            uiDebugLog(
                "SubshellCommentNav",
                "phase=adb_launch_failed post=${target.postId} target=${target.commentId} " +
                    "reason=active_account_not_authenticated",
            )
            onDebugFocusedCommentConsumed()
            return@LaunchedEffect
        }
        uiDebugLog(
            "SubshellCommentNav",
            "phase=adb_launch_resolve post=${target.postId} target=${target.commentId} " +
                "username=${target.username} account=${state.activeAccountId}",
        )
        onRefreshRedditAccountHistory(
            state.activeAccountId,
            target.username,
            RedditAccountHistoryKind.COMMENTS,
        )
        val resolvedPost = CompletableDeferred<Post?>()
        onResolveRedditProfileCommentTarget(
            state.activeAccountId,
            target.username,
            target.postId,
        ) { post ->
            if (!resolvedPost.isCompleted) resolvedPost.complete(post)
        }
        val post = withTimeoutOrNull(DEBUG_FOCUSED_COMMENT_RESOLVE_TIMEOUT_MS) {
            resolvedPost.await()
        }
        if (post == null) {
            uiDebugLog(
                "SubshellCommentNav",
                "phase=adb_launch_failed post=${target.postId} target=${target.commentId} " +
                    "reason=post_resolution_failed",
            )
            onDebugFocusedCommentConsumed()
            return@LaunchedEffect
        }
        uiDebugLog(
            "SubshellCommentNav",
            "phase=profile_open post=${post.id} target=${target.commentId} source=adb",
        )
        state.openFromUser(
            post = post,
            username = target.username,
            commentId = target.commentId,
        )
        onDebugFocusedCommentConsumed()
    }

    val linkUriHandler = LocalUriHandler.current
    LaunchedEffect(
        redditIntentLaunch?.requestId,
        state.activeAccountId,
        state.activeAccountIsAuthenticated,
    ) {
        val launch = redditIntentLaunch ?: return@LaunchedEffect
        val target = resolveRedditTarget(launch.rawUri)
        if (target == null) {
            // ISSUE41_UNSUPPORTED_REDDIT_LINK_NOTICE_V1
            onRedditLinkUnavailable(
                RedditLinkUnavailable(
                    requestId = launch.requestId,
                    rawUri = launch.rawUri,
                    reason = RedditLinkUnavailableReason.UNSUPPORTED_LINK,
                ),
            )
            onRedditIntentConsumed()
            return@LaunchedEffect
        }
        if (target is RedditTarget.ShareLink) {
            // REDDITURL03_SHARE_LINK_REDIRECT_V1: ask Reddit where the share
            // link leads, then open that URL like any other link in the app.
            val sharedUrl = CompletableDeferred<String?>()
            onResolveRedditShareLink(target.url) { url ->
                if (!sharedUrl.isCompleted) sharedUrl.complete(url)
            }
            val resolvedUrl = withTimeoutOrNull(DEEPLINK01_TARGET_RESOLVE_TIMEOUT_MS) {
                sharedUrl.await()
            }
            val sharedTarget = resolvedUrl
                ?.let(::resolveRedditTarget)
                ?.takeUnless { resolved -> resolved is RedditTarget.ShareLink }
            onRedditIntentConsumed()
            if (resolvedUrl == null || sharedTarget == null) {
                onRedditLinkUnavailable(
                    RedditLinkUnavailable(
                        requestId = launch.requestId,
                        rawUri = launch.rawUri,
                        reason = RedditLinkUnavailableReason.SHARE_LINK_UNAVAILABLE,
                    ),
                )
            } else {
                linkUriHandler.openUri(resolvedUrl)
            }
            return@LaunchedEffect
        }
        // LOWUI2_DEEP_LINK_CACHE_READ_OFF_MAIN_V1 (OCB-117): the post cache is a
        // blocking Room read; it runs on IO, the route opens on Main.
        val cachedPost = state.redditTargetPostNeedsCacheRead(target)?.let { postId ->
            val accountStableId = state.activeAccountId
            withContext(Dispatchers.IO) { state.cachedRedditTargetPost(postId, accountStableId) }
        }
        if (state.openRedditTarget(target, resolvedPost = cachedPost, readCache = false)) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "SubshellDeepLink",
                    "phase=route_open request=${launch.requestId} target=${target::class.simpleName} source=local",
                )
            }
            onRedditIntentConsumed()
            return@LaunchedEffect
        }
        val postId = target.postIdOrNull()
        if (postId == null) {
            onRedditIntentConsumed()
            // LOWUX1_REFUSED_LINK_IS_REPORTED_V1 (OCB-118): a link Subshell
            // knows but cannot open here (a /me/ feed with no Reddit account
            // active) was consumed without a word.
            onRedditLinkUnavailable(
                RedditLinkUnavailable(
                    requestId = launch.requestId,
                    rawUri = launch.rawUri,
                    reason = RedditLinkUnavailableReason.UNSUPPORTED_LINK,
                ),
            )
            return@LaunchedEffect
        }
        if (!state.activeAccountIsAuthenticated) return@LaunchedEffect
        if (BuildConfig.DEBUG) {
            Log.d(
                "SubshellDeepLink",
                "phase=resolve_start request=${launch.requestId} post=$postId account=${state.activeAccountId}",
            )
        }
        val resolvedPost = CompletableDeferred<Post?>()
        onResolveRedditNavigationTarget(
            state.activeAccountId,
            postId,
        ) { post ->
            if (!resolvedPost.isCompleted) resolvedPost.complete(post)
        }
        val post = withTimeoutOrNull(DEEPLINK01_TARGET_RESOLVE_TIMEOUT_MS) {
            resolvedPost.await()
        }
        val opened = post != null && state.openRedditTarget(
            target = target,
            resolvedPost = post,
            readCache = false,
        )
        if (BuildConfig.DEBUG) {
            Log.d(
                "SubshellDeepLink",
                "phase=${if (opened) "route_open" else "route_failed"} " +
                    "request=${launch.requestId} target=${target::class.simpleName} source=remote",
            )
        }
        if (!opened) {
            onRedditLinkUnavailable(
                RedditLinkUnavailable(
                    requestId = launch.requestId,
                    rawUri = launch.rawUri,
                    reason = RedditLinkUnavailableReason.POST_UNAVAILABLE,
                ),
            )
        }
        onRedditIntentConsumed()
    }

    LaunchedEffect(accountSessionDirectory, connectAccountState.directoryRevision) {
        accountSessionDirectory?.snapshot()?.let(state::synchronizeAccountSessions)
        if (connectAccountState.directoryRevision > 0L) {
            state.invalidatePostPaging()
        }
    }

    LaunchedEffect(state.activeAccountIsAuthenticated, state.activeAccountId) {
        if (state.activeAccountIsAuthenticated) {
            onLoadRedditSubscriptions(state.activeAccountId, false)
            onLoadRedditInbox(state.activeAccountId, false)
        }
    }
    // INBOX02_SYNC_ONLY_WITH_NOTIFICATIONS_V1: turning inbox notifications on
    // or off schedules or cancels the background sync right away (an inbox
    // that is already loaded is not fetched again). The first composition is
    // covered by the account effect above.
    val inboxNotificationsSeen = remember { booleanArrayOf(state.inboxNotificationsEnabled) }
    LaunchedEffect(state.inboxNotificationsEnabled) {
        if (state.inboxNotificationsEnabled == inboxNotificationsSeen[0]) return@LaunchedEffect
        inboxNotificationsSeen[0] = state.inboxNotificationsEnabled
        if (state.activeAccountIsAuthenticated) {
            onLoadRedditInbox(state.activeAccountId, false)
        }
    }
    LaunchedEffect(
        communityMembershipState.phase,
        communityMembershipState.accountStableId,
        communityMembershipState.communityName,
        communityMembershipState.action,
    ) {
        if (
            communityMembershipState.phase ==
            CommunityMembershipPhase.AUTHORIZATION_REQUIRED
        ) {
            onAuthorizeCommunityMembership()
        }
    }

    LaunchedEffect(
        postVoteState.phase,
        postVoteState.accountStableId,
        postVoteState.postFullname,
        postVoteState.direction,
    ) {
        if (postVoteState.phase == PostVotePhase.AUTHORIZATION_REQUIRED) {
            onAuthorizePostVote()
        }
    }

    LaunchedEffect(
        postSaveState.phase,
        postSaveState.accountStableId,
        postSaveState.postFullname,
        postSaveState.saved,
    ) {
        if (postSaveState.phase == PostSavePhase.AUTHORIZATION_REQUIRED) {
            onAuthorizePostSave()
        }
    }

    LaunchedEffect(
        postFollowRuntime.state.phase,
        postFollowRuntime.state.accountStableId,
        postFollowRuntime.state.postFullname,
        postFollowRuntime.state.followed,
    ) {
        if (
            postFollowRuntime.state.phase ==
            PostFollowPhase.AUTHORIZATION_REQUIRED
        ) {
            postFollowRuntime.onAuthorize()
        }
    }

    LaunchedEffect(
        contentSafetyState.phase,
        contentSafetyState.accountStableId,
        contentSafetyState.thingFullname,
    ) {
        if (contentSafetyState.phase == ContentSafetyPhase.AUTHORIZATION_REQUIRED) {
            onAuthorizeContentSafety()
        }
    }

    LaunchedEffect(
        commentWriteState.phase,
        commentWriteState.completionRevision,
    ) {
        // COMPOSER01_COMPLETION_KEYED_ON_ACCOUNT_V1: a success clears only the
        // draft of the account that sent it (null = draft from before UIPOST02).
        if (commentWriteState.phase == CommentWritePhase.SUCCEEDED) {
            val draftAccount = state.commentComposerDraft?.accountStableId
            if (draftAccount == null || draftAccount == commentWriteState.accountStableId) {
                commentWriteState.targetStableKey?.let(state::completeRemoteCommentComposer)
            }
            onResetCommentWriteState()
        }
    }
    LaunchedEffect(
        postComposerWriteRuntime.textPostWriteState.phase,
        postComposerWriteRuntime.textPostWriteState.completionRevision,
    ) {
        if (postComposerWriteRuntime.textPostWriteState.phase == TextPostWritePhase.SUCCEEDED) {
            val accountStableId = postComposerWriteRuntime.textPostWriteState.accountStableId
            val requestKey = postComposerWriteRuntime.textPostWriteState.requestKey
            val submittedPost = postComposerWriteRuntime.textPostWriteState.submittedPost
            if (accountStableId != null && requestKey != null && submittedPost != null) {
                val crosspost = submittedPost.crosspostFullname != null
                // CROSSPOST01A2_POST_PUBLISH_CANONICAL_READBACK_V1
                // Preserve A1E's immediate wrapper navigation, then replace that exact
                // still-open wrapper with Reddit's rich projection when available.
                // COMPOSER05_POST_PUBLISH_CANONICAL_REBIND_V1: every post type does the
                // same; a text/link/media post opened from the request is rebound too.
                val created = if (crosspost) {
                    state.completeRemoteCrosspostComposer(
                        accountStableId,
                        requestKey,
                        submittedPost,
                    ).also { postComposerWriteRuntime.onInvalidateCrosspostReview() }
                } else {
                    state.completeRemoteTextPostComposer(
                        accountStableId = accountStableId,
                        requestKey = requestKey,
                        submittedPost = submittedPost,
                    )
                }

                if (
                    created != null &&
                    state.currentNavigationRoute is SubshellRoute.Post &&
                    state.selectedPost?.let {
                        redditTargetMatchesPost(submittedPost.id, it.id)
                    } == true
                ) {
                    val resolvedPost = CompletableDeferred<Post?>()
                    onResolveRedditNavigationTarget(
                        accountStableId,
                        submittedPost.id,
                    ) { post ->
                        if (!resolvedPost.isCompleted) resolvedPost.complete(post)
                    }
                    val canonicalPost = withTimeoutOrNull(
                        CROSSPOST01A2_POST_PUBLISH_RESOLVE_TIMEOUT_MS,
                    ) {
                        resolvedPost.await()
                    }
                    if (canonicalPost != null) {
                        if (crosspost) {
                            state.rebindCompletedCrosspostToCanonical(
                                submittedPost = submittedPost,
                                canonicalPost = canonicalPost,
                            )
                        } else {
                            state.rebindCompletedTextPostToCanonical(
                                submittedPost = submittedPost,
                                canonicalPost = canonicalPost,
                            )
                        }
                    }
                }
                // Reset only after the suspending canonical readback. Resetting the
                // LaunchedEffect key before this point can cancel this very coroutine.
            }
            onResetTextPostWriteState()
        }
    }
    LaunchedEffect(
        redditFeedState.contentRevision,
        redditFeedState.queryKey,
    ) {
        val updatedQueryKey = redditFeedState.queryKey
        if (redditFeedState.contentRevision > 0L && updatedQueryKey != null) {
            val before = state.postPagingSnapshot(updatedQueryKey).items.size
            state.synchronizePostPagingAfterRemoteWrite(updatedQueryKey)
            val after = state.postPagingSnapshot(updatedQueryKey).items.size
            uiDebugLog(
                "SubshellPagingSync",
                "remote_feed_sync key=$updatedQueryKey before=$before after=$after " +
                    "revision=${redditFeedState.contentRevision}",
            )
        }
    }
    LaunchedEffect(
        redditAccountHistoryState.contentRevision,
        redditAccountHistoryState.queryKey,
        redditAccountHistoryState.kind,
    ) {
        val updatedQueryKey = redditAccountHistoryState.queryKey
        if (
            redditAccountHistoryState.contentRevision > 0L &&
            updatedQueryKey != null &&
            redditAccountHistoryState.kind?.isPostHistory == true
        ) {
            val before = state.postPagingSnapshot(updatedQueryKey).items.size
            state.synchronizePostPagingAfterRemoteWrite(updatedQueryKey)
            val after = state.postPagingSnapshot(updatedQueryKey).items.size
            uiDebugLog(
                "SubshellPagingSync",
                "remote_history_sync key=$updatedQueryKey before=$before after=$after " +
                    "revision=${redditAccountHistoryState.contentRevision}",
            )
        }
    }

    // MODERATION02A2_ROOT_REPORT_INSPECTOR_OWNER_V1
    LaunchedEffect(state.activeAccountId) {
        onModeratorReportsAccountChanged(state.activeAccountId)
    }
    // MODERATION02A3_ROOT_CONTENT_CONTROLS_OWNER_V1
    LaunchedEffect(state.activeAccountId) {
        onModeratorContentControlsAccountChanged(state.activeAccountId)
    }
    // MODERATION02A4_ROOT_MOD_LOG_OWNER_V1
    LaunchedEffect(state.activeAccountId) {
        onModeratorLogAccountChanged(state.activeAccountId)
    }
    // MODERATION02A5A1_ROOT_USER_MANAGEMENT_OWNER_V1
    LaunchedEffect(state.activeAccountId) {
        onModeratorUserManagementAccountChanged(state.activeAccountId)
    }
}

private const val DEBUG_FOCUSED_COMMENT_RESOLVE_TIMEOUT_MS = 8_000L
private const val DEEPLINK01_TARGET_RESOLVE_TIMEOUT_MS = 8_000L
private const val CROSSPOST01A2_POST_PUBLISH_RESOLVE_TIMEOUT_MS = 8_000L

/** The process records "App session started" once (OCB-116). */
internal object AppSessionStartRecord {
    private val recorded = AtomicBoolean(false)

    fun claim(): Boolean = recorded.compareAndSet(false, true)

    fun resetForTest() {
        recorded.set(false)
    }
}
