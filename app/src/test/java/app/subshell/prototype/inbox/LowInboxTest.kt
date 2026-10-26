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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * INBOX1 (low inbox findings): a stale background baseline is retaken
 * silently (OCB-557), read Modmail leaves
 * the shade from the background (OCB-561), Modmail is not read with its
 * notifications off (OCB-562), and hiding content redacts what is shown
 * (OCB-563).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowInboxTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private val reply = InboxItem(
        id = "t1_reply",
        kind = ActivityKind.REPLY,
        title = "Reply from u/replier",
        sender = "u/replier",
        age = "now",
        preview = "the secret reply text",
        unread = true,
    )

    private fun modmail(id: String) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = "Secret subject",
        participantName = "fixture_user",
        preview = "the secret modmail text",
        lastUpdatedEpochSeconds = 300L,
        lastUnreadEpochSeconds = 300L,
        latestMessageId = "m$id",
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

    @Test
    fun aBaselineOlderThanTwoHoursIsRetakenSilently() {
        var now = 1_000_000_000L
        val store = InboxBackgroundEventStore(context) { now }

        assertFalse(store.reconcile("acct", listOf("a")).initializedBefore)
        now += 15L * 60L * 1_000L
        val fresh = store.reconcile("acct", listOf("a", "b"))
        assertTrue(fresh.initializedBefore)
        assertEquals(listOf("b"), fresh.newEventKeys)

        now += 3L * 60L * 60L * 1_000L
        val stale = store.reconcile("acct", listOf("a", "b", "c", "d"))
        assertFalse("weeks of other activity are not announced", stale.initializedBefore)

        store.reset("acct")
        assertFalse(store.reconcile("acct", listOf("a")).initializedBefore)
    }

    @Test
    fun aReadModmailConversationLeavesTheShadeWhenTheUnreadListIsComplete() {
        InboxNotificationCenter.postNewModmailEvents(
            context = context,
            accountStableId = "acct",
            conversations = listOf(modmail("abc1"), modmail("abc2")),
            newEventKeys = listOf(modmail("abc1").modmailBackgroundEventKey(), modmail("abc2").modmailBackgroundEventKey()),
        )
        assertEquals(2, manager.allNotifications.size)

        val full = InboxNotificationCenter.reconcileModmailUnreadList(
            context, "acct", listOf(modmail("abc2")), pageLimit = 1,
        )
        assertEquals("a full page proves nothing", 0, full)

        val cancelled = InboxNotificationCenter.reconcileModmailUnreadList(context, "acct", listOf(modmail("abc2")))
        assertEquals(1, cancelled)
        assertEquals(1, manager.allNotifications.size)
    }

    // STEP3 (modmail06): a Modmail notification uses the inbox channel and
    // its tap opens that exact conversation once.
    @Test
    fun aModmailNotificationUsesTheInboxChannelAndItsTapOpensTheConversationOnce() {
        val eventKey = modmail("abc1").modmailBackgroundEventKey()
        InboxNotificationCenter.postNewModmailEvents(
            context = context,
            accountStableId = "acct",
            conversations = listOf(modmail("abc1")),
            newEventKeys = listOf(eventKey),
        )

        val notification = manager.allNotifications.single()
        assertEquals(INBOX_NOTIFICATION_CHANNEL_ID, notification.channelId)
        val tap = shadowOf(notification.contentIntent).savedIntent

        assertEquals(ModmailNotificationTarget("acct", "abc1", eventKey), takeModmailNotificationTarget(context, tap))
        assertEquals("a tap is taken once", null, takeModmailNotificationTarget(context, tap))
    }

    // STEP3 (modmail06): the inbox and Modmail keep separate background
    // histories, so the first Modmail check announces nothing old even after
    // the inbox was checked (and the other way round).
    @Test
    fun inboxAndModmailKeepSeparateBackgroundHistories() {
        val store = InboxBackgroundEventStore(context) { 1_000_000_000L }
        store.reconcile("acct", listOf("i1"))
        val modmailFirst = store.reconcileModmail("acct", listOf("modmail:abc1:m1"))
        assertFalse(modmailFirst.initializedBefore)
        assertFalse(modmailFirst.retainedEventKeys.contains("i1"))

        val other = InboxBackgroundEventStore(context) { 1_000_000_000L }
        other.reconcileModmail("acct2", listOf("modmail:abc1:m1"))
        val inboxFirst = other.reconcile("acct2", listOf("i1"))
        assertFalse(inboxFirst.initializedBefore)
        assertFalse(inboxFirst.retainedEventKeys.contains("modmail:abc1:m1"))
    }

    @Test
    fun modmailIsNotWantedWithItsNotificationsOff() {
        assertTrue(InboxNotificationCenter.modmailNotificationsWanted(context))
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SubshellSettings.NOTIFY_MODMAIL, false)
            .commit()
        assertFalse(InboxNotificationCenter.modmailNotificationsWanted(context))
    }

    @Test
    fun turningOnHiddenContentRedactsWhatIsAlreadyShown() {
        AppLockSettingsStore.write(context, AppLockSettings(enabled = true, hideNotificationContent = false))
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = "acct",
            items = listOf(reply),
            locallySeenIds = emptySet(),
            newEventKeys = listOf(reply.inboxBackgroundEventKey()),
        )
        assertEquals("the secret reply text", manager.allNotifications.single().text())

        AppLockSettingsStore.write(context, AppLockSettings(enabled = true, hideNotificationContent = true))

        val shown = manager.allNotifications.single()
        assertEquals("New Reddit Inbox activity", shown.text())
        assertEquals("subshell", shown.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    private fun Notification.text(): String? =
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
}
