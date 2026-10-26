package app.subshell.prototype.inbox

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.UUID
import app.subshell.prototype.MainActivity
import app.subshell.prototype.applock.AppLockSettingsStore
import app.subshell.prototype.applock.hidesNotificationContent
import app.subshell.prototype.R
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.normalizeRedditModmailId
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.reddit.MODMAIL_CONVERSATION_MAX_LIMIT
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.INBOX_NOTIFICATIONS_ON_BY_DEFAULT
import app.subshell.prototype.ui.SubshellSettings

data class InboxNotificationTarget(
    val accountStableId: String,
    val inboxItemId: String,
    val eventKey: String,
)

// MODMAIL06_SHARED_INBOX_NOTIFICATION_CENTER_V1
data class ModmailNotificationTarget(
    val accountStableId: String,
    val conversationId: String,
    val eventKey: String,
)

internal data class ModmailNotificationEvent(
    val eventKey: String,
    val conversationId: String,
    val communityName: String,
    val subject: String,
    val preview: String,
)

internal data class InboxNotificationEvent(
    val eventKey: String,
    val inboxItemId: String,
    val kind: ActivityKind,
    val title: String,
    val sender: String,
    val preview: String,
    // ISSUE253: a private message or direct chat, shown as a conversation.
    val conversation: List<app.subshell.prototype.model.MessageBubble> = emptyList(),
    val replyParentFullname: String? = null,
)

// NOTIFY01A1_GRANULAR_NOTIFICATION_POLICY_V1
internal data class InboxNotificationPolicy(
    val replies: Boolean = true,
    val mentions: Boolean = true,
    val privateMessages: Boolean = true,
    val redditNotices: Boolean = true,
    val modmail: Boolean = true,
)

internal fun InboxNotificationPolicy.allows(kind: ActivityKind): Boolean = when (kind) {
    ActivityKind.REPLY,
    ActivityKind.POST_REPLY,
    -> replies

    ActivityKind.MENTION -> mentions
    ActivityKind.MESSAGE -> privateMessages
    ActivityKind.MOD -> redditNotices
}

internal fun inboxNotificationTarget(
    action: String?,
    accountStableId: String?,
    inboxItemId: String?,
    eventKey: String?,
): InboxNotificationTarget? {
    if (action != INBOX_NOTIFICATION_ACTION_OPEN) return null
    val account = accountStableId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val item = inboxItemId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val event = eventKey?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return InboxNotificationTarget(
        accountStableId = account,
        inboxItemId = item,
        eventKey = event,
    )
}

internal fun takeInboxNotificationTarget(context: Context, intent: Intent?): InboxNotificationTarget? {
    val candidate = intent ?: return null
    if (!takeNotificationLaunchKey(context, candidate)) return null
    val target = inboxNotificationTarget(
        action = candidate.action,
        accountStableId = candidate.getStringExtra(INBOX_NOTIFICATION_ACCOUNT_EXTRA),
        inboxItemId = candidate.getStringExtra(INBOX_NOTIFICATION_ITEM_EXTRA),
        eventKey = candidate.getStringExtra(INBOX_NOTIFICATION_EVENT_EXTRA),
    ) ?: return null
    candidate.removeExtra(INBOX_NOTIFICATION_ACCOUNT_EXTRA)
    candidate.removeExtra(INBOX_NOTIFICATION_ITEM_EXTRA)
    candidate.removeExtra(INBOX_NOTIFICATION_EVENT_EXTRA)
    candidate.action = null
    return target
}

internal fun modmailNotificationTarget(
    action: String?,
    accountStableId: String?,
    conversationId: String?,
    eventKey: String?,
): ModmailNotificationTarget? {
    if (action != MODMAIL_NOTIFICATION_ACTION_OPEN) return null
    val account = accountStableId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val conversation = conversationId
        ?.let { raw ->
            runCatching { normalizeRedditModmailId(raw) }.getOrNull()
        }
        ?: return null
    val event = eventKey?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return ModmailNotificationTarget(
        accountStableId = account,
        conversationId = conversation,
        eventKey = event,
    )
}

internal fun takeModmailNotificationTarget(context: Context, intent: Intent?): ModmailNotificationTarget? {
    val candidate = intent ?: return null
    if (!takeNotificationLaunchKey(context, candidate)) return null
    val target = modmailNotificationTarget(
        action = candidate.action,
        accountStableId = candidate.getStringExtra(MODMAIL_NOTIFICATION_ACCOUNT_EXTRA),
        conversationId = candidate.getStringExtra(MODMAIL_NOTIFICATION_CONVERSATION_EXTRA),
        eventKey = candidate.getStringExtra(MODMAIL_NOTIFICATION_EVENT_EXTRA),
    ) ?: return null
    candidate.removeExtra(MODMAIL_NOTIFICATION_ACCOUNT_EXTRA)
    candidate.removeExtra(MODMAIL_NOTIFICATION_CONVERSATION_EXTRA)
    candidate.removeExtra(MODMAIL_NOTIFICATION_EVENT_EXTRA)
    candidate.action = null
    return target
}

internal fun inboxNotificationEvents(
    items: List<InboxItem>,
    locallySeenIds: Set<String>,
    newEventKeys: List<String>,
    policy: InboxNotificationPolicy = InboxNotificationPolicy(),
): List<InboxNotificationEvent> {
    val pending = newEventKeys
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    if (pending.isEmpty()) return emptyList()

    return items.mapNotNull { item ->
        if (!item.unread || item.id in locallySeenIds) return@mapNotNull null
        if (!policy.allows(item.kind)) return@mapNotNull null
        val eventKey = item.inboxBackgroundEventKey()
        if (eventKey !in pending) return@mapNotNull null
        InboxNotificationEvent(
            eventKey = eventKey,
            inboxItemId = item.id,
            kind = item.kind,
            title = item.title.trim(),
            sender = item.sender.trim(),
            preview = item.preview.trim(),
            conversation = item.conversation.takeIf { item.kind == ActivityKind.MESSAGE }.orEmpty(),
            replyParentFullname = item.takeIf { it.kind == ActivityKind.MESSAGE }
                ?.let { message -> conversationReplyParentFullname(message.id, message.conversation) },
        )
    }
}

internal fun modmailNotificationEvents(
    conversations: List<RedditModmailConversation>,
    newEventKeys: List<String>,
    policy: InboxNotificationPolicy = InboxNotificationPolicy(),
): List<ModmailNotificationEvent> {
    if (!policy.modmail) return emptyList()
    val pending = newEventKeys
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    if (pending.isEmpty()) return emptyList()

    return conversations.mapNotNull { conversation ->
        if (!conversation.unread) return@mapNotNull null
        val eventKey = conversation.modmailBackgroundEventKey()
        if (eventKey !in pending) return@mapNotNull null
        ModmailNotificationEvent(
            eventKey = eventKey,
            conversationId = conversation.id,
            communityName = conversation.communityName,
            subject = conversation.subject.trim(),
            preview = conversation.preview.trim(),
        )
    }
}


internal object InboxNotificationCenter {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
            ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                INBOX_NOTIFICATION_CHANNEL_ID,
                "Inbox",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New Reddit activity, private messages, and Modmail found by background Inbox sync"
            },
        )
    }

    fun canPost(context: Context): Boolean {
        val appContext = context.applicationContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    fun postNewEvents(
        context: Context,
        accountStableId: String,
        items: List<InboxItem>,
        locallySeenIds: Set<String>,
        newEventKeys: List<String>,
    ): Int {
        val appContext = context.applicationContext
        val account = accountStableId.trim().takeIf(String::isNotEmpty)
            ?: return 0
        if (!notificationsEnabled(appContext) || !canPost(appContext)) return 0
        val policy = notificationPolicy(appContext)
        val events = inboxNotificationEvents(
            items = items,
            locallySeenIds = locallySeenIds,
            newEventKeys = newEventKeys,
            policy = policy,
        )
        if (events.isEmpty()) return 0

        ensureChannel(appContext)
        val manager = NotificationManagerCompat.from(appContext)
        // ISSUE38_APP_LOCK_V1
        val hideContent = AppLockSettingsStore.read(appContext).hidesNotificationContent
        var postedCount = 0

        events.forEach { event ->
            val notificationId = stableNotificationId(event.eventKey)
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationPendingIntentRequestCode(
                    accountStableId = account,
                    eventKey = event.eventKey,
                ),
                Intent(appContext, MainActivity::class.java)
                    .setAction(INBOX_NOTIFICATION_ACTION_OPEN)
                    .putExtra(INBOX_NOTIFICATION_ACCOUNT_EXTRA, account)
                    .putExtra(INBOX_NOTIFICATION_ITEM_EXTRA, event.inboxItemId)
                    .putExtra(INBOX_NOTIFICATION_EVENT_EXTRA, event.eventKey)
                    .putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, NotificationLaunchKey.value(appContext))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val publicVersion = NotificationCompat.Builder(
                appContext,
                INBOX_NOTIFICATION_CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle("subshell")
                .setContentText("New Reddit Inbox activity")
                .build()

            val builder = NotificationCompat.Builder(
                appContext,
                INBOX_NOTIFICATION_CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle(event.notificationTitle())
                .setContentText(
                    event.preview.ifBlank {
                        if (event.kind == ActivityKind.MESSAGE) {
                            "Open the conversation"
                        } else {
                            "Open Reddit activity"
                        }
                    },
                )
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        event.preview.ifBlank { event.notificationTitle() },
                    ),
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(
                    if (event.kind == ActivityKind.MESSAGE) {
                        NotificationCompat.CATEGORY_MESSAGE
                    } else {
                        NotificationCompat.CATEGORY_SOCIAL
                    },
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(notificationAccountGroupKey(account))
            // ISSUE38_APP_LOCK_V1: with the app lock's "Hide notification content"
            // the notification says no more than its lock-screen version.
            if (hideContent) {
                builder
                    .setContentTitle("subshell")
                    .setContentText("New Reddit Inbox activity")
                    .setStyle(null)
            } else if (event.kind == ActivityKind.MESSAGE) {
                // ISSUE253_MESSAGES_ARE_CONVERSATION_NOTIFICATIONS_V1
                val content = conversationNotificationContent(event, System.currentTimeMillis())
                builder.setStyle(conversationMessagingStyle(content))
                if (pushConversationShortcut(appContext, account, content.sender)) {
                    builder.setShortcutId(conversationShortcutId(account, content.sender))
                }
                // ISSUE253_DIRECT_REPLY_FROM_THE_NOTIFICATION_V1
                event.replyParentFullname?.let { parent ->
                    val target = ConversationReplyTarget(
                        eventKey = event.eventKey,
                        accountStableId = account,
                        inboxItemId = event.inboxItemId,
                        parentFullname = parent,
                        channel = if (isDirectChatRoom(event.title)) {
                            app.subshell.prototype.data.account.MessageReplyChannel.DIRECT_CHAT
                        } else {
                            app.subshell.prototype.data.account.MessageReplyChannel.PRIVATE_MESSAGE_THREAD
                        },
                        notificationId = notificationId,
                    )
                    ConversationReplyTargets.put(appContext, target)
                    builder.addAction(conversationReplyAction(appContext, target))
                }
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@forEach
            }

            val record = postedNotificationRecord(
                kind = PostedNotificationKind.INBOX,
                accountStableId = account,
                targetId = event.inboxItemId,
                eventKey = event.eventKey,
                notificationId = notificationId,
            ) ?: return@forEach

            if (
                postTrackedNotification(
                    context = appContext,
                    manager = manager,
                    record = record,
                    notification = builder.build(),
                )
            ) {
                postedCount += 1
            }
        }

        return postedCount
    }

    fun postNewModmailEvents(
        context: Context,
        accountStableId: String,
        conversations: List<RedditModmailConversation>,
        newEventKeys: List<String>,
    ): Int {
        val appContext = context.applicationContext
        val account = accountStableId.trim().takeIf(String::isNotEmpty)
            ?: return 0
        if (!notificationsEnabled(appContext) || !canPost(appContext)) return 0
        val policy = notificationPolicy(appContext)
        val events = modmailNotificationEvents(
            conversations = conversations,
            newEventKeys = newEventKeys,
            policy = policy,
        )
        if (events.isEmpty()) return 0

        ensureChannel(appContext)
        val manager = NotificationManagerCompat.from(appContext)
        // ISSUE38_APP_LOCK_V1
        val hideContent = AppLockSettingsStore.read(appContext).hidesNotificationContent
        var postedCount = 0

        events.forEach { event ->
            val notificationId = stableNotificationId(event.eventKey)
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationPendingIntentRequestCode(
                    accountStableId = account,
                    eventKey = event.eventKey,
                ),
                Intent(appContext, MainActivity::class.java)
                    .setAction(MODMAIL_NOTIFICATION_ACTION_OPEN)
                    .putExtra(MODMAIL_NOTIFICATION_ACCOUNT_EXTRA, account)
                    .putExtra(MODMAIL_NOTIFICATION_CONVERSATION_EXTRA, event.conversationId)
                    .putExtra(MODMAIL_NOTIFICATION_EVENT_EXTRA, event.eventKey)
                    .putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, NotificationLaunchKey.value(appContext))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val publicVersion = NotificationCompat.Builder(
                appContext,
                INBOX_NOTIFICATION_CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle("subshell")
                .setContentText("New Reddit Modmail")
                .build()
            val title = "Modmail · r/${event.communityName}"
            val content = event.preview.ifBlank {
                event.subject.ifBlank { "Open the Modmail conversation" }
            }
            val builder = NotificationCompat.Builder(
                appContext,
                INBOX_NOTIFICATION_CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(notificationAccountGroupKey(account))
            // ISSUE38_APP_LOCK_V1: with the app lock's "Hide notification content"
            // the notification says no more than its lock-screen version.
            if (hideContent) {
                builder
                    .setContentTitle("subshell")
                    .setContentText("New Reddit Modmail")
                    .setStyle(null)
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@forEach
            }

            val record = postedNotificationRecord(
                kind = PostedNotificationKind.MODMAIL,
                accountStableId = account,
                targetId = event.conversationId,
                eventKey = event.eventKey,
                notificationId = notificationId,
            ) ?: return@forEach

            if (
                postTrackedNotification(
                    context = appContext,
                    manager = manager,
                    record = record,
                    notification = builder.build(),
                )
            ) {
                postedCount += 1
            }
        }

        return postedCount
    }

    // NOTIFY01A2_READ_STATE_SYSTEM_NOTIFICATION_RECONCILIATION_V1
    fun reconcileInboxReadState(
        context: Context,
        accountStableId: String,
        items: List<InboxItem>,
        locallySeenIds: Set<String>,
        readMutationItemIds: Set<String>,
    ): Int {
        val account = accountStableId.trim()
        if (account.isEmpty()) return 0
        val targets = settledInboxNotificationTargetIds(
            items = items,
            locallySeenIds = locallySeenIds,
            readMutationItemIds = readMutationItemIds,
        )
        return cancelPostedTargets(
            context = context,
            kind = PostedNotificationKind.INBOX,
            accountStableId = account,
            targetIds = targets,
        )
    }

    fun reconcileModmailReadState(
        context: Context,
        accountStableId: String,
        conversations: List<RedditModmailConversation>,
    ): Int {
        val account = accountStableId.trim()
        if (account.isEmpty()) return 0
        val targets = settledModmailNotificationTargetIds(conversations)
        return cancelPostedTargets(
            context = context,
            kind = PostedNotificationKind.MODMAIL,
            accountStableId = account,
            targetIds = targets,
        )
    }

    // INBOX07_PRUNE_RECORDS_ONCE_PER_RUN_V1 (#148, replaces INBOX05's pruning
    // at every post): records of notifications no longer shown (swiped away,
    // cleared, account gone) are dropped once per background run, before it
    // posts. Pruning at every post dropped the records of notifications the
    // same run had just posted, because the system lists a posted
    // notification as active only a moment later. Snoozed notifications are
    // not listed either, so their records are dropped at the next run.
    fun pruneRecordsOfNotificationsNotShown(context: Context) {
        val appContext = context.applicationContext
        val shown = activeNotificationKeys(appContext) ?: return
        val preferences = appContext.getSharedPreferences(
            INBOX_NOTIFICATION_STATE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        synchronized(INBOX_NOTIFICATION_STATE_LOCK) {
            val current = loadPostedRecords(preferences)
            val kept = current.filter { existing ->
                runCatching {
                    (notificationAccountTag(existing.accountStableId) to existing.notificationId) in shown
                }.getOrDefault(false)
            }
            if (kept.size != current.size) {
                writePostedRecords(preferences, kept)
            }
        }
    }

    // INBOX08_LOGOUT_FORGETS_ACCOUNT_NOTIFICATIONS_V1 (#147): a logged-out
    // account's notifications (both kinds) leave the shade and lock screen,
    // and their records go with them. Notifications still shown under the
    // account's tag are cancelled too, recorded or not.
    fun cancelForAccount(context: Context, accountStableId: String): Int {
        val account = accountStableId.trim()
        if (account.isEmpty()) return 0
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(
            INBOX_NOTIFICATION_STATE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val manager = NotificationManagerCompat.from(appContext)
        val tag = notificationAccountTag(account)
        return synchronized(INBOX_NOTIFICATION_STATE_LOCK) {
            val current = loadPostedRecords(preferences)
            val owned = current.filter { record -> record.accountStableId == account }
            owned.forEach { record -> manager.cancel(tag, record.notificationId) }
            activeNotificationKeys(appContext).orEmpty()
                .filter { (shownTag, _) -> shownTag == tag }
                .forEach { (_, id) -> manager.cancel(tag, id) }
            if (owned.isNotEmpty()) {
                writePostedRecords(preferences, current.filterNot { it.accountStableId == account })
            }
            owned.size
        }
    }

    /**
     * OCB-561: the background worker reads only unread Modmail. A posted
     * conversation missing from a list that is not full (one page) is no
     * longer unread; a full page proves nothing and cancels nothing.
     */
    fun reconcileModmailUnreadList(
        context: Context,
        accountStableId: String,
        unreadConversations: List<RedditModmailConversation>,
        pageLimit: Int = MODMAIL_BACKGROUND_UNREAD_PAGE_LIMIT,
    ): Int {
        val account = accountStableId.trim()
        if (account.isEmpty() || unreadConversations.size >= pageLimit) return 0
        val unread = unreadConversations.mapNotNullTo(hashSetOf()) { conversation ->
            runCatching { normalizeRedditModmailId(conversation.id) }.getOrNull()
        }
        val preferences = context.applicationContext.getSharedPreferences(
            INBOX_NOTIFICATION_STATE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val settled = synchronized(INBOX_NOTIFICATION_STATE_LOCK) {
            loadPostedRecords(preferences)
                .filter { record ->
                    record.kind == PostedNotificationKind.MODMAIL &&
                        record.accountStableId == account &&
                        record.targetId !in unread
                }
                .mapTo(linkedSetOf(), PostedNotificationRecord::targetId)
        }
        if (settled.isEmpty()) return 0
        return cancelPostedTargets(
            context = context,
            kind = PostedNotificationKind.MODMAIL,
            accountStableId = account,
            targetIds = settled,
        )
    }

    /** OCB-562: whether the background worker should read Modmail at all. */
    fun modmailNotificationsWanted(context: Context): Boolean =
        notificationPolicy(context.applicationContext).modmail

    /**
     * LOW_INBOX_HIDING_CONTENT_REDACTS_SHOWN_NOTIFICATIONS_V1 (OCB-563): turning
     * on "Hide notification content" (or the lock with it) replaces the
     * notifications already shown with their content-free version, quietly,
     * keeping where a tap leads.
     */
    fun redactShownNotifications(context: Context): Int {
        val appContext = context.applicationContext
        val shown = activeNotificationKeys(appContext) ?: return 0
        val preferences = appContext.getSharedPreferences(
            INBOX_NOTIFICATION_STATE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val manager = NotificationManagerCompat.from(appContext)
        return synchronized(INBOX_NOTIFICATION_STATE_LOCK) {
            var redacted = 0
            loadPostedRecords(preferences).forEach { record ->
                val tag = notificationAccountTag(record.accountStableId)
                if ((tag to record.notificationId) !in shown) return@forEach
                val modmail = record.kind == PostedNotificationKind.MODMAIL
                val intent = Intent(appContext, MainActivity::class.java)
                    .putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, NotificationLaunchKey.value(appContext))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (modmail) {
                    intent.setAction(MODMAIL_NOTIFICATION_ACTION_OPEN)
                        .putExtra(MODMAIL_NOTIFICATION_ACCOUNT_EXTRA, record.accountStableId)
                        .putExtra(MODMAIL_NOTIFICATION_CONVERSATION_EXTRA, record.targetId)
                        .putExtra(MODMAIL_NOTIFICATION_EVENT_EXTRA, record.eventKey)
                } else {
                    intent.setAction(INBOX_NOTIFICATION_ACTION_OPEN)
                        .putExtra(INBOX_NOTIFICATION_ACCOUNT_EXTRA, record.accountStableId)
                        .putExtra(INBOX_NOTIFICATION_ITEM_EXTRA, record.targetId)
                        .putExtra(INBOX_NOTIFICATION_EVENT_EXTRA, record.eventKey)
                }
                val pendingIntent = PendingIntent.getActivity(
                    appContext,
                    notificationPendingIntentRequestCode(
                        accountStableId = record.accountStableId,
                        eventKey = record.eventKey,
                    ),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val text = if (modmail) "New Reddit Modmail" else "New Reddit Inbox activity"
                val publicVersion = NotificationCompat.Builder(appContext, INBOX_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_subshell)
                    .setContentTitle("subshell")
                    .setContentText(text)
                    .build()
                val notification = NotificationCompat.Builder(appContext, INBOX_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_subshell)
                    .setContentTitle("subshell")
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setCategory(
                        if (modmail) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL,
                    )
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
                    .setGroup(notificationAccountGroupKey(record.accountStableId))
                    .build()
                try {
                    manager.notify(tag, record.notificationId, notification)
                    redacted += 1
                } catch (_: SecurityException) {
                    // Without the permission nothing more can be shown or changed.
                }
            }
            redacted
        }
    }

    // Master OFF remains the one intentional global cancellation operation.
    fun cancelPosted(context: Context) {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(
            INBOX_NOTIFICATION_STATE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val manager = NotificationManagerCompat.from(appContext)

        synchronized(INBOX_NOTIFICATION_STATE_LOCK) {
            cancelLegacyPostedIdsLocked(
                preferences = preferences,
                manager = manager,
            )
            loadPostedRecords(preferences).forEach { record ->
                manager.cancel(
                    notificationAccountTag(record.accountStableId),
                    record.notificationId,
                )
            }
            // OCB-558: the master switch runs on the main thread.
            preferences.edit()
                .remove(INBOX_NOTIFICATION_POSTED_RECORDS_KEY)
                .remove(INBOX_NOTIFICATION_POSTED_IDS_KEY)
                .apply()
        }
    }

    private fun postTrackedNotification(
        context: Context,
        manager: NotificationManagerCompat,
        record: PostedNotificationRecord,
        notification: android.app.Notification,
    ): Boolean {
        val preferences = context.getSharedPreferences(
            INBOX_NOTIFICATION_STATE_PREFERENCES,
            Context.MODE_PRIVATE,
        )

        return synchronized(INBOX_NOTIFICATION_STATE_LOCK) {
            try {
                manager.notify(
                    notificationAccountTag(record.accountStableId),
                    record.notificationId,
                    notification,
                )
            } catch (_: SecurityException) {
                recordNotificationPostFailureSafely(
                    context = context,
                    kind = record.kind,
                    details = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.PERMISSION,
                    ),
                )
                return@synchronized false
            }

            val current = loadPostedRecords(preferences)
            val updated = current
                .filterNot { existing ->
                    existing.kind == record.kind &&
                        existing.accountStableId == record.accountStableId &&
                        existing.eventKey == record.eventKey
                } + record

            if (!writePostedRecords(preferences, updated)) {
                manager.cancel(
                    notificationAccountTag(record.accountStableId),
                    record.notificationId,
                )
                recordNotificationPostFailureSafely(
                    context = context,
                    kind = record.kind,
                    details = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.STORAGE,
                    ),
                )
                return@synchronized false
            }

            true
        }
    }

    private fun recordNotificationPostFailureSafely(
        context: Context,
        kind: PostedNotificationKind,
        details: FailureDetails,
    ) {
        val scopeKey = when (kind) {
            PostedNotificationKind.INBOX -> "inbox_notification_post"
            PostedNotificationKind.MODMAIL -> "modmail_notification_post"
        }
        runCatching {
            DiagnosticEventStore(context).recordNotificationPostFailure(
                scopeKey = scopeKey,
                details = details,
            )
        }
    }

    private fun cancelPostedTargets(
        context: Context,
        kind: PostedNotificationKind,
        accountStableId: String,
        targetIds: Set<String>,
    ): Int {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(
            INBOX_NOTIFICATION_STATE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val manager = NotificationManagerCompat.from(appContext)

        return synchronized(INBOX_NOTIFICATION_STATE_LOCK) {
            // Pre-A2 IDs have no account/target ownership. They cannot be
            // reconciled safely, so the first A2 reconciliation removes them.
            cancelLegacyPostedIdsLocked(
                preferences = preferences,
                manager = manager,
            )

            val current = loadPostedRecords(preferences)
            val matching = postedNotificationRecordsForTargets(
                records = current,
                kind = kind,
                accountStableId = accountStableId,
                targetIds = targetIds,
            )
            if (matching.isEmpty()) return@synchronized 0

            matching.forEach { record ->
                manager.cancel(
                    notificationAccountTag(record.accountStableId),
                    record.notificationId,
                )
            }

            val matched = matching.toSet()
            writePostedRecords(
                preferences = preferences,
                records = current.filterNot(matched::contains),
            )

            matching.size
        }
    }

    private fun activeNotificationKeys(context: Context): Set<Pair<String?, Int>>? =
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.activeNotifications
                ?.mapTo(hashSetOf()) { shown -> shown.tag to shown.id }
        }.getOrNull()

    private fun loadPostedRecords(
        preferences: android.content.SharedPreferences,
    ): List<PostedNotificationRecord> =
        preferences
            .getStringSet(
                INBOX_NOTIFICATION_POSTED_RECORDS_KEY,
                emptySet(),
            )
            .orEmpty()
            .mapNotNull(::decodePostedNotificationRecord)

    private fun writePostedRecords(
        preferences: android.content.SharedPreferences,
        records: Collection<PostedNotificationRecord>,
    ): Boolean {
        val encoded = records
            .map(::encodePostedNotificationRecord)
            .toSet()
        val editor = preferences.edit()
        if (encoded.isEmpty()) {
            editor.remove(INBOX_NOTIFICATION_POSTED_RECORDS_KEY)
        } else {
            editor.putStringSet(
                INBOX_NOTIFICATION_POSTED_RECORDS_KEY,
                encoded,
            )
        }
        return editor.commit()
    }

    private fun cancelLegacyPostedIdsLocked(
        preferences: android.content.SharedPreferences,
        manager: NotificationManagerCompat,
    ) {
        val legacyIds = preferences
            .getStringSet(
                INBOX_NOTIFICATION_POSTED_IDS_KEY,
                emptySet(),
            )
            .orEmpty()
            .mapNotNull(String::toIntOrNull)

        legacyIds.forEach(manager::cancel)

        if (legacyIds.isNotEmpty()) {
            preferences.edit()
                .remove(INBOX_NOTIFICATION_POSTED_IDS_KEY)
                .commit()
        }
    }

    private fun notificationPolicy(context: Context): InboxNotificationPolicy {
        val preferences = context.getSharedPreferences(
            SUBSHELL_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        return InboxNotificationPolicy(
            replies = preferences.getBoolean(SubshellSettings.NOTIFY_REPLIES, true),
            mentions = preferences.getBoolean(SubshellSettings.NOTIFY_MENTIONS, true),
            privateMessages = preferences.getBoolean(
                SubshellSettings.NOTIFY_PRIVATE_MESSAGES,
                true,
            ),
            redditNotices = preferences.getBoolean(
                SubshellSettings.NOTIFY_REDDIT_NOTICES,
                true,
            ),
            modmail = preferences.getBoolean(SubshellSettings.NOTIFY_MODMAIL, true),
        )
    }

    private fun notificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(
            SUBSHELL_PREFERENCES,
            Context.MODE_PRIVATE,
        ).getBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, INBOX_NOTIFICATIONS_ON_BY_DEFAULT)


}

internal const val MODMAIL_BACKGROUND_UNREAD_PAGE_LIMIT = MODMAIL_CONVERSATION_MAX_LIMIT

private fun InboxNotificationEvent.notificationTitle(): String = when (kind) {
    ActivityKind.MESSAGE -> sender.takeIf(String::isNotBlank)
        ?.let { "Message from $it" }
        ?: "New message"
    else -> title.ifBlank { "New Reddit activity" }
}

private fun stableNotificationId(eventKey: String): Int =
    eventKey.hashCode() and Int.MAX_VALUE

/**
 * SECAUDIT06_NOTIFICATION_TAPS_CARRY_THE_LAUNCH_KEY_V1 (security audit #6):
 * MainActivity is exported, so another app could send it an inbox or modmail
 * "notification tap" and make Subshell switch account, open the item and mark
 * it read on Reddit. A tap now carries a random key kept only in Subshell's own
 * storage; it lasts across process restarts (a notification can outlive the
 * process that posted it) and is never backed up. A tap without it is dropped.
 */
internal object NotificationLaunchKey {
    const val PREFERENCES = "subshell_notification_launch"
    private const val KEY = "launch_key_v1"

    fun value(context: Context): String = synchronized(this) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.getString(KEY, null)?.takeIf(String::isNotEmpty)
            ?: UUID.randomUUID().toString().also { created ->
                preferences.edit().putString(KEY, created).commit()
            }
    }

    fun matches(context: Context, candidate: String?): Boolean =
        candidate != null &&
            MessageDigest.isEqual(
                candidate.toByteArray(Charsets.UTF_8),
                value(context).toByteArray(Charsets.UTF_8),
            )
}

/** Removes the launch key from [intent] and says whether it was Subshell's own. */
private fun takeNotificationLaunchKey(context: Context, intent: Intent): Boolean {
    val key = intent.getStringExtra(NOTIFICATION_LAUNCH_KEY_EXTRA)
    intent.removeExtra(NOTIFICATION_LAUNCH_KEY_EXTRA)
    return NotificationLaunchKey.matches(context, key)
}

internal const val NOTIFICATION_LAUNCH_KEY_EXTRA = "app.subshell.prototype.extra.NOTIFICATION_LAUNCH_KEY"
internal const val INBOX_NOTIFICATION_ACTION_OPEN =
    "app.subshell.prototype.action.OPEN_INBOX_NOTIFICATION"
internal const val INBOX_NOTIFICATION_ACCOUNT_EXTRA = "inbox_notification_account"
internal const val INBOX_NOTIFICATION_ITEM_EXTRA = "inbox_notification_item"
internal const val INBOX_NOTIFICATION_EVENT_EXTRA = "inbox_notification_event"
internal const val INBOX_NOTIFICATION_CHANNEL_ID = "subshell_inbox"
internal const val MODMAIL_NOTIFICATION_ACTION_OPEN =
    "app.subshell.prototype.action.OPEN_MODMAIL_NOTIFICATION"
internal const val MODMAIL_NOTIFICATION_ACCOUNT_EXTRA = "modmail_notification_account"
internal const val MODMAIL_NOTIFICATION_CONVERSATION_EXTRA = "modmail_notification_conversation"
internal const val MODMAIL_NOTIFICATION_EVENT_EXTRA = "modmail_notification_event"
private const val INBOX_NOTIFICATION_STATE_PREFERENCES = "subshell_inbox_notifications"
private const val INBOX_NOTIFICATION_POSTED_IDS_KEY = "posted_notification_ids"
private const val INBOX_NOTIFICATION_POSTED_RECORDS_KEY =
    "posted_notification_records_v1"
private val INBOX_NOTIFICATION_STATE_LOCK = Any()
private const val SUBSHELL_PREFERENCES = "subshell_preferences"
