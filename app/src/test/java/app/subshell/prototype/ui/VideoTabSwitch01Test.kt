package app.subshell.prototype.ui

import android.app.Application
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.FeedVideoPosterImageState
import app.subshell.prototype.media.FeedViewportState
import app.subshell.prototype.media.RemoteMediaLoadResult
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.MediaPerformanceTrace
import app.subshell.prototype.media.VIDEO_UNRANKED
import app.subshell.prototype.media.VideoMedia
import app.subshell.prototype.media.VideoMediaSource
import app.subshell.prototype.media.VideoMediaSourceKind
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.LocalVideoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VIDEOTABSWITCH01: switching tabs cross-fades the panes, so the Home feed is
 * still composed while it fades out. Its video kept playing (later frames were
 * drawn over the incoming tab) and its viewport took the next tab's route.
 * The feed of a tab that is left now pauses on the tap and claims nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class VideoTabSwitch01Test {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val traceLines = CopyOnWriteArrayList<String>()

    @After
    fun resetTrace() {
        MediaPerformanceTrace.resetTestSink()
    }

    private fun pausesOf(mediaId: String): Int = traceLines.count { line ->
        val tokens = line.split(' ')
        "event=feed_visibility_pause" in tokens && "media=$mediaId" in tokens
    }

    @Test
    fun aFeedInATabFadingOutPausesOnTheTapAndDoesNotTakeTheNextTab() {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        // After the coordinator: its init installs the debug log sink.
        MediaPerformanceTrace.installTestSink(clockMs = { 0L }, eventSink = traceLines::add)
        try {
            coordinator.attach(
                owner = Any(),
                media = videoMedia("a"),
                muted = true,
                transitionId = 1L,
                rank = VIDEO_UNRANKED,
                playWhenReady = true,
            )
            val state = SubshellState()
            state.activeAnimatedMediaId = "a"

            // The Home pane stays composed after the tap, as during the fade-out.
            composeRule.setContent {
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalMainPaneDestination provides MainDestination.HOME,
                ) {
                    GoogleFeedVideoViewport(state = state, sessionKey = "home") {}
                }
            }
            composeRule.waitForIdle()
            assertTrue("Home's feed holds playback", state.googleFeedOwnsPlayback)
            val pausesBefore = pausesOf("a")

            composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
            composeRule.waitForIdle()

            assertEquals("paused on the tap, not after the fade", pausesBefore + 1, pausesOf("a"))
            assertFalse("the player stopped", coordinator.player.playWhenReady)
            assertFalse("the leaving feed does not take Inbox", state.googleFeedOwnsPlayback)
        } finally {
            coordinator.releaseWithoutHanging()
        }
    }

    @Test
    fun aFeedOutsideTheMainTabsKeepsItsOldRule() {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        // After the coordinator: its init installs the debug log sink.
        MediaPerformanceTrace.installTestSink(clockMs = { 0L }, eventSink = traceLines::add)
        try {
            coordinator.attach(
                owner = Any(),
                media = videoMedia("a"),
                muted = true,
                transitionId = 1L,
                rank = VIDEO_UNRANKED,
                playWhenReady = true,
            )
            val state = SubshellState()
            state.activeAnimatedMediaId = "a"

            // A community feed (no main pane) follows the route as before.
            composeRule.setContent {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    GoogleFeedVideoViewport(state = state, sessionKey = "community") {}
                }
            }
            composeRule.waitForIdle()

            assertTrue("a feed outside the tabs holds playback as before", state.googleFeedOwnsPlayback)
        } finally {
            coordinator.releaseWithoutHanging()
        }
    }

    // A tab fading out keeps no live surface: the card shows its poster
    // (the video stays on a SurfaceView, Media3's choice from Android 7).
    @Test
    fun aFeedCardInATabFadingOutShowsItsPosterInsteadOfTheSurface() {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        val viewport = Any()
        val surface = Any()
        val post = SampleData.posts.first().copy(id = "a", kind = PostKind.VIDEO, videoWidth = 720, videoHeight = 1280)
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT).also {
            it.updateVideoFeedContext(listOf("a"), terminalVideoId = "a")
            it.registerGoogleFeedVideoViewport(viewport)
            it.updateFeedViewportState(FeedViewportState())
            it.updateGoogleFeedVideoVisibility(viewport, surface, "a", present = true)
            it.updateGoogleFeedVideoVisibility(viewport, surface, "a", qualified = true)
            it.stopVideo("a")
        }
        var root: View? = null
        try {
            composeRule.setContent {
                root = LocalView.current.rootView
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalMainPaneDestination provides MainDestination.HOME,
                ) {
                    LocalVideoPlayer(post = post, state = state, inPostDetail = false)
                }
            }
            composeRule.waitForIdle()
            assertTrue("the shown tab has its surface", requireNotNull(root).descendants().any { it is SurfaceView })

            composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
            composeRule.waitForIdle()

            assertFalse("no surface in the leaving tab", requireNotNull(root).descendants().any { it is SurfaceView })
            composeRule.onNodeWithContentDescription("Play video").assertExists()
        } finally {
            coordinator.releaseWithoutHanging()
        }
    }

    @Test
    fun aPosterStartsFromTheImageAlreadyInMemory() {
        assertEquals(
            RemoteMediaLoadResult.Ready("decoded"),
            FeedVideoPosterImageState<String>("a", "https://i.redd.it/a.jpg", initial = "decoded").result,
        )
        assertEquals(null, FeedVideoPosterImageState<String>("a", "https://i.redd.it/a.jpg").result)
    }

    private fun View.descendants(): List<View> =
        listOf(this) + ((this as? ViewGroup)?.let { group ->
            (0 until group.childCount).flatMap { group.getChildAt(it).descendants() }
        } ?: emptyList())

    private fun videoMedia(mediaId: String) = VideoMedia(
        mediaId = mediaId,
        sources = listOf(
            VideoMediaSource(
                uri = "content://media/picked/$mediaId",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = MimeTypes.VIDEO_MP4,
            ),
        ),
    )
}
