package app.subshell.prototype.inbox

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.MainActivity
import app.subshell.prototype.R
import app.subshell.prototype.applock.AppLockSettings
import app.subshell.prototype.applock.AppLockSettingsStore
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (notify01a1, notify01a1r1, notify01a1r2, inbox01b22, inbox01b23,
 * inbox01b231): a new inbox event becomes one notification on the single
 * "Inbox" channel, private on the lock screen (whose version says only that
 * there is new activity), with the app's glyph, grouped and tagged per
 * account, and its tap opens exactly that item once. Each notification type
 * follows its own setting; nothing is posted with notifications off, without
 * the permission, with the app's notifications blocked, or without an
 * account. Two accounts never share a notification or its tap. The app asks
 * for the notification permission in its manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3InboxNotificationPostingTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val platformManager get() = context.getSystemService(NotificationManager::class.java)
    private val manager get() = shadowOf(platformManager)

    private val reply = InboxItem(
        id = "t1_reply",
        kind = ActivityKind.REPLY,
        title = "Reply from u/replier",
        sender = "u/replier",
        age = "now",
        preview = "the reply text",
        unread = true,
    )
    private val mention = InboxItem(
        id = "t1_mention",
        kind = ActivityKind.MENTION,
        title = "Mention from u/mentioner",
        sender = "u/mentioner",
        age = "now",
        preview = "the mention text",
        unread = true,
    )

    private fun modmail(id: String) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = "A modmail subject",
        participantName = "fixture_user",
        preview = "the modmail text",
        lastUpdatedEpochSeconds = 300L,
        lastUnreadEpochSeconds = 300L,
        latestMessageId = "m$id",
        unread = true,
        isInternal = false,
        messageCount = 2,
    )

    private fun preferences() = context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        preferences().edit()
            .putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, true)
            .commit()
        context.getSharedPreferences("subshell_inbox_notifications", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun postInbox(account: String, vararg items: InboxItem): Int =
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = account,
            items = items.toList(),
            locallySeenIds = emptySet(),
            newEventKeys = items.map(InboxItem::inboxBackgroundEventKey),
        )

    private fun postModmail(account: String, vararg conversations: RedditModmailConversation): Int =
        InboxNotificationCenter.postNewModmailEvents(
            context = context,
            accountStableId = account,
            conversations = conversations.toList(),
            newEventKeys = conversations.map(RedditModmailConversation::modmailBackgroundEventKey),
        )

    private fun Notification.title(): String? = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text(): String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.allText(): String =
        listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        ).joinToString(" ") { key -> extras.getCharSequence(key)?.toString().orEmpty() }

    @Test
    fun aNewReplyIsOnePrivateNotificationOnTheInboxChannelWithTheGlyph() {
        assertEquals(1, postInbox("reddit:alice", reply))

        val notification = manager.allNotifications.single()
        assertEquals("subshell_inbox", notification.channelId)
        val channel = platformManager.getNotificationChannel("subshell_inbox")
        assertEquals("Inbox", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)

        assertEquals("Reply from u/replier", notification.title())
        assertEquals("the reply text", notification.text())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val lockScreen = requireNotNull(notification.publicVersion)
        assertEquals("subshell", lockScreen.title())
        assertEquals("New Reddit Inbox activity", lockScreen.text())
        assertFalse(lockScreen.allText().contains("the reply text"))

        assertEquals(R.drawable.ic_notification_subshell, notification.smallIcon.resId)
        assertEquals(R.drawable.ic_notification_subshell, lockScreen.smallIcon.resId)
        assertEquals(notificationAccountGroupKey("reddit:alice"), notification.group)
        assertEquals(
            listOf(notificationAccountTag("reddit:alice")),
            platformManager.activeNotifications.map { it.tag },
        )
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun modmailUsesTheSameChannelAndGlyphAlsoWhenContentIsHidden() {
        assertEquals(1, postModmail("reddit:alice", modmail("abc1")))
        assertEquals(1, postInbox("reddit:alice", reply))

        val modmailNotification = manager.allNotifications.first { it.title()?.startsWith("Modmail") == true }
        assertEquals("subshell_inbox", modmailNotification.channelId)
        assertEquals(R.drawable.ic_notification_subshell, modmailNotification.smallIcon.resId)
        assertEquals(R.drawable.ic_notification_subshell, modmailNotification.publicVersion.smallIcon.resId)
        assertEquals("New Reddit Modmail", modmailNotification.publicVersion.text())
        assertEquals(listOf("subshell_inbox"), platformManager.notificationChannels.map { it.id })

        // Turning on the app lock's hidden content re-posts what is shown, redacted, with the glyph.
        AppLockSettingsStore.write(context, AppLockSettings(enabled = true, hideNotificationContent = true))

        assertEquals(2, manager.allNotifications.size)
        manager.allNotifications.forEach { shown ->
            assertEquals("subshell", shown.title())
            assertEquals(R.drawable.ic_notification_subshell, shown.smallIcon.resId)
        }
    }

    @Test
    fun aTapOpensExactlyThatItemOnce() {
        postInbox("reddit:alice", reply)

        val tap = shadowOf(manager.allNotifications.single().contentIntent).savedIntent
        assertEquals(INBOX_NOTIFICATION_ACTION_OPEN, tap.action)
        assertEquals(MainActivity::class.java.name, tap.component?.className)
        assertEquals("reddit:alice", tap.getStringExtra(INBOX_NOTIFICATION_ACCOUNT_EXTRA))
        assertEquals("t1_reply", tap.getStringExtra(INBOX_NOTIFICATION_ITEM_EXTRA))
        assertEquals("activity:t1_reply", tap.getStringExtra(INBOX_NOTIFICATION_EVENT_EXTRA))
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)

        assertEquals(
            InboxNotificationTarget("reddit:alice", "t1_reply", "activity:t1_reply"),
            takeInboxNotificationTarget(context, tap),
        )
        assertNull("a tap is taken once", takeInboxNotificationTarget(context, tap))
    }

    @Test
    fun eachTypeFollowsItsOwnSetting() {
        val settings = SubshellSettings()
        assertTrue(settings.notifyReplies)
        assertTrue(settings.notifyMentions)
        assertTrue(settings.notifyPrivateMessages)
        assertTrue(settings.notifyRedditNotices)
        assertTrue(settings.notifyModmail)

        preferences().edit().putBoolean(SubshellSettings.NOTIFY_REPLIES, false).commit()
        assertEquals(1, postInbox("reddit:alice", reply, mention))
        assertEquals("the mention text", manager.allNotifications.single().text())

        preferences().edit().putBoolean(SubshellSettings.NOTIFY_MODMAIL, false).commit()
        assertEquals(0, postModmail("reddit:alice", modmail("abc1")))
        assertEquals(1, manager.allNotifications.size)
    }

    @Test
    fun nothingIsPostedWithNotificationsOff() {
        preferences().edit().putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, false).commit()

        assertEquals(0, postInbox("reddit:alice", reply))
        assertEquals(0, postModmail("reddit:alice", modmail("abc1")))
        assertTrue(manager.allNotifications.isEmpty())
    }

    @Test
    fun nothingIsPostedWithoutThePermissionABlockedAppOrAnAccount() {
        assertEquals(0, postInbox("  ", reply))

        manager.setNotificationsEnabled(false)
        assertEquals(0, postInbox("reddit:alice", reply))
        manager.setNotificationsEnabled(true)

        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(0, postInbox("reddit:alice", reply))
        assertTrue(manager.allNotifications.isEmpty())
    }

    @Test
    fun twoAccountsNeverShareANotificationOrItsTap() {
        assertEquals(1, postInbox("reddit:alice", reply))
        assertEquals(1, postInbox("reddit:bob", reply))

        val shown = platformManager.activeNotifications.toList()
        assertEquals(2, shown.size)
        assertEquals(
            setOf(notificationAccountTag("reddit:alice"), notificationAccountTag("reddit:bob")),
            shown.map { it.tag }.toSet(),
        )
        assertEquals(
            setOf(notificationAccountGroupKey("reddit:alice"), notificationAccountGroupKey("reddit:bob")),
            shown.map { it.notification.group }.toSet(),
        )
        val requestCodes = shown.map { shadowOf(it.notification.contentIntent).requestCode }
        assertNotEquals(requestCodes[0], requestCodes[1])
        assertEquals(
            setOf(
                notificationPendingIntentRequestCode("reddit:alice", "activity:t1_reply"),
                notificationPendingIntentRequestCode("reddit:bob", "activity:t1_reply"),
            ),
            requestCodes.toSet(),
        )
    }

    @Test
    fun theAppAsksForTheNotificationPermission() {
        @Suppress("DEPRECATION")
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()

        assertTrue(requested.toString(), Manifest.permission.POST_NOTIFICATIONS in requested)
    }
}
