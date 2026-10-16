package app.subshell.prototype.inbox

import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.normalizeRedditModmailId
import app.subshell.prototype.model.InboxItem
import java.nio.charset.StandardCharsets
import java.util.Base64

// NOTIFY01A2_ACCOUNT_TARGET_NOTIFICATION_RECORD_V1
internal enum class PostedNotificationKind {
    INBOX,
    MODMAIL,
}

internal data class PostedNotificationRecord(
    val kind: PostedNotificationKind,
    val accountStableId: String,
    val targetId: String,
    val eventKey: String,
    val notificationId: Int,
)

internal fun postedNotificationRecord(
    kind: PostedNotificationKind,
    accountStableId: String,
    targetId: String,
    eventKey: String,
    notificationId: Int,
): PostedNotificationRecord? {
    val account = accountStableId.trim().takeIf(String::isNotEmpty)
        ?: return null
    val target = targetId.trim().takeIf(String::isNotEmpty)
        ?: return null
    val event = eventKey.trim().takeIf(String::isNotEmpty)
        ?: return null
    if (notificationId < 0) return null

    return PostedNotificationRecord(
        kind = kind,
        accountStableId = account,
        targetId = target,
        eventKey = event,
        notificationId = notificationId,
    )
}

internal fun encodePostedNotificationRecord(
    record: PostedNotificationRecord,
): String {
    val normalized = postedNotificationRecord(
        kind = record.kind,
        accountStableId = record.accountStableId,
        targetId = record.targetId,
        eventKey = record.eventKey,
        notificationId = record.notificationId,
    ) ?: error("Posted notification record is invalid")

    return listOf(
        POSTED_NOTIFICATION_RECORD_VERSION,
        normalized.kind.name,
        encodeNotificationIdentity(normalized.accountStableId),
        encodeNotificationIdentity(normalized.targetId),
        encodeNotificationIdentity(normalized.eventKey),
        normalized.notificationId.toString(),
    ).joinToString(":")
}

internal fun decodePostedNotificationRecord(
    encoded: String,
): PostedNotificationRecord? {
    val parts = encoded.trim().split(':')
    if (parts.size != 6) return null
    if (parts[0] != POSTED_NOTIFICATION_RECORD_VERSION) return null

    val kind = runCatching {
        PostedNotificationKind.valueOf(parts[1])
    }.getOrNull() ?: return null

    val account = decodeNotificationIdentity(parts[2]) ?: return null
    val target = decodeNotificationIdentity(parts[3]) ?: return null
    val event = decodeNotificationIdentity(parts[4]) ?: return null
    val notificationId = parts[5].toIntOrNull() ?: return null

    return postedNotificationRecord(
        kind = kind,
        accountStableId = account,
        targetId = target,
        eventKey = event,
        notificationId = notificationId,
    )
}

// Android notification tag is account-scoped. The event ID can therefore
// remain stable without one account overwriting another account's event.
internal fun notificationAccountTag(accountStableId: String): String {
    val account = accountStableId.trim()
    require(account.isNotEmpty()) {
        "Notification account stable ID cannot be blank"
    }
    return "subshell_inbox:${encodeNotificationIdentity(account)}"
}

internal fun notificationAccountGroupKey(accountStableId: String): String =
    "subshell_inbox_group:${encodeNotificationIdentity(accountStableId.trim())}"

// PendingIntent identity is package-global, unlike tagged notifications.
// Include the account so identical Reddit event keys in two accounts cannot
// update each other's PendingIntent.
internal fun notificationPendingIntentRequestCode(
    accountStableId: String,
    eventKey: String,
): Int {
    val account = accountStableId.trim()
    val event = eventKey.trim()
    require(account.isNotEmpty()) {
        "Notification account stable ID cannot be blank"
    }
    require(event.isNotEmpty()) {
        "Notification event key cannot be blank"
    }
    return "subshell-notification\u0000$account\u0000$event"
        .hashCode() and Int.MAX_VALUE
}

internal fun inboxNotificationIsSettledRead(
    unread: Boolean,
    locallySeen: Boolean,
    readMutationPending: Boolean,
): Boolean {
    if (!unread) return true
    if (readMutationPending) return false
    return locallySeen
}

// Optimistic read-state must not clear the Android notification. If the Reddit
// mutation rolls back, the notification remains. Stable local-seen state is
// still a valid reconciliation signal.
internal fun settledInboxNotificationTargetIds(
    items: List<InboxItem>,
    locallySeenIds: Set<String>,
    readMutationItemIds: Set<String>,
): Set<String> {
    val seen = locallySeenIds
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    val pending = readMutationItemIds
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    return items.mapNotNullTo(linkedSetOf()) { item ->
        val id = item.id.trim().takeIf(String::isNotEmpty)
            ?: return@mapNotNullTo null
        id.takeIf {
            inboxNotificationIsSettledRead(
                unread = item.unread,
                locallySeen = id in seen,
                readMutationPending = id in pending,
            )
        }
    }
}

internal fun settledModmailNotificationTargetIds(
    conversations: List<RedditModmailConversation>,
): Set<String> = conversations
    .asSequence()
    .filterNot(RedditModmailConversation::unread)
    .mapNotNull { conversation ->
        runCatching {
            normalizeRedditModmailId(conversation.id)
        }.getOrNull()
    }
    .toCollection(linkedSetOf())

internal fun postedNotificationRecordsForTargets(
    records: Collection<PostedNotificationRecord>,
    kind: PostedNotificationKind,
    accountStableId: String,
    targetIds: Set<String>,
): List<PostedNotificationRecord> {
    val account = accountStableId.trim()
    if (account.isEmpty() || targetIds.isEmpty()) return emptyList()

    return records.filter { record ->
        record.kind == kind &&
            record.accountStableId == account &&
            record.targetId in targetIds
    }
}

private fun encodeNotificationIdentity(value: String): String =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
        )

private fun decodeNotificationIdentity(value: String): String? =
    runCatching {
        String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8,
        )
    }.getOrNull()

private const val POSTED_NOTIFICATION_RECORD_VERSION = "v1"
