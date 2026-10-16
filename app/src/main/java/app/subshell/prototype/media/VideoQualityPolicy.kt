package app.subshell.prototype.media

import app.subshell.prototype.ui.VideoQuality

/** Track-selection ceilings. The player remains adaptive below each ceiling. */
data class VideoQualityPolicy(
    val maxVideoEdgePx: Int,
    val maxVideoBitrate: Int,
    val maxVideoFrameRate: Int,
)

fun VideoQuality.toPlaybackPolicy(): VideoQualityPolicy = when (this) {
    VideoQuality.LOW -> VideoQualityPolicy(
        maxVideoEdgePx = 854,
        maxVideoBitrate = 1_500_000,
        maxVideoFrameRate = 30,
    )
    VideoQuality.MEDIUM -> VideoQualityPolicy(
        maxVideoEdgePx = 1_280,
        maxVideoBitrate = 4_000_000,
        maxVideoFrameRate = 60,
    )
    VideoQuality.HIGH -> VideoQualityPolicy(
        maxVideoEdgePx = Int.MAX_VALUE,
        maxVideoBitrate = Int.MAX_VALUE,
        maxVideoFrameRate = Int.MAX_VALUE,
    )
}

/** Persisted automatic ceilings for unmetered and metered remote playback. */
data class VideoNetworkQualityProfiles(
    val unmetered: VideoQuality = VideoQuality.HIGH,
    val metered: VideoQuality = VideoQuality.MEDIUM,
)

/**
 * Resolves the automatic quality ceiling without making connectivity a playback gate.
 * Local media always uses the unmetered profile. An offline transition preserves the last
 * effective ceiling so cached playback does not churn tracks when connectivity disappears.
 */
internal fun automaticVideoQualityFor(
    remoteSource: Boolean,
    networkClass: VideoNetworkClass,
    profiles: VideoNetworkQualityProfiles,
    offlineFallback: VideoQuality? = null,
): VideoQuality = when {
    !remoteSource -> profiles.unmetered
    networkClass == VideoNetworkClass.UNMETERED -> profiles.unmetered
    networkClass == VideoNetworkClass.METERED -> profiles.metered
    else -> offlineFallback ?: profiles.unmetered
}
