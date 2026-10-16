package app.subshell.prototype.media

import android.os.Build
import android.os.Trace
import android.util.Log
import app.subshell.prototype.BuildConfig
import java.util.concurrent.atomic.AtomicLong

internal const val MEDIA_PERFORMANCE_LOG_TAG = "subshell.media.perf"
private const val MAX_OPEN_ASYNC_SECTIONS = 64
private val MEDIA_PERFORMANCE_TOKEN_UNSAFE = Regex("[^A-Za-z0-9._-]+")

internal object MediaPerformanceTrace {
    private val nextTransitionId = AtomicLong(0L)
    private val openAsyncSections = linkedSetOf<Int>()

    @Volatile
    private var clockMs: () -> Long = { System.nanoTime() / 1_000_000L }

    @Volatile
    private var eventSink: (String) -> Unit = {}

    // LOW_MEDIA_TRACE_IDLE_IN_RELEASE_V1 (OCB-564/565): with no sink installed
    // (release builds) nothing is formatted and no open section is kept; with
    // one, at most MAX_OPEN_ASYNC_SECTIONS stay open (a transition replaced
    // before its first frame is ended as the oldest).
    @Volatile
    private var enabled = false

    @Volatile
    private var asyncBeginSink: (String, Int) -> Unit = { _, _ -> }

    @Volatile
    private var asyncEndSink: (String, Int) -> Unit = { _, _ -> }

    @Volatile
    private var counterSink: (String, Long) -> Unit = { _, _ -> }

    fun installAndroidLogSink() {
        if (!BuildConfig.DEBUG) return
        enabled = true
        eventSink = { line -> Log.i(MEDIA_PERFORMANCE_LOG_TAG, line) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            asyncBeginSink = Trace::beginAsyncSection
            asyncEndSink = Trace::endAsyncSection
            counterSink = Trace::setCounter
        }
    }

    internal fun installTestSink(
        clockMs: () -> Long,
        eventSink: (String) -> Unit,
    ) {
        this.clockMs = clockMs
        this.eventSink = eventSink
        enabled = true
    }

    internal fun installTestTraceSinks(
        asyncBegin: (String, Int) -> Unit,
        asyncEnd: (String, Int) -> Unit,
        counter: (String, Long) -> Unit,
    ) {
        asyncBeginSink = asyncBegin
        asyncEndSink = asyncEnd
        counterSink = counter
        enabled = true
    }

    internal fun resetTestSink() {
        clockMs = { System.nanoTime() / 1_000_000L }
        eventSink = {}
        asyncBeginSink = { _, _ -> }
        asyncEndSink = { _, _ -> }
        counterSink = { _, _ -> }
        enabled = false
        synchronized(openAsyncSections) { openAsyncSections.clear() }
    }

    internal fun nowMs(): Long = clockMs()

    fun beginTransition(
        mediaId: String,
        previousMediaId: String?,
        reason: String,
        visibleFraction: Float? = null,
        scrollDirection: String? = null,
        viewportMotion: String? = null,
        viewportEdge: String? = null,
    ): Long {
        val transitionId = nextTransitionId.incrementAndGet()
        if (!enabled) return transitionId
        traceAsyncBegin("subshell.media.handoff", transitionId)
        val details = buildString {
            append("reason=").append(normalizeMediaPerformanceToken(reason))
            append(" previous=").append(normalizeMediaPerformanceToken(previousMediaId ?: "none"))
            visibleFraction?.let {
                append(" visible_fraction=").append("%.3f".format(java.util.Locale.US, it))
            }
            scrollDirection?.let {
                append(" scroll_direction=").append(normalizeMediaPerformanceToken(it))
            }
            viewportMotion?.let {
                append(" viewport_motion=").append(normalizeMediaPerformanceToken(it))
            }
            viewportEdge?.let {
                append(" viewport_edge=").append(normalizeMediaPerformanceToken(it))
            }
        }
        mark(
            event = "candidate_committed",
            mediaId = mediaId,
            transitionId = transitionId,
            details = details,
        )
        return transitionId
    }

    fun endTransition(
        transitionId: Long,
        mediaId: String?,
        outcome: String,
    ) {
        if (!enabled || transitionId <= 0L || !traceAsyncEnd("subshell.media.handoff", transitionId)) return
        mark(
            event = "handoff_trace_ended",
            mediaId = mediaId,
            transitionId = transitionId,
            details = "outcome=${normalizeMediaPerformanceToken(outcome)}",
        )
    }

    fun traceCounter(name: String, value: Long) {
        counterSink(normalizeTraceName(name), value)
    }

    fun mark(
        event: String,
        mediaId: String? = null,
        transitionId: Long = 0L,
        details: String = "",
    ) {
        if (!enabled) return
        eventSink(
            formatMediaPerformanceEvent(
                timestampMs = clockMs(),
                event = event,
                mediaId = mediaId,
                transitionId = transitionId,
                details = details,
            ),
        )
    }

    private fun traceAsyncBegin(name: String, transitionId: Long) {
        val cookie = transitionId.toInt()
        val evicted = synchronized(openAsyncSections) {
            if (!openAsyncSections.add(cookie)) return
            if (openAsyncSections.size > MAX_OPEN_ASYNC_SECTIONS) {
                openAsyncSections.first().also(openAsyncSections::remove)
            } else {
                null
            }
        }
        evicted?.let { asyncEndSink(normalizeTraceName(name), it) }
        asyncBeginSink(normalizeTraceName(name), cookie)
    }

    private fun traceAsyncEnd(name: String, transitionId: Long): Boolean {
        val cookie = transitionId.toInt()
        val wasOpen = synchronized(openAsyncSections) { openAsyncSections.remove(cookie) }
        if (wasOpen) asyncEndSink(normalizeTraceName(name), cookie)
        return wasOpen
    }

    private fun normalizeTraceName(value: String): String = value
        .replace('|', '_')
        .replace('\n', '_')
        .take(120)
}

internal fun formatMediaPerformanceEvent(
    timestampMs: Long,
    event: String,
    mediaId: String?,
    transitionId: Long,
    details: String = "",
): String = buildString {
    append("t_ms=").append(timestampMs)
    append(" event=").append(normalizeMediaPerformanceToken(event))
    append(" transition=").append(transitionId)
    append(" media=").append(normalizeMediaPerformanceToken(mediaId ?: "none"))
    if (details.isNotBlank()) {
        append(' ').append(details.trim())
    }
}

internal fun normalizeMediaPerformanceToken(value: String): String = value
    .trim()
    .replace(MEDIA_PERFORMANCE_TOKEN_UNSAFE, "_")
    .ifEmpty { "none" }
