package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackFailure
import app.subshell.prototype.media.VideoPlaybackPhase
import app.subshell.prototype.media.VideoPlaybackUiState
import app.subshell.prototype.media.VideoSourceFailure
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (issue11 + media-audio-r27 error branch): a video that failed shows
 * the canonical error notice built from the coordinator's typed failure — the
 * real HTTP status and its explanation — with a Retry button, and only on the
 * video that failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3VideoErrorNoticeComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_step3_error",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/step3_error",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    private fun forbidden(mediaId: String) = VideoPlaybackUiState(
        mediaId = mediaId,
        phase = VideoPlaybackPhase.ERROR,
        errorCode = HTTP_BAD_STATUS,
        failure = VideoPlaybackFailure(
            mediaId = mediaId,
            attemptId = 1L,
            sources = listOf(
                VideoSourceFailure(
                    mediaId = mediaId,
                    attemptId = 1L,
                    sourceIndex = 0,
                    media3ErrorCode = HTTP_BAD_STATUS,
                    httpStatusCode = 403,
                ),
            ),
        ),
    )

    private fun frames() = repeat(4) { composeRule.mainClock.advanceTimeByFrame() }

    /** A released coordinator: attach does nothing, only the published state reaches the frame. */
    private fun showWith(published: VideoPlaybackUiState) {
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        state.playVideo(video.id)
        val coordinator = testVideoPlaybackCoordinator(context).apply { release() }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(post = video, state = state, inPostDetail = true)
            }
        }
        frames()
        composeRule.runOnIdle {
            coordinator.overrideUiStateForTest(published)
            Snapshot.sendApplyNotifications()
        }
        frames()
        composeRule.waitForIdle()
        frames()
    }

    @Test
    fun aFailedVideoShowsItsHttpStatusExplanationAndRetry() {
        showWith(forbidden(video.id))

        composeRule.onAllNodes(hasText("HTTP 403", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Forbidden", substring = true)).assertCountEquals(1)
        composeRule.onAllNodesWithText("Access denied by server.").assertCountEquals(1)
        composeRule.onAllNodes(hasText("Retry") and hasClickAction()).assertCountEquals(1)
        // Not the media-code-only title or the old overlay wording.
        composeRule.onAllNodes(hasText("Media3", substring = true)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Retry video").assertCountEquals(0)
    }

    @Test
    fun anotherVideosFailureIsNotShownOnThisVideo() {
        showWith(forbidden("t3_some_other_video"))

        composeRule.onAllNodes(hasText("HTTP 403", substring = true)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Access denied by server.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
    }

    private companion object {
        // androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        const val HTTP_BAD_STATUS = 2004
    }
}
