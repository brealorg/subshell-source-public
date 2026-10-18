package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.FeedViewportState
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.INLINE_VIDEO_SURFACE_TEST_TAG
import app.subshell.prototype.ui.components.LocalVideoPlayer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VIDEO13_FEED_SURFACE_STARTS_IN_THE_VIDEOS_SHAPE_V1 (owner, 0.80): a video whose
 * shape differs from its bounded feed frame gets a surface in its own shape,
 * centred, before the player knows the video size; a surface that started as the
 * whole frame and shrank later showed the picture stretched and cut off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video13FeedSurfaceShapeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun video(width: Int, height: Int): Post = SampleData.posts.first().copy(
        id = "a",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/video13",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
        videoWidth = width,
        videoHeight = height,
    )

    private val viewport = Any()
    private val surfaceA = Any()

    private fun feedState(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    ).also {
        it.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        it.registerGoogleFeedVideoViewport(viewport)
        it.updateFeedViewportState(FeedViewportState())
    }

    private fun surfaceIn(post: Post): Pair<Float, Float> {
        ensureWorkManagerInitialized(context)
        // Released: the player never learns a video size, as before the first frame.
        val coordinator = testVideoPlaybackCoordinator(context).apply { release() }
        // A feed card that is on screen and paused keeps its surface (the feed branch).
        val state = feedState().also {
            it.updateGoogleFeedVideoVisibility(viewport, surfaceA, post.id, present = true)
            it.updateGoogleFeedVideoVisibility(viewport, surfaceA, post.id, qualified = true)
            it.stopVideo(post.id)
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(post = post, state = state, inPostDetail = false)
            }
        }
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val surface = composeRule.onNodeWithTag(INLINE_VIDEO_SURFACE_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val width = (surface.right - surface.left).value
        val height = (surface.bottom - surface.top).value
        // Centred in the frame, which spans the window.
        assertEquals((root.right - surface.right).value, (surface.left - root.left).value, 1f)
        return width to height
    }

    @Test
    fun aTallVideoGetsATallSurfaceInsideItsFourByFiveFrame() {
        val (width, height) = surfaceIn(video(720, 1280))

        assertEquals(411f * 5f / 4f, height, 1f)
        assertEquals(height * 9f / 16f, width, 1f)
    }

    @Test
    fun aVeryWideVideoGetsAWideSurfaceInsideItsSixteenByNineFrame() {
        val (width, height) = surfaceIn(video(1920, 800))

        assertEquals(411f, width, 1f)
        assertEquals(411f * 800f / 1920f, height, 1f)
    }

    @Test
    fun aSixteenByNineVideoFillsItsFrame() {
        val (width, height) = surfaceIn(video(1920, 1080))

        assertEquals(411f, width, 1f)
        assertEquals(411f * 9f / 16f, height, 1f)
    }
}
