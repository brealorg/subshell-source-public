package app.subshell.prototype.inbox

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * INBOX08 (#147): logging out of an account left its inbox notifications
 * (with message previews) in the shade and on the lock screen for good, kept
 * their records, and kept its background baseline. Logout now cancels that
 * account's notifications and records and resets its baseline; another
 * account's notifications stay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Inbox08LogoutForgetsNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, true)
            .commit()
    }

    private fun post(account: String, id: String) {
        val item = InboxItem(
            id = id, kind = ActivityKind.REPLY, title = "Reply $id", sender = "u/replier",
            age = "now", preview = "private text $id", unread = true,
        )
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = account,
            items = listOf(item),
            locallySeenIds = emptySet(),
            newEventKeys = listOf(item.inboxBackgroundEventKey()),
        )
    }

    private fun shownTags(): List<String?> = manager.activeNotifications.map { it.tag }

    private fun storedRecords(): Int =
        context.getSharedPreferences("subshell_inbox_notifications", Context.MODE_PRIVATE)
            .getStringSet("posted_notification_records_v1", emptySet()).orEmpty().size

    @Test
    fun logoutCancelsOnlyThatAccountsNotificationsRecordsAndBaseline() {
        post("account-out", "t1_a")
        post("account-stays", "t1_b")
        val store = InboxBackgroundEventStore(context)
        store.reconcile("account-out", listOf("event-1"))
        store.reconcile("account-stays", listOf("event-1"))
        val baselines = context.getSharedPreferences("subshell_inbox_background", Context.MODE_PRIVATE)
        val bothBaselines = baselines.all.keys.toSet()
        assertEquals(2, storedRecords())

        forgetInboxNotificationsForAccount(context, "account-out")

        assertEquals(listOf(notificationAccountTag("account-stays")), shownTags().distinct())
        assertEquals(1, storedRecords())
        val left = baselines.all.keys.toSet()
        assertEquals(bothBaselines.size / 2, left.size)
        store.reset("account-stays")
        assertEquals(emptySet<String>(), baselines.all.keys.toSet())
    }
}
