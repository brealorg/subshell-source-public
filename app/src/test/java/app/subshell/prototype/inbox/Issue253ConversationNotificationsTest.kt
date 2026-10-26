package app.subshell.prototype.inbox

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.applock.AppLockSettingsStore
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE253: a private message or direct chat notification is a conversation
 * (MessagingStyle, a long-lived conversation shortcut, a Reply field); a
 * comment reply stays plain, and nothing of it shows while the app lock hides
 * notification content. The Reply action trusts only Subshell's own records,
 * and one reply is sent once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue253ConversationNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val message = InboxItem(
        id = "t4_first",
        kind = ActivityKind.MESSAGE,
        title = "About the beta",
        sender = "alice",
        age = "now",
        preview = "Is it out?",
        unread = true,
        conversation = listOf(
            MessageBubble(id = "t4_first", sender = "alice", age = "1h", body = "Hi", createdEpochSeconds = 100L),
            MessageBubble(id = "t4_second", sender = "me", age = "1h", body = "Hello", outgoing = true, createdEpochSeconds = 200L),
            MessageBubble(id = "t4_third", sender = "alice", age = "now", body = "Is it out?", createdEpochSeconds = 300L),
        ),
    )

    private val commentReply = InboxItem(
        id = "t1_reply",
        kind = ActivityKind.REPLY,
        title = "Reply to your comment",
        sender = "bob",
        age = "now",
        preview = "Nice",
        unread = true,
    )

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, true)
            .commit()
        InboxReplyWork.enqueue = { _, input -> enqueued += input.getString(INBOX_REPLY_TEXT_KEY).orEmpty() }
    }

    @After
    fun restoreWork() {
        InboxReplyWork.enqueue = defaultEnqueue
    }

    private val defaultEnqueue = InboxReplyWork.enqueue
    private val enqueued = mutableListOf<String>()

    private fun post(item: InboxItem) {
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = "account-1",
            items = listOf(item),
            locallySeenIds = emptySet(),
            newEventKeys = listOf(item.inboxBackgroundEventKey()),
        )
    }

    private fun shown(): Notification = manager.activeNotifications.single().notification

    private fun replyAction(notification: Notification): NotificationCompat.Action? =
        (0 until NotificationCompat.getActionCount(notification))
            .map { index -> NotificationCompat.getAction(notification, index)!! }
            .firstOrNull { action -> action.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY }

    @Test
    fun aMessageIsAConversationWithItsLastMessagesAShortcutAndAReplyField() {
        post(message)

        val notification = shown()
        val style = requireNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertEquals("About the beta", style.conversationTitle)
        assertEquals(listOf("Hi", "Hello", "Is it out?"), style.messages.map { it.text.toString() })
        // An outgoing message is the user's own (no sender person).
        assertNull(style.messages[1].person)
        assertEquals("alice", style.messages[2].person?.name)
        assertEquals(conversationShortcutId("account-1", "alice"), NotificationCompat.getShortcutId(notification))
        assertEquals(
            "Messages with alice",
            ShortcutManagerCompat.getDynamicShortcuts(context)
                .single { it.id == conversationShortcutId("account-1", "alice") }
                .longLabel
                .toString(),
        )
        assertNotNull(replyAction(notification)?.remoteInputs?.single())
        assertEquals(
            ConversationReplyTarget(
                eventKey = message.inboxBackgroundEventKey(),
                accountStableId = "account-1",
                inboxItemId = "t4_first",
                parentFullname = "t4_third",
                channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
                notificationId = manager.activeNotifications.single().id,
            ),
            ConversationReplyTargets.get(context, message.inboxBackgroundEventKey()),
        )
    }

    @Test
    fun aCommentReplyStaysPlain() {
        post(commentReply)

        val notification = shown()
        assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertNull(replyAction(notification))
    }

    @Test
    fun hiddenContentShowsNoConversation() {
        AppLockSettingsStore.write(context, AppLockSettingsStore.read(context).copy(enabled = true, hideNotificationContent = true))

        post(message)

        val notification = shown()
        assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertNull(replyAction(notification))
        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).none { it.id.startsWith("conversation:") })
    }

    private fun replyIntent(eventKey: String, launchKey: String?, text: String): Intent {
        val intent = Intent(context, InboxReplyReceiver::class.java)
            .setAction(INBOX_REPLY_ACTION)
            .putExtra(INBOX_REPLY_EVENT_EXTRA, eventKey)
            .apply { launchKey?.let { putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, it) } }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(INBOX_REPLY_TEXT_KEY).build()),
            intent,
            Bundle().apply { putCharSequence(INBOX_REPLY_TEXT_KEY, text) },
        )
        return intent
    }

    @Test
    fun aReplyShowsAtOnceAndIsHandedToTheWorker() {
        post(message)
        val eventKey = message.inboxBackgroundEventKey()
        InboxReplyReceiver().onReceive(context, replyIntent(eventKey, NotificationLaunchKey.value(context), "Yes, today"))

        val style = requireNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown()))
        assertTrue(style.messages.any { it.text.toString() == "Yes, today" && it.person == null })
        assertEquals(listOf("Yes, today"), enqueued)
    }

    @Test
    fun aReplyWithoutTheLaunchKeyOrForAConversationNotNotifiedIsDropped() {
        post(message)
        val before = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown())!!.messages.size

        InboxReplyReceiver().onReceive(context, replyIntent(message.inboxBackgroundEventKey(), launchKey = null, text = "x"))
        InboxReplyReceiver().onReceive(context, replyIntent("unknown-event", NotificationLaunchKey.value(context), "x"))

        assertEquals(before, NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown())!!.messages.size)
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun oneReplyIsSentOnceAndALostAnswerIsNotCalledSent() {
        assertTrue(InboxReplyAttempts.claim(context, "request-1"))
        assertFalse(InboxReplyAttempts.claim(context, "request-1"))

        val request = MessageReplyRequest("account-1", "t4_first", "t4_third", MessageReplyChannel.PRIVATE_MESSAGE_THREAD, "x")
        assertEquals(
            InboxReplyOutcome.SENT,
            inboxReplyOutcome(
                AuthenticatedRedditMessageReplyResult.Accepted(
                    request,
                    "x",
                    app.subshell.prototype.data.network.NetworkResponseMetadata(200, 0L, app.subshell.prototype.data.network.NetworkRateLimit(null, null, null), null),
                ),
            ),
        )
        assertEquals(
            InboxReplyOutcome.UNKNOWN,
            inboxReplyOutcome(AuthenticatedRedditMessageReplyResult.Failed(MessageReplyFailureKind.OUTCOME_UNKNOWN)),
        )
        assertEquals(
            InboxReplyOutcome.NOT_SENT,
            inboxReplyOutcome(AuthenticatedRedditMessageReplyResult.Failed(MessageReplyFailureKind.AUTHORIZATION_REQUIRED)),
        )
    }

    @Test
    fun theReplyGoesToTheNewestMessageAndADirectChatHasNoSubject() {
        assertEquals("t4_third", conversationReplyParentFullname(message.id, message.conversation))
        assertEquals("t4_only", conversationReplyParentFullname("t4_only", emptyList()))
        assertNull(conversationReplyParentFullname("chat-room-id", emptyList()))

        val chat = conversationNotificationContent(
            InboxNotificationEvent("k", "room", ActivityKind.MESSAGE, "[direct chat room]", "carol", "hey"),
            nowEpochMillis = 5L,
        )
        assertNull(chat.title)
        assertEquals(listOf(ConversationNotificationMessage("carol", "hey", 5L)), chat.messages)
    }
}
