package app.subshell.prototype.media

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (media-audio-r27 S9): "Loop videos" repeats every video while it is
 * on and none when it is off, also for a video attached after the change, and
 * the setting reaches the one player through ProvideVideoPlaybackCoordinator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3VideoLoopSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun media(id: String) = VideoMedia(
        mediaId = id,
        sources = listOf(
            VideoMediaSource(
                uri = "asset:///missing/$id.mp4",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = MimeTypes.VIDEO_MP4,
            ),
        ),
    )

    private fun VideoPlaybackCoordinator.attachMuted(id: String) = attach(
        owner = Any(),
        media = media(id),
        muted = true,
        transitionId = 1L,
        rank = VIDEO_UNRANKED,
        playWhenReady = false,
    )

    @Test
    fun loopingFollowsTheSettingForTheCurrentAndTheNextVideo() {
        ensureWorkManagerInitialized(application)
        val coordinator = testVideoPlaybackCoordinator(application)
        try {
            coordinator.attachMuted("t3_first")
            assertEquals(Player.REPEAT_MODE_OFF, coordinator.player.repeatMode)

            coordinator.updateLoopVideos(true)
            assertEquals(Player.REPEAT_MODE_ONE, coordinator.player.repeatMode)

            coordinator.attachMuted("t3_second")
            assertEquals("the next video loops too", Player.REPEAT_MODE_ONE, coordinator.player.repeatMode)

            coordinator.updateLoopVideos(false)
            assertEquals(Player.REPEAT_MODE_OFF, coordinator.player.repeatMode)
        } finally {
            coordinator.releaseWithoutHanging()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun theLoopSettingReachesThePlayer() {
        ensureWorkManagerInitialized(application)
        val state = SubshellState()
        val coordinator = testVideoPlaybackCoordinator(application)
        try {
            composeRule.setContent {
                ProvideVideoPlaybackCoordinator(state = state, coordinator = coordinator) {}
            }
            composeRule.waitForIdle()
            assertEquals(Player.REPEAT_MODE_OFF, coordinator.player.repeatMode)

            composeRule.runOnIdle { state.updateLoopVideos(true) }
            composeRule.waitForIdle()
            assertEquals(Player.REPEAT_MODE_ONE, coordinator.player.repeatMode)

            composeRule.runOnIdle { state.updateLoopVideos(false) }
            composeRule.waitForIdle()
            assertEquals(Player.REPEAT_MODE_OFF, coordinator.player.repeatMode)
        } finally {
            coordinator.releaseWithoutHanging()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
