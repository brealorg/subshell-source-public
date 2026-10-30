package app.subshell.prototype.data.diagnostics

import android.content.Context
import java.io.File

// ISSUE281_THREAD_READS_IN_THE_REPORT_V1 (#281: an inbox reply whose post
// "couldn't be opened", a mod comment whose replies never loaded, a find in a
// big thread with no matches): none of it reached the diagnostic report. A
// post Reddit answered without, a replies load that failed (and which branch)
// and how much of a thread a find searched are now kept as one line each, the
// newest READ_EVENT_LIMIT; plus Reddit's rate-limit headers as last seen and
// the lowest remaining in this process. Counts, booleans and short tokens
// only; never an id, URL, text or query.

internal const val READ_EVENT_INBOX_TARGET_EMPTY = "inbox_target_empty"
internal const val READ_EVENT_MORE_REPLIES_FAILED = "more_replies_failed"
internal const val READ_EVENT_THREAD_FIND = "thread_find"
internal const val READ_EVENT_RATE_LIMIT_LOW = "rate_limit_low"

private val READ_EVENT_KINDS = setOf(
    READ_EVENT_INBOX_TARGET_EMPTY,
    READ_EVENT_MORE_REPLIES_FAILED,
    READ_EVENT_THREAD_FIND,
    READ_EVENT_RATE_LIMIT_LOW,
)

/** A field value: a number, a boolean or a short lowercase token. */
private val READ_EVENT_VALUE_PATTERN = Regex("-?[0-9]{1,12}|true|false|[a-z0-9_]{1,64}")
private val READ_EVENT_NAME_PATTERN = Regex("[a-z][a-z0-9_]{0,31}")

internal data class RedditReadEvent(
    val atEpochMs: Long,
    val kind: String,
    val fields: List<Pair<String, String>>,
)

internal class RedditReadEventStore(private val directory: File) {
    constructor(context: Context) : this(File(context.applicationContext.noBackupFilesDir, "diagnostics"))

    private val file: File
        get() = File(directory, READ_EVENTS_FILE_NAME)

    fun record(kind: String, fields: List<Pair<String, Any?>>, atEpochMs: Long) = synchronized(LOCK) {
        if (kind !in READ_EVENT_KINDS) return@synchronized
        val event = RedditReadEvent(atEpochMs, kind, fields.mapNotNull { (name, value) -> safeField(name, value) })
        runCatching {
            val kept = (read() + event).sortedBy(RedditReadEvent::atEpochMs).takeLast(READ_EVENT_LIMIT)
            directory.mkdirs()
            file.writeText(kept.joinToString(separator = "\n", postfix = "\n", transform = ::encode))
        }
    }

    /** Oldest first. */
    fun read(): List<RedditReadEvent> = synchronized(LOCK) {
        runCatching { file.readLines() }.getOrDefault(emptyList())
            .mapNotNull(::decode)
            .sortedBy(RedditReadEvent::atEpochMs)
    }

    fun clear() = synchronized(LOCK) {
        runCatching { file.delete() }
    }

    private fun encode(event: RedditReadEvent): String = buildString {
        append(event.atEpochMs).append('|').append(event.kind)
        event.fields.forEach { (name, value) -> append('|').append(name).append('=').append(value) }
    }

    private fun decode(line: String): RedditReadEvent? {
        val parts = line.split('|')
        if (parts.size < 2 || parts[1] !in READ_EVENT_KINDS) return null
        val at = parts[0].toLongOrNull() ?: return null
        val fields = parts.drop(2).map { part ->
            val name = part.substringBefore('=', missingDelimiterValue = "")
            safeField(name, part.substringAfter('=')) ?: return null
        }
        return RedditReadEvent(at, parts[1], fields)
    }

    private companion object {
        const val READ_EVENTS_FILE_NAME = "reddit-reads-v1.txt"
        val LOCK = Any()
    }
}

private fun safeField(name: String, value: Any?): Pair<String, String>? {
    if (value == null || !name.matches(READ_EVENT_NAME_PATTERN)) return null
    val text = value.toString()
    return (name to text).takeIf { text.matches(READ_EVENT_VALUE_PATTERN) }
}

internal const val READ_EVENT_LIMIT = 24

/** Reddit's rate-limit headers: the last seen, and the lowest remaining in this process. */
internal data class RedditRateLimitSnapshot(
    val remaining: Int,
    val used: Int?,
    val resetSeconds: Int?,
    val atEpochMs: Long,
    val lowestRemaining: Int,
    val lowestAtEpochMs: Long,
)

/**
 * The network clients have no context, so the app and the background workers
 * install the store once per process; until then nothing is recorded.
 */
internal object RedditReadDiagnostics {
    @Volatile
    private var recorder: ((String, List<Pair<String, Any?>>) -> Unit)? = null

    @Volatile
    private var rateLimit: RedditRateLimitSnapshot? = null

    @Volatile
    private var lastLowRecordedAtEpochMs: Long = 0L

    fun install(context: Context) {
        if (recorder != null) return
        val store = RedditReadEventStore(context.applicationContext)
        recorder = { kind, fields -> store.record(kind, fields, System.currentTimeMillis()) }
    }

    fun report(kind: String, vararg fields: Pair<String, Any?>) {
        val record = recorder ?: return
        runCatching { record(kind, fields.toList()) }
    }

    /** Called with every Reddit answer that carries the headers. */
    fun observeRateLimit(remaining: Double?, used: Double?, resetAfterMs: Long?, atEpochMs: Long) {
        val left = remaining?.takeIf(Double::isFinite)?.toInt()?.coerceAtLeast(0) ?: return
        val previous = rateLimit
        val lowest = previous?.takeIf { it.lowestRemaining <= left }
        rateLimit = RedditRateLimitSnapshot(
            remaining = left,
            used = used?.takeIf(Double::isFinite)?.toInt()?.coerceAtLeast(0),
            resetSeconds = resetAfterMs?.let { (it / 1_000L).toInt() },
            atEpochMs = atEpochMs,
            lowestRemaining = lowest?.lowestRemaining ?: left,
            lowestAtEpochMs = lowest?.lowestAtEpochMs ?: atEpochMs,
        )
        // Once per minute at most, so a long stretch near the limit is one line.
        if (left < RATE_LIMIT_LOW_REMAINING && atEpochMs - lastLowRecordedAtEpochMs >= 60_000L) {
            lastLowRecordedAtEpochMs = atEpochMs
            report(
                READ_EVENT_RATE_LIMIT_LOW,
                "remaining" to left,
                "used" to rateLimit?.used,
                "reset_s" to rateLimit?.resetSeconds,
            )
        }
    }

    fun rateLimitSnapshot(): RedditRateLimitSnapshot? = rateLimit

    /** Whether Reddit's last answer left so few requests that optional reads should wait. */
    fun rateLimitIsLow(): Boolean = (rateLimit?.remaining ?: Int.MAX_VALUE) < RATE_LIMIT_LOW_REMAINING

    internal fun installForTest(record: ((String, List<Pair<String, Any?>>) -> Unit)?) {
        recorder = record
        rateLimit = null
        lastLowRecordedAtEpochMs = 0L
    }

    private const val RATE_LIMIT_LOW_REMAINING = 10
}

internal fun StringBuilder.appendRedditReads(
    events: List<RedditReadEvent>?,
    rateLimit: RedditRateLimitSnapshot?,
) {
    if (events == null) return
    appendLine()
    appendLine("[reddit_reads]")
    if (rateLimit == null) {
        appendLine("rate_limit=not_seen")
    } else {
        append("rate_limit remaining=${rateLimit.remaining}")
        rateLimit.used?.let { append(" used=$it") }
        rateLimit.resetSeconds?.let { append(" reset_s=$it") }
        append(" at=${formatUtcTimestamp(rateLimit.atEpochMs)}")
        append(" lowest=${rateLimit.lowestRemaining} lowest_at=${formatUtcTimestamp(rateLimit.lowestAtEpochMs)}")
        appendLine()
    }
    if (events.isEmpty()) {
        appendLine("events=none")
        return
    }
    events.forEach { event ->
        append("${formatUtcTimestamp(event.atEpochMs)} ${event.kind}")
        event.fields.forEach { (name, value) -> append(" $name=$value") }
        appendLine()
    }
}
