package app.subshell.prototype.ui

import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackPhase
import app.subshell.prototype.media.VideoPlaybackUiState
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.FullscreenVideoGesture
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoKeyAction
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import app.subshell.prototype.ui.components.videoKeyAction
import app.subshell.prototype.ui.components.volumeGestureUnmutes
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

/**
 * VIDEO10 (#227 audit C6/C7/A1/A3/A4/B7a): VIDEO10_INTERACTION_SIDE_IS_PHYSICAL_IN_RTL_V1,
 * VIDEO10_USING_A_CONTROL_RESTARTS_AUTO_HIDE_V1, VIDEO10_TALKBACK_KEEPS_THE_CONTROLS_V1,
 * VIDEO10_FRAME_SEMANTICS_DO_NOT_SWALLOW_THE_CONTROLS_V1, VIDEO10_KEYBOARD_CONTROLS_THE_VIDEO_V1,
 * VIDEO10_VOLUME_GESTURE_UNMUTES_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video10AccessTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video10",
        title = "Video ten title",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video10",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private val frame = SemanticsMatcher("video frame") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label in setOf("Hide video controls", "Show video controls")
    }

    private fun showFullscreen(
        state: SubshellState,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        playingReady: Boolean = false,
    ): VideoPlaybackCoordinator {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context).apply { release() }
        if (playingReady) {
            coordinator.overrideUiStateForTest(
                VideoPlaybackUiState(
                    mediaId = video.id,
                    phase = VideoPlaybackPhase.READY,
                    isPlaying = true,
                    playWhenReady = true,
                ),
            )
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalLayoutDirection provides layoutDirection,
            ) {
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

    private fun frames(count: Int = 4) {
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun theChosenSideIsPhysicalInARightToLeftLayout() {
        assertTrue(physicalLeftIsRowStart(left = true, layoutDirection = LayoutDirection.Ltr))
        assertFalse(physicalLeftIsRowStart(left = true, layoutDirection = LayoutDirection.Rtl))
        assertTrue(physicalLeftIsRowStart(left = false, layoutDirection = LayoutDirection.Rtl))

        val state = SubshellState(initialInteractionSide = InteractionSide.LEFT)
        state.playVideo(video.id)
        showFullscreen(state, layoutDirection = LayoutDirection.Rtl)
        val mute = composeRule.onNodeWithContentDescription("Turn sound on", useUnmergedTree = true)
            .fetchSemanticsNode()
        val root = composeRule.onNode(frame).fetchSemanticsNode()
        assertTrue(
            "mute sits on the physical left in RTL",
            mute.boundsInRoot.center.x < root.boundsInRoot.center.x,
        )
    }

    @Test
    fun usingAControlRestartsTheAutoHideTime() {
        val state = SubshellState()
        state.playVideo(video.id)
        showFullscreen(state, playingReady = true)
        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()

        composeRule.mainClock.advanceTimeBy(2_000)
        frames()
        composeRule.onNodeWithContentDescription("Turn sound on").performClick()
        frames()
        composeRule.mainClock.advanceTimeBy(2_000)
        frames()
        // 4 s after showing, 2 s after the tap: still there.
        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()

        composeRule.mainClock.advanceTimeBy(1_000)
        frames()
        composeRule.onAllNodesWithContentDescription("Exit full screen").assertCountEquals(0)
    }

    @Test
    fun touchExplorationKeepsTheControlsOnScreen() {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setTouchExplorationEnabled(true)
        val state = SubshellState()
        state.playVideo(video.id)
        showFullscreen(state, playingReady = true)

        composeRule.mainClock.advanceTimeBy(6_000)
        frames()

        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()
    }

    @Test
    fun theFrameDoesNotSwallowTheTitleAndTimes() {
        val state = SubshellState()
        state.playVideo(video.id)
        showFullscreen(state)

        val frameNode = composeRule.onNode(frame).fetchSemanticsNode()
        assertNull("the frame's own node carries no text", frameNode.config.getOrNull(SemanticsProperties.Text))
    }

    @Test
    fun keysPauseSkipMuteAndLeave() {
        assertEquals(VideoKeyAction.PLAY_PAUSE, videoKeyAction(Key.Spacebar))
        assertEquals(VideoKeyAction.PLAY_PAUSE, videoKeyAction(Key.K))
        assertEquals(VideoKeyAction.BACK, videoKeyAction(Key.DirectionLeft))
        assertEquals(VideoKeyAction.FORWARD, videoKeyAction(Key.L))
        assertEquals(VideoKeyAction.MUTE, videoKeyAction(Key.M))
        assertEquals(VideoKeyAction.FULLSCREEN, videoKeyAction(Key.F))
        assertNull(videoKeyAction(Key.Q))

        val state = SubshellState()
        state.playVideo(video.id)
        val mutedBefore = state.feedVideoMuted
        showFullscreen(state)
        composeRule.onNode(frame).performSemanticsAction(SemanticsActions.RequestFocus)
        frames()

        composeRule.onNode(frame).performKeyInput { pressKey(Key.M) }
        frames()
        assertEquals(!mutedBefore, state.feedVideoMuted)

        composeRule.onNode(frame).performKeyInput { pressKey(Key.Spacebar) }
        frames()
        assertTrue(state.isVideoManuallyPaused(video.id))
    }

    @Test
    fun aVolumeSwipeOnAMutedVideoTurnsTheSoundOn() {
        assertTrue(volumeGestureUnmutes(FullscreenVideoGesture.VOLUME, muted = true))
        assertFalse(volumeGestureUnmutes(FullscreenVideoGesture.VOLUME, muted = false))
        assertFalse(volumeGestureUnmutes(FullscreenVideoGesture.BRIGHTNESS, muted = true))
        assertFalse(volumeGestureUnmutes(null, muted = true))
    }
}
