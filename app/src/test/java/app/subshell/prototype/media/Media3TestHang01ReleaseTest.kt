package app.subshell.prototype.media

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * MEDIA3TESTHANG01: when its playback thread does not answer, ExoPlayer
 * 1.11's release() waits on Robolectric's frozen clock forever (the suite hung in
 * Media04VideoOwnershipGuardsTest's teardown). The test release helper ends
 * the wait the way a phone does, after the release timeout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Media3TestHang01ReleaseTest {
    @Test(timeout = 30_000)
    fun aCoordinatorWhosePlaybackThreadNeverAnswersStillReleases() {
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        // The playback thread takes no more messages, so the release message
        // is never handled: release() can only end by its timeout.
        shadowOf(coordinator.player.playbackLooper).pause()

        coordinator.releaseWithoutHanging()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
