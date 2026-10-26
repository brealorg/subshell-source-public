package app.subshell.prototype.inbox

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECAUDIT06 (security audit #6): another app could send the exported
 * MainActivity an inbox or modmail "notification tap" with an account it can
 * work out from public data, and Subshell switched to that account, opened the
 * item and marked it read on Reddit. A tap now needs the launch key Subshell's
 * own notifications carry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit06NotificationLaunchKeyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun inboxTap() = Intent()
        .setAction(INBOX_NOTIFICATION_ACTION_OPEN)
        .putExtra(INBOX_NOTIFICATION_ACCOUNT_EXTRA, "reddit:redreader:t2_victim")
        .putExtra(INBOX_NOTIFICATION_ITEM_EXTRA, "t4_message")
        .putExtra(INBOX_NOTIFICATION_EVENT_EXTRA, "activity:t4_message")

    private fun modmailTap() = Intent()
        .setAction(MODMAIL_NOTIFICATION_ACTION_OPEN)
        .putExtra(MODMAIL_NOTIFICATION_ACCOUNT_EXTRA, "reddit:redreader:t2_victim")
        .putExtra(MODMAIL_NOTIFICATION_CONVERSATION_EXTRA, "abc1")
        .putExtra(MODMAIL_NOTIFICATION_EVENT_EXTRA, "modmail:abc1")

    @Test
    fun aTapWithoutSubshellsKeyIsDropped() {
        assertNull(takeInboxNotificationTarget(context, inboxTap()))
        assertNull(takeModmailNotificationTarget(context, modmailTap()))

        val guessed = inboxTap().putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, "00000000-0000-0000-0000-000000000000")
        assertNull(takeInboxNotificationTarget(context, guessed))
        assertFalse(guessed.hasExtra(NOTIFICATION_LAUNCH_KEY_EXTRA))
    }

    @Test
    fun aTapWithTheKeyOpensItsItem() {
        val key = NotificationLaunchKey.value(context)

        assertEquals(
            InboxNotificationTarget("reddit:redreader:t2_victim", "t4_message", "activity:t4_message"),
            takeInboxNotificationTarget(context, inboxTap().putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, key)),
        )
        assertEquals(
            ModmailNotificationTarget("reddit:redreader:t2_victim", "abc1", "modmail:abc1"),
            takeModmailNotificationTarget(context, modmailTap().putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, key)),
        )
    }

    @Test
    fun theKeyStaysTheSameForNotificationsPostedBeforeARestart() {
        val first = NotificationLaunchKey.value(context)

        assertEquals(36, first.length)
        assertEquals(first, NotificationLaunchKey.value(context))
        assertEquals(
            first,
            context.getSharedPreferences("subshell_notification_launch", 0).getString("launch_key_v1", null),
        )
    }
}
