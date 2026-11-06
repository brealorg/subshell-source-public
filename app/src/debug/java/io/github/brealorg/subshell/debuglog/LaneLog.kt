package io.github.brealorg.subshell.debuglog

import android.app.ActivityManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Process
import io.github.brealorg.subshell.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream

// LANELOG01_THE_LANE_BUILD_KEEPS_ITS_LOG_V1 (owner 2026-11-05): logcat's ring buffer holds far too
// little to read back a week of use, so a dev1/dev2 build copies this process's own logcat lines to
// rolling files in no-backup storage. tools/pull-lane-log.sh fetches them. All of it lives in
// src/debug: a release build has neither this code nor the Log calls (SECAUDIT15), and nothing
// leaves the phone by itself. An app can only read its own log lines, so no other app's are kept.

private const val LANE_LOG_DIRECTORY = "lane-log"
private const val LANE_LOG_MAX_FILE_BYTES = 2L * 1024 * 1024
private const val LANE_LOG_MAX_TOTAL_BYTES = 200L * 1024 * 1024
private const val LANE_LOG_RETRY_MS = 60_000L
private const val LANE_LOG_EXIT_REASONS = 16
private const val LANE_LOG_LAST_EXIT_FILE = "last-exit.txt"
private const val ROBOLECTRIC_FINGERPRINT = "robolectric"

/** Only a lane build on a real device: unit tests start this provider too. */
internal fun laneLogRuns(debugSlot: String, fingerprint: String): Boolean =
    debugSlot.isNotEmpty() && fingerprint != ROBOLECTRIC_FINGERPRINT

/** Appends lines to `lane-<ms>.log` files; each start begins a new one, the oldest go first. */
internal class LaneLogFiles(
    private val directory: File,
    private val maxFileBytes: Long = LANE_LOG_MAX_FILE_BYTES,
    private val maxTotalBytes: Long = LANE_LOG_MAX_TOTAL_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var current: File? = null
    private var output: FileOutputStream? = null
    private var currentBytes = 0L

    /** Written straight through, so a killed process loses nothing it had read. */
    @Synchronized
    fun append(line: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        if (output == null || currentBytes + bytes.size > maxFileBytes) roll()
        output?.write(bytes)
        currentBytes += bytes.size
    }

    private fun roll() {
        output?.close()
        directory.mkdirs()
        var stamp = now()
        var next = logFile(stamp)
        while (next.exists()) next = logFile(++stamp)
        current = next
        output = FileOutputStream(next, true)
        currentBytes = 0L
        trim()
    }

    private fun logFile(stamp: Long) = File(directory, "lane-%013d.log".format(stamp))

    private fun trim() {
        val files = directory.listFiles { file -> file.name.endsWith(".log") }
            .orEmpty()
            .sortedBy(File::getName)
        var total = files.sumOf(File::length)
        files.filter { it != current }.forEach { file ->
            if (total <= maxTotalBytes - maxFileBytes) return
            total -= file.length()
            file.delete()
        }
    }
}

/** Copies every line of [reader] until it ends. */
internal fun copyLaneLog(reader: BufferedReader, files: LaneLogFiles) {
    reader.forEachLine(files::append)
}

private fun followOwnLogcat(files: LaneLogFiles) {
    var firstRun = true
    while (true) {
        // A later run asks only for new lines: the buffer still holds what was already copied.
        val command = listOf("logcat", "-v", "threadtime", "--pid", Process.myPid().toString()) +
            if (firstRun) emptyList() else listOf("-T", "1")
        firstRun = false
        runCatching {
            val logcat = ProcessBuilder(command).redirectErrorStream(true).start()
            try {
                logcat.inputStream.bufferedReader().use { copyLaneLog(it, files) }
            } finally {
                logcat.destroy()
            }
        }.onFailure { failure -> files.append("lane-log: logcat stopped: ${failure.javaClass.name}") }
        files.append("lane-log: reading logcat again in ${LANE_LOG_RETRY_MS / 1000} s")
        Thread.sleep(LANE_LOG_RETRY_MS)
    }
}

/** Why earlier processes ended, which logcat lines of this process cannot say. Each is told once. */
private fun recordExitsSinceLastStart(context: Context, directory: File, files: LaneLogFiles) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val marker = File(directory, LANE_LOG_LAST_EXIT_FILE)
    val lastTold = marker.takeIf(File::exists)?.readText()?.trim()?.toLongOrNull() ?: 0L
    val exits = context.getSystemService(ActivityManager::class.java)
        .getHistoricalProcessExitReasons(null, 0, LANE_LOG_EXIT_REASONS)
        .filter { it.timestamp > lastTold }
        .sortedBy { it.timestamp }
    exits.forEach { exit ->
        files.append(
            "lane-log: exit time=${exit.timestamp} pid=${exit.pid} reason=${exit.reason} " +
                "status=${exit.status} importance=${exit.importance} pss_kb=${exit.pss} " +
                "description=${exit.description}",
        )
    }
    exits.lastOrNull()?.let { marker.writeText(it.timestamp.toString()) }
}

private fun runLaneLog(context: Context) {
    val directory = File(context.noBackupFilesDir, LANE_LOG_DIRECTORY)
    val files = LaneLogFiles(directory)
    files.append(
        "lane-log: start time=${System.currentTimeMillis()} pid=${Process.myPid()} " +
            "slot=${BuildConfig.DEBUG_SLOT} version=${BuildConfig.VERSION_NAME} sdk=${Build.VERSION.SDK_INT}",
    )
    runCatching { recordExitsSinceLastStart(context, directory, files) }
        .onFailure { failure -> files.append("lane-log: exits unknown: ${failure.javaClass.name}") }
    followOwnLogcat(files)
}

/** Android starts a provider with the process, so main needs no line for this. */
class LaneLogProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context ?: return false
        if (laneLogRuns(BuildConfig.DEBUG_SLOT, Build.FINGERPRINT)) {
            Thread({ runLaneLog(appContext) }, "subshell-lane-log").apply {
                isDaemon = true
                start()
            }
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
