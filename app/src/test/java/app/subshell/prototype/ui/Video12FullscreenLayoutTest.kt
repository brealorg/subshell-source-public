package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackProgress
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoMediaActions
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** VIDEO12 (#227, owner from a screenshot): VIDEO12_FULLSCREEN_KEEPS_THE_TITLE_V1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video12FullscreenLayoutTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video12",
        title = "UA soldier dances on the Norway-Russia border",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video12",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun show(state: SubshellState) {
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context).apply {
            release()
            overrideProgressForTest(VideoPlaybackProgress(mediaId = video.id, positionMs = 12_000L, durationMs = 27_000L))
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(
                    post = video,
                    state = state,
                    presentation = VideoPlayerPresentation.FULLSCREEN,
                    onExitFullscreen = {},
                    mediaActions = VideoMediaActions(share = { true }, download = { null }),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun theTopKeepsBackTheTitleAndOneMenu() {
        val state = SubshellState(initialVideoSpeedControls = true)
        state.playVideo(video.id)
        show(state)

        composeRule.onNodeWithText(video.title).assertExists()
        composeRule.onNodeWithContentDescription("Exit full screen").assertExists()
        composeRule.onNodeWithContentDescription("Playback options").assertExists()
        // Share and download wait in the menu.
        composeRule.onAllNodesWithText("Share").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Share media").assertCountEquals(0)

        // Mute and speed sit by the time at the bottom.
        val root = composeRule.onNodeWithContentDescription("Exit full screen").fetchSemanticsNode().boundsInRoot
        val mute = composeRule.onNodeWithContentDescription("Turn sound on", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val speed = composeRule.onNodeWithContentDescription("Playback speed, 1×", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("mute is far below the top row", mute.top > root.bottom + 400f)
        assertTrue("speed is far below the top row", speed.top > root.bottom + 400f)
        composeRule.onNodeWithText("0:12 / 0:27").assertExists()
    }

    @Test
    fun theMenuStartsWithShareAndDownload() {
        val state = SubshellState()
        state.playVideo(video.id)
        show(state)

        composeRule.onNodeWithContentDescription("Playback options").performClick()
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }

        composeRule.onNodeWithText("Share").assertExists()
        composeRule.onNodeWithText("Download").assertExists()
        composeRule.onNodeWithText("Quality").assertExists()
    }
}
