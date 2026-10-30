package app.subshell.prototype.data.diagnostics

import android.content.Context
import java.io.File

// ISSUE32_REDDIT_REFUSALS_IN_THE_REPORT_V1 (#32): Reddit starts removing API
// access from apps it has not registered on 2027-01-12, and nobody knows yet
// how that refusal will look (a status, a reason in the body, an OAuth error
// at the token endpoint). Each kind of refusal the app meets is kept as one
// line with a count and its first and last time, so a reader's report shows
// the exact shape the first time it happens. Status codes and Reddit's short
// reason tokens only; never a URL, body, client ID or token.

internal const val REDDIT_REFUSAL_API = "api"
internal const val REDDIT_REFUSAL_TOKEN = "token"

/** HTTP statuses from the Reddit API that count as a refusal. */
internal fun isRedditApiRefusalStatus(statusCode: Int): Boolean =
    statusCode == 401 || statusCode == 403 || statusCode == 429 || statusCode == 451

/** One kind of refusal: where, which status, which reason, how often. */
internal data class RedditRefusal(
    val source: String,
    val statusCode: Int,
    val reason: String?,
    val count: Int,
    val firstEpochMs: Long,
    val lastEpochMs: Long,
)

internal class RedditRefusalStore(private val directory: File) {
    constructor(context: Context) : this(File(context.applicationContext.noBackupFilesDir, "diagnostics"))

    private val file: File
        get() = File(directory, REDDIT_REFUSALS_FILE_NAME)

    fun record(source: String, statusCode: Int, reason: String?, atEpochMs: Long) = synchronized(LOCK) {
        if (source != REDDIT_REFUSAL_API && source != REDDIT_REFUSAL_TOKEN) return@synchronized
        if (statusCode !in 100..599) return@synchronized
        val safeReason = reason?.takeIf { it.matches(SAFE_ENUM_TOKEN_PATTERN) }
        runCatching {
            val refusals = read().toMutableList()
            val index = refusals.indexOfFirst {
                it.source == source && it.statusCode == statusCode && it.reason == safeReason
            }
            if (index >= 0) {
                val known = refusals[index]
                refusals[index] = known.copy(
                    count = (known.count + 1).coerceAtMost(MAX_COUNT),
                    lastEpochMs = atEpochMs,
                )
            } else {
                refusals += RedditRefusal(source, statusCode, safeReason, 1, atEpochMs, atEpochMs)
            }
            val kept = refusals.sortedByDescending(RedditRefusal::lastEpochMs).take(MAX_KINDS)
            directory.mkdirs()
            file.writeText(kept.joinToString(separator = "\n", postfix = "\n", transform = ::encode))
        }
    }

    /** Newest first. */
    fun read(): List<RedditRefusal> = synchronized(LOCK) {
        runCatching { file.readLines() }.getOrDefault(emptyList())
            .mapNotNull(::decode)
            .sortedByDescending(RedditRefusal::lastEpochMs)
    }

    fun clear() = synchronized(LOCK) {
        runCatching { file.delete() }
    }

    private fun encode(refusal: RedditRefusal): String = listOf(
        refusal.source,
        refusal.statusCode.toString(),
        refusal.reason ?: "-",
        refusal.count.toString(),
        refusal.firstEpochMs.toString(),
        refusal.lastEpochMs.toString(),
    ).joinToString("|")

    private fun decode(line: String): RedditRefusal? {
        val parts = line.split('|')
        if (parts.size != 6 || parts[0] !in setOf(REDDIT_REFUSAL_API, REDDIT_REFUSAL_TOKEN)) return null
        return runCatching {
            RedditRefusal(
                source = parts[0],
                statusCode = parts[1].toInt().takeIf { it in 100..599 } ?: return null,
                reason = parts[2].takeUnless { it == "-" }?.takeIf { it.matches(SAFE_ENUM_TOKEN_PATTERN) },
                count = parts[3].toInt().takeIf { it > 0 } ?: return null,
                firstEpochMs = parts[4].toLong(),
                lastEpochMs = parts[5].toLong(),
            )
        }.getOrNull()
    }

    private companion object {
        const val REDDIT_REFUSALS_FILE_NAME = "reddit-refusals-v1.txt"
        const val MAX_KINDS = 16
        const val MAX_COUNT = 999_999
        val LOCK = Any()
    }
}

/**
 * The network clients have no context, so the activity and the background
 * workers install the store once per process; until then a refusal is not
 * recorded.
 */
internal object RedditRefusals {
    @Volatile
    private var recorder: ((String, Int, String?) -> Unit)? = null

    fun install(context: Context) {
        if (recorder != null) return
        val store = RedditRefusalStore(context.applicationContext)
        recorder = { source, status, reason -> store.record(source, status, reason, System.currentTimeMillis()) }
    }

    fun report(source: String, statusCode: Int, reason: String?) {
        val record = recorder ?: return
        runCatching { record(source, statusCode, reason) }
    }

    internal fun installForTest(record: ((String, Int, String?) -> Unit)?) {
        recorder = record
    }
}

internal fun StringBuilder.appendRedditRefusals(
    oauthClientMode: String?,
    refusals: List<RedditRefusal>?,
) {
    if (oauthClientMode == null && refusals == null) return
    appendLine()
    appendLine("[reddit_refusals]")
    oauthClientMode?.let { appendLine("oauth_client_mode=$it") }
    if (refusals.isNullOrEmpty()) {
        appendLine("refusals=none")
        return
    }
    refusals.forEach { refusal ->
        append("${refusal.source} http=${refusal.statusCode}")
        refusal.reason?.let { append(" reason=$it") }
        append(" count=${refusal.count}")
        append(" first=${formatUtcTimestamp(refusal.firstEpochMs)}")
        append(" last=${formatUtcTimestamp(refusal.lastEpochMs)}")
        appendLine()
    }
}
