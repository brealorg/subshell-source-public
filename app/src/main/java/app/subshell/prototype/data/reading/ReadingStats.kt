package app.subshell.prototype.data.reading

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ISSUE268_READING_STATS_STAY_ON_THE_DEVICE_V1 (#268, owner): "Your reading"
// counts, per account and community, how often the reader opened it, how many
// of its posts they opened and how long it was on screen. Counts only, no
// titles or post IDs. Kept in no-backup storage (never in a backup, never
// sent), one bucket per day, the last READING_STATS_WINDOW_DAYS days only.
// Off by default; nothing is recorded while off, and turning it off deletes
// what was recorded.

internal const val READING_STATS_WINDOW_DAYS = 30
internal const val READING_STATS_SUGGESTION_MIN_DAYS = 14

/** The Home feed's share of the time, kept apart from any community. */
internal const val READING_STATS_HOME_TARGET = "*home"

internal data class CommunityReading(
    val communityName: String,
    val opens: Int,
    val postOpens: Int,
    val seconds: Long,
)

internal data class ReadingSummary(
    /** The day recording began (or was last cleared). */
    val sinceEpochDay: Long,
    /** How many days the summary covers, at most [READING_STATS_WINDOW_DAYS]. */
    val days: Int,
    val homeSeconds: Long,
    /** Communities with any reading, most time first. */
    val communities: List<CommunityReading>,
) {
    val totalSeconds: Long
        get() = homeSeconds + communities.sumOf(CommunityReading::seconds)

    /** True once recording has run long enough to call a community unread. */
    val suggestionsReady: Boolean
        get() = days >= READING_STATS_SUGGESTION_MIN_DAYS

    fun hasRead(communityName: String): Boolean =
        communities.any { it.communityName.equals(communityName, ignoreCase = true) }
}

/**
 * The reading counts on this device. Every call reads or writes files, so
 * callers run it off the main thread.
 */
internal class ReadingStatsStore(
    private val directory: File,
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) {
    private val lock = Any()
    private val enabledState = MutableStateFlow<Boolean?>(null)
    private val revisionState = MutableStateFlow(0L)
    private var loaded: Loaded? = null

    /** Null until [load] has read the settings. */
    val enabled: StateFlow<Boolean?> = enabledState.asStateFlow()

    /** Moves on every change, so a page showing a summary reads it again. */
    val revision: StateFlow<Long> = revisionState.asStateFlow()

    fun load() {
        synchronized(lock) { state() }
    }

    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        val current = state()
        if (current.enabled == enabled) return@synchronized
        // Turning it off deletes what was recorded; turning it on starts today.
        save(Loaded(enabled = enabled, sinceEpochDay = today(), buckets = emptyMap()))
    }

    fun clear() = synchronized(lock) {
        val current = state()
        save(current.copy(sinceEpochDay = today(), buckets = emptyMap()))
    }

    fun record(
        accountStableId: String,
        target: String,
        opens: Int = 0,
        postOpens: Int = 0,
        seconds: Long = 0L,
    ) = synchronized(lock) {
        val current = state()
        if (!current.enabled) return@synchronized
        if (opens <= 0 && postOpens <= 0 && seconds <= 0L) return@synchronized
        val key = BucketKey(today(), accountKey(accountStableId), normalizeTarget(target) ?: return@synchronized)
        val before = current.buckets[key] ?: Counts()
        val after = Counts(
            opens = before.opens + opens.coerceAtLeast(0),
            postOpens = before.postOpens + postOpens.coerceAtLeast(0),
            seconds = before.seconds + seconds.coerceAtLeast(0L),
        )
        save(current.copy(buckets = current.buckets + (key to after)))
    }

    fun summary(accountStableId: String): ReadingSummary = synchronized(lock) {
        val current = state()
        val day = today()
        val firstDay = maxOf(current.sinceEpochDay, day - READING_STATS_WINDOW_DAYS + 1)
        val account = accountKey(accountStableId)
        val byTarget = current.buckets
            .filterKeys { key -> key.account == account && key.epochDay >= firstDay }
            .entries
            .groupBy({ it.key.target }, { it.value })
            .mapValues { (_, counts) ->
                Counts(
                    opens = counts.sumOf(Counts::opens),
                    postOpens = counts.sumOf(Counts::postOpens),
                    seconds = counts.sumOf(Counts::seconds),
                )
            }
        ReadingSummary(
            sinceEpochDay = current.sinceEpochDay,
            days = (day - firstDay + 1).toInt().coerceIn(1, READING_STATS_WINDOW_DAYS),
            homeSeconds = byTarget[READING_STATS_HOME_TARGET]?.seconds ?: 0L,
            communities = byTarget
                .filterKeys { it != READING_STATS_HOME_TARGET }
                .map { (name, counts) -> CommunityReading(name, counts.opens, counts.postOpens, counts.seconds) }
                .sortedWith(
                    compareByDescending<CommunityReading> { it.seconds }
                        .thenByDescending { it.opens + it.postOpens }
                        .thenBy { it.communityName },
                ),
        )
    }

    private fun state(): Loaded {
        loaded?.let { return it }
        val read = readFiles()
        val pruned = read.copy(buckets = prune(read.buckets))
        loaded = pruned
        enabledState.value = pruned.enabled
        return pruned
    }

    private fun save(next: Loaded) {
        val pruned = next.copy(buckets = prune(next.buckets))
        loaded = pruned
        runCatching { writeFiles(pruned) }
        enabledState.value = pruned.enabled
        revisionState.value += 1
    }

    private fun prune(buckets: Map<BucketKey, Counts>): Map<BucketKey, Counts> {
        val oldest = today() - READING_STATS_WINDOW_DAYS + 1
        return buckets.filterKeys { it.epochDay >= oldest }
    }

    private fun readFiles(): Loaded {
        val settings = runCatching { File(directory, SETTINGS_FILE).readLines() }.getOrDefault(emptyList())
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        val enabled = settings["enabled"] == "true"
        val since = settings["since"]?.toLongOrNull() ?: today()
        if (!enabled) return Loaded(enabled = false, sinceEpochDay = since, buckets = emptyMap())
        val buckets = runCatching { File(directory, DAYS_FILE).readLines() }.getOrDefault(emptyList())
            .mapNotNull(::decodeBucket)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, counts) ->
                Counts(counts.sumOf(Counts::opens), counts.sumOf(Counts::postOpens), counts.sumOf(Counts::seconds))
            }
        return Loaded(enabled = true, sinceEpochDay = since, buckets = buckets)
    }

    private fun writeFiles(state: Loaded) {
        directory.mkdirs()
        File(directory, SETTINGS_FILE).writeText("enabled=${state.enabled}\nsince=${state.sinceEpochDay}\n")
        val days = File(directory, DAYS_FILE)
        if (!state.enabled || state.buckets.isEmpty()) {
            days.delete()
            return
        }
        val text = state.buckets.entries.joinToString(separator = "\n", postfix = "\n") { (key, counts) ->
            listOf(
                RECORD_PREFIX,
                key.epochDay.toString(),
                key.account,
                key.target,
                counts.opens.toString(),
                counts.postOpens.toString(),
                counts.seconds.toString(),
            ).joinToString("|")
        }
        val temporary = File(directory, "$DAYS_FILE.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(days)) {
            days.writeText(text)
            temporary.delete()
        }
    }

    private fun decodeBucket(line: String): Pair<BucketKey, Counts>? {
        val parts = line.split('|')
        if (parts.size != 7 || parts[0] != RECORD_PREFIX) return null
        val day = parts[1].toLongOrNull() ?: return null
        val account = parts[2].takeIf { it.matches(ACCOUNT_KEY_PATTERN) } ?: return null
        val target = normalizeTarget(parts[3]) ?: return null
        val opens = parts[4].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val postOpens = parts[5].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val seconds = parts[6].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        return BucketKey(day, account, target) to Counts(opens, postOpens, seconds)
    }

    private data class Loaded(
        val enabled: Boolean,
        val sinceEpochDay: Long,
        val buckets: Map<BucketKey, Counts>,
    )

    private data class BucketKey(val epochDay: Long, val account: String, val target: String)

    private data class Counts(val opens: Int = 0, val postOpens: Int = 0, val seconds: Long = 0L)

    companion object {
        private const val SETTINGS_FILE = "settings-v1.txt"
        private const val DAYS_FILE = "days-v1.log"
        private const val RECORD_PREFIX = "D"
        private val ACCOUNT_KEY_PATTERN = Regex("[0-9a-f]{16}")
        private val TARGET_PATTERN = Regex("[a-z0-9_]{1,64}")

        /** The account is kept as a short hash, so the file holds no account ID. */
        internal fun accountKey(accountStableId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(accountStableId.toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString("") { byte -> "%02x".format(byte) }

        internal fun normalizeTarget(target: String): String? {
            if (target == READING_STATS_HOME_TARGET) return target
            val name = target.trim().removePrefix("/").removePrefix("r/").lowercase()
            return name.takeIf { it.matches(TARGET_PATTERN) }
        }
    }
}

/** One store per process, in no-backup storage. */
internal object ReadingStats {
    @Volatile
    private var store: ReadingStatsStore? = null

    fun store(context: Context): ReadingStatsStore =
        store ?: synchronized(this) {
            store ?: ReadingStatsStore(File(context.applicationContext.noBackupFilesDir, "reading"))
                .also { store = it }
        }

    internal fun installForTest(replacement: ReadingStatsStore?) {
        store = replacement
    }
}
