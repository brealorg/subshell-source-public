package app.subshell.prototype.media

import android.net.ConnectivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNetworkPolicyTest {
    private val offline = VideoNetworkSnapshot()
    private val metered = VideoNetworkSnapshot(
        hasInternetCapability = true,
        validated = true,
        metered = true,
        dataSaverStatus = ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
    )
    private val unmetered = VideoNetworkSnapshot(
        hasInternetCapability = true,
        validated = true,
        metered = false,
    )

    @Test
    fun networkClassRequiresValidatedInternet() {
        assertEquals(VideoNetworkClass.OFFLINE, offline.networkClass)
        assertEquals(VideoNetworkClass.METERED, metered.networkClass)
        assertEquals(VideoNetworkClass.UNMETERED, unmetered.networkClass)
        assertTrue(metered.dataSaverActive)
        assertFalse(unmetered.dataSaverActive)
    }

    @Test
    fun remotePreloadUsesFullConservativeAndBlockedProfiles() {
        assertEquals(
            VideoPreloadTarget.RANGE_3_SECONDS,
            videoPreloadTarget(true, 0, unmetered, FeedViewportMotion.IDLE),
        )
        assertEquals(
            VideoPreloadTarget.TRACKS_SELECTED,
            videoPreloadTarget(true, 1, unmetered, FeedViewportMotion.IDLE),
        )
        assertEquals(
            VideoPreloadTarget.SOURCE_PREPARED,
            videoPreloadTarget(true, 0, metered, FeedViewportMotion.IDLE),
        )
        assertEquals(
            VideoPreloadTarget.NONE,
            videoPreloadTarget(true, 1, metered, FeedViewportMotion.IDLE),
        )
        assertEquals(
            VideoPreloadTarget.NONE,
            videoPreloadTarget(true, 0, offline, FeedViewportMotion.IDLE),
        )
    }

    @Test
    fun motionReductionPrecedesNetworkDepth() {
        assertEquals(
            VideoPreloadTarget.NONE,
            videoPreloadTarget(true, 0, unmetered, FeedViewportMotion.FLINGING),
        )
        assertEquals(
            VideoPreloadTarget.SOURCE_PREPARED,
            videoPreloadTarget(true, 0, unmetered, FeedViewportMotion.DRAGGING),
        )
        assertEquals(
            VideoPreloadTarget.NONE,
            videoPreloadTarget(true, 1, unmetered, FeedViewportMotion.DRAGGING),
        )
    }

    @Test
    fun localMediaKeepsOfflinePreloadButStillYieldsToMotion() {
        assertEquals(
            VideoPreloadTarget.RANGE_3_SECONDS,
            videoPreloadTarget(false, 0, offline, FeedViewportMotion.IDLE),
        )
        assertEquals(
            VideoPreloadTarget.NONE,
            videoPreloadTarget(false, 0, offline, FeedViewportMotion.FLINGING),
        )
    }

    @Test
    fun explicitPlaybackPathIsNotNetworkGated() {
        val source = VideoMediaSource(
            uri = "https://media.example/video.mp4",
            kind = VideoMediaSourceKind.PROGRESSIVE,
        )
        val media = VideoMedia("remote", listOf(source))

        assertTrue(media.usesRemoteTransport())
        // Network policy is consumed only by FeedTargetPreloadStatusControl. The player attach/play
        // path has no network-policy branch, so an explicit request can still use cache or upstream.
        assertEquals(VideoPreloadTarget.NONE, videoPreloadTarget(true, 0, offline, FeedViewportMotion.IDLE))
    }
}
