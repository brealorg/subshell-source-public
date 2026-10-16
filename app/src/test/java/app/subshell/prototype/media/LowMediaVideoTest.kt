package app.subshell.prototype.media

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * MEDIA-B (low media findings, video core; owner-approved), on the real
 * coordinator: a video outside the feed leaves the feed's preloads alone
 * (OCB-653), the same video with fresh URL signatures is not reloaded
 * (OCB-651), a retry continues where the video stopped (OCB-568), and the
 * network monitor follows the callback rather than a fresh activeNetwork read
 * (OCB-649).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowMediaVideoTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val coordinator = VideoPlaybackCoordinator(application)
    private val owner = Any()

    @After
    fun releaseCoordinator() {
        coordinator.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun anUnrankedVideoIsPlayedWithoutJoiningTheFeedPreloads() {
        coordinator.registerMedia(media("t3_feed_0"), rank = 0)
        coordinator.registerMedia(media("t3_feed_1"), rank = 1)
        assertEquals(2, coordinator.preloadSourceCountForTest)

        coordinator.attach(
            owner, media("t3_post_screen"), muted = true, transitionId = 1L,
            rank = VIDEO_UNRANKED, playWhenReady = false,
        )

        assertEquals(2, coordinator.preloadSourceCountForTest)
        assertEquals(3, coordinator.registeredMediaCountForTest)
        assertTrue(coordinator.ownsPresentation(owner, "t3_post_screen"))
    }

    @Test
    fun theSameVideoWithFreshSignaturesKeepsItsLoadedItem() {
        coordinator.attach(
            owner, media("t3_signed", query = "?sig=a"), muted = true, transitionId = 1L,
            rank = 0, playWhenReady = false,
        )
        val loaded = coordinator.player.currentMediaItem

        coordinator.attach(
            owner, media("t3_signed", query = "?sig=b"), muted = true, transitionId = 2L,
            rank = 0, playWhenReady = false,
        )

        assertSame(loaded, coordinator.player.currentMediaItem)
        assertTrue(sameVideoMedia(media("t3_signed", "?sig=a"), media("t3_signed", "?sig=b")))
        assertFalse(sameVideoMedia(media("t3_signed"), media("t3_other")))
    }

    @Test
    fun aRetryContinuesFromTheCurrentPosition() {
        coordinator.attach(
            owner, media("t3_retry"), muted = true, transitionId = 1L,
            rank = 0, playWhenReady = false,
        )
        coordinator.player.seekTo(42_000L)

        coordinator.retry(owner)

        assertEquals(42_000L, coordinator.player.currentPosition)
    }

    @Test
    fun aLostDefaultNetworkIsOfflineEvenWhileActiveNetworkStillAnswers() {
        val connectivity = application.getSystemService(ConnectivityManager::class.java)
        val online = ShadowNetworkCapabilities.newInstance().also { capabilities ->
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val network = connectivity.activeNetwork!!
        shadowOf(connectivity).setNetworkCapabilities(network, online)
        val snapshots = mutableListOf<VideoNetworkSnapshot>()
        val monitor = AndroidVideoNetworkMonitor(application) { snapshots += it }
        monitor.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(VideoNetworkClass.METERED, snapshots.last().networkClass)

        shadowOf(connectivity).networkCallbacks.forEach { it.onLost(network) }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(VideoNetworkClass.OFFLINE, snapshots.last().networkClass)
        monitor.close()
    }

    private fun media(id: String, query: String = "") = VideoMedia(
        mediaId = id,
        sources = listOf(
            VideoMediaSource(
                uri = "https://v.redd.it/$id/DASH_720.mp4$query",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = "video/mp4",
            ),
        ),
    )
}
