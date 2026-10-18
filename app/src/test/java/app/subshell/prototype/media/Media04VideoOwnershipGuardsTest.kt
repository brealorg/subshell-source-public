package app.subshell.prototype.media

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * MEDIA04 (review of the pre-Claude media package, findings 2, 4 and 9;
 * owner-approved video ownership changes), on the real coordinator:
 * - replay and scrubbing cannot take the player from the surface that owns it;
 * - one registration per preload rank, never the playing video evicted, and
 *   a bounded number of registrations;
 * - after release every entry point is a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Media04VideoOwnershipGuardsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val coordinator = testVideoPlaybackCoordinator(application)
    private val owner = Any()
    private val stale = Any()

    @After
    fun releaseCoordinator() {
        coordinator.releaseWithoutHanging()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aStaleSurfaceCannotReplayOrScrubTheOwnersVideo() {
        val media = media("t3_owned")
        coordinator.attach(owner, media, muted = true, transitionId = 1L, rank = 0, playWhenReady = false)

        coordinator.replay(stale)
        assertTrue(coordinator.ownsPresentation(owner, media.mediaId))

        assertFalse(coordinator.beginScrubbing(stale, media.mediaId))
        assertTrue(coordinator.ownsPresentation(owner, media.mediaId))
    }

    @Test
    fun theOwnerCanStillReplay() {
        val media = media("t3_owned")
        coordinator.attach(owner, media, muted = true, transitionId = 1L, rank = 0, playWhenReady = false)
        coordinator.replay(owner)
        assertTrue(coordinator.ownsPresentation(owner, media.mediaId))
    }

    @Test
    fun aNewVideoAtAnOccupiedRankReplacesTheOldRegistration() {
        coordinator.registerMedia(media("t3_old_generation"), rank = 5)
        coordinator.registerMedia(media("t3_new_generation"), rank = 5)
        assertEquals(1, coordinator.registeredMediaCountForTest)
    }

    @Test
    fun thePlayingVideoIsNeverEvicted() {
        val playing = media("t3_playing")
        coordinator.attach(owner, playing, muted = true, transitionId = 1L, rank = 3, playWhenReady = false)
        coordinator.registerMedia(media("t3_same_rank"), rank = 3)
        assertEquals(2, coordinator.registeredMediaCountForTest)

        repeat(VIDEO_MAX_REGISTERED_MEDIA + 10) { index ->
            coordinator.registerMedia(media("t3_scrolled_$index"), rank = 100 + index)
        }
        assertEquals(VIDEO_MAX_REGISTERED_MEDIA, coordinator.registeredMediaCountForTest)
        assertTrue(coordinator.ownsPresentation(owner, playing.mediaId))
    }

    @Test
    fun aReleasedCoordinatorIgnoresLateCalls() {
        val media = media("t3_late")
        coordinator.releaseWithoutHanging()
        coordinator.registerMedia(media, rank = 0)
        coordinator.attach(owner, media, muted = true, transitionId = 1L, rank = 0, playWhenReady = true)
        coordinator.detach(owner)
        assertEquals(0, coordinator.registeredMediaCountForTest)
        assertFalse(coordinator.ownsPresentation(owner, media.mediaId))
    }

    private fun media(id: String) = VideoMedia(
        mediaId = id,
        sources = listOf(
            VideoMediaSource(
                uri = "https://v.redd.it/$id/DASH_720.mp4",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = "video/mp4",
            ),
        ),
    )
}
