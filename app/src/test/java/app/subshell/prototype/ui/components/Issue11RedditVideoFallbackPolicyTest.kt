package app.subshell.prototype.ui.components

import androidx.media3.common.MimeTypes
import app.subshell.prototype.media.VideoMediaSourceKind
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Test

class Issue11RedditVideoFallbackPolicyTest {
    @Test
    fun redditDashHlsAndProgressiveCandidatesBecomeOrderedPlayerSources() {
        val post = videoPost(
            primary = "https://v.redd.it/issue11/DASHPlaylist.mpd",
            hls = "https://v.redd.it/issue11/HLSPlaylist.m3u8",
            fallback = "https://v.redd.it/issue11/DASH_720.mp4",
        )

        val sources = remoteVideoMediaSources(post)

        assertEquals(3, sources.size)

        assertEquals(
            "https://v.redd.it/issue11/DASHPlaylist.mpd",
            sources[0].uri,
        )
        assertEquals(VideoMediaSourceKind.DASH, sources[0].kind)
        assertEquals(MimeTypes.APPLICATION_MPD, sources[0].resolvedMimeType)

        assertEquals(
            "https://v.redd.it/issue11/HLSPlaylist.m3u8",
            sources[1].uri,
        )
        assertEquals(VideoMediaSourceKind.HLS, sources[1].kind)
        assertEquals(MimeTypes.APPLICATION_M3U8, sources[1].resolvedMimeType)

        assertEquals(
            "https://v.redd.it/issue11/DASH_720.mp4",
            sources[2].uri,
        )
        assertEquals(VideoMediaSourceKind.PROGRESSIVE, sources[2].kind)
        assertEquals(MimeTypes.VIDEO_MP4, sources[2].resolvedMimeType)
    }

    @Test
    fun fallbackProjectionDeduplicatesPrimaryWhenRedditDidNotProvideDash() {
        val hls = "https://v.redd.it/issue11/HLSPlaylist.m3u8"
        val fallback = "https://v.redd.it/issue11/DASH_720.mp4"

        val sources = remoteVideoMediaSources(
            videoPost(
                primary = hls,
                hls = hls,
                fallback = fallback,
            ),
        )

        assertEquals(
            listOf(hls, fallback),
            sources.map { source -> source.uri },
        )
        assertEquals(
            listOf(
                VideoMediaSourceKind.HLS,
                VideoMediaSourceKind.PROGRESSIVE,
            ),
            sources.map { source -> source.kind },
        )
    }

    @Test
    fun oldSingleSourcePostRemainsBackwardCompatible() {
        val dash = "https://v.redd.it/legacy/DASHPlaylist.mpd"

        val sources = remoteVideoMediaSources(
            videoPost(
                primary = dash,
                hls = null,
                fallback = null,
            ),
        )

        assertEquals(1, sources.size)
        assertEquals(dash, sources.single().uri)
        assertEquals(VideoMediaSourceKind.DASH, sources.single().kind)
    }

    private fun videoPost(
        primary: String?,
        hls: String?,
        fallback: String?,
    ): Post = Post(
        id = "t3_issue11",
        community = "mildlyinfuriating",
        author = "issue11",
        age = "now",
        title = "Issue 11 regression",
        score = 1,
        comments = 0,
        kind = PostKind.VIDEO,
        mediaLabel = "Video",
        remoteVideoUri = primary,
        remoteVideoHlsUri = hls,
        remoteVideoFallbackUri = fallback,
    )
}
