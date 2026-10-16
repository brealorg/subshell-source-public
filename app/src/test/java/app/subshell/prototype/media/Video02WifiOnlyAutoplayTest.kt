package app.subshell.prototype.media

import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.VideoAutoplay
import app.subshell.prototype.ui.videoAutoplayAllows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VIDEO02 (#227): VIDEO02_AUTOPLAY_FOLLOWS_WIFI_ONLY_V1. */
class Video02WifiOnlyAutoplayTest {
    @Test
    fun theSettingDecidesWithTheNetwork() {
        assertTrue(videoAutoplayAllows(VideoAutoplay.ALWAYS, unmetered = false))
        assertTrue(videoAutoplayAllows(VideoAutoplay.ALWAYS, unmetered = true))
        assertTrue(videoAutoplayAllows(VideoAutoplay.WIFI_ONLY, unmetered = true))
        assertFalse(videoAutoplayAllows(VideoAutoplay.WIFI_ONLY, unmetered = false))
        assertFalse(videoAutoplayAllows(VideoAutoplay.NEVER, unmetered = true))
    }

    @Test
    fun wifiOnlyAutoplaysOnWifiAndStopsOnMobileData() {
        val state = newState()
        val surface = Any()
        state.updateFeedViewportState(idle())
        state.updateVideoAutoplay(VideoAutoplay.WIFI_ONLY)
        state.updateVideoNetworkUnmetered(false)

        state.updateVideoVisibility("a", surface, 0.80f)
        assertNull(state.activeAnimatedMediaId)

        state.updateVideoNetworkUnmetered(true)
        state.updateVideoVisibility("a", surface, 0.80f)
        assertEquals("a", state.activeAnimatedMediaId)

        // Back on mobile data: the autoplayed video stops.
        state.updateVideoNetworkUnmetered(false)
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun aVideoTheReaderStartedKeepsPlayingOnMobileData() {
        val state = newState()
        val surface = Any()
        state.updateFeedViewportState(idle())
        state.updateVideoAutoplay(VideoAutoplay.WIFI_ONLY)
        state.updateVideoNetworkUnmetered(false)

        state.playVideo("a")
        state.updateVideoVisibility("a", surface, 0.80f)
        assertEquals("a", state.activeAnimatedMediaId)

        state.updateVideoNetworkUnmetered(true)
        state.updateVideoNetworkUnmetered(false)
        assertEquals("a", state.activeAnimatedMediaId)
    }

    @Test
    fun alwaysIgnoresTheNetwork() {
        val state = newState()
        val surface = Any()
        state.updateFeedViewportState(idle())
        state.updateVideoNetworkUnmetered(false)

        state.updateVideoVisibility("a", surface, 0.80f)
        assertEquals("a", state.activeAnimatedMediaId)
    }

    private fun newState(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    )

    private fun idle(): FeedViewportState = FeedViewportState(motion = FeedViewportMotion.IDLE)
}
