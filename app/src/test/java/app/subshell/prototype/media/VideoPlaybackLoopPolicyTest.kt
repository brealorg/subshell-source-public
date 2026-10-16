package app.subshell.prototype.media

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackLoopPolicyTest {
    @Test
    fun disabledLoopingUsesRepeatOff() {
        assertEquals(Player.REPEAT_MODE_OFF, videoRepeatMode(loopVideos = false))
    }

    @Test
    fun enabledLoopingRepeatsOnlyTheCurrentVideo() {
        assertEquals(Player.REPEAT_MODE_ONE, videoRepeatMode(loopVideos = true))
    }
}
