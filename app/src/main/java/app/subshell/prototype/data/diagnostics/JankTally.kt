package app.subshell.prototype.data.diagnostics

/**
 * ISSUE248_JANK_SUMMARY_PER_FOREGROUND_SESSION_V1 (#248): counts the frames
 * JankStats reports while the app is in front, per screen type (a route
 * token, never content), and hands one summary per foreground session to the
 * diagnostics log. Fed on the main thread by JankStats' listener; drained in
 * onStop.
 */
internal class JankTally {
    private var frames = 0
    private var jankFrames = 0
    private var worstFrameNanos = 0L
    private val jankByScreen = linkedMapOf<String, Int>()

    fun add(durationNanos: Long, isJank: Boolean, screen: String?) {
        frames += 1
        if (!isJank) return
        jankFrames += 1
        if (durationNanos > worstFrameNanos) worstFrameNanos = durationNanos
        val token = screen?.takeIf { it.matches(SCREEN_TOKEN) } ?: UNKNOWN_SCREEN
        jankByScreen[token] = (jankByScreen[token] ?: 0) + 1
    }

    /** The session's summary, or null when no frame was slow; resets the tally. */
    fun drain(): JankSummary? {
        val summary = if (jankFrames == 0) {
            null
        } else {
            JankSummary(
                frames = frames,
                jankFrames = jankFrames,
                worstFrameMs = worstFrameNanos / 1_000_000,
                worstScreen = jankByScreen.maxByOrNull { (_, count) -> count }?.key ?: UNKNOWN_SCREEN,
            )
        }
        frames = 0
        jankFrames = 0
        worstFrameNanos = 0L
        jankByScreen.clear()
        return summary
    }

    private companion object {
        val SCREEN_TOKEN = Regex("[a-z0-9_]{1,64}")
        const val UNKNOWN_SCREEN = "unknown"
    }
}

internal data class JankSummary(
    val frames: Int,
    val jankFrames: Int,
    val worstFrameMs: Long,
    /** The screen type with the most slow frames. */
    val worstScreen: String,
)
