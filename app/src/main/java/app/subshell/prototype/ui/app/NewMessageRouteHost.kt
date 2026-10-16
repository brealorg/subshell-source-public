package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.DirectChatStartPhase
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.composerActionsAtStart
import app.subshell.prototype.ui.resolveDirectChatPostSendHandoffItem
import app.subshell.prototype.ui.screens.NewMessageScreen
import app.subshell.prototype.ui.uiDebugLog
import kotlinx.coroutines.delay

/**
 * The new-message route: the people search, the composer screen and the
 * post-send handoff that turns a started direct chat into the conversation
 * route (SUBSHELL_MESSAGES02D5D). The guard that selects this route stays in
 * SubshellApp, because it ends the composition for every other surface.
 *
 * Moved out of SubshellApp verbatim; the discovery and inbox entry points
 * arrive as the bundles SubshellApp receives.
 */
@Composable
internal fun NewMessageRouteHost(
    state: SubshellState,
    redditCommunity: CommunityEntryPoints,
    redditInbox: InboxEntryPoints,
    directChatStartState: DirectChatStartUiState,
    onStartRedditDirectChat: (DirectChatStartRequest) -> Unit,
    onResetRedditDirectChatStartState: () -> Unit,
) {
    val redditCommunityDiscoveryState = redditCommunity.redditCommunityDiscoveryState
    val onLoadRedditCommunityDiscovery = redditCommunity.onLoadRedditCommunityDiscovery
    val redditInboxState = redditInbox.redditInboxState
    val onLoadRedditInbox = redditInbox.onLoadRedditInbox
    val newMessagePeopleSearch =
        redditCommunityDiscoveryState.peopleSearch
    // SUBSHELL_MESSAGES02D5D_AUTHORITATIVE_POST_SEND_HANDOFF_V1
    var directChatHandoffRefreshRevision by remember(
        state.activeAccountId,
    ) {
        mutableStateOf<Long?>(null)
    }
    LaunchedEffect(
        directChatStartState.phase,
        directChatStartState.completionRevision,
        directChatStartState.accountStableId,
        directChatStartState.participantUsername,
        redditInboxState.accountStableId,
        redditInboxState.contentRevision,
        redditInboxState.loading,
        directChatHandoffRefreshRevision,
    ) {
        if (
            directChatStartState.phase !=
            DirectChatStartPhase.SUCCEEDED
        ) {
            return@LaunchedEffect
        }

        val account = directChatStartState.accountStableId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return@LaunchedEffect
        val participant = directChatStartState.participantUsername
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return@LaunchedEffect
        if (account != state.activeAccountId) {
            return@LaunchedEffect
        }

        val authoritativeItem =
            if (redditInboxState.matches(account)) {
                resolveDirectChatPostSendHandoffItem(
                    participantUsername = participant,
                    items = redditInboxState.items,
                )
            } else {
                null
            }
        if (authoritativeItem != null) {
            if (
                state.replaceCurrentNewMessageWithConversation(
                    authoritativeItem,
                )
            ) {
                uiDebugLog(
                    "subshell-direct-chat-handoff",
                    "result=accepted source=authoritative_inbox",
                )
                onResetRedditDirectChatStartState()
            }
            return@LaunchedEffect
        }

        if (redditInboxState.loading) {
            return@LaunchedEffect
        }

        val revision = directChatStartState.completionRevision
        if (directChatHandoffRefreshRevision != revision) {
            // The ViewModel starts one authoritative Inbox refresh immediately after
            // accepted send. Give that synchronous transition a short chance to become
            // visible before issuing the single bounded extra read-only refresh.
            delay(750L)
            directChatHandoffRefreshRevision = revision
            uiDebugLog(
                "subshell-direct-chat-handoff",
                "result=refresh_requested source=authoritative_inbox",
            )
            onLoadRedditInbox(account, true)
            return@LaunchedEffect
        }

        // Any Inbox state change cancels/restarts this effect. Reaching the end of this
        // delay therefore means the bounded refresh settled without a matching
        // authoritative Direct Chat projection.
        delay(1_500L)
        uiDebugLog(
            "subshell-direct-chat-handoff",
            "result=fallback_inbox reason=authoritative_item_missing",
        )
        onResetRedditDirectChatStartState()
        state.closeSecondary()
    }
    NewMessageScreen(
        accountStableId = state.activeAccountId,
        authenticated =
            state.activeAccountIsAuthenticated,
        peopleSearchQuery =
            newMessagePeopleSearch.searchQuery.orEmpty(),
        peopleSearchItems =
            newMessagePeopleSearch.items,
        peopleSearchLoading =
            newMessagePeopleSearch.loading,
        peopleSearchLoaded =
            newMessagePeopleSearch.loaded,
        directChatStartState =
            directChatStartState,
        onSearchPeople = { query ->
            onLoadRedditCommunityDiscovery(
                state.activeAccountId,
                query,
                null,
                null,
                false,
            )
        },
        onStartDirectChat =
            onStartRedditDirectChat,
        onResetDirectChatStartState =
            onResetRedditDirectChatStartState,
        onRefreshInbox = {
            onLoadRedditInbox(
                state.activeAccountId,
                true,
            )
        },
        interactionActionsAtStart =
            state.interactionSide.composerActionsAtStart(),
        consumePrefill = state::consumeNewMessagePrefill,
        prefillPending = state.hasPendingNewMessagePrefill,
        onBack = state::closeSecondary,
    )
}
