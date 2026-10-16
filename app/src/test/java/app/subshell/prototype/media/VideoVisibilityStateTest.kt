package app.subshell.prototype.media

import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.ui.VideoAutoplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoVisibilityStateTest {
    @Test
    fun manuallyStartedVideoCannotBeStolenWhileItsSurfaceRemainsVisible() {
        val state = newState()
        val surfaceA = Any()
        val surfaceB = Any()
        state.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        state.updateFeedViewportState(idle())
        state.playVideo("a")
        state.updateVideoVisibility("a", surfaceA, metrics(0.12f, 0.05f, -0.45f))
        state.updateVideoVisibility("b", surfaceB, metrics(0.88f, 0.30f, 0.02f))

        assertEquals("a", state.activeAnimatedMediaId)

        state.updateVideoVisibility("a", surfaceA, 0f)
        assertEquals("b", state.activeAnimatedMediaId)
    }

    @Test
    fun directDragHandsOffLargeDToShortEWithoutWaitingForFingerRelease() {
        val state = newState()
        val surfaceD = Any()
        val surfaceE = Any()
        state.updateVideoFeedContext(listOf("d", "e"), terminalVideoId = "e")
        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("d", surfaceD, metrics(0.90f, 0.42f, 0.05f))
        assertEquals("d", state.activeAnimatedMediaId)

        state.updateFeedViewportState(draggingDown())
        state.updateVideoVisibility("d", surfaceD, metrics(0.56f, 0.31f, -0.16f))
        state.updateVideoVisibility("e", surfaceE, metrics(0.44f, 0.08f, 0.06f, terminal = true))

        assertEquals("e", state.activeAnimatedMediaId)
    }

    @Test
    fun flingPreloadsButDoesNotStartAReplacement() {
        val state = newState()
        val surfaceF = Any()
        val surfaceG = Any()
        state.updateVideoFeedContext(listOf("f", "g"), terminalVideoId = "g")
        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("f", surfaceF, metrics(0.90f, 0.42f, 0.05f))
        assertEquals("f", state.activeAnimatedMediaId)

        state.updateFeedViewportState(
            FeedViewportState(
                motion = FeedViewportMotion.FLINGING,
                direction = FeedScrollDirection.DOWN,
            ),
        )
        state.updateVideoVisibility("f", surfaceF, metrics(0.12f, 0.03f, -0.40f))
        state.updateVideoVisibility("g", surfaceG, metrics(0.90f, 0.20f, 0.02f, terminal = true))

        assertEquals("f", state.activeAnimatedMediaId)
        assertEquals("g", state.preloadVideoMediaId)
    }

    @Test
    fun steadyDragProducesFThenGThenHWithoutSkipOrPingPong() {
        val state = newState()
        val surfaceF = Any()
        val surfaceG = Any()
        val surfaceH = Any()
        state.updateVideoFeedContext(listOf("f", "g", "h"), terminalVideoId = "h")
        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("f", surfaceF, metrics(0.90f, 0.42f, 0.05f))
        assertEquals("f", state.activeAnimatedMediaId)

        state.updateFeedViewportState(draggingDown())
        state.updateVideoVisibility("f", surfaceF, metrics(0.52f, 0.29f, -0.15f))
        state.updateVideoVisibility("g", surfaceG, metrics(0.42f, 0.06f, 0.08f))
        state.updateVideoVisibility("h", surfaceH, metrics(0.18f, 0.045f, 0.31f, terminal = true))
        assertEquals("g", state.activeAnimatedMediaId)

        state.updateFeedViewportState(
            FeedViewportState(
                motion = FeedViewportMotion.IDLE,
                direction = FeedScrollDirection.DOWN,
                atEnd = true,
            ),
        )
        assertEquals("h", state.activeAnimatedMediaId)

        // Remaining parked at bottom cannot bounce back to G.
        state.updateVideoVisibility("g", surfaceG, metrics(0.70f, 0.08f, -0.08f))
        assertEquals("h", state.activeAnimatedMediaId)
    }

    @Test
    fun upwardReverseAtBottomHandsHBackToGThenF() {
        val state = newState()
        val surfaceF = Any()
        val surfaceG = Any()
        val surfaceH = Any()
        state.updateVideoFeedContext(listOf("f", "g", "h"), terminalVideoId = "h")
        state.updateFeedViewportState(
            FeedViewportState(
                motion = FeedViewportMotion.IDLE,
                direction = FeedScrollDirection.DOWN,
                atEnd = true,
            ),
        )
        state.updateVideoVisibility("g", surfaceG, metrics(0.70f, 0.08f, -0.08f))
        state.updateVideoVisibility("h", surfaceH, metrics(0.18f, 0.045f, 0.34f, terminal = true))
        assertEquals("h", state.activeAnimatedMediaId)

        state.updateFeedViewportState(
            FeedViewportState(
                motion = FeedViewportMotion.DRAGGING,
                direction = FeedScrollDirection.UP,
                atEnd = false,
            ),
        )
        state.updateVideoVisibility("g", surfaceG, metrics(0.42f, 0.06f, -0.08f))
        state.updateVideoVisibility("h", surfaceH, metrics(0.42f, 0.10f, 0.16f, terminal = true))
        assertEquals("g", state.activeAnimatedMediaId)

        state.updateVideoVisibility("g", surfaceG, metrics(0.52f, 0.08f, 0.15f))
        state.updateVideoVisibility("f", surfaceF, metrics(0.44f, 0.12f, -0.06f))
        assertEquals("f", state.activeAnimatedMediaId)
    }

    @Test
    fun signedCenterOffsetChangeTriggersOwnerReevaluation() {
        val state = newState()
        val surfaceD = Any()
        val surfaceE = Any()
        state.updateVideoFeedContext(listOf("d", "e"), terminalVideoId = "e")
        state.updateFeedViewportState(draggingDown())
        state.updateFeedViewportMotionMetrics(
            FeedViewportMotionMetrics(
                viewportHeightPx = 1_000,
                distanceSinceDirectionChangePx = 120f,
            ),
        )
        state.updateVideoVisibility("d", surfaceD, metrics(0.60f, 0.20f, 0.10f))
        state.updateVideoVisibility("e", surfaceE, metrics(0.44f, 0.08f, 0.14f, terminal = true))
        assertEquals("d", state.activeAnimatedMediaId)

        // Same absolute distance, opposite sign: this must no longer be ignored.
        state.updateVideoVisibility("d", surfaceD, metrics(0.60f, 0.20f, -0.10f))
        assertEquals("e", state.activeAnimatedMediaId)
    }

    @Test
    fun scrubbingLocksCurrentViewportOwnerUntilRelease() {
        val state = newState()
        val surfaceD = Any()
        val surfaceE = Any()
        state.updateVideoFeedContext(listOf("d", "e"), terminalVideoId = "e")
        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("d", surfaceD, metrics(0.90f, 0.42f, 0.05f))
        assertEquals("d", state.activeAnimatedMediaId)

        state.beginVideoScrubbing("d")
        assertTrue(state.isVideoScrubbing("d"))
        state.updateFeedViewportState(draggingDown())
        state.updateVideoVisibility("d", surfaceD, metrics(0.20f, 0.05f, -0.40f))
        state.updateVideoVisibility("e", surfaceE, metrics(0.90f, 0.30f, 0.02f, terminal = true))
        assertEquals("d", state.activeAnimatedMediaId)

        state.endVideoScrubbing("d")
        assertFalse(state.isVideoScrubbing("d"))
        assertEquals("e", state.activeAnimatedMediaId)
    }

    @Test
    fun manualPauseBlocksImmediateAutoplayReacquireUntilExplicitPlay() {
        val state = newState()
        val surfaceA = Any()
        state.updateVideoFeedContext(listOf("a"), terminalVideoId = "a")
        state.updateFeedViewportState(idle(atStart = true, atEnd = true))
        state.updateVideoVisibility("a", surfaceA, metrics(0.80f, 0.30f, 0f, terminal = true))
        assertEquals("a", state.activeAnimatedMediaId)

        state.stopVideo("a")
        assertNull(state.activeAnimatedMediaId)
        assertTrue(state.isVideoManuallyPaused("a"))
        state.updateVideoVisibility("a", surfaceA, metrics(0.82f, 0.30f, 0f, terminal = true))
        assertNull(state.activeAnimatedMediaId)

        state.playVideo("a")
        assertEquals("a", state.activeAnimatedMediaId)
        assertFalse(state.isVideoManuallyPaused("a"))
    }

    @Test
    fun manualPauseCanYieldToDifferentVideoAfterPausedSurfaceLeaves() {
        val state = newState()
        val surfaceA = Any()
        val surfaceB = Any()
        state.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        state.updateFeedViewportState(idle(atStart = true))
        state.updateVideoVisibility("a", surfaceA, metrics(0.80f, 0.30f, 0f))
        state.stopVideo("a")
        assertNull(state.activeAnimatedMediaId)

        state.updateFeedViewportState(draggingDown())
        state.updateVideoVisibility("a", surfaceA, 0f)
        state.updateVideoVisibility("b", surfaceB, metrics(0.80f, 0.30f, 0f, terminal = true))
        assertEquals("b", state.activeAnimatedMediaId)
    }

    @Test
    fun autoplayNeverDisablesAutomaticStartButNotTapToPlay() {
        val state = newState()
        val surfaceA = Any()
        state.updateFeedViewportState(idle())
        state.updateVideoAutoplay(VideoAutoplay.NEVER)
        state.updateVideoVisibility("a", surfaceA, 0.80f)
        assertNull(state.activeAnimatedMediaId)

        state.playVideo("a")
        state.updateVideoVisibility("a", surfaceA, 0.80f)
        assertEquals("a", state.activeAnimatedMediaId)

        state.updateVideoVisibility("a", surfaceA, 0f)
        assertNull(state.activeAnimatedMediaId)
    }


    @Test
    fun routeAndFullscreenHandoffsPreserveTheSameVideoIntent() {
        val state = newState()
        val post = SampleData.posts.first { it.id == "video-lab-a" }
        val feedSurface = Any()
        val postSurface = Any()
        val returnedPostSurface = Any()

        state.updateVideoFeedContext(listOf(post.id), terminalVideoId = post.id)
        state.updateFeedViewportState(idle(atStart = true, atEnd = true))
        state.updateVideoVisibility(
            post.id,
            feedSurface,
            metrics(0.80f, 0.30f, 0f, terminal = true),
        )
        assertEquals(post.id, state.activeAnimatedMediaId)

        state.open(post)
        assertTrue(state.isVideoPresentationHandoff(post.id))
        state.removeVideoSurface(feedSurface)
        assertEquals(post.id, state.activeAnimatedMediaId)
        state.updateVideoVisibility(
            post.id,
            postSurface,
            metrics(0.80f, 0.30f, 0f, terminal = true),
        )
        state.completeVideoPresentationHandoff(post.id)
        assertEquals(post.id, state.activeAnimatedMediaId)

        state.openMedia(post)
        assertTrue(state.isVideoPresentationHandoff(post.id))
        state.removeVideoSurface(postSurface)
        state.completeVideoPresentationHandoff(post.id)
        assertEquals(post.id, state.activeAnimatedMediaId)
        assertEquals(post, state.selectedMediaPost)

        state.closeMedia()
        assertTrue(state.isVideoPresentationHandoff(post.id))
        state.updateVideoVisibility(
            post.id,
            returnedPostSurface,
            metrics(0.80f, 0.30f, 0f, terminal = true),
        )
        state.completeVideoPresentationHandoff(post.id)
        assertEquals(post.id, state.activeAnimatedMediaId)
        assertNull(state.selectedMediaPost)
    }

    @Test
    fun fullscreenPreservesManualPauseAndForegroundResumeIntent() {
        val state = newState()
        val post = SampleData.posts.first { it.id == "video-lab-a" }

        state.playVideo(post.id)
        state.stopVideo(post.id)
        state.openMedia(post)
        state.completeVideoPresentationHandoff(post.id)
        assertNull(state.activeAnimatedMediaId)
        assertTrue(state.isVideoManuallyPaused(post.id))

        state.playVideo(post.id)
        state.suspendVideoPlaybackForBackground()
        assertNull(state.activeAnimatedMediaId)
        state.resumeVideoPlaybackAfterForeground()
        assertEquals(post.id, state.activeAnimatedMediaId)
    }

    @Test
    fun muteChoiceIsSharedAcrossTheFeedSession() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialStartVideosMuted = true,
        )

        assertTrue(state.feedVideoMuted)
        state.toggleVideoMuted()
        assertFalse(state.feedVideoMuted)
        state.playVideo("a")
        state.playVideo("b")
        assertFalse(state.feedVideoMuted)
        state.updateStartVideosMuted(true)
        assertTrue(state.feedVideoMuted)
    }

    @Test
    fun foregroundResumeRequiresSameVisibleMediaAndIdleViewport() {
        val state = newState()
        val surfaceA = Any()
        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("a", surfaceA, 0.80f)
        assertEquals("a", state.activeAnimatedMediaId)

        state.suspendVideoPlaybackForBackground()
        assertNull(state.activeAnimatedMediaId)
        state.resumeVideoPlaybackAfterForeground()
        assertNull(state.activeAnimatedMediaId)

        state.updateFeedViewportState(idle())
        assertEquals("a", state.activeAnimatedMediaId)
    }

    @Test
    fun foregroundDoesNotResumeMediaThatIsNoLongerVisible() {
        val state = newState()
        val surfaceA = Any()
        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("a", surfaceA, 0.80f)
        state.suspendVideoPlaybackForBackground()
        state.updateVideoVisibility("a", surfaceA, 0f)
        state.resumeVideoPlaybackAfterForeground()
        state.updateFeedViewportState(idle())

        assertNull(state.activeAnimatedMediaId)
    }

    private fun newState(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    )

    private fun idle(
        atStart: Boolean = false,
        atEnd: Boolean = false,
    ): FeedViewportState = FeedViewportState(
        motion = FeedViewportMotion.IDLE,
        atStart = atStart,
        atEnd = atEnd,
    )

    private fun draggingDown(): FeedViewportState = FeedViewportState(
        motion = FeedViewportMotion.DRAGGING,
        direction = FeedScrollDirection.DOWN,
    )

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
