package app.subshell.prototype.data.cache

import app.subshell.prototype.model.InboxItem

/**
 * INBOXSSOT01_REFRESH_KEEPS_THE_OLDER_PAGES_V1: the list after a refresh is
 * the fresh first page plus the stored items it does not reach, so pages the
 * reader already scrolled to stay.
 *
 * INBOXFAST01_A_REFRESH_IS_COMPLETE_ONLY_SINCE_ITS_OLDER_EDGE_V1: the fresh
 * page holds every item since [completeSinceEpochSeconds] (the later edge of
 * the inbox and sent pages). A stored item from that time on that is not on
 * the page is gone at Reddit or moved, and is dropped; an older one (or one
 * without a time) is kept, in time order. Joining at the oldest fresh item
 * instead cut the list at an old sent-only thread and lost every inbox item
 * between (53 on the owner's phone). When the stored list does not reach that
 * time, more items arrived than a page holds and the pages cannot be joined:
 * the fresh page stands alone with its own cursor. An unknown edge keeps
 * every stored item. The stored cursor is kept only while stored items
 * remain beside the fresh ones, since it points past their end. A fresh
 * message thread that is also stored keeps its older messages
 * ([mergeRefreshedInboxItem]).
 */
internal fun refreshedLiveInboxHead(
    storedItems: List<InboxItem>,
    storedCursor: String?,
    freshItems: List<InboxItem>,
    freshCursor: String?,
    completeSinceEpochSeconds: Long?,
    unreadItemIds: Set<String>? = null,
): Pair<List<InboxItem>, String?> {
    val storedById = storedItems.associateBy(InboxItem::id)
    val head = freshItems.map { item ->
        storedById[item.id]?.let { stored -> mergeRefreshedInboxItem(stored, item) } ?: item
    }
    val freshIds = freshItems.mapTo(hashSetOf(), InboxItem::id)
    val notOnThePage = storedItems.filterNot { item -> item.id in freshIds }
    val kept = if (completeSinceEpochSeconds == null) {
        notOnThePage
    } else {
        val storedReachesTheEdge = storedItems.any { item ->
            (item.createdEpochSeconds ?: 0L) >= completeSinceEpochSeconds
        }
        if (!storedReachesTheEdge) return head to freshCursor
        notOnThePage.filter { item ->
            val created = item.createdEpochSeconds
            created == null || created < completeSinceEpochSeconds
        }
    }
    if (kept.isEmpty()) return head to freshCursor
    // INBOXORDER01_STORED_ROWS_READ_ELSEWHERE_TURN_READ_V1: a kept row is not
    // on the fresh page, so its unread mark may be from before it was read on
    // another device; Reddit's whole unread list settles it.
    val settled = if (unreadItemIds == null) {
        kept
    } else {
        kept.map { item -> if (item.unread && item.id !in unreadItemIds) item.copy(unread = false) else item }
    }
    val ordered = (head + settled).sortedByDescending { item -> item.createdEpochSeconds ?: Long.MIN_VALUE }
    return ordered to storedCursor
}
