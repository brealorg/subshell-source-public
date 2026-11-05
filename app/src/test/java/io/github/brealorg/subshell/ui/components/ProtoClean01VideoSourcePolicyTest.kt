package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.model.MediaAsset
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoClean01VideoSourcePolicyTest {
    @Test
    fun removedRedditVideoWithoutSourceDoesNotAcquirePrototypeMedia() {
        assertFalse(
            hasPlayableVideoSource(
                videoPost(),
            ),
        )
    }

    @Test
    fun remoteRedditVideoRemainsPlayable() {
        assertTrue(
            hasPlayableVideoSource(
                videoPost(
                    remoteVideoUri = "https://v.redd.it/example/DASHPlaylist.mpd",
                ),
            ),
        )
    }

    @Test
    fun localComposerVideoRemainsPlayable() {
        assertTrue(
            hasPlayableVideoSource(
                videoPost(
                    localMediaUri = "content://subshell/video",
                    localMediaMimeType = "video/mp4",
                ),
            ),
        )
    }

    @Test
    fun knownVideoLabFixtureRemainsExplicitInsteadOfBecomingGenericFallback() {
        assertTrue(
            hasPlayableVideoSource(
                videoPost(
                    mediaAsset = MediaAsset.VIDEO_LAB_A,
                ),
            ),
        )
        assertFalse(
            hasPlayableVideoSource(
                videoPost(
                    mediaAsset = MediaAsset.HOMELAB,
                ),
            ),
        )
    }

    private fun videoPost(
        mediaAsset: MediaAsset? = null,
        remoteVideoUri: String? = null,
        localMediaUri: String? = null,
        localMediaMimeType: String? = null,
    ): Post = Post(
        id = "removed-video",
        community = "lies",
        author = "unknown",
        age = "1y",
        title = "[Removed by Reddit]",
        score = 0,
        comments = 0,
        kind = PostKind.VIDEO,
        mediaAsset = mediaAsset,
        remoteVideoUri = remoteVideoUri,
        localMediaUri = localMediaUri,
        localMediaMimeType = localMediaMimeType,
    )
}
