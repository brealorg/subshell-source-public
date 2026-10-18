package app.subshell.prototype.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.window.layout.FoldingFeature
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.PICTURE_IN_PICTURE_PLAY_PAUSE_ACTION
import app.subshell.prototype.media.ProvideVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackPhase
import app.subshell.prototype.media.VideoPlaybackUiState
import app.subshell.prototype.media.pictureInPicturePlayPauseLabel
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import app.subshell.prototype.ui.components.tabletopControlsAlignment
import app.subshell.prototype.ui.screens.MediaViewerScreen
import app.subshell.prototype.ui.screens.fullscreenRotatesToLandscape
import app.subshell.prototype.ui.screens.tabletopFoldTopPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * VIDEO11 (#227, owner: everything left): VIDEO11_FULLSCREEN_VIDEO_SHARES_AND_DOWNLOADS_V1,
 * VIDEO11_ROTATE_FOR_WIDE_VIDEOS_V1, VIDEO11_PICTURE_IN_PICTURE_PLAY_PAUSE_V1,
 * VIDEO11_TECHNICAL_DETAILS_BEHIND_A_SETTING_V1, VIDEO11_TABLETOP_SPLITS_PICTURE_AND_CONTROLS_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video11FeaturesTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video11",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video11",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
        videoWidth = 1920,
        videoHeight = 1080,
    )

    private fun released(): VideoPlaybackCoordinator {
        ensureWorkManagerInitialized(context)
        return testVideoPlaybackCoordinator(context).apply { release() }
    }

    private fun frames(count: Int = 30) {
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun openPanel(technicalDetails: Boolean) {
        val state = SubshellState(initialVideoTechnicalDetails = technicalDetails)
        state.playVideo(video.id)
        val coordinator = released()
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
        composeRule.onNodeWithContentDescription("Playback options").performClick()
        frames()
    }

    @Test
    fun technicalDetailsAreHiddenByDefault() {
        openPanel(technicalDetails = false)
        composeRule.onAllNodesWithText("Technical details").assertCountEquals(0)
    }

    @Test
    fun technicalDetailsShowWithTheSetting() {
        openPanel(technicalDetails = true)
        composeRule.onNodeWithText("Technical details").assertExists()
    }

    @Test
    fun theFullScreenVideoOffersShareAndDownload() {
        val state = SubshellState()
        state.openMedia(video)
        val coordinator = released()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                MediaViewerScreen(post = video, state = state, onBack = {})
            }
        }
        frames()

        // VIDEO12: share and download are rows of the full-screen menu.
        composeRule.onNodeWithContentDescription("Playback options").performClick()
        frames()
        composeRule.onNodeWithText("Share").assertExists()
        composeRule.onNodeWithText("Download").assertExists()
    }

    @Test
    fun aLongPressInFullScreenBringsBackTheControls() {
        val state = SubshellState()
        state.playVideo(video.id)
        val coordinator = released().apply {
            overrideUiStateForTest(
                VideoPlaybackUiState(mediaId = video.id, phase = VideoPlaybackPhase.READY, isPlaying = true, playWhenReady = true),
            )
        }
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
        composeRule.mainClock.advanceTimeBy(4_000)
        frames()
        composeRule.onAllNodesWithContentDescription("Exit full screen").assertCountEquals(0)

        val frame = SemanticsMatcher("long press shows controls") { node ->
            node.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Show controls"
        }
        composeRule.onNode(frame).performSemanticsAction(SemanticsActions.OnLongClick)
        frames()
        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()
    }

    @Test
    fun aBusyShareKeepsTheControlsOnScreen() {
        val state = SubshellState()
        state.playVideo(video.id)
        val coordinator = released().apply {
            overrideUiStateForTest(
                VideoPlaybackUiState(mediaId = video.id, phase = VideoPlaybackPhase.READY, isPlaying = true, playWhenReady = true),
            )
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(
                    post = video,
                    state = state,
                    presentation = VideoPlayerPresentation.FULLSCREEN,
                    onExitFullscreen = {},
                    holdControls = true,
                )
            }
        }
        frames()
        composeRule.mainClock.advanceTimeBy(6_000)
        frames()
        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()
    }

    @Test
    fun aWideVideoTurnsToLandscapeOnlyWithTheSettingAndGivesItBack() {
        assertTrue(fullscreenRotatesToLandscape(enabled = true, width = 1920, height = 1080))
        assertFalse(fullscreenRotatesToLandscape(enabled = true, width = 1080, height = 1920))
        assertFalse(fullscreenRotatesToLandscape(enabled = false, width = 1920, height = 1080))
        assertFalse(fullscreenRotatesToLandscape(enabled = true, width = null, height = 1080))

        val state = SubshellState(initialVideoRotateFullscreen = true)
        state.openMedia(video)
        val coordinator = released()
        lateinit var activity: ComponentActivity
        var shown by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            activity = LocalContext.current as ComponentActivity
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                if (shown) MediaViewerScreen(post = video, state = state, onBack = {})
            }
        }
        frames()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)

        composeRule.runOnIdle { shown = false }
        frames()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
    }

    @Test
    fun theSmallWindowsPlayPauseIsTheReadersPause() {
        assertEquals("Pause", pictureInPicturePlayPauseLabel(playing = true))
        assertEquals("Play", pictureInPicturePlayPauseLabel(playing = false))

        val state = SubshellState(initialVideoPictureInPicture = true)
        state.openMedia(video)
        val coordinator = released()
        composeRule.setContent {
            ProvideVideoPlaybackCoordinator(state = state, coordinator = coordinator) {}
        }
        composeRule.waitForIdle()
        assertFalse(state.isVideoManuallyPaused(video.id))

        context.sendBroadcast(Intent(PICTURE_IN_PICTURE_PLAY_PAUSE_ACTION).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(state.isVideoManuallyPaused(video.id))

        context.sendBroadcast(Intent(PICTURE_IN_PICTURE_PLAY_PAUSE_ACTION).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(video.id, state.activeAnimatedMediaId)
    }

    @Test
    fun tabletopPutsThePictureAboveTheFoldAndTheControlsBelow() {
        assertEquals(1200, tabletopFoldTopPx(listOf(fold(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.HORIZONTAL))))
        assertNull(tabletopFoldTopPx(listOf(fold(FoldingFeature.State.FLAT, FoldingFeature.Orientation.HORIZONTAL))))
        assertNull(tabletopFoldTopPx(listOf(fold(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.VERTICAL))))
        assertNull(tabletopFoldTopPx(emptyList()))
        // Fold at the middle: the controls centre at three quarters down.
        assertEquals(0.5f, tabletopControlsAlignment(foldTopPx = 1000f, heightPx = 2000f).verticalBias, 0.0001f)

        val state = SubshellState()
        state.playVideo(video.id)
        state.stopVideo(video.id)
        val coordinator = released().apply {
            overrideUiStateForTest(
                VideoPlaybackUiState(mediaId = video.id, phase = VideoPlaybackPhase.READY, isPlaying = false, playWhenReady = false),
            )
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(
                    post = video,
                    state = state,
                    presentation = VideoPlayerPresentation.FULLSCREEN,
                    onExitFullscreen = {},
                    tabletopVideoHeight = 400.dp,
                )
            }
        }
        frames()
        val button = composeRule.onNodeWithContentDescription("Resume video", useUnmergedTree = true).fetchSemanticsNode()
        val foldPx = with(composeRule.density) { 400.dp.toPx() }
        assertTrue("the play button sits below the fold", button.boundsInRoot.top > foldPx)
    }

    private fun fold(state: FoldingFeature.State, orientation: FoldingFeature.Orientation) = object : FoldingFeature {
        override val bounds: Rect = Rect(0, 1200, 1080, 1210)
        override val isSeparating: Boolean = true
        override val occlusionType: FoldingFeature.OcclusionType = FoldingFeature.OcclusionType.NONE
        override val orientation: FoldingFeature.Orientation = orientation
        override val state: FoldingFeature.State = state
    }
}
