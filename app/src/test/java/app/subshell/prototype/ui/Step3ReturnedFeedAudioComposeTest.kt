package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.FeedViewportState
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.MediaPerformanceTrace
import app.subshell.prototype.media.VIDEO_UNRANKED
import app.subshell.prototype.media.VideoMedia
import app.subshell.prototype.media.VideoMediaSource
import app.subshell.prototype.media.VideoMediaSourceKind
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.PostKind
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (issue18): a feed the reader returns to never lets the
 * Activity-retained player go on playing a video it no longer owns, and a
 * queued feed start from an old route or visibility epoch applies nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ReturnedFeedAudioComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val viewport = Any()
    private val surface = Any()
    private val traceLines = CopyOnWriteArrayList<String>()

    @After
    fun resetTrace() {
        MediaPerformanceTrace.resetTestSink()
    }

    /** A feed whose video "a" is on screen and paused by the reader. */
    private fun feedWithPausedVideo(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    ).also {
        it.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        it.registerGoogleFeedVideoViewport(viewport)
        it.updateFeedViewportState(FeedViewportState())
        it.updateGoogleFeedVideoVisibility(viewport, surface, "a", present = true)
        it.updateGoogleFeedVideoVisibility(viewport, surface, "a", qualified = true)
        it.stopVideo("a")
    }

    private fun request(
        state: SubshellState,
        route: SubshellRoute = state.currentNavigationRoute,
        epoch: Int = state.googleFeedVisibilityEpoch,
    ): List<String> {
        val calls = mutableListOf<String>()
        state.applyCurrentGoogleFeedVideoRequest(
            mediaId = "a",
            requestRoute = route,
            requestVisibilityEpoch = epoch,
            onApply = { playWhenReady, _ -> calls += "apply:$playWhenReady" },
            onRevoked = { calls += "revoked" },
        )
        return calls
    }

    @Test
    fun aQueuedFeedStartFromAnOldRouteOrVisibilityEpochAppliesNothing() {
        val state = feedWithPausedVideo()
        val composedEpoch = state.googleFeedVisibilityEpoch

        // Written for this route and epoch, it applies (the reader's pause).
        assertEquals(listOf("apply:false"), request(state))

        // Another route asks for nothing.
        assertEquals(
            emptyList<String>(),
            request(state, route = SubshellRoute.User(MainDestination.HOME, "someone")),
        )

        // The app went to the background and came back: the request composed
        // before that belongs to the old visibility epoch.
        state.suspendVideoPlaybackForBackground()
        state.resumeVideoPlaybackAfterForeground()
        assertTrue(state.googleFeedVisibilityEpoch != composedEpoch)
        assertEquals(emptyList<String>(), request(state, epoch = composedEpoch))
    }

    @Test
    fun aVideoHandedToTheViewerIsNoLongerHandedOffOnceItIsGone() {
        val state = feedWithPausedVideo()
        val post = SampleData.posts.first().copy(id = "a", kind = PostKind.VIDEO)
        state.openMedia(post)
        assertTrue("the paused video is handed to the viewer", state.isVideoPresentationHandoff("a"))

        state.applyDeletedPost("a")

        assertFalse(state.isVideoPresentationHandoff("a"))
    }

    @Test
    fun aReturningFeedPausesThePlayerItDoesNotOwn() {
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context)
        // After the coordinator: its init installs the debug log sink.
        MediaPerformanceTrace.installTestSink(clockMs = { 0L }, eventSink = traceLines::add)
        try {
            // The Activity-retained player still holds "old" from the post the
            // reader just left; the feed has no animated media of its own.
            coordinator.attach(
                owner = Any(),
                media = videoMedia("old"),
                muted = true,
                transitionId = 1L,
                rank = VIDEO_UNRANKED,
                playWhenReady = true,
            )
            assertEquals("old", coordinator.uiState.mediaId)
            assertTrue(coordinator.player.playWhenReady)
            val state = SubshellState()
            assertEquals(null, state.activeAnimatedMediaId)

            composeRule.setContent {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    GoogleFeedVideoViewport(state = state, sessionKey = "returned") {}
                }
            }
            composeRule.waitForIdle()

            assertFalse("the orphaned player is paused", coordinator.player.playWhenReady)
            assertEquals(1, pausesOf("old"))

            // A pause asked for another video leaves the player alone.
            coordinator.pauseForFeedVisibility("other")
            assertEquals(0, pausesOf("other"))
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun aReturningFeedThatSelectsTheSameVideoKeepsItsPlayer() {
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context)
        // After the coordinator: its init installs the debug log sink.
        MediaPerformanceTrace.installTestSink(clockMs = { 0L }, eventSink = traceLines::add)
        try {
            coordinator.attach(
                owner = Any(),
                media = videoMedia("old"),
                muted = true,
                transitionId = 1L,
                rank = VIDEO_UNRANKED,
                playWhenReady = true,
            )
            val state = SubshellState()
            state.activeAnimatedMediaId = "old"

            composeRule.setContent {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    GoogleFeedVideoViewport(state = state, sessionKey = "returned") {}
                }
            }
            composeRule.waitForIdle()

            assertEquals(0, pausesOf("old"))
        } finally {
            coordinator.release()
        }
    }

    private fun pausesOf(mediaId: String): Int = traceLines.count { line ->
        val tokens = line.split(' ')
        "event=feed_visibility_pause" in tokens && "media=$mediaId" in tokens
    }

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
