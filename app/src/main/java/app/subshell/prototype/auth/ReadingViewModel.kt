package app.subshell.prototype.auth

import app.subshell.prototype.data.diagnostics.recordFailureWithoutAScreen
import app.subshell.prototype.data.isValidRedditUsername
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RedditFeedSort
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import app.subshell.prototype.data.account.RedditUserBlockRequest

/**
 * AUTHSPLIT05_READING_VIEWMODEL_V1: the reading area's state holder (Google:
 * one ViewModel per screen area): feeds, search and user posts, the comment
 * thread with comment write/edit/delete (they update the thread and the
 * account history), post votes, saves, follows and content safety (a settled
 * comment vote/save reaches the thread), account and saved history, profile
 * comments and link/notification targets. They stay together because they
 * update each other's state. Moved out of AuthConnectViewModel verbatim; it
 * shares the auth runtime through [SharedAuthRuntime], registers its owners'
 * capability callback routes there, applies moderated comments from there
 * and tells the composer policy about completed comment authorizations.
 */
internal class ReadingViewModel internal constructor(
    private val shared: SharedAuthRuntime,
) : ViewModel() {
    private val runtime = shared.runtime
    private val application = shared.application

    companion object {
        val Factory = viewModelFactory {
            initializer { ReadingViewModel(requireNotNull(this[AuthRuntimeKey])) }
        }
    }

    // SUBSHELL_COMMENTTHREAD01_COORDINATOR_V1: the comment-thread reads and
    // local edit application live in CommentThreadCoordinator.
    private val commentThreadCoordinator = CommentThreadCoordinator(
        commentLoader = runtime.redditCommentLoader,
        scope = viewModelScope,
    )
    // AUTHSPLIT01: a comment a moderator removed or approved (ModerationViewModel).
    init {
        viewModelScope.launch {
            shared.moderatedComments.collect { moderated ->
                commentThreadCoordinator.applyModeratedComment(
                    accountStableId = moderated.accountStableId,
                    commentFullname = moderated.commentFullname,
                    removed = moderated.removed,
                )
            }
        }
    }
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
        recordLookupFailure = { scopeKey, details -> recordFailureWithoutAScreen(application, scopeKey, details) },
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
    private val postVoteCoordinator = PostVoteCoordinator(
        capability = runtime.postVoteCapability,
        writer = runtime.postVoteWriter,
        clientMode = runtime.postVoteClientMode,
        activeAccountStableId = {
            runtime.accountSessionDirectory
                ?.snapshot()
                ?.activeAccountId
        },
        onVoted = { request ->
            if (request.postFullname.startsWith("t1_")) {
                commentThreadCoordinator.applySettledCommentAction(request.accountStableId, request.postFullname) { comment ->
                    comment.copy(score = comment.score + (request.direction - comment.vote), vote = request.direction)
                }
            }
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
        onSaved = { request ->
            if (request.postFullname.startsWith("t1_")) {
                commentThreadCoordinator.applySettledCommentAction(request.accountStableId, request.postFullname) { comment ->
                    comment.copy(saved = request.saved)
                }
            }
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
        // PROFILESSOT01_HISTORY_KEEPS_EDITS_AND_DELETES_V1: the thread and the
        // account's comment history both keep a confirmed edit.
        onCommentEdited = { request ->
            commentThreadCoordinator.applyEditedComment(request)
            redditAccountHistoryCoordinator.applyEditedComment(request.commentFullname, request.text)
        },
    )
    private val commentDeleteCoordinator = CommentDeleteCoordinator(
        capability =
            runtime.commentDeleteAuthorizationCapability,
        deleter = runtime.commentDeleter,
        accountDirectory = runtime.accountSessionDirectory,
        clientMode = runtime.commentEditClientMode,
        onCommentDeleted = { request ->
            commentThreadCoordinator.applyDeletedComment(request)
            redditAccountHistoryCoordinator.applyDeletedComment(request.commentFullname)
        },
    )

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

    val commentWriteUiState: StateFlow<CommentWriteUiState> =
        commentWriteCoordinator.uiState

    val commentEditUiState: StateFlow<CommentEditUiState> =
        commentEditCoordinator.uiState

    val commentDeleteUiState: StateFlow<CommentDeleteUiState> =
        commentDeleteCoordinator.uiState

    // AUTHSPLIT03: the composer policy lives in PostComposerViewModel; a
    // comment authorization tells it through the shared runtime.
    private fun reloadComposerPolicyAfterCapabilityAuthorization() =
        shared.capabilityAuthorizationCompleted()

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
    private fun showNsfwPostsSetting(): Boolean = shared.showNsfwPosts()

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

    private fun activeAuthenticatedProfileAccountId(): String? = shared.activeAuthenticatedAccountId()

    // WIKI01A2_VIEWMODEL_WIKI_LOAD_AND_AUTH_V1
    private fun updateRedditFeed(
        accountStableId: String,
        operation: RepositoryLoadOperation,
        target: AuthenticatedRedditFeedTarget,
    ) {
        redditFeedCoordinator.update(accountStableId, operation, target)
    }

    init {
        shared.registerCapabilityRoutes(
            mapOf(
                AuthCapabilityAuthorizationOwner.COMMENT_EDIT to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        commentEditCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    },
                    resume = {
                        if (commentEditCoordinator.resumeStoredAuthorizationIfPresent()) {
                            reloadComposerPolicyAfterCapabilityAuthorization()
                        }
                    },
                ),

                AuthCapabilityAuthorizationOwner.COMMENT_DELETE to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        commentDeleteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    },
                    resume = {
                        if (commentDeleteCoordinator.resumeStoredAuthorizationIfPresent()) {
                            reloadComposerPolicyAfterCapabilityAuthorization()
                        }
                    },
                ),

                AuthCapabilityAuthorizationOwner.COMMENT_WRITE to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        commentWriteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    },
                    resume = {
                        if (commentWriteCoordinator.resumeStoredAuthorizationIfPresent()) {
                            reloadComposerPolicyAfterCapabilityAuthorization()
                        }
                    },
                ),

                AuthCapabilityAuthorizationOwner.POST_VOTE to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        postVoteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        postVoteCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.POST_SAVE to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        postSaveCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        postSaveCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.POST_FOLLOW to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        postFollowCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        postFollowCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.CONTENT_SAFETY to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        contentSafetyCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                    },
                    resume = {
                        contentSafetyCoordinator.resumeStoredAuthorizationIfPresent()
                    },
                ),

                AuthCapabilityAuthorizationOwner.PROFILE_HISTORY to CapabilityCallbackRoute(
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
                ),
            ),
        )
    }

    override fun onCleared() {
        redditFeedCoordinator.cancelPending()
        redditAccountHistoryCoordinator.cancelPending()
        commentThreadCoordinator.cancelPending()
        super.onCleared()
    }
}

private const val FEED_LOAD_LOG_TAG = "subshell-feed"

private fun AuthenticatedRedditFeedTarget.feedLogScope(): String = when {
    normalizedUsername != null -> "user:$normalizedUsername"
    normalizedSearchQuery != null -> "search"
    normalizedCommunityName != null -> "community:$normalizedCommunityName"
    else -> requireNotNull(mainFeed).queryToken
}
