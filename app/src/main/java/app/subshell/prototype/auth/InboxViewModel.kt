package app.subshell.prototype.auth

import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import kotlinx.coroutines.flow.StateFlow
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

/**
 * AUTHSPLIT02_INBOX_VIEWMODEL_V1: the inbox area's state holder (Google: one
 * ViewModel per screen area): the inbox and its read marks and read
 * authorization, private message replies and direct chat (start and the live
 * session). They stay together because a reply and a started chat reload
 * this inbox. Moved out of AuthConnectViewModel verbatim; it shares the auth
 * runtime through [SharedAuthRuntime] and registers the inbox read
 * authorization's callback route there.
 */
internal class InboxViewModel internal constructor(
    private val shared: SharedAuthRuntime,
) : ViewModel() {
    private val runtime = shared.runtime
    private val application = shared.application

    companion object {
        val Factory = viewModelFactory {
            initializer { InboxViewModel(requireNotNull(this[AuthRuntimeKey])) }
        }
    }

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
        backgroundSyncKeeper = shared.inboxBackgroundSync,
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

    init {
        shared.registerCapabilityRoutes(
            mapOf(
                AuthCapabilityAuthorizationOwner.INBOX_READ to CapabilityCallbackRoute(
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
                ),
            ),
        )
    }
}
