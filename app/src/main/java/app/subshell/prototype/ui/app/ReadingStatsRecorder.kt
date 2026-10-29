package app.subshell.prototype.ui.app

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.subshell.prototype.data.reading.ReadingStats
import app.subshell.prototype.data.reading.ReadingStatsStore
import app.subshell.prototype.ui.SubshellState
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

internal enum class ReadingTargetKind {
    HOME,
    COMMUNITY,
    POST,
}

/** What the screen in front counts as for "Your reading" (#268). */
internal data class ReadingTarget(
    val communityName: String,
    val kind: ReadingTargetKind,
    /** Changes only when a new page is opened. */
    val visitKey: String,
)

/** A screen left on for longer than this counts this much (the reader may have walked away). */
internal const val READING_STRETCH_MAX_SECONDS = 30L * 60L
internal const val READING_FLUSH_INTERVAL_MS = 60_000L
internal const val READING_COUNTED_VISITS_KEPT = 256

/**
 * ISSUE268_READING_STATS_COUNT_OPENS_AND_TIME_V1 (#268): turns what is on
 * screen into counts. A new community page counts one open, a new post one
 * post open for its community, and the time a target is in front (app
 * resumed) is added to it when it changes, the app pauses or [flush] runs.
 * Coming back to the same page (after a post, or after the app was away) is
 * not another open.
 */
internal class ReadingStatsRecorder(
    private val write: (account: String, target: String, opens: Int, postOpens: Int, seconds: Long) -> Unit,
) {
    private var current: Pair<String, ReadingTarget>? = null
    private var startedAtMs = 0L
    // Visits already counted (most recent last), so coming back to a page
    // under a post is not another open.
    private val countedVisits = LinkedHashSet<Pair<String, String>>()

    fun update(account: String?, target: ReadingTarget?, nowMs: Long) {
        val next = if (account != null && target != null) account to target else null
        if (next == current) return
        flush(nowMs)
        current = next
        startedAtMs = nowMs
        next ?: return
        val visit = next.first to next.second.visitKey
        if (!countedVisits.add(visit)) return
        if (countedVisits.size > READING_COUNTED_VISITS_KEPT) countedVisits.remove(countedVisits.first())
        when (next.second.kind) {
            ReadingTargetKind.COMMUNITY -> write(next.first, next.second.communityName, 1, 0, 0L)
            ReadingTargetKind.POST -> write(next.first, next.second.communityName, 0, 1, 0L)
            ReadingTargetKind.HOME -> Unit
        }
    }

    fun flush(nowMs: Long) {
        val (account, target) = current ?: return
        val seconds = ((nowMs - startedAtMs) / 1_000L).coerceIn(0L, READING_STRETCH_MAX_SECONDS)
        startedAtMs += seconds * 1_000L
        if (nowMs - startedAtMs > READING_STRETCH_MAX_SECONDS * 1_000L) startedAtMs = nowMs
        if (seconds > 0L) write(account, target.communityName, 0, 0, seconds)
    }
}

/** Reading-stats file work runs here, one call after another, off the main thread. */
internal val readingStatsExecutor: Executor by lazy {
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "subshell-reading-stats").apply { isDaemon = true }
    }
}

internal fun ReadingStatsStore.onDisk(block: ReadingStatsStore.() -> Unit) {
    readingStatsExecutor.execute { runCatching { block() } }
}

// One recorder per process and store, so a configuration change (the
// composition is rebuilt on the same page) is not counted as another open.
private var processRecorder: Pair<ReadingStatsStore, ReadingStatsRecorder>? = null

private fun processReadingStatsRecorder(store: ReadingStatsStore): ReadingStatsRecorder {
    processRecorder?.takeIf { it.first === store }?.let { return it.second }
    return ReadingStatsRecorder { account, target, opens, postOpens, seconds ->
        store.onDisk { record(account, target, opens, postOpens, seconds) }
    }.also { processRecorder = store to it }
}

@Composable
internal fun ReadingStatsEffect(state: SubshellState) {
    val context = LocalContext.current
    val store = remember(context) { ReadingStats.store(context) }
    LaunchedEffect(store) { store.onDisk { load() } }
    val enabled by store.enabled.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val recorder = remember(store) { processReadingStatsRecorder(store) }
    val target = state.readingTarget.takeIf {
        enabled == true && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    }
    val account = state.activeAccountId
    SideEffect { recorder.update(account, target, SystemClock.elapsedRealtime()) }
    LaunchedEffect(recorder, target != null) {
        while (target != null) {
            delay(READING_FLUSH_INTERVAL_MS)
            recorder.flush(SystemClock.elapsedRealtime())
        }
    }
    DisposableEffect(recorder) {
        onDispose { recorder.update(null, null, SystemClock.elapsedRealtime()) }
    }
}
