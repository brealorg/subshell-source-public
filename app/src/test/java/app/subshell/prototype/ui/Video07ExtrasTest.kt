package app.subshell.prototype.ui

import android.app.Application
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.ProvideVideoPlaybackCoordinator
import app.subshell.prototype.media.VIDEO_UNRANKED
import app.subshell.prototype.media.VideoMedia
import app.subshell.prototype.media.VideoMediaSource
import app.subshell.prototype.media.VideoMediaSourceKind
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.videoSpeedLabel
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import app.subshell.prototype.ui.components.doubleTapSeekDeltaMs
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
 * VIDEO07 (#227) extras, each behind a setting that is off by default:
 * VIDEO07_PLAYBACK_SPEED_V1, VIDEO07_DOUBLE_TAP_SEEK_V1,
 * VIDEO07_HOLD_FOR_DOUBLE_SPEED_V1, VIDEO07_PICTURE_IN_PICTURE_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video07ExtrasTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video07",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video07",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
        videoWidth = 720,
        videoHeight = 1280,
    )

    private fun media(id: String) = VideoMedia(
        mediaId = id,
        sources = listOf(
            VideoMediaSource(
                uri = "content://media/picked/$id",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = MimeTypes.VIDEO_MP4,
            ),
        ),
    )

    private fun VideoPlaybackCoordinator.attachForTest(id: String) = attach(
        owner = Any(),
        media = media(id),
        muted = true,
        transitionId = 1L,
        rank = VIDEO_UNRANKED,
        playWhenReady = false,
    )

    private fun showFullscreen(state: SubshellState, released: Boolean = true): VideoPlaybackCoordinator {
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context).apply { if (released) release() }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(
                    post = video,
                    state = state,
                    presentation = VideoPlayerPresentation.FULLSCREEN,
                    onExitFullscreen = {},
                )
            }
        }
        frames()
        return coordinator
    }

    private fun frames(count: Int = 30) {
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private val videoFrame = SemanticsMatcher("video frame") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label in setOf("Hide video controls", "Show video controls")
    }

    @Test
    fun everyExtraIsOffByDefault() {
        val state = SubshellState()
        assertFalse(state.videoSpeedControls)
        assertFalse(state.videoDoubleTapSeek)
        assertFalse(state.videoHoldForDoubleSpeed)
        assertFalse(state.videoPictureInPicture)
    }

    @Test
    fun speedIsPerVideoAndHoldingLendsTwoTimes() {
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context)
        try {
            coordinator.attachForTest("t3_a")
            coordinator.choosePlaybackSpeed(1.5f)
            assertEquals(1.5f, coordinator.player.playbackParameters.speed)

            coordinator.holdDoubleSpeed(true)
            assertEquals(2f, coordinator.player.playbackParameters.speed)
            coordinator.holdDoubleSpeed(false)
            assertEquals(1.5f, coordinator.player.playbackParameters.speed)

            coordinator.attachForTest("t3_b")
            assertEquals(1f, coordinator.playbackSpeed)
            assertEquals(1f, coordinator.player.playbackParameters.speed)
        } finally {
            coordinator.release()
        }
        assertEquals("Normal", videoSpeedLabel(1f))
        assertEquals("1.5×", videoSpeedLabel(1.5f))
        assertEquals("0.75×", videoSpeedLabel(0.75f))
    }

    @Test
    fun playbackOptionsOfferSpeedOnlyWithTheSetting() {
        val off = SubshellState()
        off.playVideo(video.id)
        showFullscreen(off)
        composeRule.onNodeWithContentDescription("Playback options").performClick()
        frames()
        composeRule.onNodeWithText("Choices apply to this video session").assertExists()
        composeRule.onAllNodesWithText("Speed").assertCountEquals(0)
    }

    @Test
    fun playbackOptionsShowSpeedWhenTheReaderTurnedItOn() {
        val on = SubshellState(initialVideoSpeedControls = true)
        on.playVideo(video.id)
        showFullscreen(on)
        composeRule.onNodeWithContentDescription("Playback options").performClick()
        frames()
        composeRule.onNodeWithText("Speed").assertExists()
        composeRule.onNodeWithText("Speed").performClick()
        frames()
        composeRule.onNodeWithText("1.5×").assertExists()
    }

    @Test
    fun fullScreenHasASpeedButtonDownToAQuarterWithTheSetting() {
        val on = SubshellState(initialVideoSpeedControls = true)
        on.playVideo(video.id)
        showFullscreen(on)

        composeRule.onNodeWithContentDescription("Playback speed, 1×").performClick()
        frames()
        composeRule.onNodeWithText("0.25×").assertExists()
        composeRule.onNodeWithText("0.5×").assertExists()
    }

    @Test
    fun withoutTheSettingFullScreenHasNoSpeedButton() {
        val off = SubshellState()
        off.playVideo(video.id)
        showFullscreen(off)
        composeRule.onAllNodesWithContentDescription("Playback speed, 1×").assertCountEquals(0)
    }

    @Test
    fun doubleTapSidesGoBackAndForward() {
        assertEquals(-10_000L, doubleTapSeekDeltaMs(x = 100f, width = 1000f))
        assertEquals(10_000L, doubleTapSeekDeltaMs(x = 900f, width = 1000f))
    }

    @Test
    fun aDoubleTapOnTheLeftShowsTenSecondsBack() {
        val state = SubshellState(initialVideoDoubleTapSeek = true)
        state.playVideo(video.id)
        showFullscreen(state)

        composeRule.onNode(videoFrame).performTouchInput {
            click(centerLeft.copy(x = width * 0.2f))
            advanceEventTime(80)
            click(centerLeft.copy(x = width * 0.2f))
        }
        frames(4)

        composeRule.onNodeWithText("−10 s").assertExists()
    }

    @Test
    fun withoutTheSettingADoubleTapIsNotASkip() {
        val state = SubshellState()
        state.playVideo(video.id)
        showFullscreen(state)

        composeRule.onNode(videoFrame).performTouchInput {
            click(centerLeft.copy(x = width * 0.2f))
            advanceEventTime(80)
            click(centerLeft.copy(x = width * 0.2f))
        }
        frames(4)

        composeRule.onAllNodesWithText("−10 s").assertCountEquals(0)
    }

    @Test
    fun holdingThePictureShowsTwoTimesAndLetsGo() {
        val state = SubshellState(initialVideoHoldForDoubleSpeed = true)
        state.playVideo(video.id)
        val coordinator = showFullscreen(state, released = false)
        try {
            composeRule.onNode(videoFrame).performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(800)
            frames(4)
            assertTrue(coordinator.holdingDoubleSpeed)
            composeRule.onNodeWithText("2× speed").assertExists()

            composeRule.onNode(videoFrame).performTouchInput { up() }
            frames(4)
            assertFalse(coordinator.holdingDoubleSpeed)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun onlyAPlayingFullScreenVideoWithTheSettingMayGoPictureInPicture() {
        assertTrue(videoPictureInPictureAllowed(enabled = true, viewerOpen = true, isVideo = true, playing = true))
        assertFalse(videoPictureInPictureAllowed(enabled = false, viewerOpen = true, isVideo = true, playing = true))
        assertFalse(videoPictureInPictureAllowed(enabled = true, viewerOpen = true, isVideo = true, playing = false))
        assertFalse(videoPictureInPictureAllowed(enabled = true, viewerOpen = false, isVideo = true, playing = true))
        assertEquals(720 to 1280, pictureInPictureAspect(720, 1280))
        assertEquals(239 to 100, pictureInPictureAspect(3000, 1000))
        assertEquals(16 to 9, pictureInPictureAspect(null, 1080))

        val state = SubshellState(initialVideoPictureInPicture = true)
        assertNull(state.pictureInPictureVideo())
        state.openMedia(video)
        assertEquals(video.id, state.pictureInPictureVideo()?.id)
        state.stopVideo(video.id)
        assertNull("a paused video stays out of the small window", state.pictureInPictureVideo())
    }

    @Test
    fun theWindowsPictureInPictureModeReachesTheState() {
        val state = SubshellState()
        lateinit var activity: ComponentActivity
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context).apply { release() }
        composeRule.setContent {
            activity = LocalContext.current as ComponentActivity
            ProvideVideoPlaybackCoordinator(state = state, coordinator = coordinator) {}
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { activity.onPictureInPictureModeChanged(true, Configuration()) }
        assertTrue(state.inPictureInPicture)
        composeRule.runOnIdle { activity.onPictureInPictureModeChanged(false, Configuration()) }
        assertFalse(state.inPictureInPicture)
    }

    @Test
    fun theSmallWindowShowsThePictureWithoutControls() {
        val state = SubshellState()
        state.playVideo(video.id)
        showFullscreen(state)
        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()

        composeRule.runOnIdle { state.updateInPictureInPicture(true) }
        frames()
        composeRule.onAllNodesWithContentDescription("Exit full screen").assertCountEquals(0)
    }
}
