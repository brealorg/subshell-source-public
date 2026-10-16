package app.subshell.prototype.media

import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.encodeSubshellProcessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour tests for the video-ownership guarantees that
 * VideoVisibilityStateTest does not already cover: the feed viewport reset,
 * the three-way mute initialisation and the transition id that changes with
 * the owner. They replaced the SubshellState text pins of the retired
 * media-audio-r27 checker.
 */
class VideoOwnershipStateTest {
    @Test
    fun resettingTheViewportReturnsToFlingingAndStopsAutoplayUntilItSettles() {
        val state = newState()
        val surface = Any()
        state.updateVideoFeedContext(listOf("a"), terminalVideoId = "a")
        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("a", surface, metrics(0.9f, 0.4f, 0.0f))
        assertEquals("a", state.activeAnimatedMediaId)

        state.resetFeedViewportState()
        assertEquals(FeedViewportMotion.FLINGING, state.feedViewportState.motion)
        // While flinging, a new surface that would otherwise win is preloaded
        // but not started, even after the old owner has scrolled away.
        val surfaceB = Any()
        state.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        state.updateVideoVisibility("a", surface, 0f)
        state.updateVideoVisibility("b", surfaceB, metrics(0.95f, 0.5f, 0.0f, terminal = true))
        assertNull(state.activeAnimatedMediaId)

        state.updateFeedViewportState(idle())
        state.updateVideoVisibility("b", surfaceB, metrics(0.95f, 0.5f, 0.0f, terminal = true))
        assertEquals("b", state.activeAnimatedMediaId)
    }

    @Test
    fun muteStartsFromTheSnapshotThenTheSettingThenTheSavedFeedValue() {
        // No snapshot: the saved feed value wins over the setting.
        val saved = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialStartVideosMuted = true,
            initialFeedVideoMuted = false,
        )
        assertFalse(saved.feedVideoMuted)
        val setting = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialStartVideosMuted = false,
        )
        assertFalse(setting.feedVideoMuted)

        // A restored snapshot wins over both.
        val source = newState()
        source.toggleVideoMuted()
        val token = encodeSubshellProcessSnapshot(source.processStateSnapshot())
        val restored = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialProcessSnapshotToken = token,
            initialStartVideosMuted = true,
            initialFeedVideoMuted = true,
        )
        assertEquals(source.feedVideoMuted, restored.feedVideoMuted)

        // A rejected snapshot falls back to the setting, ignoring the saved feed value.
        val rejected = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialProcessSnapshotToken = "not a snapshot",
            initialStartVideosMuted = false,
            initialFeedVideoMuted = true,
        )
        assertFalse(rejected.feedVideoMuted)
    }

    @Test
    fun transitionIdChangesWhenOwnershipChangesAndClearsWhenNothingPlays() {
        val state = newState()
        val surfaceA = Any()
        val surfaceB = Any()
        assertEquals(0L, state.activeVideoTransitionId)
        state.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        state.updateFeedViewportState(idle())

        state.updateVideoVisibility("a", surfaceA, metrics(0.9f, 0.4f, 0.0f))
        val first = state.activeVideoTransitionId
        assertNotEquals(0L, first)

        state.updateVideoVisibility("a", surfaceA, 0f)
        state.updateVideoVisibility("b", surfaceB, metrics(0.9f, 0.4f, 0.0f, terminal = true))
        assertEquals("b", state.activeAnimatedMediaId)
        assertNotEquals(first, state.activeVideoTransitionId)

        state.updateVideoVisibility("b", surfaceB, 0f)
        assertNull(state.activeAnimatedMediaId)
        assertEquals(0L, state.activeVideoTransitionId)
    }

    private fun newState(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    )

    private fun idle(): FeedViewportState = FeedViewportState(motion = FeedViewportMotion.IDLE)

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
