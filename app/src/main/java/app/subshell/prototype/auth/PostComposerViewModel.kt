package app.subshell.prototype.auth

import app.subshell.prototype.ui.CrosspostReviewUiState
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.crosspostDraftWriteAvailable
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostDeleteRequest
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.ui.ComposerCommunitySearchSnapshot
import app.subshell.prototype.ui.CommentMediaPolicy
import app.subshell.prototype.ui.ComposerPolicySnapshot
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState

/**
 * AUTHSPLIT03_POST_COMPOSER_VIEWMODEL_V1: the post composing area's state
 * holder (Google: one ViewModel per screen area): text/link/media post submit
 * (and its pending-submit restore), crosspost review, post edit, delete and
 * management, user flair, the composer's flair and post-requirements
 * authorizations and the composer policy (also the comment composer's media
 * rules). Moved out of AuthConnectViewModel verbatim; it shares the auth
 * runtime through [SharedAuthRuntime], registers its owners' capability
 * callback routes there and reloads the composer policy when any capability
 * authorization completes (a comment authorization reports it there).
 */
internal class PostComposerViewModel internal constructor(
    private val shared: SharedAuthRuntime,
) : ViewModel() {
    private val runtime = shared.runtime
    private val application = shared.application

    companion object {
        val Factory = viewModelFactory {
            initializer { PostComposerViewModel(requireNotNull(this[AuthRuntimeKey])) }
        }
    }

    private val textPostSubmitAttemptStore = TextPostSubmitAttemptStore(
        application.getSharedPreferences("subshell_preferences", 0),
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

    init {
        shared.registerCapabilityRoutes(
            mapOf(
                AuthCapabilityAuthorizationOwner.TEXT_POST_WRITE to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        textPostWriteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    },
                    resume = {
                        if (textPostWriteCoordinator.resumeStoredAuthorizationIfPresent()) {
                            reloadComposerPolicyAfterCapabilityAuthorization()
                        }
                    },
                ),

                AuthCapabilityAuthorizationOwner.POST_EDIT to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        postEditCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    },
                    resume = {
                        if (postEditCoordinator.resumeStoredAuthorizationIfPresent()) {
                            reloadComposerPolicyAfterCapabilityAuthorization()
                        }
                    },
                ),

                AuthCapabilityAuthorizationOwner.POST_DELETE to CapabilityCallbackRoute(
                    handle = { resultToken, _ ->
                        postDeleteCoordinator.handleCallbackResultTokenIfOwned(resultToken)
                        reloadComposerPolicyAfterCapabilityAuthorization()
                    },
                    resume = {
                        if (postDeleteCoordinator.resumeStoredAuthorizationIfPresent()) {
                            reloadComposerPolicyAfterCapabilityAuthorization()
                        }
                    },
                ),

                AuthCapabilityAuthorizationOwner.POST_MANAGE to CapabilityCallbackRoute(
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
                ),

                AuthCapabilityAuthorizationOwner.COMPOSER_FLAIR to CapabilityCallbackRoute(
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
                ),

                AuthCapabilityAuthorizationOwner.COMPOSER_REQUIREMENTS to CapabilityCallbackRoute(
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
                ),
            ),
        )
        viewModelScope.launch {
            shared.capabilityAuthorizations.collect {
                reloadComposerPolicyAfterCapabilityAuthorization()
            }
        }
    }

    override fun onCleared() {
        composerPolicyCoordinator.cancelPending()
        super.onCleared()
    }
}
