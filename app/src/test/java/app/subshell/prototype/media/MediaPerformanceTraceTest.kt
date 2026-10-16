package app.subshell.prototype.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPerformanceTraceTest {
    @Test
    fun eventLineIsStableAndLogcatParseable() {
        assertEquals(
            "t_ms=123 event=first_frame_rendered transition=7 media=video-lab-a state=ready",
            formatMediaPerformanceEvent(
                timestampMs = 123L,
                event = "first frame rendered",
                mediaId = "video-lab-a",
                transitionId = 7L,
                details = "state=ready",
            ),
        )
    }

    @Test
    fun playbackStateNamesAreStable() {
        assertEquals("idle", playbackStateName(androidx.media3.common.Player.STATE_IDLE))
        assertEquals("buffering", playbackStateName(androidx.media3.common.Player.STATE_BUFFERING))
        assertEquals("ready", playbackStateName(androidx.media3.common.Player.STATE_READY))
        assertEquals("ended", playbackStateName(androidx.media3.common.Player.STATE_ENDED))
    }

    // LOW_MEDIA_TRACE_IDLE_IN_RELEASE_V1 (OCB-564/565)
    @Test
    fun withoutASinkNothingIsTracedAndOpenSectionsAreBounded() {
        val begins = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        MediaPerformanceTrace.resetTestSink()
        MediaPerformanceTrace.mark(event = "ignored")
        // No sink installed (a release build): nothing is opened.
        MediaPerformanceTrace.beginTransition("video-a", null, "autoplay")
        MediaPerformanceTrace.installTestTraceSinks(
            asyncBegin = { _, cookie -> begins += cookie },
            asyncEnd = { _, cookie -> ends += cookie },
            counter = { _, _ -> },
        )
        try {
            repeat(70) { index -> MediaPerformanceTrace.beginTransition("video-$index", null, "autoplay") }
        } finally {
            MediaPerformanceTrace.resetTestSink()
        }

        assertEquals(70, begins.size)
        assertEquals("transitions never ended are closed oldest first", 6, ends.size)
        assertEquals(begins.take(6), ends)
    }

    @Test
    fun traceSinkIsJvmTestSafeAndDeterministic() {
        val lines = mutableListOf<String>()
        MediaPerformanceTrace.installTestSink(
            clockMs = { 321L },
            eventSink = lines::add,
        )

        try {
            MediaPerformanceTrace.mark(
                event = "candidate committed",
                mediaId = "video-lab-a",
                transitionId = 9L,
                details = "reason=autoplay",
            )
        } finally {
            MediaPerformanceTrace.resetTestSink()
        }

        assertEquals(
            listOf(
                "t_ms=321 event=candidate_committed transition=9 media=video-lab-a reason=autoplay",
            ),
            lines,
        )
    }
    @Test
    fun transitionIncludesViewportMotionAndEdge() {
        val lines = mutableListOf<String>()
        MediaPerformanceTrace.installTestSink(
            clockMs = { 500L },
            eventSink = lines::add,
        )

        try {
            MediaPerformanceTrace.beginTransition(
                mediaId = "video-lab-h",
                previousMediaId = "video-lab-g",
                reason = "viewport_end_owner",
                visibleFraction = 0.18f,
                scrollDirection = "down",
                viewportMotion = "idle",
                viewportEdge = "end",
            )
        } finally {
            MediaPerformanceTrace.resetTestSink()
        }

        val line = lines.single()
        assertTrue(line.contains("reason=viewport_end_owner"))
        assertTrue(line.contains("previous=video-lab-g"))
        assertTrue(line.contains("viewport_motion=idle"))
        assertTrue(line.contains("viewport_edge=end"))
    }

    @Test
    fun transitionOpensAndFirstFrameCanCloseAsyncTrace() {
        val begins = mutableListOf<Pair<String, Int>>()
        val ends = mutableListOf<Pair<String, Int>>()
        val counters = mutableListOf<Pair<String, Long>>()
        MediaPerformanceTrace.installTestSink(
            clockMs = { 700L },
            eventSink = {},
        )
        MediaPerformanceTrace.installTestTraceSinks(
            asyncBegin = { name, cookie -> begins += name to cookie },
            asyncEnd = { name, cookie -> ends += name to cookie },
            counter = { name, value -> counters += name to value },
        )

        try {
            val transition = MediaPerformanceTrace.beginTransition(
                mediaId = "video-lab-e",
                previousMediaId = "video-lab-d",
                reason = "viewport_drag_takeover",
            )
            MediaPerformanceTrace.traceCounter("subshell.media.score_delta_milli", 42L)
            MediaPerformanceTrace.endTransition(
                transitionId = transition,
                mediaId = "video-lab-e",
                outcome = "first_frame",
            )
        } finally {
            MediaPerformanceTrace.resetTestSink()
        }

        assertEquals(1, begins.size)
        assertEquals(begins, ends)
        assertEquals(listOf("subshell.media.score_delta_milli" to 42L), counters)
    }


}
