package app.subshell.prototype.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMediaCachePolicyTest {
    @Test
    fun remoteProgressiveSourceGetsStableGeneratedIdentity() {
        assertEquals(
            "subshell:video:post-42:source:0",
            progressiveRemoteCacheKey(
                uri = "https://cdn.example/video.mp4?token=rotates",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                explicitCacheKey = null,
                generatedCacheKey = "subshell:video:post-42:source:0",
            ),
        )
    }

    @Test
    fun explicitProviderIdentityWinsOverRotatingUrl() {
        assertEquals(
            "provider:video:abc123",
            progressiveRemoteCacheKey(
                uri = "https://cdn.example/video.mp4?token=first",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                explicitCacheKey = " provider:video:abc123 ",
                generatedCacheKey = "subshell:video:fallback:source:0",
            ),
        )
    }

    @Test
    fun adaptiveAndLocalSourcesKeepUriLevelCacheKeys() {
        assertNull(
            progressiveRemoteCacheKey(
                uri = "https://cdn.example/manifest.mpd",
                kind = VideoMediaSourceKind.DASH,
                explicitCacheKey = "must-not-collapse-segments",
                generatedCacheKey = "unused",
            ),
        )
        assertNull(
            progressiveRemoteCacheKey(
                uri = "asset:///media03/split/video.mp4",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                explicitCacheKey = "must-not-cache-local-assets",
                generatedCacheKey = "unused",
            ),
        )
    }

    @Test
    fun cacheBudgetAndTimeoutsRemainBounded() {
        assertEquals(VideoMediaCacheLimit.GB_1, VideoMediaCacheLimit.DEFAULT)
        assertEquals(1024L * 1024L * 1024L, VideoMediaCacheLimit.DEFAULT.bytes)
        assertEquals(
            listOf(256L, 512L, 1024L, 2048L),
            VideoMediaCacheLimit.entries.map { limit -> limit.bytes / (1024L * 1024L) },
        )
        assertEquals(10_000, VIDEO_MEDIA_HTTP_CONNECT_TIMEOUT_MS)
        assertEquals(20_000, VIDEO_MEDIA_HTTP_READ_TIMEOUT_MS)
    }

    @Test
    fun cacheSizeFormattingAndSummaryRemainDeterministic() {
        assertEquals("0 B", formatVideoMediaCacheBytes(0L))
        assertEquals("1 KB", formatVideoMediaCacheBytes(1L))
        assertEquals("1.5 MB", formatVideoMediaCacheBytes(1_572_864L))
        assertEquals("256 MB", formatVideoMediaCacheBytes(VideoMediaCacheLimit.MB_256.bytes))
        assertEquals("1 GB", formatVideoMediaCacheBytes(VideoMediaCacheLimit.GB_1.bytes))
        assertEquals("1.5 GB", formatVideoMediaCacheBytes(1_610_612_736L))
        for (limit in VideoMediaCacheLimit.entries) {
            assertEquals(limit.label, formatVideoMediaCacheBytes(limit.bytes))
        }
        assertEquals(
            "1.5 MB used · 1 GB limit",
            videoMediaCacheSummary(
                VideoMediaCacheStatus(
                    bytesUsed = 1_572_864L,
                    maxBytes = VideoMediaCacheLimit.DEFAULT.bytes,
                    resourceCount = 2,
                ),
            ),
        )
    }

    @Test
    fun anUnknownOrMissingStoredLimitFallsBackToTheDefault() {
        assertEquals(VideoMediaCacheLimit.DEFAULT, VideoMediaCacheLimit.fromStored(null))
        assertEquals(VideoMediaCacheLimit.DEFAULT, VideoMediaCacheLimit.fromStored("GB_8"))
        assertEquals(VideoMediaCacheLimit.MB_512, VideoMediaCacheLimit.fromStored("MB_512"))
    }

    @Test
    fun cacheClearResultReportsOnlyActuallyRemovedBytes() {
        val result = VideoMediaCacheClearResult(
            before = VideoMediaCacheStatus(4_096L, VideoMediaCacheLimit.DEFAULT.bytes, 2),
            after = VideoMediaCacheStatus(1_024L, VideoMediaCacheLimit.DEFAULT.bytes, 1),
            resourcesRemoved = 1,
        )
        assertEquals(3_072L, result.bytesRemoved)
    }
}
