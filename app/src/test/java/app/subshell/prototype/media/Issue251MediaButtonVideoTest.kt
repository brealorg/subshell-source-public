package app.subshell.prototype.media

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE251_MEDIA_BUTTONS_CONTROL_THE_VIDEO_V1 (#251), on the real coordinator:
 * media buttons may control only the video a surface presents, and only while
 * it has sound; a muted autoplaying video leaves them to other apps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue251MediaButtonVideoTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val coordinator = testVideoPlaybackCoordinator(application)
    private val owner = Any()
    private val media = VideoMedia(
        mediaId = "t3_video",
        sources = listOf(
            VideoMediaSource(
                uri = "https://v.redd.it/t3_video/DASH_720.mp4",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = "video/mp4",
            ),
        ),
    )

    @After
    fun releaseCoordinator() {
        coordinator.releaseWithoutHanging()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aMutedVideoLeavesTheButtonsToOtherApps() {
        coordinator.attach(owner, media, muted = true, transitionId = 1L, rank = 0, playWhenReady = true)
        assertNull(coordinator.mediaButtonVideoId())
    }

    @Test
    fun aPresentedVideoWithSoundTakesTheButtons() {
        coordinator.attach(owner, media, muted = false, transitionId = 1L, rank = 0, playWhenReady = true)
        assertEquals("t3_video", coordinator.mediaButtonVideoId())

        coordinator.setMuted(true)
        assertNull("muted later", coordinator.mediaButtonVideoId())
    }

    @Test
    fun withoutASurfaceOrAfterReleaseNothingIsControlled() {
        coordinator.attach(owner, media, muted = false, transitionId = 1L, rank = 0, playWhenReady = true)
        coordinator.detach(owner)
        assertNull("no surface", coordinator.mediaButtonVideoId())

        coordinator.attach(owner, media, muted = false, transitionId = 2L, rank = 0, playWhenReady = true)
        coordinator.releaseWithoutHanging()
        assertNull("released", coordinator.mediaButtonVideoId())
    }
}
