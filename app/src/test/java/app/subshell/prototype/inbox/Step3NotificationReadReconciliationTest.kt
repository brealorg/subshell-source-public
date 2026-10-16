package app.subshell.prototype.inbox

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.R
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
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
 * STEP3 (notify01a2): a notification leaves the shade once its item is read
 * on Reddit — only that account's, and not while a read is still on its way
 * (an optimistic read can roll back) unless the item was seen here. Modmail
 * works the same per conversation. Old untracked notifications are removed
 * at the first reconciliation. The app's reconciliation follows the live
 * inbox and Modmail states of the signed-in account, ignores a state without
 * an account and stops with its scope. Turning notifications off removes
 * everything shown and forgets the records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3NotificationReadReconciliationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val platformManager get() = context.getSystemService(NotificationManager::class.java)
    private val manager get() = shadowOf(platformManager)
    private val state get() = context.getSharedPreferences("subshell_inbox_notifications", Context.MODE_PRIVATE)
    private val runtimeJob = SupervisorJob()

    private fun item(id: String, unread: Boolean = true) = InboxItem(
        id = id,
        kind = ActivityKind.REPLY,
        title = "Reply $id",
        sender = "u/replier",
        age = "now",
        preview = "text of $id",
        unread = unread,
    )

    private fun modmail(id: String, unread: Boolean = true) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = "Subject $id",
        participantName = "fixture_user",
        preview = "modmail $id",
        lastUpdatedEpochSeconds = 300L,
        lastUnreadEpochSeconds = 300L,
        latestMessageId = "m$id",
        unread = unread,
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
        state.edit().clear().commit()
    }

    @After
    fun stopRuntime() {
        runtimeJob.cancel()
    }

    private fun postInbox(account: String, vararg items: InboxItem) {
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = account,
            items = items.toList(),
            locallySeenIds = emptySet(),
            newEventKeys = items.map(InboxItem::inboxBackgroundEventKey),
        )
    }

    private fun postModmail(account: String, vararg conversations: RedditModmailConversation) {
        InboxNotificationCenter.postNewModmailEvents(
            context = context,
            accountStableId = account,
            conversations = conversations.toList(),
            newEventKeys = conversations.map(RedditModmailConversation::modmailBackgroundEventKey),
        )
    }

    /** What is shown, as (account tag, text). */
    private fun shown(): Set<Pair<String?, String?>> =
        platformManager.activeNotifications.map { notification ->
            notification.tag to notification.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        }.toSet()

    private fun records(): Set<String> =
        state.getStringSet("posted_notification_records_v1", emptySet()).orEmpty()

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        return condition()
    }

    private val alice = "reddit:alice"
    private val bob = "reddit:bob"

    @Test
    fun anItemReadOnRedditLeavesOnlyThatAccountsShade() {
        postInbox(alice, item("t1_a"), item("t1_b"))
        postInbox(bob, item("t1_a"))
        assertEquals(3, platformManager.activeNotifications.size)

        val cancelled = InboxNotificationCenter.reconcileInboxReadState(
            context = context,
            accountStableId = alice,
            items = listOf(item("t1_a", unread = false), item("t1_b", unread = true)),
            locallySeenIds = emptySet(),
            readMutationItemIds = emptySet(),
        )

        assertEquals(1, cancelled)
        assertEquals(
            setOf(notificationAccountTag(alice) to "text of t1_b", notificationAccountTag(bob) to "text of t1_a"),
            shown(),
        )
        assertEquals(2, records().size)
    }

    @Test
    fun aReadStillOnItsWayKeepsTheNotificationUntilItSettles() {
        postInbox(alice, item("t1_a"))

        val pending = InboxNotificationCenter.reconcileInboxReadState(
            context = context,
            accountStableId = alice,
            items = listOf(item("t1_a", unread = true)),
            locallySeenIds = setOf("t1_a"),
            readMutationItemIds = setOf("t1_a"),
        )
        assertEquals(0, pending)
        assertEquals(1, platformManager.activeNotifications.size)

        val settled = InboxNotificationCenter.reconcileInboxReadState(
            context = context,
            accountStableId = alice,
            items = listOf(item("t1_a", unread = true)),
            locallySeenIds = setOf("t1_a"),
            readMutationItemIds = emptySet(),
        )
        assertEquals(1, settled)
        assertTrue(platformManager.activeNotifications.isEmpty())
    }

    @Test
    fun aReadModmailConversationLeavesOnlyThatAccountsShade() {
        postModmail(alice, modmail("abc1"), modmail("abc2"))
        postModmail(bob, modmail("abc1"))

        val cancelled = InboxNotificationCenter.reconcileModmailReadState(
            context = context,
            accountStableId = alice,
            conversations = listOf(modmail("abc1", unread = false), modmail("abc2", unread = true)),
        )

        assertEquals(1, cancelled)
        assertEquals(
            setOf(notificationAccountTag(alice) to "modmail abc2", notificationAccountTag(bob) to "modmail abc1"),
            shown(),
        )
    }

    @Test
    fun oldUntrackedNotificationsAreRemovedAtTheFirstReconciliation() {
        InboxNotificationCenter.ensureChannel(context)
        val legacy = NotificationCompat.Builder(context, INBOX_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_subshell)
            .setContentText("an old notification")
            .build()
        platformManager.notify(4242, legacy)
        state.edit().putStringSet("posted_notification_ids", setOf("4242")).commit()
        assertEquals(1, platformManager.activeNotifications.size)

        InboxNotificationCenter.reconcileInboxReadState(
            context = context,
            accountStableId = alice,
            items = emptyList(),
            locallySeenIds = emptySet(),
            readMutationItemIds = emptySet(),
        )

        assertTrue(platformManager.activeNotifications.none { it.id == 4242 })
        assertFalse(state.contains("posted_notification_ids"))
    }

    @Test
    fun theAppsReconciliationFollowsTheLiveStatesAndStopsWithItsScope() {
        val inbox = MutableStateFlow(RedditInboxUiState())
        val modmailStates = MutableStateFlow(RedditModmailUiState())
        startNotificationReadReconciliation(CoroutineScope(runtimeJob), context, inbox, modmailStates)

        postInbox(alice, item("t1_a"))
        inbox.value = RedditInboxUiState(
            accountStableId = alice,
            items = listOf(item("t1_a", unread = false)),
            contentRevision = 1,
        )
        assertTrue("a read item leaves the shade", waitUntil { platformManager.activeNotifications.isEmpty() })

        // A state without an account reconciles nothing.
        postInbox(alice, item("t1_c"))
        inbox.value = RedditInboxUiState(items = listOf(item("t1_c", unread = false)), contentRevision = 2)
        Thread.sleep(500)
        assertEquals(setOf(notificationAccountTag(alice) to "text of t1_c"), shown())

        // Modmail: a read conversation in the open thread counts too.
        postModmail(alice, modmail("abc1"))
        modmailStates.value = RedditModmailUiState(
            accountStableId = alice,
            thread = RedditModmailThread(conversation = modmail("abc1", unread = false), messages = emptyList()),
            contentRevision = 1,
        )
        assertTrue(
            "a read conversation leaves the shade",
            waitUntil { shown() == setOf(notificationAccountTag(alice) to "text of t1_c") },
        )

        // Stopped with its scope: a later read state changes nothing.
        runtimeJob.cancel()
        inbox.value = RedditInboxUiState(
            accountStableId = alice,
            items = listOf(item("t1_c", unread = false)),
            contentRevision = 3,
        )
        Thread.sleep(500)
        assertEquals(setOf(notificationAccountTag(alice) to "text of t1_c"), shown())
    }

    @Test
    fun turningNotificationsOffRemovesEverythingAndForgetsTheRecords() {
        postInbox(alice, item("t1_a"))
        postModmail(bob, modmail("abc1"))
        state.edit().putStringSet("posted_notification_ids", setOf("4242")).commit()
        assertEquals(2, platformManager.activeNotifications.size)

        InboxNotificationCenter.cancelPosted(context)

        assertTrue(platformManager.activeNotifications.isEmpty())
        assertFalse(state.contains("posted_notification_records_v1"))
        assertFalse(state.contains("posted_notification_ids"))
    }
}
