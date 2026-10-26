package app.subshell.prototype.data.cache

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble

// INBOX04_APPEND_MERGES_MESSAGE_THREADS_V1: an appended page can carry more of
// a message thread that is already stored (e.g. stored from your recent
// replies as a sent-only thread, while the other side's older messages come
// on a later page). The thread keeps its place and gains those messages and
// their unread state instead of dropping them. Other activity kinds keep the
// stored item.
internal fun mergeAppendedInboxItem(existing: InboxItem, incoming: InboxItem): InboxItem {
    if (existing.kind != ActivityKind.MESSAGE || incoming.kind != ActivityKind.MESSAGE) return existing
    val conversation = (existing.conversation + incoming.conversation)
        .distinctBy(MessageBubble::id)
        .sortedWith(
            compareBy<MessageBubble> { bubble -> bubble.createdEpochSeconds ?: 0L }
                .thenBy { bubble -> bubble.id },
        )
    val header = if ((incoming.createdEpochSeconds ?: 0L) > (existing.createdEpochSeconds ?: 0L)) {
        incoming
    } else {
        existing
    }
    val peer = conversation.firstOrNull { bubble -> !bubble.outgoing }?.sender ?: header.sender
    return header.copy(
        sender = peer,
        unread = existing.unread || incoming.unread,
        conversation = conversation,
    )
}

/**
 * INBOXFAST01_A_REFRESHED_THREAD_KEEPS_ITS_OLDER_MESSAGES_V1: a refresh reads
 * only the first inbox and sent pages, so a stored message thread can hold
 * older messages than the fresh one carries (from the first load's history or
 * a later page). The thread keeps every message by id; its header and its
 * read state are Reddit's current ones. Other kinds take the fresh item.
 */
internal fun mergeRefreshedInboxItem(stored: InboxItem, fresh: InboxItem): InboxItem {
    if (stored.kind != ActivityKind.MESSAGE || fresh.kind != ActivityKind.MESSAGE) return fresh
    val conversation = (stored.conversation + fresh.conversation)
        .distinctBy(MessageBubble::id)
        .sortedWith(
            compareBy<MessageBubble> { bubble -> bubble.createdEpochSeconds ?: 0L }
                .thenBy { bubble -> bubble.id },
        )
    if (conversation.size == fresh.conversation.size) return fresh
    val peer = conversation.firstOrNull { bubble -> !bubble.outgoing }?.sender ?: fresh.sender
    return fresh.copy(sender = peer, conversation = conversation)
}
