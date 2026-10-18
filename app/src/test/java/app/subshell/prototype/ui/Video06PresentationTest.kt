package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.FeedViewportState
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VIDEO_UNRANKED
import app.subshell.prototype.media.VideoMedia
import app.subshell.prototype.media.VideoMediaSource
import app.subshell.prototype.media.VideoMediaSourceKind
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackPhase
import app.subshell.prototype.media.VideoPlaybackUiState
import app.subshell.prototype.media.videoTracksAreSilent
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.boundedInlineVideoAspectRatio
import app.subshell.prototype.ui.components.videoControlsForcedVisible
import app.subshell.prototype.ui.components.videoPhaseShowsControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VIDEO06 (#227) presentation pass: VIDEO06_FEED_VIDEO_STARTS_WITHOUT_CONTROLS_V1,
 * VIDEO06_SILENT_VIDEO_LOOPS_WITHOUT_A_MUTE_BUTTON_V1,
 * VIDEO06_PAUSED_VIDEO_OPENS_PAUSED_IN_FULL_SCREEN_V1, VIDEO06_ONE_WAY_OUT_OF_FULL_SCREEN_V1,
 * VIDEO06_TALL_FEED_VIDEO_IS_BOUNDED_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video06PresentationTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val viewport = Any()
    private val surfaceA = Any()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video06",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video06",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun feedState(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    ).also {
        it.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        it.registerGoogleFeedVideoViewport(viewport)
        it.updateFeedViewportState(FeedViewportState())
    }

    private val videoFrame = SemanticsMatcher("video frame") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label in setOf("Hide video controls", "Show video controls")
    }

    private fun showReleased(state: SubshellState, inPostDetail: Boolean) {
        ensureWorkManagerInitialized(context)
        // Released: only the state the test publishes reaches the frame.
        val coordinator = testVideoPlaybackCoordinator(context).apply { release() }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(post = video, state = state, inPostDetail = inPostDetail)
            }
        }
        frames()
    }

    private fun frames() {
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun aPausedFeedVideoStaysPausedWhenItOpensFullscreen() {
        val state = feedState()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = true)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.stopVideo("a")
        assertTrue(state.isVideoManuallyPaused("a"))

        state.openMedia(SampleData.posts.first().copy(id = "a", kind = PostKind.VIDEO))
        // The feed card leaves the screen behind the viewer.
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = false)
        state.removeGoogleFeedVideoSurface(surfaceA)
        // HomeScreen leaving composition empties the feed context (the device cause).
        state.updateVideoFeedContext(emptyList(), null)

        assertTrue("paused in the viewer", state.isVideoManuallyPaused("a"))
    }

    @Test
    fun aFeedVideoLeavingTheScreenStillDropsItsPause() {
        // Control for the test above: without the viewer, the old rule holds.
        val state = feedState()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = true)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.stopVideo("a")

        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = false)
        state.updateVideoFeedContext(emptyList(), null)

        assertFalse(state.isVideoManuallyPaused("a"))
    }

    @Test
    fun quietFeedControlsComeBackOnlyAtTheEndOrOnAFailure() {
        val buffering = VideoPlaybackUiState(mediaId = "a", phase = VideoPlaybackPhase.BUFFERING)
        val ended = VideoPlaybackUiState(mediaId = "a", phase = VideoPlaybackPhase.ENDED)
        assertFalse(videoControlsForcedVisible(buffering, isScrubbing = false, quietFeed = true))
        assertTrue(videoControlsForcedVisible(ended, isScrubbing = false, quietFeed = true))
        assertTrue(videoControlsForcedVisible(buffering, isScrubbing = true, quietFeed = true))
        assertTrue(videoControlsForcedVisible(buffering, isScrubbing = false, quietFeed = false))
        assertFalse(videoPhaseShowsControls(VideoPlaybackPhase.BUFFERING, quietFeed = true))
        assertTrue(videoPhaseShowsControls(VideoPlaybackPhase.ERROR, quietFeed = true))
        assertTrue(videoPhaseShowsControls(VideoPlaybackPhase.BUFFERING, quietFeed = false))
    }

    @Test
    fun anAutoplayedFeedVideoWaitsForATapBeforeShowingControls() {
        val state = SubshellState()
        state.playVideo(video.id)
        showReleased(state, inPostDetail = false)

        composeRule.onAllNodesWithContentDescription("Enter full screen").assertCountEquals(0)

        composeRule.onNode(videoFrame).performClick()
        frames()
        composeRule.onAllNodesWithContentDescription("Enter full screen").assertCountEquals(1)
    }

    @Test
    fun aPostVideoShowsItsControlsRightAway() {
        val state = SubshellState()
        state.playVideo(video.id)
        showReleased(state, inPostDetail = true)

        composeRule.onAllNodesWithContentDescription("Enter full screen").assertCountEquals(1)
    }

    @Test
    fun aVideoWithoutSoundLoopsAndIsKnownAsSilent() {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        try {
            coordinator.updateLoopVideos(false)
            coordinator.attach(
                owner = Any(),
                media = VideoMedia(
                    mediaId = "t3_gif",
                    sources = listOf(
                        VideoMediaSource(
                            uri = "content://media/picked/gif",
                            kind = VideoMediaSourceKind.PROGRESSIVE,
                            mimeType = MimeTypes.VIDEO_MP4,
                        ),
                    ),
                ),
                muted = true,
                transitionId = 1L,
                rank = VIDEO_UNRANKED,
                playWhenReady = false,
            )
            assertEquals(Player.REPEAT_MODE_OFF, coordinator.player.repeatMode)

            coordinator.noteSoundOfActiveMedia(tracks(MimeTypes.VIDEO_H264))
            assertEquals("t3_gif", coordinator.silentMediaId)
            assertEquals(Player.REPEAT_MODE_ONE, coordinator.player.repeatMode)

            coordinator.noteSoundOfActiveMedia(tracks(MimeTypes.VIDEO_H264, MimeTypes.AUDIO_AAC))
            assertNull(coordinator.silentMediaId)
            assertEquals(Player.REPEAT_MODE_OFF, coordinator.player.repeatMode)
        } finally {
            coordinator.releaseWithoutHanging()
        }
    }

    @Test
    fun silenceNeedsKnownVideoTracks() {
        assertTrue(videoTracksAreSilent(tracks(MimeTypes.VIDEO_H264)))
        assertFalse(videoTracksAreSilent(tracks(MimeTypes.VIDEO_H264, MimeTypes.AUDIO_AAC)))
        assertFalse(videoTracksAreSilent(Tracks.EMPTY))
    }

    @Test
    fun tallFeedVideosAreBoundedLikeFeedImages() {
        assertEquals(4f / 5f, boundedInlineVideoAspectRatio(9f / 16f), 0.0001f)
        assertEquals(1f, boundedInlineVideoAspectRatio(1f), 0.0001f)
        assertEquals(16f / 9f, boundedInlineVideoAspectRatio(21f / 9f), 0.0001f)
        assertEquals(16f / 9f, boundedInlineVideoAspectRatio(0f), 0.0001f)
    }

    private fun tracks(vararg mimeTypes: String): Tracks = Tracks(
        mimeTypes.map { mime ->
            Tracks.Group(
                TrackGroup(Format.Builder().setSampleMimeType(mime).build()),
                false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(true),
            )
        },
    )
}
