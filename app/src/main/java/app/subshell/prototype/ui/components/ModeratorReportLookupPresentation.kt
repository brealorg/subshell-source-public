package app.subshell.prototype.ui.components

import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.ModeratorReportsUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind

// MODERATION02A2R2_LOADED_QUEUE_NOT_SESSION_HISTORY_V1
// These are navigation choices only. Selecting one still performs the existing
// exact-target read; no cached queue item can authorize a report write.
internal data class ModeratorReportLookupContent(
    val queueItems: List<RedditModeratorQueueItem>,
    val recentTargets: List<ModeratorReportTarget>,
    val queueMessage: String?,
)

internal fun moderatorReportLookupContent(
    accountStableId: String,
    moderation: RedditModerationUiState,
    reports: ModeratorReportsUiState,
): ModeratorReportLookupContent {
    if (!moderation.matchesAccount(accountStableId)) {
        return ModeratorReportLookupContent(
            emptyList(), emptyList(),
            "The queue is unavailable for this account. Open an item by link or ID.",
        )
    }
    val queueItems = if (moderation.queueLoaded) {
        moderation.queueItems.filter { item ->
            moderation.moderates(item.communityName) &&
                moderation.queueContainsContext(accountStableId, item.communityName) &&
                item.fullname.matches(Regex("t[13]_[a-z0-9]{1,32}")) &&
                ((item.kind == RedditModeratorQueueKind.POST) == item.fullname.startsWith("t3_"))
        }.distinctBy { it.fullname }
    } else {
        emptyList()
    }
    val queuedFullnames = queueItems.mapTo(mutableSetOf()) { it.fullname }
    val recentTargets = if (reports.accountStableId == accountStableId) {
        reports.recentTargets.filter { target ->
            target.communityName?.let(moderation::moderates) == true &&
                target.fullname !in queuedFullnames
        }.distinctBy { it.fullname }
    } else {
        emptyList()
    }
    val queueMessage = when {
        moderation.queueLoading -> "Loading the moderation queue…"
        moderation.queueFailureKind != null ->
            "The queue could not be refreshed. Any items shown are from the last successful load."
        !moderation.queueLoaded ->
            "No queue has been loaded. Open an item by link or ID, or return to the queue to refresh."
        queueItems.isEmpty() ->
            "No items from the loaded queue are available here. You can still open an item by link or ID."
        moderation.queueNextCursor != null ->
            "Showing loaded items only. More may be available in the moderation queue."
        else -> null
    }
    return ModeratorReportLookupContent(queueItems, recentTargets, queueMessage)
}

internal fun moderatorReportQuantity(count: Int): String =
    if (count == 1) "1 report" else "$count reports"
