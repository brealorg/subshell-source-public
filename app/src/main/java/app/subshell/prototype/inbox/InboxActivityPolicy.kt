package app.subshell.prototype.inbox

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import java.time.Instant
import java.time.ZoneId

sealed interface InboxTarget {
    data class ExactComment(
        val postId: String,
        val commentFullname: String,
        val subreddit: String?,
        val permalink: String?,
    ) : InboxTarget

    data class Post(
        val postId: String,
        val subreddit: String?,
        val permalink: String?,
    ) : InboxTarget

    data class MessageThread(
        val threadId: String,
    ) : InboxTarget
}

internal enum class InboxActivitySection(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    EARLIER("Earlier"),
}

internal fun canonicalRedditCommentFullname(value: String?): String? {
    val normalized = value?.trim()?.lowercase().orEmpty()
    if (normalized.isEmpty()) return null
    val bare = normalized.removePrefix("t1_")
    if (bare.isEmpty() || !bare.all(Char::isLetterOrDigit)) return null
    return "t1_$bare"
}

internal fun InboxItem.canonicalInboxTarget(): InboxTarget? {
    if (kind == ActivityKind.MESSAGE) {
        return InboxTarget.MessageThread(threadId = id)
    }
    val postId = targetPostId?.trim()?.removePrefix("t3_")?.takeIf(String::isNotEmpty)
        ?: return null
    val comment = canonicalRedditCommentFullname(targetCommentId)
    return if (comment != null) {
        InboxTarget.ExactComment(
            postId = postId,
            commentFullname = comment,
            subreddit = subreddit,
            permalink = contextPermalink,
        )
    } else {
        InboxTarget.Post(
            postId = postId,
            subreddit = subreddit,
            permalink = contextPermalink,
        )
    }
}

internal fun InboxItem.hiddenByActivityLifecycle(
    dismissedIds: Set<String>,
    clearBoundaryEpochSeconds: Long,
): Boolean {
    if (kind == ActivityKind.MESSAGE) return false
    if (id in dismissedIds) return true
    if (clearBoundaryEpochSeconds <= 0L) return false
    val created = createdEpochSeconds ?: 0L
    return created <= clearBoundaryEpochSeconds
}

internal fun inboxActivitySection(
    createdEpochSeconds: Long?,
    nowEpochSeconds: Long = Instant.now().epochSecond,
    zoneId: ZoneId = ZoneId.systemDefault(),
): InboxActivitySection {
    if (createdEpochSeconds == null || createdEpochSeconds <= 0L) {
        return InboxActivitySection.EARLIER
    }
    val today = Instant.ofEpochSecond(nowEpochSeconds).atZone(zoneId).toLocalDate()
    val created = Instant.ofEpochSecond(createdEpochSeconds).atZone(zoneId).toLocalDate()
    return when (created) {
        today -> InboxActivitySection.TODAY
        today.minusDays(1) -> InboxActivitySection.YESTERDAY
        else -> InboxActivitySection.EARLIER
    }
}
