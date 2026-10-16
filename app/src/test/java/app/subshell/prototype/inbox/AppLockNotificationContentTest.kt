package app.subshell.prototype.inbox

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.applock.AppLockSettings
import app.subshell.prototype.applock.AppLockSettingsStore
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Issue #38: "Hide notification content" on the notifications Subshell actually posts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLockNotificationContentTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val reply = InboxItem(
        id = "t1_reply",
        kind = ActivityKind.REPLY,
        title = "Reply from u/replier",
        sender = "u/replier",
        age = "now",
        preview = "the secret reply text",
        unread = true,
    )

    private val modmail = RedditModmailConversation(
        id = "abc123",
        communityName = "subshell",
        subject = "Secret subject",
        participantName = "fixture_user",
        preview = "the secret modmail text",
        lastUpdatedEpochSeconds = 300L,
        lastUnreadEpochSeconds = 300L,
        latestMessageId = "m1",
        unread = true,
        isInternal = false,
        messageCount = 2,
    )

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, true)
            .commit()
    }

    private fun postBoth(lock: AppLockSettings): List<Notification> {
        AppLockSettingsStore.write(context, lock)
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = "account-1",
            items = listOf(reply),
            locallySeenIds = emptySet(),
            newEventKeys = listOf(reply.inboxBackgroundEventKey()),
        )
        InboxNotificationCenter.postNewModmailEvents(
            context = context,
            accountStableId = "account-1",
            conversations = listOf(modmail),
            newEventKeys = listOf(modmail.modmailBackgroundEventKey()),
        )
        return shadowOf(context.getSystemService(NotificationManager::class.java))
            .allNotifications
            .sortedBy { it.extras.getCharSequence(Notification.EXTRA_TITLE).toString() }
    }

    private fun Notification.text(): String? =
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.bigText(): String? =
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

    @Test
    fun withTheLockOnAndHidingChosenOnlyTheGenericTextIsPosted() {
        val posted = postBoth(AppLockSettings(enabled = true, hideNotificationContent = true))

        assertEquals(2, posted.size)
        assertEquals(
            listOf("New Reddit Inbox activity", "New Reddit Modmail"),
            posted.map { it.text() }.sortedBy { it },
        )
        posted.forEach { notification ->
            assertEquals("subshell", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertNull(notification.bigText())
        }
    }

    @Test
    fun withHidingOffTheContentIsShown() {
        val posted = postBoth(AppLockSettings(enabled = true, hideNotificationContent = false))

        assertEquals(
            listOf("the secret modmail text", "the secret reply text"),
            posted.map { it.text() }.sortedBy { it },
        )
    }

    @Test
    fun hidingDoesNothingWhileTheLockIsOff() {
        val posted = postBoth(AppLockSettings(enabled = false, hideNotificationContent = true))

        assertEquals(
            listOf("the secret modmail text", "the secret reply text"),
            posted.map { it.text() }.sortedBy { it },
        )
    }
}
