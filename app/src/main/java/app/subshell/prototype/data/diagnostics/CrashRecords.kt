package app.subshell.prototype.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import app.subshell.prototype.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

// CRASH01_CRASHES_ARE_RECORDED_LOCALLY_V1 (owner 2026-10-16): an uncaught crash,
// a native crash or an ANR is kept on the device, in no-backup storage next to
// the diagnostic events. Nothing leaves the device by itself: on the next start
// the tray offers "Send report", which opens Diagnostics, where the reader sees
// the report before choosing where it goes. A record holds only the exception
// class names and code locations (class, method, line) — never an exception's
// own text, which can carry paths, URLs or account details.

internal enum class CrashKind(val token: String) {
    CRASH("crash"),
    NATIVE_CRASH("native_crash"),
    NOT_RESPONDING("not_responding"),
}

internal data class CrashRecord(
    val kind: CrashKind,
    val timestampEpochMs: Long,
    /** The app version that crashed, or null when Android did not say (ANR, native). */
    val appVersionCode: Long?,
    val exceptionClasses: List<String> = emptyList(),
    val topFrame: String? = null,
    val appFrames: List<String> = emptyList(),
    val acknowledged: Boolean = false,
) {
    init {
        require(timestampEpochMs >= 0L)
        require(appVersionCode == null || appVersionCode > 0L)
        require(exceptionClasses.size <= MAX_CRASH_EXCEPTION_CLASSES)
        require(appFrames.size <= MAX_CRASH_APP_FRAMES)
        (exceptionClasses + appFrames + listOfNotNull(topFrame)).forEach { token ->
            require(token.matches(CRASH_TOKEN_PATTERN))
        }
    }
}

/** The crash signature of [throwable]: class names and code locations only. */
internal fun crashRecordOf(
    throwable: Throwable,
    nowEpochMs: Long = System.currentTimeMillis(),
    appVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
): CrashRecord {
    val chain = generateSequence(throwable) { it.cause }
        .take(MAX_CRASH_CHAIN_WALK)
        .distinct()
        .toList()
    val appFrames = chain.asSequence()
        .flatMap { it.stackTrace.asSequence() }
        .filter { frame -> frame.className.startsWith(APP_PACKAGE_PREFIX) }
        .map { frame -> crashFrameToken(frame, stripAppPackage = true) }
        .filterNotNull()
        .distinct()
        .take(MAX_CRASH_APP_FRAMES)
        .toList()
    return CrashRecord(
        kind = CrashKind.CRASH,
        timestampEpochMs = nowEpochMs,
        appVersionCode = appVersionCode.takeIf { it > 0L },
        exceptionClasses = chain.mapNotNull { safeCrashToken(it.javaClass.name) }
            .take(MAX_CRASH_EXCEPTION_CLASSES),
        topFrame = throwable.stackTrace.firstOrNull()?.let { crashFrameToken(it, stripAppPackage = false) },
        appFrames = appFrames,
    )
}

private fun crashFrameToken(frame: StackTraceElement, stripAppPackage: Boolean): String? {
    val className = if (stripAppPackage) {
        frame.className.removePrefix(APP_PACKAGE_PREFIX)
    } else {
        frame.className
    }
    return safeCrashToken("$className.${frame.methodName}:${frame.lineNumber}")
}

private fun safeCrashToken(value: String): String? =
    value.takeIf { it.matches(CRASH_TOKEN_PATTERN) }

/**
 * The device's last [MAX_CRASH_RECORDS] crashes. Kept apart from the diagnostic
 * event buffer so the uncaught-exception handler writes one small line and
 * nothing else while the process is going down.
 */
internal class CrashRecordStore internal constructor(private val directory: File) {
    constructor(context: Context) : this(
        directory = File(context.applicationContext.noBackupFilesDir, CRASH_DIRECTORY_NAME),
    )

    private val file get() = File(directory, CRASH_FILE_NAME)

    fun record(record: CrashRecord) = synchronized(CRASH_LOCK) {
        runCatching { write((readLocked() + record).takeLast(MAX_CRASH_RECORDS)) }
    }

    fun records(): List<CrashRecord> = synchronized(CRASH_LOCK) { readLocked() }

    /** The records not yet offered to the reader, now marked as offered. */
    fun takeUnacknowledged(): List<CrashRecord> = synchronized(CRASH_LOCK) {
        val records = readLocked()
        val fresh = records.filterNot { it.acknowledged }
        if (fresh.isNotEmpty()) {
            runCatching { write(records.map { it.copy(acknowledged = true) }) }
        }
        fresh
    }

    fun clear() = synchronized(CRASH_LOCK) {
        runCatching { file.delete() }
    }

    private fun readLocked(): List<CrashRecord> = runCatching {
        if (!file.isFile || file.length() > MAX_CRASH_FILE_BYTES) return emptyList()
        file.readLines(Charsets.UTF_8).mapNotNull(::decodeCrashRecord)
    }.getOrDefault(emptyList())

    private fun write(records: List<CrashRecord>) {
        directory.mkdirs()
        val temp = File(directory, "$CRASH_FILE_NAME.tmp")
        temp.writeText(records.joinToString(separator = "") { encodeCrashRecord(it) + "\n" }, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}

internal fun encodeCrashRecord(record: CrashRecord): String = listOf(
    CRASH_RECORD_PREFIX,
    record.kind.token,
    record.timestampEpochMs.toString(),
    record.appVersionCode?.toString() ?: NULL_FIELD,
    if (record.acknowledged) "1" else "0",
    record.exceptionClasses.joinToString(",").ifEmpty { NULL_FIELD },
    record.topFrame ?: NULL_FIELD,
    record.appFrames.joinToString(",").ifEmpty { NULL_FIELD },
).joinToString("|")

internal fun decodeCrashRecord(line: String): CrashRecord? = runCatching {
    val fields = line.split("|")
    if (fields.size != CRASH_RECORD_FIELD_COUNT || fields[0] != CRASH_RECORD_PREFIX) return null
    fun list(field: String) = if (field == NULL_FIELD) emptyList() else field.split(",")
    CrashRecord(
        kind = CrashKind.entries.first { it.token == fields[1] },
        timestampEpochMs = fields[2].toLong(),
        appVersionCode = fields[3].takeUnless { it == NULL_FIELD }?.toLong(),
        acknowledged = when (fields[4]) {
            "1" -> true
            "0" -> false
            else -> return null
        },
        exceptionClasses = list(fields[5]),
        topFrame = fields[6].takeUnless { it == NULL_FIELD },
        appFrames = list(fields[7]),
    )
}.getOrNull()

/** Installs the uncaught-exception recorder once per process, ahead of the previous handler. */
internal object CrashRecorder {
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        Thread.setDefaultUncaughtExceptionHandler(
            handler(CrashRecordStore(context), Thread.getDefaultUncaughtExceptionHandler()),
        )
    }

    /** Records the crash, then hands it to [previous] exactly as before. */
    fun handler(
        store: CrashRecordStore,
        previous: Thread.UncaughtExceptionHandler?,
    ): Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        try {
            store.record(crashRecordOf(throwable))
        } catch (_: Throwable) {
            // Recording must never stand between the crash and Android.
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
}

/**
 * Adds the native crashes and ANRs Android recorded since the last look
 * (Android 11+). Java crashes come from [CrashRecorder] with their code
 * locations, so Android's own crash entries are not added twice. The first
 * look only sets the mark: exits from before this existed are not offered.
 */
internal fun recordProcessExitReasons(context: Context, store: CrashRecordStore) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val preferences = context.applicationContext
        .getSharedPreferences(CRASH_PREFERENCES, Context.MODE_PRIVATE)
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
    val exits = runCatching {
        activityManager.getHistoricalProcessExitReasons(null, 0, MAX_EXIT_REASONS)
    }.getOrDefault(emptyList())
    val seenUntil = preferences.getLong(EXIT_REASONS_SEEN_UNTIL_KEY, -1L)
    val newest = exits.maxOfOrNull { it.timestamp } ?: return
    if (seenUntil >= 0L) {
        exits.filter { it.timestamp > seenUntil }
            .sortedBy { it.timestamp }
            .forEach { exit ->
                val kind = when (exit.reason) {
                    ApplicationExitInfo.REASON_ANR -> CrashKind.NOT_RESPONDING
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashKind.NATIVE_CRASH
                    else -> null
                }
                if (kind != null) {
                    store.record(CrashRecord(kind = kind, timestampEpochMs = exit.timestamp, appVersionCode = null))
                }
            }
    }
    if (newest > seenUntil) {
        preferences.edit().putLong(EXIT_REASONS_SEEN_UNTIL_KEY, newest).apply()
    }
}

internal const val MAX_CRASH_RECORDS = 10
internal const val MAX_CRASH_APP_FRAMES = 8
internal const val MAX_CRASH_EXCEPTION_CLASSES = 4
private const val MAX_CRASH_CHAIN_WALK = 8
private const val MAX_EXIT_REASONS = 16
private const val MAX_CRASH_FILE_BYTES = 64L * 1024L
private const val APP_PACKAGE_PREFIX = "app.subshell.prototype."
private const val CRASH_DIRECTORY_NAME = "crashes"
private const val CRASH_FILE_NAME = "crashes-v1.log"
private const val CRASH_PREFERENCES = "subshell_crash_reports"
private const val EXIT_REASONS_SEEN_UNTIL_KEY = "exit_reasons_seen_until_epoch_ms"
private const val CRASH_RECORD_PREFIX = "C1"
private const val CRASH_RECORD_FIELD_COUNT = 8
private const val NULL_FIELD = "-"
private val CRASH_TOKEN_PATTERN = Regex("[A-Za-z0-9_.$<>:-]{1,200}")
private val CRASH_LOCK = Any()
