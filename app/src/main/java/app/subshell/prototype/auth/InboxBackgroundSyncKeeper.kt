package app.subshell.prototype.auth

import android.app.Application
import app.subshell.prototype.inbox.InboxBackgroundSyncScheduler
import app.subshell.prototype.inbox.inboxNotificationsEnabled

/**
 * The inbox's background sync schedule, kept by the inbox and modmail loads
 * alike. Moved out of InboxCoordinator verbatim so the moderation ViewModel
 * (modmail) and the inbox share one copy of what was last scheduled
 * (AUTHSPLIT01).
 */
internal class InboxBackgroundSyncKeeper(private val application: Application) {
    // LOW11_BACKGROUND_SYNC_SCHEDULED_ON_CHANGE_V1 (OCB-420/405): every inbox
    // (and modmail) load re-enqueued the periodic work. It is scheduled or
    // cancelled only when the account or the notification setting changed,
    // and never while notifications are off (modmail scheduled regardless).
    // The worker can cancel its own work (account gone), so an unchanged
    // schedule is still renewed after BACKGROUND_SYNC_RENEW_MS.
    private var scheduledBackgroundSync: Pair<String, Boolean>? = null
    private var scheduledBackgroundSyncAtMs = 0L

    fun keep(account: String) {
        if (account.isEmpty()) return
        // INBOX02_SYNC_ONLY_WITH_NOTIFICATIONS_V1: the background sync only
        // feeds notifications, so it runs only while they are on.
        val enabled = inboxNotificationsEnabled(application)
        val wanted = account to enabled
        val now = android.os.SystemClock.elapsedRealtime()
        if (scheduledBackgroundSync == wanted && now - scheduledBackgroundSyncAtMs < BACKGROUND_SYNC_RENEW_MS) return
        scheduledBackgroundSync = wanted
        scheduledBackgroundSyncAtMs = now
        if (enabled) {
            InboxBackgroundSyncScheduler.schedule(
                context = application,
                accountStableId = account,
            )
        } else {
            InboxBackgroundSyncScheduler.cancel(application)
        }
    }
}

private const val BACKGROUND_SYNC_RENEW_MS = 15L * 60L * 1_000L
