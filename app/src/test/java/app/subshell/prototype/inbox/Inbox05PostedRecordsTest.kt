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
 * INBOX05 / INBOX07 (#148): a posted-notification record whose notification is no longer
 * shown (swiped away, cleared) is dropped when the next one is posted, so the
 * stored set does not grow without bound.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Inbox05PostedRecordsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun reply(id: String) = InboxItem(
        id = id,
        kind = ActivityKind.REPLY,
        title = "Reply $id",
        sender = "u/replier",
        age = "now",
        preview = "text $id",
        unread = true,
    )

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, true)
            .commit()
    }

    private fun post(item: InboxItem) {
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = "account-1",
            items = listOf(item),
            locallySeenIds = emptySet(),
            newEventKeys = listOf(item.inboxBackgroundEventKey()),
        )
    }

    private fun storedRecords(): Int =
        context.getSharedPreferences("subshell_inbox_notifications", Context.MODE_PRIVATE)
            .getStringSet("posted_notification_records_v1", emptySet())
            .orEmpty()
            .size

    @Test
    fun aRecordOfASwipedAwayNotificationIsDroppedAtTheNextRun() {
        post(reply("t1_first"))
        assertEquals(1, storedRecords())

        // The user swipes it away; the next background run prunes first.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        InboxNotificationCenter.pruneRecordsOfNotificationsNotShown(context)
        post(reply("t1_second"))

        assertEquals(1, storedRecords())
    }

    // INBOX07 (#148): the system lists a posted notification as active only a
    // moment later, so posting must not prune records of the same run.
    @Test
    fun aNotificationNotYetListedKeepsItsRecordWhenTheRunPostsTheNextOne() {
        post(reply("t1_first"))
        // Not listed as active yet (NotificationManagerService adds it later).
        context.getSystemService(NotificationManager::class.java).cancelAll()
        post(reply("t1_second"))

        assertEquals(2, storedRecords())
    }

    @Test
    fun aRecordOfAShownNotificationIsKept() {
        post(reply("t1_first"))
        post(reply("t1_second"))

        assertEquals(2, storedRecords())
    }
}
