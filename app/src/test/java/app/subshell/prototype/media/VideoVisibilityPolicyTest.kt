package app.subshell.prototype.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoVisibilityPolicyTest {
    @Test
    fun manualVideoKeepsPlaybackWhileAnyMeaningfulSurfaceRemainsVisible() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "a",
            manualVideoId = "a",
            metricsByMediaId = mapOf(
                "a" to metrics(visible = 0.12f, viewport = 0.05f, offset = -0.45f),
                "b" to metrics(visible = 0.88f, viewport = 0.30f, offset = 0.02f),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(motion = FeedViewportMotion.DRAGGING),
            orderedVideoIds = listOf("a", "b"),
        )

        assertEquals("a", decision.activeVideoId)
        assertEquals("a", decision.manualVideoId)
    }

    @Test
    fun autoplayNeverStillAllowsManualPlayback() {
        val manual = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "a",
            manualVideoId = "a",
            metricsByMediaId = mapOf("a" to metrics(0.80f, 0.30f, 0f)),
            autoplayEnabled = false,
            viewport = FeedViewportState(motion = FeedViewportMotion.DRAGGING),
        )
        assertEquals("a", manual.activeVideoId)

        val automatic = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = null,
            manualVideoId = null,
            metricsByMediaId = mapOf("a" to metrics(0.80f, 0.30f, 0f)),
            autoplayEnabled = false,
            viewport = FeedViewportState(),
        )
        assertNull(automatic.activeVideoId)
    }

    @Test
    fun idleOwnerIsClosestToViewportAnchorIndependentOfSurfaceSize() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "d",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "d" to metrics(visible = 0.66f, viewport = 0.34f, offset = -0.22f),
                "e" to metrics(visible = 0.32f, viewport = 0.07f, offset = 0.03f),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(motion = FeedViewportMotion.IDLE),
            orderedVideoIds = listOf("d", "e"),
        )

        assertEquals("e", decision.activeVideoId)
        assertEquals("viewport_idle_takeover", decision.transitionReason)
    }

    @Test
    fun downwardDragUsesForwardAnchorForLargeToSmallHandoff() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "d",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "d" to metrics(visible = 0.56f, viewport = 0.31f, offset = -0.16f),
                "e" to metrics(visible = 0.44f, viewport = 0.08f, offset = 0.06f),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.DRAGGING,
                direction = FeedScrollDirection.DOWN,
            ),
            orderedVideoIds = listOf("d", "e"),
        )

        assertEquals("e", decision.activeVideoId)
        assertEquals("viewport_drag_takeover", decision.transitionReason)
    }

    @Test
    fun downwardDragCannotSkipShortGForH() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "f",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "f" to metrics(0.52f, 0.29f, -0.15f),
                "g" to metrics(0.42f, 0.06f, 0.08f),
                "h" to metrics(0.22f, 0.05f, 0.31f, terminal = true),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.DRAGGING,
                direction = FeedScrollDirection.DOWN,
            ),
            orderedVideoIds = listOf("f", "g", "h"),
        )

        assertEquals("g", decision.activeVideoId)
    }

    @Test
    fun physicalBottomSelectsHDeterministically() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "g",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "g" to metrics(0.70f, 0.08f, -0.08f),
                "h" to metrics(0.18f, 0.045f, 0.34f, terminal = true),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.IDLE,
                direction = FeedScrollDirection.DOWN,
                atEnd = true,
            ),
            orderedVideoIds = listOf("g", "h"),
        )

        assertEquals("h", decision.activeVideoId)
        assertEquals("viewport_end_owner", decision.transitionReason)
    }

    @Test
    fun physicalEndDoesNotForceAnUnexhaustedTerminalCandidate() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "g",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "g" to metrics(0.70f, 0.08f, -0.08f),
                "h" to metrics(0.18f, 0.045f, 0.34f, terminal = false),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.IDLE,
                direction = FeedScrollDirection.DOWN,
                atEnd = true,
            ),
            orderedVideoIds = listOf("g", "h"),
        )

        assertEquals("g", decision.activeVideoId)
    }

    @Test
    fun physicalTopSelectsA() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "b",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "a" to metrics(0.18f, 0.045f, -0.34f),
                "b" to metrics(0.70f, 0.20f, 0.05f),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.IDLE,
                direction = FeedScrollDirection.UP,
                atStart = true,
            ),
            orderedVideoIds = listOf("a", "b"),
        )

        assertEquals("a", decision.activeVideoId)
        assertEquals("viewport_start_owner", decision.transitionReason)
    }

    @Test
    fun flingNeverStartsOrReplacesPlayback() {
        val retained = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "f",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "f" to metrics(0.12f, 0.03f, -0.40f),
                "g" to metrics(0.90f, 0.20f, 0.02f),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.FLINGING,
                direction = FeedScrollDirection.DOWN,
            ),
            orderedVideoIds = listOf("f", "g"),
        )
        assertEquals("f", retained.activeVideoId)

        val noAcquire = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = null,
            manualVideoId = null,
            metricsByMediaId = mapOf("g" to metrics(0.90f, 0.20f, 0.02f)),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.FLINGING,
                direction = FeedScrollDirection.DOWN,
            ),
            orderedVideoIds = listOf("g"),
        )
        assertNull(noAcquire.activeVideoId)
    }

    @Test
    fun hysteresisRetainsCurrentOwnerWhenAnchorScoresAreNearlyTied() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "d",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "d" to metrics(0.55f, 0.20f, -0.01f),
                "e" to metrics(0.55f, 0.20f, 0.01f),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(motion = FeedViewportMotion.IDLE),
            orderedVideoIds = listOf("d", "e"),
        )

        assertEquals("d", decision.activeVideoId)
    }

    @Test
    fun upwardReverseFromHSelectsG() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "h",
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "g" to metrics(0.42f, 0.06f, -0.08f),
                "h" to metrics(0.42f, 0.10f, 0.16f, terminal = true),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.DRAGGING,
                direction = FeedScrollDirection.UP,
                atEnd = false,
            ),
            orderedVideoIds = listOf("g", "h"),
        )

        assertEquals("g", decision.activeVideoId)
    }

    @Test
    fun shortWideMediaQualifiesByActualVisibleSurface() {
        val decision = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = null,
            manualVideoId = null,
            metricsByMediaId = mapOf(
                "g" to metrics(visible = 0.42f, viewport = 0.06f, offset = 0.08f),
            ),
            autoplayEnabled = true,
            viewport = FeedViewportState(
                motion = FeedViewportMotion.DRAGGING,
                direction = FeedScrollDirection.DOWN,
            ),
            orderedVideoIds = listOf("g"),
        )

        assertEquals("g", decision.activeVideoId)
    }

    @Test
    fun preloadFollowsFirstVisibleVideoAheadInTravelDirection() {
        val preload = VideoVisibilityPolicy.selectPreloadCandidate(
            activeVideoId = "f",
            metricsByMediaId = mapOf(
                "f" to metrics(0.52f, 0.29f, -0.15f),
                "g" to metrics(0.08f, 0.02f, 0.28f),
                "h" to metrics(0.18f, 0.045f, 0.31f, terminal = true),
            ),
            orderedVideoIds = listOf("f", "g", "h"),
            viewport = FeedViewportState(
                motion = FeedViewportMotion.DRAGGING,
                direction = FeedScrollDirection.DOWN,
            ),
        )

        assertEquals("g", preload)
    }

    @Test
    fun terminalIdentityUsesLastVideoOnlyWhenFeedIsExhausted() {
        assertEquals(
            "h",
            VideoVisibilityPolicy.selectTerminalVideoId(
                orderedVideoIds = listOf("a", "g", "h"),
                feedExhausted = true,
            ),
        )
        assertNull(
            VideoVisibilityPolicy.selectTerminalVideoId(
                orderedVideoIds = listOf("a", "g", "h"),
                feedExhausted = false,
            ),
        )
    }

    @Test
    fun rampedAnchorStartsNeutralAndReachesFivePercentThroughProductionPolicy() {
        val metrics = mapOf(
            "f" to metrics(0.50f, 0.20f, -0.02f),
            "g" to metrics(0.50f, 0.20f, 0.04f),
        )
        val viewport = FeedViewportState(
            motion = FeedViewportMotion.DRAGGING,
            direction = FeedScrollDirection.DOWN,
        )

        val atDirectionChange = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "f",
            manualVideoId = null,
            metricsByMediaId = metrics,
            autoplayEnabled = true,
            viewport = viewport,
            orderedVideoIds = listOf("f", "g"),
            distanceSinceDirectionChangeFraction = 0f,
        )
        val afterRamp = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "f",
            manualVideoId = null,
            metricsByMediaId = metrics,
            autoplayEnabled = true,
            viewport = viewport,
            orderedVideoIds = listOf("f", "g"),
            distanceSinceDirectionChangeFraction = 0.12f,
        )

        assertEquals("f", atDirectionChange.activeVideoId)
        assertEquals("g", afterRamp.activeVideoId)
    }

    @Test
    fun rampedAnchorIsSymmetricForUpwardTravel() {
        val metrics = mapOf(
            "f" to metrics(0.50f, 0.20f, -0.04f),
            "g" to metrics(0.50f, 0.20f, 0.02f),
        )
        val viewport = FeedViewportState(
            motion = FeedViewportMotion.DRAGGING,
            direction = FeedScrollDirection.UP,
        )

        val atDirectionChange = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "g",
            manualVideoId = null,
            metricsByMediaId = metrics,
            autoplayEnabled = true,
            viewport = viewport,
            orderedVideoIds = listOf("f", "g"),
            distanceSinceDirectionChangeFraction = 0f,
        )
        val afterRamp = VideoVisibilityPolicy.selectViewportOwner(
            activeVideoId = "g",
            manualVideoId = null,
            metricsByMediaId = metrics,
            autoplayEnabled = true,
            viewport = viewport,
            orderedVideoIds = listOf("f", "g"),
            distanceSinceDirectionChangeFraction = 0.12f,
        )

        assertEquals("g", atDirectionChange.activeVideoId)
        assertEquals("f", afterRamp.activeVideoId)
    }

    @Test
    fun surfaceMeasurementIncludesViewportGeometry() {
        val measured = VideoVisibilityPolicy.measureSurface(
            left = 0f,
            top = 100f,
            right = 100f,
            bottom = 300f,
            viewportWidth = 100,
            viewportHeight = 1_000,
            isTerminalItem = false,
        )

        assertEquals(0.10f, measured.surfaceTopFraction, 0.0001f)
        assertEquals(0.30f, measured.surfaceBottomFraction, 0.0001f)
        assertEquals(0.20f, measured.surfaceHeightFraction, 0.0001f)
        assertTrue(measured.centerOffsetFraction < 0f)
    }

    @Test
    fun visibleAreaUsesTheMediaRectangleRatherThanThePostCard() {
        assertEquals(
            0.5f,
            VideoVisibilityPolicy.visibleAreaFraction(
                left = 0f,
                top = -50f,
                right = 100f,
                bottom = 50f,
                viewportWidth = 100,
                viewportHeight = 200,
            ),
            0.0001f,
        )
    }

    private fun metrics(
        visible: Float,
        viewport: Float,
        offset: Float,
        terminal: Boolean = false,
    ): VideoSurfaceMetrics = VideoSurfaceMetrics(
        visibleFraction = visible,
        visibleViewportFraction = viewport,
        centerDistanceFraction = kotlin.math.abs(offset),
        isTerminalItem = terminal,
        centerOffsetFraction = offset,
    )
}
