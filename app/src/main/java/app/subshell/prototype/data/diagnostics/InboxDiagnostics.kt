package app.subshell.prototype.data.diagnostics

import android.content.Context
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import java.io.File

// ISSUE272_INBOX_STATUS_IN_THE_REPORT_V1 (#272): "I get no notifications"
// needed several questions to tell an empty inbox from notifications that
// are off. The report now says what the last inbox load in the app and the
// last background check found, in counts only (no names, titles or IDs),
// kept as one overwritten line each so a 15-minute check never fills the
// event log.

internal const val INBOX_STATUS_APP = "app"
internal const val INBOX_STATUS_BACKGROUND = "background"

/** What one inbox read found. Tokens and counts only. */
internal data class InboxCheckSummary(
    val atEpochMs: Long,
    /** loaded, baseline, checked, failed, authorization_required, not_cached, loader_unavailable … */
    val outcome: String,
    val activity: Int? = null,
    val messages: Int? = null,
    val unread: Int? = null,
    /** Items Reddit just sent (the rest were stored pages). */
    val fresh: Int? = null,
    val newEvents: Int? = null,
    val posted: Int? = null,
    val fromCache: Boolean? = null,
) {
    init {
        require(outcome.matches(SAFE_ENUM_TOKEN_PATTERN))
    }
}

/** Counts of an inbox list the way the Inbox screen splits it. */
internal fun inboxCheckSummary(
    atEpochMs: Long,
    outcome: String,
    items: List<InboxItem>,
    fresh: Int? = null,
    newEvents: Int? = null,
    posted: Int? = null,
    fromCache: Boolean? = null,
): InboxCheckSummary = InboxCheckSummary(
    atEpochMs = atEpochMs,
    outcome = outcome,
    activity = items.count { it.kind == ActivityKind.REPLY || it.kind == ActivityKind.MENTION || it.kind == ActivityKind.POST_REPLY },
    messages = items.count { it.kind == ActivityKind.MESSAGE || it.kind == ActivityKind.MOD },
    unread = items.count(InboxItem::unread),
    fresh = fresh,
    newEvents = newEvents,
    posted = posted,
    fromCache = fromCache,
)

/** The last app load and the last background check, one line each. */
internal class InboxDiagnosticStatusStore(private val directory: File) {
    constructor(context: Context) : this(File(context.applicationContext.noBackupFilesDir, "diagnostics"))

    private val file: File
        get() = File(directory, INBOX_STATUS_FILE_NAME)

    fun record(source: String, summary: InboxCheckSummary) = synchronized(LOCK) {
        runCatching {
            val lines = read().toMutableMap()
            lines[source] = summary
            directory.mkdirs()
            file.writeText(
                lines.entries.joinToString(separator = "\n", postfix = "\n") { (key, value) -> encode(key, value) },
            )
        }
    }

    fun read(): Map<String, InboxCheckSummary> = synchronized(LOCK) {
        runCatching { file.readLines() }.getOrDefault(emptyList())
            .mapNotNull(::decode)
            .toMap()
    }

    fun clear() = synchronized(LOCK) {
        runCatching { file.delete() }
    }

    private fun encode(source: String, summary: InboxCheckSummary): String = listOf(
        source,
        summary.atEpochMs.toString(),
        summary.outcome,
        summary.activity.token(),
        summary.messages.token(),
        summary.unread.token(),
        summary.fresh.token(),
        summary.newEvents.token(),
        summary.posted.token(),
        summary.fromCache?.toString() ?: "-",
    ).joinToString("|")

    private fun decode(line: String): Pair<String, InboxCheckSummary>? {
        val parts = line.split('|')
        if (parts.size != 10 || parts[0] !in setOf(INBOX_STATUS_APP, INBOX_STATUS_BACKGROUND)) return null
        return runCatching {
            parts[0] to InboxCheckSummary(
                atEpochMs = parts[1].toLong(),
                outcome = parts[2],
                activity = parts[3].count(),
                messages = parts[4].count(),
                unread = parts[5].count(),
                fresh = parts[6].count(),
                newEvents = parts[7].count(),
                posted = parts[8].count(),
                fromCache = parts[9].takeUnless { it == "-" }?.toBooleanStrict(),
            )
        }.getOrNull()
    }

    private fun Int?.token(): String = this?.toString() ?: "-"

    private fun String.count(): Int? = takeUnless { it == "-" }?.toInt()?.takeIf { it >= 0 }

    private companion object {
        const val INBOX_STATUS_FILE_NAME = "inbox-status-v1.txt"
        val LOCK = Any()
    }
}

/**
 * ISSUE272_NOTIFICATION_STATE_IN_THE_REPORT_V1 (#272): what decides whether a
 * notification can reach the reader, read when the report is made. Tokens
 * and booleans only; null where Android does not say.
 */
internal data class NotificationDiagnostics(
    val appNotificationsEnabled: Boolean?,
    /** granted, denied or not_required (before Android 13). */
    val postPermission: String,
    val inboxNotificationsSetting: Boolean,
    /** replies, mentions, private_messages, reddit_notices, modmail → on/off. */
    val types: Map<String, Boolean>,
    /** Subshell's own channel IDs → none, min, low, default, high, missing. */
    val channels: Map<String, String>,
    /** The inbox check's WorkManager state: enqueued, running, blocked, none … */
    val inboxBackgroundWork: String,
    val backgroundRestricted: Boolean?,
    val batteryOptimizationIgnored: Boolean?,
    /** active, working_set, frequent, rare, restricted or unknown. */
    val standbyBucket: String,
)

internal fun StringBuilder.appendNotificationDiagnostics(
    notifications: NotificationDiagnostics?,
    inboxStatus: Map<String, InboxCheckSummary>?,
) {
    if (notifications == null && inboxStatus == null) return
    appendLine()
    appendLine("[notifications]")
    notifications?.let { state ->
        appendLine("app_notifications_enabled=${state.appNotificationsEnabled ?: "unknown"}")
        appendLine("post_notifications_permission=${state.postPermission}")
        appendLine("inbox_notifications_setting=${if (state.inboxNotificationsSetting) "on" else "off"}")
        state.types.forEach { (type, on) -> appendLine("notify_$type=${if (on) "on" else "off"}") }
        state.channels.forEach { (id, importance) -> appendLine("channel_$id=$importance") }
        appendLine("inbox_background_work=${state.inboxBackgroundWork}")
        appendLine("background_restricted=${state.backgroundRestricted ?: "unknown"}")
        appendLine("battery_optimization_ignored=${state.batteryOptimizationIgnored ?: "unknown"}")
        appendLine("standby_bucket=${state.standbyBucket}")
    }
    listOf(INBOX_STATUS_APP to "last_app_inbox_load", INBOX_STATUS_BACKGROUND to "last_background_inbox_check")
        .forEach { (source, label) ->
            val summary = inboxStatus?.get(source)
            if (summary == null) {
                appendLine("$label=none")
                return@forEach
            }
            append("$label=${formatUtcTimestamp(summary.atEpochMs)} outcome=${summary.outcome}")
            summary.activity?.let { append(" activity=$it") }
            summary.messages?.let { append(" messages=$it") }
            summary.unread?.let { append(" unread=$it") }
            summary.fresh?.let { append(" fresh=$it") }
            summary.newEvents?.let { append(" new=$it") }
            summary.posted?.let { append(" posted=$it") }
            summary.fromCache?.let { append(" from_cache=$it") }
            appendLine()
        }
}
