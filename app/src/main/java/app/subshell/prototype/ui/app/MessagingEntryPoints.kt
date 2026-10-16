package app.subshell.prototype.ui.app

import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.MessageReplyUiState

/**
 * The messaging runtime as SubshellApp receives it from MainActivity: the
 * message-reply and direct-chat live UI states, the reply entry points and
 * the direct-chat live/typing/delete/react entry points with their failure
 * resets. Property names and defaults are the former SubshellApp parameters,
 * unchanged.
 */
data class MessagingEntryPoints(
    val messageReplyState: MessageReplyUiState = MessageReplyUiState(),
    val directChatLiveState: DirectChatLiveUiState = DirectChatLiveUiState(),
    val onSendRedditMessageReply: (MessageReplyRequest) -> Unit = {},
    val onResetRedditMessageReplyState: () -> Unit = {},
    val onStartRedditDirectChatLive: (String, String, String) -> Unit = { _, _, _ -> },
    val onStopRedditDirectChatLive: (String, String) -> Unit = { _, _ -> },
    val onUpdateRedditDirectChatTyping: (String, String, String, Boolean) -> Unit =
        { _, _, _, _ -> },
    val onDeleteRedditDirectChatMessage: (String, String, String, String) -> Unit =
        { _, _, _, _ -> },
    val onResetRedditDirectChatDeleteFailure: (String, String) -> Unit = { _, _ -> },
    val onReactRedditDirectChatMessage: (String, String, String, String, String) -> Unit =
        { _, _, _, _, _ -> },
    val onResetRedditDirectChatReactionFailure: (String, String) -> Unit = { _, _ -> },
)
