package app.subshell.prototype.ui

import app.subshell.prototype.media.FeedViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MEDIA05 (review of the pre-Claude media package, the rest of finding 4):
 * Replay of an ended video goes through SubshellState like Play, so the video
 * is the active one and the rules that pause a playing video (scrolling it
 * away) pause the replayed one too. Before, the coordinator played it while
 * SubshellState did not consider it active, so nothing paused it.
 */
class Media05ReplayThroughStateTest {
    private val viewport = Any()
    private val surface = Any()

    private fun state(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    ).also {
        it.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        it.registerGoogleFeedVideoViewport(viewport)
        it.updateFeedViewportState(FeedViewportState())
    }

    @Test
    fun replayMakesTheVideoActiveEvenAfterAManualPause() {
        val state = state()
        state.stopVideo("a")
        assertNull(state.activeAnimatedMediaId)

        state.replayVideo("a")

        assertEquals("a", state.activeAnimatedMediaId)
        assertFalse(state.isVideoManuallyPaused("a"))
    }

    @Test
    fun aReplayedVideoIsPausedWhenItLeavesTheScreen() {
        val state = state()
        state.updateGoogleFeedVideoVisibility(viewport, surface, "a", present = true)
        state.replayVideo("a")
        assertEquals("a", state.activeAnimatedMediaId)

        state.updateGoogleFeedVideoVisibility(viewport, surface, "a", present = false)

        assertNull(state.activeAnimatedMediaId)
    }
}
