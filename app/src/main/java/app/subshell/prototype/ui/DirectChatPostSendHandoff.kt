package app.subshell.prototype.ui

// SUBSHELL_MESSAGES02D5D_AUTHORITATIVE_POST_SEND_HANDOFF_V1

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.screens.ConversationDetailKind
import app.subshell.prototype.ui.screens.conversationDetailKind
import app.subshell.prototype.ui.screens.conversationParticipantUsername

internal fun directChatPostSendHandoffParticipantUsername(
    item: InboxItem,
): String? {
    if (item.kind != ActivityKind.MESSAGE) return null
    if (conversationDetailKind(item) != ConversationDetailKind.DIRECT_CHAT) return null
    return item.conversation
        .firstOrNull { message -> !message.outgoing }
        ?.sender
        ?.let(::conversationParticipantUsername)
        ?: conversationParticipantUsername(item.sender)
}

internal fun resolveDirectChatPostSendHandoffItem(
    participantUsername: String,
    items: List<InboxItem>,
): InboxItem? {
    val participant = conversationParticipantUsername(participantUsername)
        ?: return null
    return items.firstOrNull { item ->
        directChatPostSendHandoffParticipantUsername(item)
            ?.equals(participant, ignoreCase = true) == true
    }
}
