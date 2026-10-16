package app.subshell.prototype.media

import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTrackDiagnosticsTest {
    @Test
    fun sourceDiagnosticsExposeFallbackPositionAndSplitCandidate() {
        val media = VideoMedia(
            mediaId = "fixture",
            sources = listOf(
                VideoMediaSource(
                    uri = "asset:///video.mp4",
                    companionAudio = VideoMediaTrackSource("asset:///audio.mp4"),
                ),
                VideoMediaSource(
                    uri = "asset:///fallback.m3u8",
                    kind = VideoMediaSourceKind.HLS,
                ),
            ),
        )

        assertEquals(
            "Progressive · source 1/2 · split A/V",
            initialVideoPlaybackDiagnostics(media, 0).sourceSummary(),
        )
        assertEquals(
            "HLS · source 2/2",
            initialVideoPlaybackDiagnostics(media, 1).sourceSummary(),
        )
    }

    @Test
    fun trackInventorySummaryDistinguishesKnownEmptyAndAdaptiveVideo() {
        val inventory = VideoTrackInventory(
            known = true,
            video = VideoTrackTypeInventory(
                groupCount = 1,
                trackCount = 3,
                supportedTrackCount = 3,
                selectedTrackCount = 3,
            ),
            audio = VideoTrackTypeInventory(
                groupCount = 1,
                trackCount = 1,
                supportedTrackCount = 1,
                selectedTrackCount = 1,
            ),
            adaptiveVideoGroupCount = 1,
        )

        assertEquals("3 selected · 3/3 supported · 1 adaptive", inventory.videoSummary())
        assertEquals("1 selected · 1/1 supported", inventory.audioSummary())
        assertEquals("None", inventory.textSummary())
        assertEquals("Pending", VideoTrackInventory().videoSummary())
    }

    @Test
    @AndroidxOptIn(UnstableApi::class)
    fun rendererFormatsProduceCompactHumanReadableSummaries() {
        val video = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setCodecs("avc1.64001f")
            .setWidth(1280)
            .setHeight(720)
            .setFrameRate(30f)
            .setAverageBitrate(2_400_000)
            .build()
            .toVideoTrackFormatDiagnostics()
        val audio = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setCodecs("mp4a.40.2")
            .setChannelCount(2)
            .setSampleRate(48_000)
            .setAverageBitrate(128_000)
            .build()
            .toVideoTrackFormatDiagnostics()
            .copy(language = "en")

        assertEquals("1280×720 · 30 fps · 2.40 Mbps · avc1.64001f", video.videoSummary())
        assertEquals("2 ch · 48.0 kHz · 128 kbps · en · mp4a.40.2", audio.audioSummary())
        assertTrue(decoderSummary("c2.android.avc.decoder", 7).contains("7 ms init"))
    }
}
