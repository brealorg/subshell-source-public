package app.subshell.prototype.inbox

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.subshell.prototype.MainActivity
import app.subshell.prototype.R
import app.subshell.prototype.auth.AuthApplicationRuntimeFactory
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.shortcuts.LauncherShortcutTarget
import app.subshell.prototype.shortcuts.launcherShortcutTargetIntent
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/*
 * ISSUE253 (Google: conversation notifications): a private message or direct
 * chat notification is a conversation — MessagingStyle with the sender and the
 * last messages, a long-lived conversation shortcut (it opens the account's
 * Inbox, like the launcher's Inbox shortcut, so no launch key sits in a
 * shortcut) and a Reply field. Comment replies and mentions stay plain (owner,
 * 2026-10-25). With the app lock hiding notification content none of this is
 * added.
 *
 * The Reply action carries only the notification's event key and the launch
 * key; account, item and the message replied to are looked up in Subshell's
 * own records, so an app that can fire the action (a notification listener)
 * can only reply to a conversation Subshell itself notified about. The reply
 * is sent once by a worker and never re-sent (a lost answer is "outcome
 * unknown").
 */

internal data class ConversationReplyTarget(
    val eventKey: String,
    val accountStableId: String,
    val inboxItemId: String,
    val parentFullname: String,
    val channel: MessageReplyChannel,
    val notificationId: Int,
)

/** The conversation part of a private message or direct chat notification. */
internal data class ConversationNotificationContent(
    val title: String?,
    val messages: List<ConversationNotificationMessage>,
    val sender: String,
)

internal data class ConversationNotificationMessage(
    val sender: String?,
    val text: String,
    val epochMillis: Long,
)

private const val CONVERSATION_NOTIFICATION_MESSAGES = 5
private const val DIRECT_CHAT_ROOM_TITLE = "[direct chat room]"
private val PRIVATE_MESSAGE_FULLNAME = Regex("^t4_[0-9a-z]+$")

internal fun isDirectChatRoom(title: String): Boolean =
    title.trim().equals(DIRECT_CHAT_ROOM_TITLE, ignoreCase = true)

/** The message a reply answers: the newest canonical one in the thread, else the item. */
internal fun conversationReplyParentFullname(itemId: String, conversation: List<MessageBubble>): String? =
    conversation.asReversed()
        .asSequence()
        .map { message -> message.id.trim().lowercase(Locale.ROOT) }
        .firstOrNull(PRIVATE_MESSAGE_FULLNAME::matches)
        ?: itemId.trim().lowercase(Locale.ROOT).takeIf(PRIVATE_MESSAGE_FULLNAME::matches)

internal fun conversationNotificationContent(
    event: InboxNotificationEvent,
    nowEpochMillis: Long,
): ConversationNotificationContent {
    val messages = event.conversation
        .filter { message -> message.body.isNotBlank() }
        .takeLast(CONVERSATION_NOTIFICATION_MESSAGES)
        .map { message ->
            ConversationNotificationMessage(
                sender = message.sender.trim().takeUnless { message.outgoing },
                text = message.body.trim(),
                epochMillis = message.createdEpochSeconds?.times(1_000L) ?: nowEpochMillis,
            )
        }
        .ifEmpty {
            listOf(
                ConversationNotificationMessage(
                    sender = event.sender,
                    text = event.preview.ifBlank { "New message" },
                    epochMillis = nowEpochMillis,
                ),
            )
        }
    return ConversationNotificationContent(
        title = event.title.trim().takeUnless { title -> title.isEmpty() || isDirectChatRoom(title) },
        messages = messages,
        sender = event.sender.ifBlank { "Reddit" },
    )
}

private fun person(name: String): Person =
    Person.Builder()
        .setName(name)
        .setKey("reddit-user:${name.lowercase(Locale.ROOT)}")
        .build()

internal fun conversationMessagingStyle(content: ConversationNotificationContent): NotificationCompat.MessagingStyle =
    NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
        .setConversationTitle(content.title)
        .setGroupConversation(false)
        .also { style ->
            content.messages.forEach { message ->
                style.addMessage(message.text, message.epochMillis, message.sender?.let(::person))
            }
        }

internal fun conversationShortcutId(accountStableId: String, sender: String): String =
    "conversation:$accountStableId:${sender.lowercase(Locale.ROOT)}"

/** Publishes the long-lived conversation shortcut; false when the launcher refused it. */
internal fun pushConversationShortcut(context: Context, accountStableId: String, sender: String): Boolean =
    runCatching {
        ShortcutManagerCompat.pushDynamicShortcut(
            context,
            ShortcutInfoCompat.Builder(context, conversationShortcutId(accountStableId, sender))
                .setShortLabel(sender)
                .setLongLabel("Messages with $sender")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_inbox))
                .setIntent(
                    launcherShortcutTargetIntent(LauncherShortcutTarget.Inbox(accountStableId, requestId = 0L))
                        .setClass(context, MainActivity::class.java),
                )
                .setLongLived(true)
                .setPerson(person(sender))
                .build(),
        )
        true
    }.getOrDefault(false)

internal fun conversationReplyAction(context: Context, target: ConversationReplyTarget): NotificationCompat.Action {
    val intent = Intent(context, InboxReplyReceiver::class.java)
        .setAction(INBOX_REPLY_ACTION)
        .putExtra(INBOX_REPLY_EVENT_EXTRA, target.eventKey)
        .putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, NotificationLaunchKey.value(context))
    // RemoteInput fills the typed text in, so the intent must be mutable; it
    // names Subshell's own receiver and carries no routing that is trusted.
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        target.notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    return NotificationCompat.Action.Builder(R.drawable.ic_notification_subshell, "Reply", pendingIntent)
        .addRemoteInput(RemoteInput.Builder(INBOX_REPLY_TEXT_KEY).setLabel("Reply").build())
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        .setShowsUserInterface(false)
        .setAllowGeneratedReplies(false)
        .build()
}

/** The conversations Subshell notified about, by event key (ids only, no text). */
internal object ConversationReplyTargets {
    private const val PREFERENCES = "subshell_conversation_replies"
    private const val ORDER_KEY = "order"
    private const val KEPT = 64

    fun put(context: Context, target: ConversationReplyTarget) = synchronized(this) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val order = preferences.getString(ORDER_KEY, "").orEmpty()
            .split('\n')
            .filter { key -> key.isNotEmpty() && key != target.eventKey } + target.eventKey
        val dropped = order.dropLast(KEPT)
        preferences.edit()
            .apply { dropped.forEach { key -> remove("target:$key") } }
            .putString(
                "target:${target.eventKey}",
                listOf(
                    target.accountStableId,
                    target.inboxItemId,
                    target.parentFullname,
                    target.channel.name,
                    target.notificationId.toString(),
                ).joinToString("\n"),
            )
            .putString(ORDER_KEY, order.takeLast(KEPT).joinToString("\n"))
            .apply()
    }

    fun get(context: Context, eventKey: String): ConversationReplyTarget? = synchronized(this) {
        val fields = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString("target:$eventKey", null)
            ?.split('\n')
            ?.takeIf { values -> values.size == 5 }
            ?: return null
        ConversationReplyTarget(
            eventKey = eventKey,
            accountStableId = fields[0],
            inboxItemId = fields[1],
            parentFullname = fields[2],
            channel = runCatching { MessageReplyChannel.valueOf(fields[3]) }.getOrNull() ?: return null,
            notificationId = fields[4].toIntOrNull() ?: return null,
        )
    }
}

/**
 * Adds [text] to a shown conversation notification (from the user, or from
 * Subshell when [fromSubshell]) and posts it again quietly; the system's
 * "sending" spinner stops with it. A notification no longer shown stays gone.
 */
internal fun appendToConversationNotification(
    context: Context,
    target: ConversationReplyTarget,
    text: String,
    fromSubshell: Boolean,
) {
    val appContext = context.applicationContext
    val tag = notificationAccountTag(target.accountStableId)
    val shown = appContext.getSystemService(NotificationManager::class.java)
        ?.activeNotifications
        ?.firstOrNull { notification -> notification.tag == tag && notification.id == target.notificationId }
        ?.notification
        ?: return
    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown) ?: return
    style.addMessage(
        text,
        System.currentTimeMillis(),
        if (fromSubshell) Person.Builder().setName("subshell").build() else null,
    )
    runCatching {
        NotificationManagerCompat.from(appContext).notify(
            tag,
            target.notificationId,
            NotificationCompat.Builder(appContext, shown)
                .setStyle(style)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }
}

/** Takes the typed reply from the notification and hands it to [InboxReplyWorker]. */
class InboxReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != INBOX_REPLY_ACTION) return
        if (!NotificationLaunchKey.matches(context, intent.getStringExtra(NOTIFICATION_LAUNCH_KEY_EXTRA))) return
        val eventKey = intent.getStringExtra(INBOX_REPLY_EVENT_EXTRA)?.trim()?.takeIf(String::isNotEmpty) ?: return
        val target = ConversationReplyTargets.get(context, eventKey) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(INBOX_REPLY_TEXT_KEY)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return
        appendToConversationNotification(context, target, text, fromSubshell = false)
        InboxReplyWork.enqueue(
            context.applicationContext,
            workDataOf(
                INBOX_REPLY_EVENT_EXTRA to eventKey,
                INBOX_REPLY_TEXT_KEY to text,
                INBOX_REPLY_REQUEST_INPUT to UUID.randomUUID().toString(),
            ),
        )
    }
}

internal object InboxReplyWork {
    /** Replaced by tests, which do not run the worker. */
    var enqueue: (Context, Data) -> Unit = { context, input ->
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<InboxReplyWorker>().setInputData(input).build(),
        )
    }
}

/** Sends one notification reply for the account the notification belongs to, never twice. */
internal class InboxReplyWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val eventKey = inputData.getString(INBOX_REPLY_EVENT_EXTRA) ?: return Result.success()
        val text = inputData.getString(INBOX_REPLY_TEXT_KEY) ?: return Result.success()
        val requestId = inputData.getString(INBOX_REPLY_REQUEST_INPUT) ?: return Result.success()
        val target = ConversationReplyTargets.get(applicationContext, eventKey) ?: return Result.success()
        // A worker run again after the process died may already have sent it.
        if (!InboxReplyAttempts.claim(applicationContext, requestId)) {
            report(target, InboxReplyOutcome.UNKNOWN)
            return Result.success()
        }
        val outcome = try {
            send(target, text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.d(INBOX_REPLY_LOG_TAG, "failed ${error.javaClass.simpleName}")
            InboxReplyOutcome.UNKNOWN
        }
        report(target, outcome)
        return Result.success()
    }

    private suspend fun send(target: ConversationReplyTarget, text: String): InboxReplyOutcome {
        val runtime = AuthApplicationRuntimeFactory.create(applicationContext)
        if (inboxWorkerAccountSignedIn(runtime.accountSessionDirectory, target.accountStableId) != true) {
            return InboxReplyOutcome.NOT_SENT
        }
        val writer = runtime.messageReplyWriter ?: return InboxReplyOutcome.NOT_SENT
        return inboxReplyOutcome(
            writer.send(
                MessageReplyRequest(
                    accountStableId = target.accountStableId,
                    inboxItemId = target.inboxItemId,
                    parentFullname = target.parentFullname,
                    channel = target.channel,
                    text = text,
                ),
            ),
        )
    }

    private fun report(target: ConversationReplyTarget, outcome: InboxReplyOutcome) {
        val message = when (outcome) {
            InboxReplyOutcome.SENT -> return
            InboxReplyOutcome.NOT_SENT -> "Your reply was not sent. Open the conversation to try again."
            InboxReplyOutcome.UNKNOWN -> "Your reply may not have been sent. Open the conversation to check."
        }
        appendToConversationNotification(applicationContext, target, message, fromSubshell = true)
    }
}

internal enum class InboxReplyOutcome { SENT, NOT_SENT, UNKNOWN }

internal fun inboxReplyOutcome(result: AuthenticatedRedditMessageReplyResult): InboxReplyOutcome = when (result) {
    is AuthenticatedRedditMessageReplyResult.Accepted -> InboxReplyOutcome.SENT
    is AuthenticatedRedditMessageReplyResult.Failed ->
        if (result.kind == app.subshell.prototype.data.account.MessageReplyFailureKind.OUTCOME_UNKNOWN) {
            InboxReplyOutcome.UNKNOWN
        } else {
            InboxReplyOutcome.NOT_SENT
        }
}

/** One send per notification reply, also when WorkManager runs the worker again. */
internal object InboxReplyAttempts {
    private const val PREFERENCES = "subshell_conversation_reply_attempts"
    private const val KEPT = 32

    fun claim(context: Context, requestId: String): Boolean = synchronized(this) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val claimed = preferences.getString("claimed", "").orEmpty().split('\n').filter(String::isNotEmpty)
        if (requestId in claimed) return false
        preferences.edit()
            .putString("claimed", (claimed + requestId).takeLast(KEPT).joinToString("\n"))
            .commit()
    }
}

internal const val INBOX_REPLY_ACTION = "app.subshell.prototype.action.INBOX_NOTIFICATION_REPLY"
internal const val INBOX_REPLY_EVENT_EXTRA = "inbox_reply_event"
internal const val INBOX_REPLY_TEXT_KEY = "inbox_reply_text"
private const val INBOX_REPLY_REQUEST_INPUT = "inbox_reply_request"
private const val INBOX_REPLY_LOG_TAG = "SubshellInboxReply"
