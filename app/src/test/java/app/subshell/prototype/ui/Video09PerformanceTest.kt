package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackProgress
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoPlayerCompositionProbe
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VIDEO09 (#227 audit H2/H3): VIDEO09_PROGRESS_REDRAWS_ONLY_THE_TIMELINE_V1,
 * VIDEO09_FULLSCREEN_POSITION_SAVED_ON_STOP_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video09PerformanceTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video09",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video09",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun progress(positionMs: Long) = VideoPlaybackProgress(
        mediaId = video.id,
        positionMs = positionMs,
        durationMs = 60_000L,
    )

    private fun frames(count: Int = 4) {
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun aMovingPositionRedrawsTheTimeTextButNotThePlayer() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        state.playVideo(video.id)
        // Released: only the progress the test reports reaches the player.
        val coordinator = testVideoPlaybackCoordinator(context).apply {
            release()
            overrideProgressForTest(progress(1_000L))
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
        composeRule.mainClock.advanceTimeBy(300)
        frames()
        composeRule.onNodeWithText("0:01 / 1:00").assertExists()
        val before = VideoPlayerCompositionProbe.compositions

        (2..5).forEach { second ->
            coordinator.overrideProgressForTest(progress(second * 1_000L))
            composeRule.mainClock.advanceTimeBy(260)
            frames()
        }

        composeRule.onNodeWithText("0:05 / 1:00").assertExists()
        assertEquals("the player did not recompose for the position", before, VideoPlayerCompositionProbe.compositions)
    }

    @Test
    fun fullScreenSavesThePositionWhenItLeavesNotOnEveryPoll() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        state.playVideo(video.id)
        val coordinator = testVideoPlaybackCoordinator(context).apply {
            release()
            overrideProgressForTest(progress(7_000L))
        }
        val saved = mutableListOf<Long>()
        var shown by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                if (shown) {
                    LocalVideoPlayer(
                        post = video,
                        state = state,
                        presentation = VideoPlayerPresentation.FULLSCREEN,
                        onExitFullscreen = {},
                        onPlaybackPositionChanged = { saved += it },
                    )
                }
            }
        }
        repeat(4) {
            composeRule.mainClock.advanceTimeBy(260)
            frames()
        }
        assertEquals(emptyList<Long>(), saved)

        composeRule.runOnIdle { shown = false }
        frames()
        assertEquals(listOf(7_000L), saved)
    }
}
