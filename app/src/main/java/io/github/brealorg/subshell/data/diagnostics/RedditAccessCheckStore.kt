package io.github.brealorg.subshell.data.diagnostics

import android.content.Context
import io.github.brealorg.subshell.data.account.RedditAccessProbe
import io.github.brealorg.subshell.data.account.RedditAccessProbeOutcome
import java.io.File

// REDDITCHECK01_CHECK_REDDIT_ACCESS_V1: the last Check Reddit access, one
// overwritten file in no-backup diagnostics/. Each line is a fixed endpoint
// name, the outcome, the status, Reddit's reason or the failure kind and the
// time it took; never a URL, a body, a name or an id.

/** The last check: when it ran and what each read got. */
internal data class RedditAccessCheckRecord(
    val checkedAtEpochMs: Long,
    val probes: List<RedditAccessProbe>,
)

internal class RedditAccessCheckStore(private val directory: File) {
    constructor(context: Context) : this(File(context.applicationContext.noBackupFilesDir, "diagnostics"))

    private val file: File
        get() = File(directory, REDDIT_ACCESS_CHECK_FILE_NAME)

    fun write(probes: List<RedditAccessProbe>, atEpochMs: Long) = synchronized(LOCK) {
        runCatching {
            directory.mkdirs()
            val lines = listOf("checked|$atEpochMs") + probes.mapNotNull(::encode)
            file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    fun read(): RedditAccessCheckRecord? = synchronized(LOCK) {
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList()).filter(String::isNotBlank)
        val header = lines.firstOrNull()?.split('|') ?: return@synchronized null
        if (header.size != 2 || header[0] != "checked") return@synchronized null
        val checkedAt = header[1].toLongOrNull() ?: return@synchronized null
        RedditAccessCheckRecord(checkedAt, lines.drop(1).take(MAX_PROBES).mapNotNull(::decode))
    }

    fun clear() = synchronized(LOCK) {
        runCatching { file.delete() }
    }

    private fun encode(probe: RedditAccessProbe): String? {
        if (!probe.endpoint.matches(SAFE_ENUM_TOKEN_PATTERN)) return null
        return listOf(
            probe.endpoint,
            probe.outcome.token,
            probe.statusCode?.takeIf { it in 100..599 }?.toString() ?: "-",
            probe.detail?.takeIf { it.matches(SAFE_ENUM_TOKEN_PATTERN) } ?: "-",
            probe.elapsedMs.coerceIn(0L, MAX_ELAPSED_MS).toString(),
        ).joinToString("|")
    }

    private fun decode(line: String): RedditAccessProbe? {
        val parts = line.split('|')
        if (parts.size != 5 || !parts[0].matches(SAFE_ENUM_TOKEN_PATTERN)) return null
        return RedditAccessProbe(
            endpoint = parts[0],
            outcome = RedditAccessProbeOutcome.fromToken(parts[1]) ?: return null,
            statusCode = parts[2].toIntOrNull()?.takeIf { it in 100..599 },
            detail = parts[3].takeUnless { it == "-" }?.takeIf { it.matches(SAFE_ENUM_TOKEN_PATTERN) },
            elapsedMs = parts[4].toLongOrNull()?.coerceIn(0L, MAX_ELAPSED_MS) ?: return null,
        )
    }

    private companion object {
        const val REDDIT_ACCESS_CHECK_FILE_NAME = "reddit-access-check-v1.txt"
        const val MAX_PROBES = 32
        const val MAX_ELAPSED_MS = 600_000L
        val LOCK = Any()
    }
}

internal fun StringBuilder.appendRedditAccessCheck(record: RedditAccessCheckRecord?) {
    if (record == null) return
    appendLine()
    appendLine("[reddit_access_check]")
    appendLine("checked_utc=${formatUtcTimestamp(record.checkedAtEpochMs)}")
    record.probes.forEach { probe ->
        append("${probe.endpoint} ${probe.outcome.token}")
        probe.statusCode?.let { append(" http=$it") }
        probe.detail?.let { append(" detail=$it") }
        if (probe.outcome != RedditAccessProbeOutcome.SKIPPED) append(" ms=${probe.elapsedMs}")
        appendLine()
    }
}
