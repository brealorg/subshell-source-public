package app.subshell.prototype.media

import app.subshell.prototype.ui.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoQualityPolicyTest {
    @Test
    fun lowCapsDataAndFrameRate() {
        assertEquals(
            VideoQualityPolicy(854, 1_500_000, 30),
            VideoQuality.LOW.toPlaybackPolicy(),
        )
    }

    @Test
    fun mediumAllows720pAnd60Fps() {
        assertEquals(
            VideoQualityPolicy(1_280, 4_000_000, 60),
            VideoQuality.MEDIUM.toPlaybackPolicy(),
        )
    }

    @Test
    fun highLeavesTrackSelectionUnbounded() {
        assertEquals(
            VideoQualityPolicy(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
            VideoQuality.HIGH.toPlaybackPolicy(),
        )
    }
    @Test
    fun networkProfilesResolveUnmeteredMeteredOfflineAndLocalWithoutPlaybackGating() {
        val profiles = VideoNetworkQualityProfiles(
            unmetered = VideoQuality.HIGH,
            metered = VideoQuality.LOW,
        )

        assertEquals(
            VideoQuality.HIGH,
            automaticVideoQualityFor(true, VideoNetworkClass.UNMETERED, profiles),
        )
        assertEquals(
            VideoQuality.LOW,
            automaticVideoQualityFor(true, VideoNetworkClass.METERED, profiles),
        )
        assertEquals(
            VideoQuality.MEDIUM,
            automaticVideoQualityFor(
                remoteSource = true,
                networkClass = VideoNetworkClass.OFFLINE,
                profiles = profiles,
                offlineFallback = VideoQuality.MEDIUM,
            ),
        )
        VideoNetworkClass.entries.forEach { networkClass ->
            assertEquals(
                VideoQuality.HIGH,
                automaticVideoQualityFor(false, networkClass, profiles, VideoQuality.LOW),
            )
        }
    }

}
