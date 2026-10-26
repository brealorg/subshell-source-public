package app.subshell.prototype.auth


import android.app.Application
import app.subshell.prototype.inbox.forgetInboxNotificationsForAccount
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationRoutingKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.account.SharedPreferencesRedditFavoriteCommunityStore
import app.subshell.prototype.data.auth.isBrowserEndedWithoutCallbackToken

// PLAINVIEWMODEL01_NO_ANDROIDVIEWMODEL_V1: a plain ViewModel given the
// Application by its factory (Google: avoid AndroidViewModel, hand
// dependencies in through a ViewModelProvider.Factory).
internal class AuthConnectViewModel internal constructor(
    private val application: Application,
    // HARNESS02_RUNTIME_SEAM_V1: tests inject a runtime; production passes null.
    runtimeOverride: AuthApplicationRuntime?,
) : ViewModel() {
    constructor(application: Application) : this(application, null)

    companion object {
        val Factory = viewModelFactory {
            initializer { AuthConnectViewModel(requireNotNull(this[APPLICATION_KEY])) }
        }
    }

    // AUTHSTART01_RUNTIME_PREPARED_OFF_MAIN_V1: MainActivity has the runtime
    // built on IO first; building it here is the fallback.
    private val runtime = runtimeOverride
        ?: AuthRuntimePreparation.takePrepared()
        ?: AuthApplicationRuntimeFactory.create(application)

    // AUTHSPLIT01_ONE_OWNER_OF_THE_AUTH_RUNTIME_V1: the area ViewModels
    // (ModerationViewModel, …) share this runtime through their CreationExtras.
    internal val sharedRuntime = SharedAuthRuntime(runtime, application)

    init {
        AuthRuntimePreparation.liveViewModels.incrementAndGet()
    }
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

    init {
        viewModelScope.launch {
            runtime.accountSessionDirectory?.revision?.collect {
                onlineVisibilityCoordinator.accountChanged()
            }
        }
    }
    val accountSessionDirectory: AccountSessionDirectory?
        get() = runtime.accountSessionDirectory

    val redditUserAgent: String?
        get() = runtime.redditUserAgent

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
                    forgetInboxNotificationsForAccount(application, removed)
                    // REDDITFAV03_LOGOUT_FORGETS_REDDIT_FAVORITES_V1
                    SharedPreferencesRedditFavoriteCommunityStore(application)
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

    val uiState: StateFlow<ConnectAccountUiState>
        get() = connectFlow.uiState

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

    /** The route an area ViewModel registered; none yet leaves the callback stored for its resume. */
    private fun registeredCapabilityRoute(owner: AuthCapabilityAuthorizationOwner): CapabilityCallbackRoute =
        sharedRuntime.capabilityRoute(owner) ?: CapabilityCallbackRoute(handle = { _, _ -> }, resume = { })

    // SUBSHELL_AUTHCALLBACK01_OWNER_ROUTE_TABLE_V1
    private fun callbackOwnerRoute(
        authorizationOwner: AuthCapabilityAuthorizationOwner,
    ): CapabilityCallbackRoute =
        when (authorizationOwner) {
            // AUTHSPLIT05: the reading coordinators (comment writes, votes, saves,
            // follows, content safety, profile history) live in ReadingViewModel,
            // which registers their routes.
            AuthCapabilityAuthorizationOwner.COMMENT_EDIT,
            AuthCapabilityAuthorizationOwner.COMMENT_DELETE,
            AuthCapabilityAuthorizationOwner.COMMENT_WRITE,
            AuthCapabilityAuthorizationOwner.POST_VOTE,
            AuthCapabilityAuthorizationOwner.POST_SAVE,
            AuthCapabilityAuthorizationOwner.POST_FOLLOW,
            AuthCapabilityAuthorizationOwner.CONTENT_SAFETY,
            AuthCapabilityAuthorizationOwner.PROFILE_HISTORY -> registeredCapabilityRoute(authorizationOwner)

            // AUTHSPLIT03: the post composing coordinators live in
            // PostComposerViewModel, which registers their routes.
            AuthCapabilityAuthorizationOwner.TEXT_POST_WRITE,
            AuthCapabilityAuthorizationOwner.POST_EDIT,
            AuthCapabilityAuthorizationOwner.POST_DELETE,
            AuthCapabilityAuthorizationOwner.POST_MANAGE,
            AuthCapabilityAuthorizationOwner.COMPOSER_FLAIR,
            AuthCapabilityAuthorizationOwner.COMPOSER_REQUIREMENTS -> registeredCapabilityRoute(authorizationOwner)

            // AUTHSPLIT01: the moderation owners' coordinators live in
            // ModerationViewModel, which registers their routes.
            AuthCapabilityAuthorizationOwner.MODERATION_ACTION,
            AuthCapabilityAuthorizationOwner.MODERATION_REASON,
            AuthCapabilityAuthorizationOwner.MODMAIL_READ,
            AuthCapabilityAuthorizationOwner.MODERATION_LOG,
            AuthCapabilityAuthorizationOwner.MODERATION_USERS,
            AuthCapabilityAuthorizationOwner.MODERATION_TEAM -> registeredCapabilityRoute(authorizationOwner)

            // AUTHSPLIT04: the community coordinators live in CommunitiesViewModel,
            // which registers their routes.
            AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP,
            AuthCapabilityAuthorizationOwner.REDDIT_FAVORITE,
            AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
            AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE -> registeredCapabilityRoute(authorizationOwner)

            // AUTHSPLIT02: the inbox coordinator lives in InboxViewModel, which
            // registers its route.
            AuthCapabilityAuthorizationOwner.INBOX_READ -> registeredCapabilityRoute(authorizationOwner)

        }

    override fun onCleared() {
        AuthRuntimePreparation.liveViewModels.decrementAndGet()
        super.onCleared()
    }

}
