package app.subshell.prototype.inbox

import android.content.Context
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModmailUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// NOTIFY01A2_ACTIVITY_OWNED_RECONCILIATION_RUNTIME_V2
internal fun startNotificationReadReconciliation(
    scope: CoroutineScope,
    context: Context,
    inboxStates: StateFlow<RedditInboxUiState>,
    modmailStates: StateFlow<RedditModmailUiState>,
) {
    val appContext = context.applicationContext

    scope.launch(Dispatchers.IO) {
        inboxStates
            .map { state ->
                state.accountStableId to state.contentRevision
            }
            .distinctUntilChanged()
            .collectLatest {
                val state = inboxStates.value
                val account =
                    state.accountStableId
                        .orEmpty()
                        .trim()

                if (account.isEmpty()) {
                    return@collectLatest
                }

                InboxNotificationCenter.reconcileInboxReadState(
                    context = appContext,
                    accountStableId = account,
                    items = state.items,
                    locallySeenIds = state.locallySeenIds,
                    readMutationItemIds = state.readMutationItemIds,
                )
            }
    }

    scope.launch(Dispatchers.IO) {
        modmailStates
            .map { state ->
                state.accountStableId to state.contentRevision
            }
            .distinctUntilChanged()
            .collectLatest {
                val state = modmailStates.value
                val account =
                    state.accountStableId
                        .orEmpty()
                        .trim()

                if (account.isEmpty()) {
                    return@collectLatest
                }

                val conversations = buildList {
                    addAll(state.conversations)
                    state.thread
                        ?.conversation
                        ?.let(::add)
                }

                InboxNotificationCenter.reconcileModmailReadState(
                    context = appContext,
                    accountStableId = account,
                    conversations = conversations,
                )
            }
    }
}
