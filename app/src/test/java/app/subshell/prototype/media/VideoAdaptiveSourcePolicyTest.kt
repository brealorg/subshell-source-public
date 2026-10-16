package app.subshell.prototype.media

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAdaptiveSourcePolicyTest {
    @Test
    fun sourceKindsResolveToFormatAwareMedia3MimeTypes() {
        assertNull(VideoMediaSourceKind.PROGRESSIVE.defaultMimeType())
        assertEquals(MimeTypes.APPLICATION_MPD, VideoMediaSourceKind.DASH.defaultMimeType())
        assertEquals(MimeTypes.APPLICATION_M3U8, VideoMediaSourceKind.HLS.defaultMimeType())

        assertEquals(
            MimeTypes.VIDEO_MP4,
            VideoMediaSource(
                uri = "https://media.example/video.mp4",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = MimeTypes.VIDEO_MP4,
            ).resolvedMimeType,
        )
    }

    @Test
    fun adaptiveAssetFixturePreservesOrderedProgressiveFallback() {
        val fallback = VideoMediaSource(
            uri = "android.resource://io.github.brealorg.subshell.debug/123",
            kind = VideoMediaSourceKind.PROGRESSIVE,
            mimeType = MimeTypes.VIDEO_MP4,
        )
        val media = adaptiveAssetVideoMedia(
            mediaId = "media03-fallback",
            assetPath = "media03/missing/primary.mpd",
            kind = VideoMediaSourceKind.DASH,
            fallbackSources = listOf(fallback),
        )

        assertEquals("asset:///media03/missing/primary.mpd", media.sources[0].uri)
        assertEquals(VideoMediaSourceKind.DASH, media.sources[0].kind)
        assertEquals(fallback, media.sources[1])
    }

    @Test
    fun splitAssetFixtureModelsOneCompositeCandidateAndOrderedFallback() {
        val fallback = VideoMediaSource(
            uri = "asset:///media03/split/fallback.mp4",
            mimeType = MimeTypes.VIDEO_MP4,
        )
        val media = splitAssetVideoMedia(
            mediaId = "media03-split",
            videoAssetPath = "media03/split/video.mp4",
            audioAssetPath = "media03/split/audio.mp4",
            fallbackSources = listOf(fallback),
        )

        val composite = media.sources.first()
        assertTrue(composite.isComposite)
        assertNotNull(composite.companionAudio)
        assertEquals("asset:///media03/split/video.mp4", composite.uri)
        assertEquals("asset:///media03/split/audio.mp4", composite.companionAudio?.uri)
        assertEquals(MimeTypes.AUDIO_MP4, composite.companionAudio?.resolvedMimeType)
        assertEquals(fallback, media.sources[1])
    }

    @Test
    fun progressiveCandidateRemainsSingleComponent() {
        val source = VideoMediaSource(
            uri = "asset:///media03/split/fallback.mp4",
            mimeType = MimeTypes.VIDEO_MP4,
        )

        assertNull(source.companionAudio)
    }

    @Test
    fun fallbackPositionNeverBecomesNegative() {
        assertEquals(0L, fallbackStartPositionMs(-1L))
        assertEquals(12_345L, fallbackStartPositionMs(12_345L))
    }
}
