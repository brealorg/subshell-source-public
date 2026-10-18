package app.subshell.prototype.ui

import android.app.Application
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VIDEO_UNRANKED
import app.subshell.prototype.media.VideoMedia
import app.subshell.prototype.media.VideoMediaSource
import app.subshell.prototype.media.VideoMediaSourceKind
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackPhase
import app.subshell.prototype.media.VideoPlaybackUiState
import app.subshell.prototype.media.VideoSystemPause
import app.subshell.prototype.media.videoPausedBySystem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import app.subshell.prototype.ui.components.videoKeepsScreenOn
import app.subshell.prototype.ui.components.videoSystemPauseStops
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
 * VIDEO01 (#227): VIDEO01_SYSTEM_PAUSE_REACHES_THE_SELECTION_V1,
 * VIDEO01_HEADPHONES_UNPLUGGED_PAUSES_AUDIBLE_VIDEO_V1,
 * VIDEO01_PLAYING_VIDEO_KEEPS_THE_SCREEN_ON_V1.
 *
 * Robolectric cannot decode video, so the coordinator is driven synchronously
 * (no looper runs between the calls) and the frame test uses a released
 * coordinator whose state only the test sets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video01PlatformHygieneTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_video01",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video01",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
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

    private fun VideoPlaybackCoordinator.attachForTest(owner: Any, media: VideoMedia, playWhenReady: Boolean) =
        attach(
            owner = owner,
            media = media,
            muted = false,
            transitionId = 1L,
            rank = VIDEO_UNRANKED,
            playWhenReady = playWhenReady,
        )

    @Test
    fun onlyHeadphonesAndAPermanentFocusLossCountAsTheSystemPausing() {
        assertTrue(videoPausedBySystem(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY))
        assertTrue(videoPausedBySystem(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS))
        assertFalse(videoPausedBySystem(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
        assertFalse(videoPausedBySystem(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM))
        assertFalse(videoPausedBySystem(true, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS))
    }

    @Test
    fun theCoordinatorReportsASystemPauseUntilPlayIsAskedForAgain() {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        val owner = Any()
        try {
            coordinator.attachForTest(owner, media("t3_a"), playWhenReady = true)

            coordinator.handlePlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            assertNull(coordinator.systemPause)

            coordinator.handlePlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
            assertEquals(VideoSystemPause("t3_a", 1L), coordinator.systemPause)

            // The surface records the reader's pause: the event stays, so a
            // surface composed later still agrees that the video is paused.
            coordinator.attachForTest(owner, media("t3_a"), playWhenReady = false)
            assertEquals(VideoSystemPause("t3_a", 1L), coordinator.systemPause)

            // Play again ends it.
            coordinator.attachForTest(owner, media("t3_a"), playWhenReady = true)
            assertNull(coordinator.systemPause)

            coordinator.handlePlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            assertEquals(VideoSystemPause("t3_a", 2L), coordinator.systemPause)

            // Another video ends it too.
            coordinator.attachForTest(owner, media("t3_b"), playWhenReady = false)
            assertNull(coordinator.systemPause)
        } finally {
            coordinator.releaseWithoutHanging()
        }
    }

    @Test
    fun onlyTheOwningSurfaceOfThatVideoTurnsASystemPauseIntoAPause() {
        val pause = VideoSystemPause("t3_a", 1L)
        assertTrue(videoSystemPauseStops(pause, "t3_a", ownsPresentation = true))
        assertFalse(videoSystemPauseStops(pause, "t3_a", ownsPresentation = false))
        assertFalse(videoSystemPauseStops(pause, "t3_b", ownsPresentation = true))
        assertFalse(videoSystemPauseStops(null, "t3_a", ownsPresentation = true))
    }

    @Test
    fun theScreenStaysOnForSoundOrFullscreenPlaybackOnly() {
        val ready = VideoPlaybackPhase.READY
        assertTrue(videoKeepsScreenOn(ready, playWhenReady = true, fullscreen = false, muted = false))
        assertTrue(videoKeepsScreenOn(ready, playWhenReady = true, fullscreen = true, muted = true))
        assertTrue(videoKeepsScreenOn(VideoPlaybackPhase.BUFFERING, playWhenReady = true, fullscreen = true, muted = true))
        // A muted feed clip may loop for ever; it does not hold the screen.
        assertFalse(videoKeepsScreenOn(ready, playWhenReady = true, fullscreen = false, muted = true))
        assertFalse(videoKeepsScreenOn(ready, playWhenReady = false, fullscreen = true, muted = false))
        assertFalse(videoKeepsScreenOn(VideoPlaybackPhase.ENDED, playWhenReady = true, fullscreen = true, muted = false))
        assertFalse(videoKeepsScreenOn(VideoPlaybackPhase.ERROR, playWhenReady = true, fullscreen = true, muted = false))
    }

    @Test
    fun theVideoFrameHoldsTheScreenOnlyWhileThePlayerPlays() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        state.playVideo(video.id)
        // Released: attach does nothing, so only the state set below reaches the frame.
        val coordinator = testVideoPlaybackCoordinator(context).apply { release() }
        lateinit var hostView: View
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(
                    post = video,
                    state = state,
                    presentation = VideoPlayerPresentation.FULLSCREEN,
                    onExitFullscreen = {},
                )
            }
        }
        fun frames() = repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        fun publish(playWhenReady: Boolean) {
            composeRule.runOnIdle {
                coordinator.overrideUiStateForTest(
                    VideoPlaybackUiState(
                        mediaId = video.id,
                        phase = VideoPlaybackPhase.READY,
                        isPlaying = playWhenReady,
                        playWhenReady = playWhenReady,
                    ),
                )
                Snapshot.sendApplyNotifications()
            }
            frames()
            composeRule.waitForIdle()
            frames()
        }
        frames()

        publish(playWhenReady = true)
        assertTrue(hostView.keepScreenOn)

        publish(playWhenReady = false)
        assertFalse(hostView.keepScreenOn)

        publish(playWhenReady = true)
        assertTrue(hostView.keepScreenOn)
    }
}
