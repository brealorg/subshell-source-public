package app.subshell.prototype.data.diagnostics

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE248_JANK_SUMMARY_PER_FOREGROUND_SESSION_V1 (#248): slow frames are
 * counted per screen type and recorded once per foreground session as counts
 * only; the log line keeps its older shapes readable.
 */
class Issue248JankSummaryTest {
    @Test
    fun aSessionWithSlowFramesIsSummarisedPerScreenAndReset() {
        val tally = JankTally()
        repeat(100) { tally.add(durationNanos = 8_000_000, isJank = false, screen = "home") }
        tally.add(durationNanos = 40_000_000, isJank = true, screen = "home")
        tally.add(durationNanos = 90_000_000, isJank = true, screen = "post")
        tally.add(durationNanos = 30_000_000, isJank = true, screen = "post")
        tally.add(durationNanos = 20_000_000, isJank = true, screen = "Some Title With Spaces")

        assertEquals(
            JankSummary(frames = 104, jankFrames = 4, worstFrameMs = 90, worstScreen = "post"),
            tally.drain(),
        )
        assertNull(tally.drain())
    }

    @Test
    fun aSessionWithoutSlowFramesRecordsNothing() {
        val tally = JankTally()
        repeat(50) { tally.add(durationNanos = 8_000_000, isJank = false, screen = "home") }

        assertNull(tally.drain())
    }

    @Test
    fun theSummaryRoundTripsThroughTheLogAndTheReport() {
        val directory = Files.createTempDirectory("jank").toFile()
        val store = DiagnosticEventStore(directory)
        store.recordJankSummary(JankSummary(frames = 104, jankFrames = 4, worstFrameMs = 90, worstScreen = "post"))

        val event = DiagnosticEventStore(directory).snapshot().events.single()

        assertEquals(DiagnosticOperation.UI_JANK_OBSERVED, event.operation)
        assertEquals("post", event.action)
        assertEquals(104, event.frames)
        assertEquals(4, event.jankFrames)
        assertEquals(90L, event.worstFrameMs)
    }
}
