package app.subshell.prototype.media

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackAudioPolicyTest {
    @Test
    fun mutedPlaybackUsesZeroPlayerVolume() {
        assertEquals(0f, playbackVolume(muted = true), 0f)
    }

    @Test
    fun audiblePlaybackUsesFullPlayerVolume() {
        assertEquals(1f, playbackVolume(muted = false), 0f)
    }

    @Test
    fun playbackUiPhaseDistinguishesBufferingEndedAndTerminalError() {
        assertEquals(
            VideoPlaybackPhase.BUFFERING,
            videoPlaybackPhase(Player.STATE_BUFFERING, hasTerminalError = false),
        )
        assertEquals(
            VideoPlaybackPhase.ENDED,
            videoPlaybackPhase(Player.STATE_ENDED, hasTerminalError = false),
        )
        assertEquals(
            VideoPlaybackPhase.ERROR,
            videoPlaybackPhase(Player.STATE_IDLE, hasTerminalError = true),
        )
    }

    @Test
    fun controlsAutoHideOnlyDuringReadyPlayingPlayback() {
        val readyPlaying = VideoPlaybackUiState(
            mediaId = "a",
            phase = VideoPlaybackPhase.READY,
            isPlaying = true,
            playWhenReady = true,
        )
        assertTrue(videoControlsAutoHideEligible(readyPlaying))
        assertFalse(videoControlsMustRemainVisible(readyPlaying))

        val paused = readyPlaying.copy(isPlaying = false, playWhenReady = false)
        assertFalse(videoControlsAutoHideEligible(paused))
        assertTrue(videoControlsMustRemainVisible(paused))

        val ended = readyPlaying.copy(phase = VideoPlaybackPhase.ENDED, isPlaying = false)
        assertFalse(videoControlsAutoHideEligible(ended))
        assertTrue(videoControlsMustRemainVisible(ended))

        assertFalse(videoControlsAutoHideEligible(readyPlaying, isScrubbing = true))
        assertTrue(videoControlsMustRemainVisible(readyPlaying, isScrubbing = true))
    }

    @Test
    fun playbackModalSuppressesTransportControlsUntilDismissed() {
        assertTrue(videoTransportControlsVisible(requestedVisible = true, modalVisible = false))
        assertFalse(videoTransportControlsVisible(requestedVisible = true, modalVisible = true))
        assertFalse(videoTransportControlsVisible(requestedVisible = false, modalVisible = false))
    }

    @Test
    fun playbackDurationSeekClampAndTimeFormattingAreDeterministic() {
        assertNull(normalizedVideoDuration(androidx.media3.common.C.TIME_UNSET))
        assertNull(normalizedVideoDuration(0L))
        assertEquals(35_000L, normalizedVideoDuration(35_000L))
        assertEquals(0L, clampVideoSeekPosition(-500L, 35_000L))
        assertEquals(12_500L, clampVideoSeekPosition(12_500L, 35_000L))
        assertEquals(35_000L, clampVideoSeekPosition(50_000L, 35_000L))
        assertEquals(0f, videoBufferedFraction(-1L, 35_000L), 0f)
        assertEquals(0.5f, videoBufferedFraction(17_500L, 35_000L), 0f)
        assertEquals(1f, videoBufferedFraction(50_000L, 35_000L), 0f)
        assertEquals(0f, videoBufferedFraction(1_000L, 0L), 0f)
        assertEquals("0:00", formatVideoPlaybackTime(0L))
        assertEquals("1:05", formatVideoPlaybackTime(65_000L))
        assertEquals("1:01:05", formatVideoPlaybackTime(3_665_000L))
    }
}
